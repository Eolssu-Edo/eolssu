"""Read CAT Live Sampler ZIPs as data. No plug-in or installer is executed."""
import gzip
import hashlib
import io
from pathlib import Path
import zipfile
import xml.etree.ElementTree as ET
import numpy as np
import soundfile as sf
from .midi import controls_at
from .score import PROFILES, require

FS = 44100
CREDIT = "이 2차 저작물은 서울예술과학센터에서 개발한 국악 가상악기 음원 라이브러리를 사용하였습니다. (서울예술과학센터: catsnu.com, 문의: catantsnu@gmail.com)"


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


class CatSampler:
    def __init__(self, archive, instrument):
        self.instrument = instrument
        self.archive = Path(archive)
        self.zip = zipfile.ZipFile(archive)
        self.cache = {}
        names = self.zip.namelist()
        wanted = PROFILES[instrument]["preset_name"]
        presets = [x for x in names if x.endswith("/"+wanted) and not x.startswith("__MACOSX/")]
        require(len(presets) == 1, f"Expected one {wanted}; pass the v1.3 installer ZIP, not the outer download")
        data = self.zip.read(presets[0])
        xml = gzip.decompress(data) if data.startswith(b"\x1f\x8b") else data
        x = ET.fromstring(xml).find("MultiSampler")
        require(x is not None, "Expected Ableton MultiSampler")

        def value(node, path):
            item = node.find(path)
            require(item is not None and item.get("Value") is not None, f"Missing preset field {path}")
            return float(item.get("Value"))

        self.zones = []
        for part in x.findall(".//MultiSamplePart"):
            require(value(part, "VelocityRange/Min") == 1 and value(part, "VelocityRange/Max") == 127,
                    "Velocity layers require a separate selector")
            require(value(part, "SelectorRange/Min") == 0 and value(part, "SelectorRange/Max") == 127,
                    "Articulation selector requires a separate renderer")
            require(value(part, "TuneScale") == 100, "Nonstandard key tracking unsupported")
            name = part.find("SampleRef/FileRef/Name").get("Value")
            matches = [p for p in names if p.endswith("/"+name) and not p.startswith("__MACOSX/")]
            require(len(matches) == 1, f"Missing/ambiguous sample {name}")
            z = {k: value(part, p) for k, p in {
                "low":"KeyRange/Min", "high":"KeyRange/Max", "root":"RootKey", "detune":"Detune",
                "volume":"Volume", "start":"SampleStart", "end":"SampleEnd", "loop":"SustainLoop/Mode"}.items()}
            require(z["loop"] == 0, "Looped presets require a separate renderer")
            z.update(file=name, member=matches[0])
            self.zones.append(z)
        self.attack = value(x, "VolumeAndPan/Envelope/AttackTime/Manual")/1000
        self.release = value(x, "VolumeAndPan/Envelope/ReleaseTime/Manual")/1000
        require(abs(self.release-.05) < 1e-6, "This version assumes a 50ms release for breaths")
        self.gain = 10**(value(x, "VolumeAndPan/Volume/Manual")/20)
        self.manifest = {"archive": str(self.archive), "sha256": sha(self.archive), "preset": presets[0],
                         "preset_xml_sha256": hashlib.sha256(xml).hexdigest(), "zones": self.zones,
                         "attack_seconds": self.attack, "release_seconds": self.release, "gain": self.gain}

    def close(self):
        self.zip.close()

    def zone(self, pitch):
        matches = [z for z in self.zones if z["low"] <= pitch <= z["high"]]
        require(len(matches) == 1, f"{self.instrument}: pitch {pitch} has {len(matches)} matching sample zones")
        return matches[0]

    def samples(self, zone):
        # Each sampler owns its cache. No cross-preset root/detune assumptions.
        key = zone["member"]
        if key not in self.cache:
            data = self.zip.read(key)
            raw, rate = sf.read(io.BytesIO(data), always_2d=True, dtype="float64")
            require(np.isfinite(raw).all(), "Nonfinite sample")
            self.cache[key] = (raw.mean(axis=1), rate, hashlib.sha256(data).hexdigest())
        raw, rate, digest = self.cache[key]
        a, b = int(zone["start"]), int(zone["end"])+1
        require(0 <= a < b <= len(raw), "Invalid preset trim")
        return raw[a:b], rate, digest

    def render(self, midi):
        """Render read-back MIDI, including channel-wide CC11 and PB.

        Mono mixdown, linear interpolation/envelopes and crossfades are authored
        approximations, not a reproduction of Ableton filters/FX/velocity curves.
        """
        size = round(midi["seconds"]*FS)
        grid = np.arange(size)/FS
        bends = controls_at(midi["bends"], grid, 0)
        # Hold MIDI CC values. Interpolating across a note boundary would apply
        # the next note's expression early, especially after a fade to silence.
        expression = controls_at(midi["cc11"], grid, 1)
        integral = np.concatenate(([0.], np.cumsum(2**(bends/1200))))
        output = np.zeros(size)
        trace, previous_cursor = [], 0.
        notes = midi["notes"]
        for i, note in enumerate(notes):
            zone = self.zone(note["pitch"])
            samples, rate, digest = self.samples(zone)
            at, end = round(note["start"]*FS), round(note["end"]*FS)
            connected = bool(i and note["legato"] and abs(notes[i-1]["end"]-note["start"]) < 1e-6)
            legato_out = bool(i+1 < len(notes) and notes[i+1]["legato"] and abs(note["end"]-notes[i+1]["start"]) < 1e-6)
            same = connected and notes[i-1]["pitch"] == note["pitch"]
            # Only the earlier Danso experiment tested a 100ms sustain offset.
            offset = .1 if connected and self.instrument == "danso" else 0
            cursor = previous_cursor if same else offset*rate
            release = round((.02 if legato_out else self.release)*FS)
            attack = min(end-at, round((.02 if connected else self.attack)*FS))
            stop = end+release
            require(0 <= at < end and stop <= size, "MIDI render bounds invalid")
            base_ratio = rate/FS * 2**((note["pitch"]-zone["root"]+zone["detune"]/100)/12)
            positions = cursor + base_ratio*(integral[at:stop]-integral[at])
            require(positions[-1] < len(samples)-1, f"{self.instrument} pitch {note['pitch']}: sample exhausted; shorten/split or provide a validated loop")
            sound = np.interp(positions, np.arange(len(samples)), samples)
            sound[:attack] *= np.linspace(0, 1, attack)
            sound[end-at:] *= np.linspace(1, 0, release)
            sound *= self.gain*zone["volume"]*note["velocity"]/127
            output[at:stop] += sound
            previous_cursor = cursor + base_ratio*(integral[end]-integral[at])
            trace.append({**note, "sample": zone["file"], "sample_sha256": digest,
                          "root": zone["root"], "detune_cents": zone["detune"], "ratio": base_ratio,
                          "same_pitch_continuation": same, "sample_cursor": cursor,
                          "sample_last_position": float(positions[-1]), "sample_length": len(samples)})
        output *= expression
        require(np.isfinite(output).all() and np.max(np.abs(output)) > 0, "Silent/nonfinite render")
        return output, trace
