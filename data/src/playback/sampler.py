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
from .midi import control_table, controls_at
from .score import PROFILES, require

FS = 44100
# Sample-rate control curves are built in slices: a 600s render must fit 1GB.
CONTROL_CHUNK = 1 << 20
NOTE_CHUNK = 1 << 16
CREDIT = "이 2차 저작물은 서울예술과학센터에서 개발한 국악 가상악기 음원 라이브러리를 사용하였습니다. (서울예술과학센터: catsnu.com, 문의: catantsnu@gmail.com)"

# Rendering choices, not an assertion about an acoustic player's breathing.
SUSTAIN_POLICY = {
    "name": "validated-sustain-crossfade-v2",
    "status": "synthetic-extension-not-performance-standard",
    "minimum_sample_seconds": 1.25, "skip_attack_seconds": .4,
    "minimum_period_seconds": .5, "crossfade_seconds": .05,
    "minimum_correlation": .98, "maximum_boundary_rms_ratio": 1.18,
    "maximum_sustain_rms_ratio": 1.5, "maximum_pitch_spread_cents": 35,
    "candidate_end_fractions": [.45, .6, .75, .85],
    "fallback_end_fractions": [step / 100 for step in range(10, 96, 2)],
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
    Preserve the original four-candidate result when it passes. Otherwise a
    bounded, finer grid can find an earlier steady passage in a changing tone.
    Both searches use the same quality limits; no sample substitution or tuning.
    """
    policy = SUSTAIN_POLICY
    if len(samples) < rate * policy["minimum_sample_seconds"]:
        return None
    for strategy, fractions in (("primary-fractions", policy["candidate_end_fractions"]),
                                ("fallback-grid", policy["fallback_end_fractions"])):
        candidates = _sustain_candidates(samples, rate, root, fractions)
        if candidates:
            return {**max(candidates, key=lambda item: item["correlation"]), "search": strategy}
    return None


def _sustain_candidates(samples, rate, root, fractions):
    """Apply identical phase, level and pitch checks to each candidate seam."""
    policy = SUSTAIN_POLICY
    width = round(rate * policy["crossfade_seconds"])
    minimum_start = round(rate * policy["skip_attack_seconds"])
    minimum_period = round(rate * policy["minimum_period_seconds"])
    candidates = []
    for fraction in fractions:
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
    return candidates


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


def control_slices(size, chunk):
    for start in range(0, size, chunk):
        stop = min(size, start+chunk)
        yield start, stop, np.arange(start, stop)/FS


def pitch_integral(bends, size, chunk=CONTROL_CHUNK):
    integral = np.empty(size+1)
    integral[0] = 0.
    for start, stop, grid in control_slices(size, chunk):
        steps = 2**(controls_at(bends, grid)/1200)
        # Same addition order as one cumsum, so results stay bit-identical.
        steps[0] += integral[start]
        integral[start+1:stop+1] = np.cumsum(steps)
    return integral


def apply_expression(output, expression, chunk=CONTROL_CHUNK):
    for start, stop, grid in control_slices(len(output), chunk):
        output[start:stop] *= controls_at(expression, grid)


class CatAssetError(ValueError):
    """The configured local bank cannot be decoded as a supported preset."""


class CatSampler:
    def __init__(self, archive, instrument, *, synthesis_extension=False):
        self.instrument = instrument
        self.synthesis_extension = synthesis_extension
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
        if not matches and getattr(self,'synthesis_extension',False):
            # The opt-in export policy selects only the original preset's
            # nearest sample and preserves the requested sounding pitch.
            require(self.zones, 'No source samples')
            return min(self.zones,key=lambda zone: (max(zone['low']-pitch,0,pitch-zone['high']),abs(zone['root']-pitch)))
        if not matches:
            extension = PROFILES[self.instrument].get("sample_zone_extensions")
            if extension and any(item["zone"]["low"] <= pitch <= item["zone"]["high"]
                                 for item in extension["zones"]):
                matches = [z for z in self._extended_zones(extension) if z["low"] <= pitch <= z["high"]]
                used = self.manifest["sample_zone_extensions"]["used_pitches"]
                if pitch not in used:
                    used.append(pitch)
                    used.sort()
        require(len(matches) == 1, f"{self.instrument}: pitch {pitch} has {len(matches)} matching sample zones")
        return matches[0]

    def _extended_zones(self, extension):
        """Use only the audited same-archive samples; keep original zones intact."""
        if hasattr(self, "_extension_zones"):
            return self._extension_zones
        target = PROFILES[self.instrument]["cat_provenance"]
        source = PROFILES[extension["source_instrument"]]
        origin = extension["source_preset"]
        require(self.manifest["sha256"] == target["archive_sha256"]
                and self.manifest["preset_xml_sha256"] == target["preset_xml_sha256"],
                "Sample zone extension requires the audited target archive and preset")
        require(origin["archive_sha256"] == source["cat_provenance"]["archive_sha256"]
                and origin["xml_sha256"] == source["cat_provenance"]["preset_xml_sha256"]
                and origin["member"].endswith("/" + source["preset_name"]),
                "Sample zone extension source preset provenance mismatch")
        zones = []
        for item in extension["zones"]:
            zone = dict(item["zone"])
            require(zone["low"] == zone["high"] == zone["root"] and zone["loop"] == 0,
                    "Sample zone extension must preserve one source root without a preset loop")
            require(not any(z["low"] <= zone["root"] <= z["high"] for z in self.zones + zones),
                    "Sample zone extension overlaps an existing zone")
            matches = [name for name in self.zip.namelist() if name.endswith("/" + zone["file"])
                       and not name.startswith("__MACOSX/")]
            require(len(matches) == 1, f"Missing/ambiguous extension sample {zone['file']}")
            zone.update(member=matches[0], origin="explicit-shared-bank-extension")
            samples, rate, digest = self.samples(zone)
            require(digest == item["sample_sha256"] and rate == item["sample_rate"]
                    and len(samples) == item["frames"], "Sample zone extension SHA/rate/trim mismatch")
            zones.append(zone)
        self._extension_zones = zones
        self.manifest["sample_zone_extensions"] = {
            "status": extension["status"], "basis": extension["basis"], "source_preset": dict(origin),
            "original_key_range": [min(z["low"] for z in self.zones), max(z["high"] for z in self.zones)],
            "effective_key_range": [min(z["low"] for z in self.zones + zones),
                                    max(z["high"] for z in self.zones + zones)],
            "zones": [{**z, "sample_sha256": item["sample_sha256"]}
                      for z, item in zip(zones, extension["zones"])], "used_pitches": [],
        }
        return zones

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
        integral = pitch_integral(control_table(midi["bends"], 0), size)
        output = np.zeros(size)
        trace, previous_cursor = [], 0.
        notes = midi["notes"]
        crossfades = [0.] * len(notes)
        for i in range(1, len(notes)):
            previous, note = notes[i - 1], notes[i]
            if note["legato"] and abs(previous["end"] - note["start"]) < 1e-6:
                fade = .02
                if (self.instrument in ("jeongak-daegeum", "sanjo-daegeum")
                        and previous["pitch"] != note["pitch"]):
                    # A fixed 20ms tail can mask a quieter short grace sample.
                    # Synthesis policy: share at most its first fifth between
                    # incoming attack and outgoing release, capped at 20ms.
                    fade = min(fade, (note["end"] - note["start"]) / 5)
                crossfades[i] = fade
        natural_stops = []
        for i, note in enumerate(notes):
            fade = crossfades[i + 1] if i + 1 < len(notes) else 0.
            release = fade or note.get("release_seconds", self.release)
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
            attack = min(end-at, round((crossfades[i] if connected else self.attack)*FS))
            stop = end+release
            require(0 <= at < end and stop <= size, "MIDI render bounds invalid")
            base_ratio = rate/FS * 2**((note["pitch"]-zone["root"]+zone["detune"]/100)/12)
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
                        loop = find_sustain_loop(samples, rate, zone["root"]) if self.instrument in ("danso", "sogeum", "jeongak-daegeum", "sanjo-daegeum") else None
                        cache[key] = ({**loop, "sample": zone["file"], "sample_sha256": digest,
                                       "policy": dict(SUSTAIN_POLICY)} if loop else None)
                    selected_loop = cache[key]
                    require(selected_loop is not None,
                            f"{self.instrument} pitch {note['pitch']}: sample exhausted; no validated stable sustain loop")
                    if hasattr(self, "manifest"):
                        self.manifest["sustain_extension"] = {"policy": dict(SUSTAIN_POLICY),
                                                             "loops": [loop for loop in cache.values() if loop]}
            # Slice sustained notes too: one permitted note can span 600s.
            # Absolute integral/envelope indices preserve phase and boundaries.
            attack_curve = np.linspace(0, 1, attack)
            release_curve = np.linspace(1, 0, release)
            for left in range(at, stop, NOTE_CHUNK):
                right = min(stop, left + NOTE_CHUNK)
                positions = cursor + base_ratio*(integral[left:right]-integral[at])
                if selected_loop is None:
                    require(positions[-1] < len(samples)-1,
                            f"{self.instrument} pitch {note['pitch']}: sample exhausted")
                    sound = np.interp(positions, np.arange(len(samples)), samples)
                else:
                    sound = looped_samples(samples, positions, selected_loop)
                attack_stop = min(right, at + attack)
                if left < attack_stop:
                    sound[:attack_stop-left] *= attack_curve[left-at:attack_stop-at]
                release_start = max(left, end)
                if release_start < right:
                    sound[release_start-left:] *= release_curve[release_start-end:right-end]
                sound *= self.gain*zone["volume"]*note["velocity"]/127
                output[left:right] += sound
            previous_cursor = cursor + base_ratio*(integral[end]-integral[at])
            trace.append({**note, "sample": zone["file"], "sample_sha256": digest,
                          "sample_zone_origin": zone.get("origin", "preset"),
                          "rendered_release_seconds": release / FS,
                          "root": zone["root"], "detune_cents": zone["detune"], "ratio": base_ratio,
                          "same_pitch_continuation": same, "sample_cursor": cursor,
                          "sample_last_position": float(positions[-1]), "sample_length": len(samples),
                          "sustain_loop": selected_loop})
        del integral
        # Hold MIDI CC values. Interpolating across a note boundary would apply
        # the next note's expression early, especially after a fade to silence.
        apply_expression(output, control_table(midi["cc11"], 1))
        require(np.isfinite(output).all() and np.max(np.abs(output)) > 0, "Silent/nonfinite render")
        return output, trace
