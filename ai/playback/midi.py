"""One monophonic channel, ordered RPN, expression and pitch bend."""
import math
import mido
import numpy as np
from .score import PPQN, require


def write_midi(performance, path):
    tempo = performance["tempo_us"]
    sec_tick = tempo / 1_000_000 / PPQN
    entries = []

    def add(t, priority, kind, **kwargs):
        entries.append((t, priority, len(entries), mido.Message(kind, channel=0, **kwargs)))

    for cc, v in [(101, 0), (100, 0), (6, 2), (38, 0), (101, 127), (100, 127)]:
        add(0, -10, "control_change", control=cc, value=v)
    for e in performance["events"]:
        a, b = e["start_tick"], e["end_tick"]
        add(a, 1, "control_change", control=68, value=127 if e["legato"] else 0)
        add(a, 3, "note_on", note=e["pitch"], velocity=e["velocity"])
        add(b, 0, "note_off", note=e["pitch"], velocity=0)
        for tick in sorted(set(range(a, b, max(1, round(.005/sec_tick)))) | {a, b}):
            q = (tick-a)/(b-a)
            cc = float(np.interp(q, [0, .45, 1], e["expression"]))
            cents = e["tuning_cents"]
            y = e["yoseong"]
            if y:
                t = (tick-a)*sec_tick
                gate = max(0, min(1, (t-y["delay_seconds"])/.15, (b-tick)*sec_tick/.15))
                wave = math.sin(2*math.pi*y["hz"]*max(0, t-y["delay_seconds"])) * gate
                if y["mode"] in ("amplitude", "mixed"):
                    cc *= 1-y["depth"] * (1-wave)/2 * gate
                if y["mode"] in ("pitch", "mixed"):
                    cents += y["cents"]*wave
            bend = round(cents/200*8192)
            require(-8192 <= bend <= 8191, "Pitch bend exceeds explicit +/-2 semitone range")
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
    notes, cc11, bends, rpn = [], [], [], []
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
            elif msg.control in (101, 100, 6, 38):
                require(clock == 0, "RPN changes after start are unsupported")
                rpn.append((msg.control, msg.value))
            else:
                raise ValueError(f"Unsupported controller {msg.control}")
        elif msg.type == "pitchwheel":
            bends.append((clock, msg.pitch*200/8192))
        elif msg.type == "note_on" and msg.velocity:
            require(active is None, "Polyphonic input unsupported")
            active = {"pitch": msg.note, "start": clock, "velocity": msg.velocity, "legato": legato}
        elif msg.type in ("note_off", "note_on"):
            require(active is not None and active["pitch"] == msg.note, "Unmatched note off")
            active["end"] = clock
            notes.append(active)
            active = None
        else:
            raise ValueError(f"Unsupported MIDI message {msg.type}")
    require(active is None and notes, "Incomplete/empty MIDI")
    require(rpn == [(101,0),(100,0),(6,2),(38,0),(101,127),(100,127)], "Expected ordered +/-2 RPN")
    return {"notes": notes, "cc11": cc11, "bends": bends, "seconds": clock}


def controls_at(messages, grid, default, linear=False):
    values = {0.: default}
    values.update(messages)
    times = np.array(sorted(values))
    data = np.array([values[t] for t in times])
    if linear:
        return np.interp(grid, times, data)
    return data[np.maximum(0, np.searchsorted(times, grid, side="right")-1)]
