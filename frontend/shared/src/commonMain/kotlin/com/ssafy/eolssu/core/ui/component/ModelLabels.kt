package com.ssafy.eolssu.core.ui.component

import androidx.compose.runtime.Composable
import com.ssafy.eolssu.core.model.Jangdan
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.SongLevel
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.date_month_day
import eolssu.shared.generated.resources.jangdan_label
import eolssu.shared.generated.resources.song_level_elementary
import eolssu.shared.generated.resources.song_level_intermediate
import eolssu.shared.generated.resources.song_level_intro
import eolssu.shared.generated.resources.time_am
import eolssu.shared.generated.resources.time_pm
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** 곡 목록·상세·연습·결과가 함께 쓰는 난이도·장단 이름. */
@Composable
fun songLevelLabel(level: SongLevel): String = stringResource(
    when (level) {
        SongLevel.Intro -> Res.string.song_level_intro
        SongLevel.Elementary -> Res.string.song_level_elementary
        SongLevel.Intermediate -> Res.string.song_level_intermediate
    },
)

/** '세마치장단'처럼 장단을 붙인다. '가곡 16박'처럼 박으로 끝나는 이름은 그대로 쓴다. */
@Composable
fun jangdanLabel(jangdan: Jangdan): String =
    if (jangdan.name.endsWith("박")) jangdan.name else stringResource(Res.string.jangdan_label, jangdan.name)

/** 초를 '분:초'로(0:48). */
fun formatClock(seconds: Double): String {
    val total = seconds.coerceAtLeast(0.0).roundToInt()
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

/** '10월 20일'. */
@Composable
fun monthDayLabel(dateTime: KoreanDateTime): String = stringResource(Res.string.date_month_day, dateTime.month, dateTime.day)

/** '오후 3:20'. 0시·12시는 12로 적는다. */
@Composable
fun timeLabel(dateTime: KoreanDateTime): String {
    val hour12 = (dateTime.hour % 12).let { if (it == 0) 12 else it }
    val minute = dateTime.minute.toString().padStart(2, '0')
    return stringResource(if (dateTime.hour < 12) Res.string.time_am else Res.string.time_pm, hour12, minute)
}
