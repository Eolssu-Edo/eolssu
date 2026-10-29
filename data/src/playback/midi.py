"""One monophonic channel, ordered RPN, expression and pitch bend."""
import math
import mido
import numpy as np
from .score import PPQN, require
from .expressions import PLAYBACK_VIBRATO

RELEASE_CC = {"controller": 72, "maximum_seconds": .05, "default_value": 127}


def automatic_vibrato(event, sec_tick, instrument):
    y = event["yoseong"]
    return (instrument == "danso" and
            (event["end_tick"] - event["start_tick"]) * sec_tick >= PLAYBACK_VIBRATO["minimum_note_seconds"]
            and not (y and y["mode"] in ("pitch", "mixed")))


def pitch_bend_range(performance, sec_tick):
    """Reserve enough RPN range for all simultaneous authored pitch controls."""
    maximum = 0
    for e in performance["events"]:
        y, curve = e["yoseong"], e.get("pitch_curve")
        motion = y["cents"] if y and y["mode"] in ("pitch", "mixed") else 0
        if automatic_vibrato(e, sec_tick, performance["instrument"]):
            motion += PLAYBACK_VIBRATO["cents"]
        values = [0]
        if curve:
            values += [p[1] for p in curve["points"]] if "points" in curve else [curve["end_cents"]]
        maximum = max(maximum, abs(e["tuning_cents"] + min(values) - motion),
                      abs(e["tuning_cents"] + max(values) + motion))
    semitones = max(2, math.ceil(maximum / 100))
    require(semitones <= 12, "Combined pitch controls exceed the supported +/-12 semitone range")
    return semitones


def write_midi(performance, path):
    tempo = performance["tempo_us"]
    sec_tick = tempo / 1_000_000 / PPQN
    entries = []
    bend_range = pitch_bend_range(performance, sec_tick)

    def add(t, priority, kind, **kwargs):
        entries.append((t, priority, len(entries), mido.Message(kind, channel=0, **kwargs)))

    for cc, v in [(101, 0), (100, 0), (6, bend_range), (38, 0), (101, 127), (100, 127)]:
        add(0, -10, "control_change", control=cc, value=v)
    release_value = 127
    for e in performance["events"]:
        a, b = e["start_tick"], e["end_tick"]
        value = round(e.get("release_seconds", .05) / .05 * 127)
        if value != release_value:
            add(a, 1, "control_change", control=72, value=value)
            release_value = value
        y = e["yoseong"]
        vibrato = PLAYBACK_VIBRATO if automatic_vibrato(e, sec_tick, performance["instrument"]) else None
        add(a, 1, "control_change", control=68, value=127 if e["legato"] else 0)
        add(a, 3, "note_on", note=e["pitch"], velocity=e["velocity"])
        add(b, 0, "note_off", note=e["pitch"], velocity=0)
        controls = set(range(a, b, max(1, round(.005/sec_tick)))) | {a, b}
        curve = e.get("pitch_curve")
        step_tick = None
        if curve and curve.get("step_at_delay"):
            step_tick = a + round(curve["delay_seconds"] / sec_tick)
            require(a < step_tick < b, "pitch curve step delay must resolve inside the note")
            controls.update((step_tick - 1, step_tick))
        for tick in sorted(controls):
            q = (tick-a)/(b-a)
            cc = float(np.interp(q, [0, .45, 1], e["expression"]))
            cents = e["tuning_cents"]
            if y:
                t = (tick-a)*sec_tick
                gate = max(0, min(1, (t-y["delay_seconds"])/.15, (b-tick)*sec_tick/.15))
                wave = math.sin(2*math.pi*y["hz"]*max(0, t-y["delay_seconds"])) * gate
                if y["mode"] in ("amplitude", "mixed"):
                    cc *= 1-y["depth"] * (1-wave)/2 * gate
                if y["mode"] in ("pitch", "mixed"):
                    cents += y["cents"]*wave
            if vibrato:
                elapsed = (tick-a)*sec_tick - vibrato["delay_seconds"]
                attack = max(0, min(1, elapsed/vibrato["fade_in_seconds"]))
                release = min(1, (b-tick)*sec_tick/vibrato["fade_out_seconds"])
                cents += vibrato["cents"] * attack * release * math.sin(2*math.pi*vibrato["hz"]*elapsed)
            curve = e.get("pitch_curve")
            if curve:
                length = (b-a)*sec_tick
                delay = curve.get("delay_seconds", 0)
                if "points" in curve:
                    elapsed = (tick - step_tick) * sec_tick if step_tick is not None else (tick-a)*sec_tick - delay
                    if elapsed >= 0:
                        span = (b - step_tick) * sec_tick if step_tick is not None else length - delay
                        progress = min(1, elapsed/span)
                        cents += float(np.interp(progress, [p[0] for p in curve["points"]],
                                                [p[1] for p in curve["points"]]))
                else:
                    tail = min(length*curve["tail_fraction"], curve["maximum_seconds"], length-delay)
                    progress = max(0, min(1, ((tick-a)*sec_tick-(length-tail))/tail))
                    cents += curve["end_cents"]*progress
            require(abs(cents) <= bend_range*100 + 1e-8, "Pitch bend exceeds declared RPN range")
            # MIDI's positive endpoint has one fewer step than its negative one.
            bend = min(8191, round(cents/(bend_range*100)*8192))
            add(tick, 2, "pitchwheel", pitch=bend)
            add(tick, 2, "control_change", control=11, value=round(cc))
    reset = performance["end_tick"] + round(.05/sec_tick)
    for cc, v in [(68, 0), (11, 127)]:
        add(reset, 2, "control_change", control=cc, value=v)
    add(reset, 2, "pitchwheel", pitch=0)
    wire = mido.MidiFile(type=0, ticks_per_beat=PPQN)
    track = mido.MidiTrack()
    wire.tracks.append(track)
    track.append(mido.MetaMessage("set_tempo", tempo=tempo))
    track.append(mido.MetaMessage("track_name", name=performance["instrument"]))
    last = 0
    for tick, _, _, message in sorted(entries):
        track.append(message.copy(time=tick-last))
        last = tick
    track.append(mido.MetaMessage("end_of_track", time=round(.5/sec_tick)))
    wire.save(path)


def read_midi(path):
    """Read our emitted subset; reject ignored controls or polyphony."""
    wire = mido.MidiFile(path)
    tempo, clock, active, legato = 500000, 0., None, False
    release_seconds = .05
    notes, cc11, bends, rpn = [], [], [], []
    bend_range_cents = None
    for msg in mido.merge_tracks(wire.tracks):
        clock += mido.tick2second(msg.time, wire.ticks_per_beat, tempo)
        if msg.is_meta:
            if msg.type == "set_tempo":
                tempo = msg.tempo
            continue
        require(msg.channel == 0, "This pipeline renders one mono channel per instrument")
        if msg.type == "control_change":
            if msg.control == 11:
                cc11.append((clock, msg.value/127))
            elif msg.control == 68:
                legato = msg.value >= 64
            elif msg.control == 72:
                release_seconds = msg.value / 127 * .05
            elif msg.control in (101, 100, 6, 38):
                require(clock == 0, "RPN changes after start are unsupported")
                expected = [(101, 0), (100, 0), (6, None), (38, None), (101, 127), (100, 127)]
                require(len(rpn) < len(expected), "Expected one ordered pitch bend RPN")
                control, value = expected[len(rpn)]
                require(msg.control == control and (value is None or msg.value == value),
                        "Expected ordered pitch bend RPN")
                if msg.control == 6:
                    require(2 <= msg.value <= 12, "RPN semitone range must be 2..12")
                if msg.control == 38:
                    require(msg.value <= 99 and rpn[2][1]*100 + msg.value <= 1200,
                            "RPN cents range must be 0..99 with total <=1200")
                rpn.append((msg.control, msg.value))
                if len(rpn) == 6:
                    bend_range_cents = rpn[2][1]*100 + rpn[3][1]
            else:
                raise ValueError(f"Unsupported controller {msg.control}")
        elif msg.type == "pitchwheel":
            require(bend_range_cents is not None, "Pitch bend before complete ordered RPN")
            bends.append((clock, msg.pitch*bend_range_cents/8192))
        elif msg.type == "note_on" and msg.velocity:
            require(bend_range_cents is not None, "Note before complete ordered RPN")
            require(active is None, "Polyphonic input unsupported")
            active = {"pitch": msg.note, "start": clock, "velocity": msg.velocity, "legato": legato,
                      "release_seconds": release_seconds}
        elif msg.type in ("note_off", "note_on"):
            require(active is not None and active["pitch"] == msg.note, "Unmatched note off")
            active["end"] = clock
            notes.append(active)
            active = None
        else:
            raise ValueError(f"Unsupported MIDI message {msg.type}")
    require(active is None and notes, "Incomplete/empty MIDI")
    require(len(rpn) == 6, "Expected complete ordered pitch bend RPN")
    return {"notes": notes, "cc11": cc11, "bends": bends, "seconds": clock,
            "pitch_bend_range_cents": bend_range_cents}


def controls_at(messages, grid, default, linear=False):
    values = {0.: default}
    values.update(messages)
    times = np.array(sorted(values))
    data = np.array([values[t] for t in times])
    if linear:
        return np.interp(grid, times, data)
    return data[np.maximum(0, np.searchsorted(times, grid, side="right")-1)]
