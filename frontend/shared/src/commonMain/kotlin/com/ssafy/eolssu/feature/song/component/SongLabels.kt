package com.ssafy.eolssu.feature.song.component

import androidx.compose.runtime.Composable
import com.ssafy.eolssu.core.model.Jangdan
import com.ssafy.eolssu.core.model.SongLevel
import com.ssafy.eolssu.feature.song.SongLevelFilter
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.jangdan_label
import eolssu.shared.generated.resources.song_level_all
import eolssu.shared.generated.resources.song_level_elementary
import eolssu.shared.generated.resources.song_level_intermediate
import eolssu.shared.generated.resources.song_level_intro
import org.jetbrains.compose.resources.stringResource

@Composable
fun songLevelLabel(level: SongLevel): String = stringResource(
    when (level) {
        SongLevel.Intro -> Res.string.song_level_intro
        SongLevel.Elementary -> Res.string.song_level_elementary
        SongLevel.Intermediate -> Res.string.song_level_intermediate
    },
)

@Composable
fun songLevelFilterLabel(filter: SongLevelFilter): String =
    filter.level?.let { songLevelLabel(it) } ?: stringResource(Res.string.song_level_all)

/** '세마치장단'처럼 장단을 붙인다. '가곡 16박'처럼 박으로 끝나는 이름은 그대로 쓴다. */
@Composable
fun jangdanLabel(jangdan: Jangdan): String =
    if (jangdan.name.endsWith("박")) jangdan.name else stringResource(Res.string.jangdan_label, jangdan.name)
