"""채점 검사·비교에 쓰는 합성 음계."""

import numpy as np

from ai_service.pipelines.scoring import HWANG_HZ, Note

SR = 16000
BPM = 120
REF_HZ = HWANG_HZ
# 기준음에서의 반음 수로 적은 오르내리는 음계
SCALE = [Note(s, 1) for s in (0, 2, 5, 7, 9, 12, 9, 7, 5, 2, 0)]


def synth(
    notes,
    cents=0.0,
    shift=None,
    delay=None,
    drop=(),
    seconds=None,
    noise_db=None,
    vibrato=None,
    harmonics=(1.0, 0.5, 0.25),
    gap=0.0,
    gain=0.3,
    breath_db=None,
    click_db=None,
    seed=0,
    bpm=BPM,
):
    """음표를 이어 부는 합성음.

    음표에 start_beat가 있으면 그 박에서 시작하고 음 사이는 비운다(쉼). bpm: 1분에 부는 박 수.
    shift: 음표별 반음 변경. delay: 음표별 늦게 시작할 초(음수면 일찍). drop: 비운 음표.
    noise_db: 전체 잡음의 신호 대비 크기(dB). vibrato: 음표별 (폭 센트, 빠르기 Hz[, 중심 이동 센트]).
    harmonics: 기본음부터의 배음 크기. gap: 음표마다 끝을 비우는 초(끊어 불기).
    breath_db: 음표 시작마다 60ms 숨소리 잡음의 신호 대비 크기(dB).
    click_db: 박마다 들어가는 메트로놈 딸깍 소리의 신호 대비 크기(dB).
    """
    shift, delay, vibrato = shift or {}, delay or {}, vibrato or {}
    rng = np.random.default_rng(seed)
    beat = 60 / bpm
    lengths = np.array([n.beats * beat for n in notes])
    begins = np.concatenate([[0.0], np.cumsum(lengths)[:-1]])
    if notes[0].start_beat is not None:  # 쉼이 있는 악보. 첫 음을 0초로 둔다
        begins = np.array([(n.start_beat - notes[0].start_beat) * beat for n in notes])
    ends = begins + lengths
    t = np.arange(int(SR * (seconds if seconds is not None else ends[-1]))) / SR
    onsets = begins + [delay.get(i, 0.0) for i in range(len(notes))]
    k = np.clip(np.searchsorted(onsets, t, side="right") - 1, 0, None)

    semis = np.array([n.semitone + shift.get(i, 0) for i, n in enumerate(notes)], dtype=float)
    pitch = 100 * semis[k] + cents
    for i, (depth, rate, *center) in vibrato.items():
        pitch[k == i] += (center[0] if center else 0.0) + depth * np.sin(2 * np.pi * rate * (t[k == i] - onsets[i]))

    amp = np.ones_like(t)
    for i in range(len(notes)):
        if i in drop:
            amp[(t >= begins[i]) & (t < ends[i])] = 0.0
        elif gap and i + 1 < len(notes):  # 다음 음이 실제로 시작하기 직전을 비운다
            amp[(t >= onsets[i + 1] - gap) & (t < onsets[i + 1])] = 0.0
        if i + 1 < len(notes) and ends[i] < begins[i + 1] - 1e-9:  # 쉼(소수점 오차는 무시): 이 음이 끝나고 다음 음이 실제로 시작할 때까지
            amp[(t >= ends[i]) & (t < onsets[i + 1])] = 0.0
    amp = np.convolve(amp, np.ones(160) / 160, mode="same")  # 10ms 페이드로 끊김 잡음 제거

    phase = 2 * np.pi * np.cumsum(REF_HZ * 2 ** (pitch / 1200)) / SR
    audio = gain * amp * sum(h * np.sin((n + 1) * phase) for n, h in enumerate(harmonics))
    rms = np.sqrt(np.mean(audio**2)) or gain
    if breath_db is not None:
        burst = np.zeros_like(t)
        for i, onset in enumerate(onsets):
            if i not in drop:
                burst[(t >= onset) & (t < onset + 0.06)] = 1.0
        audio = audio + burst * rng.normal(0, rms / 10 ** (breath_db / 20), t.shape)
    if noise_db is not None:
        audio = audio + rng.normal(0, rms / 10 ** (noise_db / 20), t.shape)
    if click_db is not None:
        # 박마다 20ms 동안 빠르게 줄어드는 2kHz 딸깍 소리
        click_t = np.arange(int(SR * 0.02)) / SR
        click = np.sin(2 * np.pi * 2000 * click_t) * np.exp(-click_t / 0.004)
        click *= rms * 10 ** (click_db / 20) / np.sqrt(np.mean(click**2))
        for beat_s in np.arange(0, t[-1], beat):
            i = int(beat_s * SR)
            audio[i : i + click.size] += click[: audio.size - i]
    return audio
