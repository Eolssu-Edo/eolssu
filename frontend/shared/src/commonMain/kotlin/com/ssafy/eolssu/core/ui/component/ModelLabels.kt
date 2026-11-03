package com.ssafy.eolssu.core.ui.component

import androidx.compose.runtime.Composable
import com.ssafy.eolssu.core.model.Jangdan
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.SongLevel
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.Instrument
import eolssu.shared.generated.resources.instrument_unknown
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.date_month_day
import eolssu.shared.generated.resources.jangdan_label
import eolssu.shared.generated.resources.jangdan_unassigned
import eolssu.shared.generated.resources.guide_instrument_daegeum_jeongak
import eolssu.shared.generated.resources.guide_instrument_daegeum_sanjo
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
    jangdanLabel(jangdan.name)

@Composable
fun jangdanLabel(name: String): String =
    when {
        name.isBlank() -> stringResource(Res.string.jangdan_unassigned)
        name.endsWith("박") -> name
        else -> stringResource(Res.string.jangdan_label, name)
    }

/** 악보의 실제 instrument code를 표시한다. 알 수 없는 코드는 다른 악기로 대체하지 않는다. */
@Composable
fun songInstrumentLabel(song: Song): String {
    when (song.score.source?.instrument) {
        "JEONGAK_DAEGEUM" -> return stringResource(Res.string.guide_instrument_daegeum_jeongak)
        "SANJO_DAEGEUM" -> return stringResource(Res.string.guide_instrument_daegeum_sanjo)
    }
    val instrument = Instrument.entries.firstOrNull { it.serverCode == song.score.source?.instrument }
    return stringResource(instrument?.label ?: Res.string.instrument_unknown)
}

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
