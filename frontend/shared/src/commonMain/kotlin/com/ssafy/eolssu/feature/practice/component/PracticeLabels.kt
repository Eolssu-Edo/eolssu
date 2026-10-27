package com.ssafy.eolssu.feature.practice.component

import androidx.compose.runtime.Composable
import com.ssafy.eolssu.feature.practice.TempoStep
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.practice_tempo_fast
import eolssu.shared.generated.resources.practice_tempo_fastest
import eolssu.shared.generated.resources.practice_tempo_normal
import eolssu.shared.generated.resources.practice_tempo_slow
import eolssu.shared.generated.resources.practice_tempo_slowest
import org.jetbrains.compose.resources.stringResource

@Composable
fun tempoLabel(step: TempoStep): String = stringResource(
    when (step) {
        TempoStep.Slowest -> Res.string.practice_tempo_slowest
        TempoStep.Slow -> Res.string.practice_tempo_slow
        TempoStep.Normal -> Res.string.practice_tempo_normal
        TempoStep.Fast -> Res.string.practice_tempo_fast
        TempoStep.Fastest -> Res.string.practice_tempo_fastest
    },
)
