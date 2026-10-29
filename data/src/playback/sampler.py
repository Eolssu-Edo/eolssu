"""Read CAT Live Sampler ZIPs as data. No plug-in or installer is executed."""
import gzip
import hashlib
import io
from pathlib import Path
import zipfile
import zlib
import xml.etree.ElementTree as ET
import numpy as np
import soundfile as sf
from .midi import controls_at
from .score import PROFILES, require

FS = 44100
CREDIT = "이 2차 저작물은 서울예술과학센터에서 개발한 국악 가상악기 음원 라이브러리를 사용하였습니다. (서울예술과학센터: catsnu.com, 문의: catantsnu@gmail.com)"

# Rendering choices, not an assertion about an acoustic player's breathing.
SUSTAIN_POLICY = {
    "name": "validated-sustain-crossfade-v1",
    "status": "synthetic-extension-not-performance-standard",
    "minimum_sample_seconds": 1.25, "skip_attack_seconds": .4,
    "minimum_period_seconds": .5, "crossfade_seconds": .05,
    "minimum_correlation": .98, "maximum_boundary_rms_ratio": 1.18,
    "maximum_sustain_rms_ratio": 1.5, "maximum_pitch_spread_cents": 35,
    "candidate_end_fractions": [.45, .6, .75, .85],
}


def sustain_pitch_spread(samples, rate, root):
    """Bound pitch motion inside a candidate; never retune the sample itself."""
    length = round(.1 * rate)
    expected = 440 * 2 ** ((root - 69) / 12)
    low = max(1, int(expected * 2 ** (-1 / 12) * length / rate))
    high = min(length // 2 - 1, int(expected * 2 ** (1 / 12) * length / rate) + 1)
    if high <= low:
        return float("inf")
    pitches = []
    for start in np.linspace(0, len(samples) - length, 12).astype(int):
        chunk = samples[start:start + length]
        magnitude = np.abs(np.fft.rfft((chunk - chunk.mean()) * np.hanning(length)))
        peak = int(np.argmax(magnitude[low:high + 1])) + low
        if peak in (low, high) or magnitude[peak] <= 1e-12:
            return float("inf")
        a, b, c = np.log(np.maximum(magnitude[peak - 1:peak + 2], 1e-30))
        denominator = a - 2 * b + c
        shift = .5 * (a - c) / denominator if denominator else 0
        pitches.append((peak + shift) * rate / length)
    return float(1200 * np.log2(max(pitches) / min(pitches)))


def find_sustain_loop(samples, rate, root):
    """Select matching, steady sustain windows; reject noise and short samples.

    A cosine crossfade joins the end window to its correlated start window.
    FFT correlation keeps the bounded four-candidate search practical without
    a new dependency. Thresholds are conservative audition policy choices.
    """
    policy = SUSTAIN_POLICY
    if len(samples) < rate * policy["minimum_sample_seconds"]:
        return None
    width = round(rate * policy["crossfade_seconds"])
    minimum_start = round(rate * policy["skip_attack_seconds"])
    minimum_period = round(rate * policy["minimum_period_seconds"])
    candidates = []
    for fraction in policy["candidate_end_fractions"]:
        seam = round(len(samples) * fraction)
        last_start = seam - minimum_period
        if last_start <= minimum_start or seam + width >= len(samples):
            continue
        target = samples[seam:seam + width]
        source = samples[:last_start + width]
        target_power = float(np.dot(target, target))
        target_energy = float(np.dot(target - target.mean(), target - target.mean()))
        if target_power <= 1e-16 or target_energy <= 1e-16:
            continue
        fft_size = 1 << (len(source) + width - 2).bit_length()
        dots = np.fft.irfft(np.fft.rfft(source, fft_size)
                           * np.fft.rfft(target[::-1], fft_size), fft_size)[width - 1:len(source)]
        sums = np.r_[0., np.cumsum(source)]
        powers = np.r_[0., np.cumsum(source * source)]
        window_sums = sums[width:] - sums[:-width]
        window_powers = powers[width:] - powers[:-width]
        energy = np.maximum(0, window_powers - window_sums ** 2 / width)
        correlation = np.clip((dots - window_sums * target.mean())
                              / np.maximum(1e-30, np.sqrt(energy * target_energy)), -1, 1)
        ratio = np.sqrt(np.maximum(0, window_powers) / target_power)
        limit = policy["maximum_boundary_rms_ratio"]
        correlation[:minimum_start] = -1
        correlation[(ratio < 1 / limit) | (ratio > limit)] = -1
        start = int(np.argmax(correlation))
        if correlation[start] < policy["minimum_correlation"]:
            continue
        sustain = samples[start:seam + width]
        levels = [float(np.sqrt(np.mean(chunk * chunk))) for chunk in np.array_split(sustain, 12)]
        if min(levels) <= 1e-10:
            continue
        level_ratio = max(levels) / min(levels)
        spread = sustain_pitch_spread(sustain, rate, root)
        if level_ratio > policy["maximum_sustain_rms_ratio"] or spread > policy["maximum_pitch_spread_cents"]:
            continue
        candidates.append({"start_frame": start, "end_frame": seam + width,
                           "crossfade_start_frame": seam, "crossfade_frames": width,
                           "period_frames": seam - start, "source_sample_rate": rate,
                           "correlation": float(correlation[start]), "boundary_rms_ratio": float(ratio[start]),
                           "sustain_rms_ratio": level_ratio, "pitch_spread_cents": spread})
    return max(candidates, key=lambda item: item["correlation"]) if candidates else None


def looped_samples(samples, positions, loop):
    """Read a virtual sustain, with continuous phase across repeated reads."""
    seam, width = loop["crossfade_start_frame"], loop["crossfade_frames"]
    repeated = positions >= seam
    phase = np.remainder(positions[repeated] - seam, loop["period_frames"])
    mapped = positions.copy()
    mapped[repeated] = loop["start_frame"] + phase
    sound = np.interp(mapped, np.arange(len(samples)), samples)
    fading = phase < width - 1
    if np.any(fading):
        indices = np.flatnonzero(repeated)[fading]
        weight = .5 - .5 * np.cos(np.pi * phase[fading] / (width - 1))
        tail = np.interp(seam + phase[fading], np.arange(len(samples)), samples)
        sound[indices] = tail * (1 - weight) + sound[indices] * weight
    return sound


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


class CatAssetError(ValueError):
    """The configured local bank cannot be decoded as a supported preset."""


class CatSampler:
    def __init__(self, archive, instrument):
        self.instrument = instrument
        self.archive = Path(archive)
        self.zip = zipfile.ZipFile(archive)
        self.cache = {}
        try:
            self._read_preset()
        except (ValueError, OSError, KeyError, EOFError, zlib.error, ET.ParseError) as exc:
            self.zip.close()
            raise CatAssetError(str(exc)) from exc
        except Exception:
            self.zip.close()
            raise

    def _read_preset(self):
        names = self.zip.namelist()
        wanted = PROFILES[self.instrument]["preset_name"]
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
            sample_name = part.find("SampleRef/FileRef/Name")
            require(sample_name is not None and sample_name.get("Value"), "Missing preset sample filename")
            name = sample_name.get("Value")
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
        natural_stops = []
        for i, note in enumerate(notes):
            connected_next = bool(i + 1 < len(notes) and notes[i + 1]["legato"]
                                  and abs(note["end"] - notes[i + 1]["start"]) < 1e-6)
            release = .02 if connected_next else note.get("release_seconds", self.release)
            natural_stops.append(round(note["end"] * FS) + round(release * FS))
        # Only protect an actual gap, retaining existing crossfades where the
        # next note already sounds. Include every tail spanning that gap.
        silence_bounds = [stop if i + 1 == len(notes) or stop < round(notes[i + 1]["start"] * FS)
                          else size for i, stop in enumerate(natural_stops)]
        stops = np.minimum(natural_stops, np.minimum.accumulate(np.array(silence_bounds)[::-1])[::-1])
        selected_loop = None
        for i, note in enumerate(notes):
            zone = self.zone(note["pitch"])
            samples, rate, digest = self.samples(zone)
            at, end = round(note["start"]*FS), round(note["end"]*FS)
            connected = bool(i and note["legato"] and abs(notes[i-1]["end"]-note["start"]) < 1e-6)
            same = connected and notes[i-1]["pitch"] == note["pitch"]
            # Only the earlier Danso experiment tested a 100ms sustain offset.
            offset = .1 if connected and self.instrument == "danso" else 0
            cursor = previous_cursor if same else offset*rate
            release = int(stops[i]) - end
            attack = min(end-at, round((.02 if connected else self.attack)*FS))
            stop = end+release
            require(0 <= at < end and stop <= size, "MIDI render bounds invalid")
            base_ratio = rate/FS * 2**((note["pitch"]-zone["root"]+zone["detune"]/100)/12)
            positions = cursor + base_ratio*(integral[at:stop]-integral[at])
            if not same:
                # A tied/legato same-pitch run must decide together, including
                # release tails, so splitting one long sound cannot reset phase
                # or turn looping on only after a previous note has played.
                last = i
                while (last + 1 < len(notes) and notes[last + 1]["legato"]
                       and notes[last + 1]["pitch"] == note["pitch"]
                       and abs(notes[last]["end"] - notes[last + 1]["start"]) < 1e-6):
                    last += 1
                extent = int(max(stops[i:last + 1]))
                needed = cursor + base_ratio * (integral[extent - 1] - integral[at])
                selected_loop = None
                if needed >= len(samples) - 1:
                    cache = getattr(self, "_sustain_loops", {})
                    self._sustain_loops = cache
                    key = (digest, rate, zone["root"], zone.get("start", 0), zone.get("end", len(samples) - 1))
                    if key not in cache:
                        loop = find_sustain_loop(samples, rate, zone["root"]) if self.instrument in ("danso", "sogeum", "jeongak-daegeum") else None
                        cache[key] = ({**loop, "sample": zone["file"], "sample_sha256": digest,
                                       "policy": dict(SUSTAIN_POLICY)} if loop else None)
                    selected_loop = cache[key]
                    require(selected_loop is not None,
                            f"{self.instrument} pitch {note['pitch']}: sample exhausted; no validated stable sustain loop")
                    if hasattr(self, "manifest"):
                        self.manifest["sustain_extension"] = {"policy": dict(SUSTAIN_POLICY),
                                                             "loops": [loop for loop in cache.values() if loop]}
            if selected_loop is None:
                require(positions[-1] < len(samples)-1,
                        f"{self.instrument} pitch {note['pitch']}: sample exhausted")
                sound = np.interp(positions, np.arange(len(samples)), samples)
            else:
                sound = looped_samples(samples, positions, selected_loop)
            sound[:attack] *= np.linspace(0, 1, attack)
            sound[end-at:] *= np.linspace(1, 0, release)
            sound *= self.gain*zone["volume"]*note["velocity"]/127
            output[at:stop] += sound
            previous_cursor = cursor + base_ratio*(integral[end]-integral[at])
            trace.append({**note, "sample": zone["file"], "sample_sha256": digest,
                          "rendered_release_seconds": release / FS,
                          "root": zone["root"], "detune_cents": zone["detune"], "ratio": base_ratio,
                          "same_pitch_continuation": same, "sample_cursor": cursor,
                          "sample_last_position": float(positions[-1]), "sample_length": len(samples),
                          "sustain_loop": selected_loop})
        output *= expression
        require(np.isfinite(output).all() and np.max(np.abs(output)) > 0, "Silent/nonfinite render")
        return output, trace
