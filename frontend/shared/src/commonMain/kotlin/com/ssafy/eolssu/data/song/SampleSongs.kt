package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.model.Jangdan
import com.ssafy.eolssu.core.model.Score
import com.ssafy.eolssu.core.model.ScoreCell
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.SongLevel
import com.ssafy.eolssu.core.model.Yul
import kotlin.math.floor
import kotlin.math.roundToInt

/*
 * 와이어프레임(data.js)의 예시 곡·장단. 화면 구성과 이동을 확인하기 위한 예시이며 실제 곡의 악보가 아니다.
 * 곡 API가 생기면 SongRepository 구현을 바꾸고 이 파일은 지운다.
 * 정간 수·대강 분절 출처: jeongganbo-generator (https://github.com/danbinaerinHan/jeongganbo-generator)
 */

internal object SampleJangdans {
    val Semachi = Jangdan("세마치", listOf(3, 3, 3), beats = 3, subdivision = 3, gueum = listOf("덩", "", "", "덩", "", "", "덕", "쿵", "덕"))
    val Gutgeori = Jangdan(
        "굿거리", listOf(3, 3, 3, 3), beats = 4, subdivision = 3,
        gueum = listOf("덩", "", "기덕", "쿵", "", "더러러러", "쿵", "", "기덕", "쿵", "", "더러러러"),
    )
    val Jungmori = Jangdan("중모리", listOf(3, 3, 3, 3), beats = 12, subdivision = 1)
    val Taryeong = Jangdan("타령", listOf(3, 3, 3, 3), beats = 4, subdivision = 3)
    val Dodeuri = Jangdan("도드리", listOf(6), beats = 6, subdivision = 1)
    val Seryeongsan = Jangdan("세령산", listOf(3, 2, 2, 3), beats = 10, subdivision = 1)
    val Gagok16 = Jangdan("가곡 16박", listOf(11, 5), beats = 16, subdivision = 1)
    val Yeominrak = Jangdan("여민락", listOf(6, 4, 4, 6), beats = 20, subdivision = 1)
}

private val Y = Yul.entries.associateBy { it.symbol }

/** 교과서식 평조 대응 예시(황=솔). 이 곡·악기 기준 데이터가 있는 곡에만 계이름을 표시한다. */
private val PyeongjoSolfege = mapOf(
    Yul.Hwangjong to "솔", Yul.Taeju to "라", Yul.Jungnyeo to "도", Yul.Imjong to "레", Yul.Namnyeo to "미",
    Yul.CheongHwangjong to "솔′", Yul.CheongTaeju to "라′",
)

private fun cell(vararg symbols: String) = ScoreCell(symbols.map { requireNotNull(ScoreNote.fromSymbol(it)) { it } })

/** "黃"·"太仲"(한 정간을 나눈 음)처럼 적은 각 하나. 두 글자 율명은 없으므로 한 글자씩 끊는다. */
private fun column(vararg cells: String) = cells.map { c -> cell(*c.map(Char::toString).toTypedArray()) }

/** 아리랑: 선율 흐름을 흉내 낸 손으로 쓴 예시(실제 악보 아님). */
private val ArirangColumns = listOf(
    column("黃", "ㅡ", "太", "黃", "ㅡ", "太", "仲", "ㅡ", "ㅡ"),
    column("黃", "ㅡ", "太", "黃", "ㅡ", "太仲", "仲", "ㅡ", "ㅡ"),
    column("仲", "ㅡ", "林", "仲", "太黃", "黃", "太", "ㅡ", "△"),
    column("仲", "ㅡ", "林", "南", "ㅡ", "潢", "南林", "林", "仲"),
    column("林", "ㅡ", "仲", "太", "ㅡ", "黃", "太", "ㅡ", "△"),
    column("林", "ㅡ", "南", "潢", "ㅡ", "ㅡ", "南", "林", "仲"),
    column("林", "ㅡ", "南", "潢", "ㅡ", "ㅡ", "南", "林", "仲"),
    column("仲", "ㅡ", "林", "仲", "太", "黃", "太", "ㅡ", "△"),
    column("仲", "ㅡ", "林", "南", "ㅡ", "潢", "南", "林", "仲"),
    column("林", "ㅡ", "仲", "太", "ㅡ", "黃", "黃", "ㅡ", "△"),
)

/** 와이어프레임의 결정적 난수(32비트 선형 합동). 같은 씨앗이면 와이어프레임과 같은 예시 악보가 나온다. */
private fun seeded(seed: Long): () -> Double {
    var s = seed and 0xFFFFFFFFL
    return {
        s = (s * 1664525L + 1013904223L) and 0xFFFFFFFFL
        s / 4294967296.0
    }
}

/** 예시 악보 생성(와이어프레임 genScore와 같은 규칙). */
internal fun generateScore(seed: Long, columns: Int, cells: Int, scaleSymbols: String): Score {
    val scale = scaleSymbols.map { requireNotNull(Y[it.toString()]) }
    val r = seeded(seed)
    var prev = scale[floor(r() * scale.size).toInt()]
    val out = List(columns) {
        List(cells) { j ->
            val v = r()
            when {
                j > 0 && v < 0.38 -> ScoreCell(ScoreNote.Hold)
                v > 0.93 -> ScoreCell(ScoreNote.Rest)
                else -> {
                    val idx = (scale.indexOf(prev) + ((r() - 0.5) * 3).roundToInt()).coerceIn(0, scale.size - 1)
                    prev = scale[idx]
                    ScoreCell(ScoreNote.Pitch(prev))
                }
            }
        }
    }
    return Score(out)
}

/** 예시 곡 10개. [Song.evaluable]이 아닌 곡은 혼자 연습만 할 수 있다. */
internal val SampleSongs: List<Song> = listOf(
    Song(
        id = "arirang", title = "아리랑", level = SongLevel.Intro, jangdan = SampleJangdans.Semachi, baseBpm = 60,
        description = "가장 널리 알려진 경기민요예요. 음 수가 적고 느려서 대금 첫 연습곡으로 알맞아요.",
        evaluable = true, solfege = PyeongjoSolfege, score = Score(ArirangColumns + ArirangColumns.take(6)),
    ),
    Song(
        id = "doraji", title = "도라지타령", level = SongLevel.Intro, jangdan = SampleJangdans.Semachi, baseBpm = 66,
        description = "밝고 경쾌한 경기민요예요. 아리랑과 같은 세마치장단으로 박 감각을 익히기 좋아요.",
        evaluable = true, solfege = PyeongjoSolfege, score = generateScore(11, 8, 9, "黃太仲林南潢"),
    ),
    Song(
        id = "milyang", title = "밀양아리랑", level = SongLevel.Elementary, jangdan = SampleJangdans.Semachi, baseBpm = 76,
        description = "경상도 민요예요. 아리랑보다 빠르고 음의 오르내림이 커요.",
        evaluable = true, solfege = null, score = generateScore(23, 10, 9, "黃太仲林南無潢"),
    ),
    Song(
        id = "niliriya", title = "늴리리야", level = SongLevel.Elementary, jangdan = SampleJangdans.Gutgeori, baseBpm = 54,
        description = "굿거리장단의 경기민요예요. 4박의 흥겨운 느낌을 익힐 수 있어요.",
        evaluable = true, solfege = null, score = generateScore(37, 8, 12, "黃太仲林南潢"),
    ),
    Song(
        id = "hanobaek", title = "한오백년", level = SongLevel.Intermediate, jangdan = SampleJangdans.Jungmori, baseBpm = 48,
        description = "강원도 민요예요. 느린 중모리장단에서 긴 음을 고르게 유지하는 연습이 돼요.",
        evaluable = false, solfege = null, score = generateScore(41, 7, 12, "黃太仲林南無潢汰"),
    ),
    Song(
        id = "taryeong", title = "타령", level = SongLevel.Intermediate, jangdan = SampleJangdans.Taryeong, baseBpm = 60,
        description = "영산회상에 들어 있는 곡이에요. 12정간 한 각이 3·3·3·3 대강으로 나뉘어요.",
        evaluable = true, solfege = null, score = generateScore(53, 9, 12, "黃太仲林南無潢汰"),
    ),
    Song(
        id = "dodeuri", title = "도드리", level = SongLevel.Intro, jangdan = SampleJangdans.Dodeuri, baseBpm = 80,
        description = "6정간 한 각의 짧은 장단이에요. 한 각이 그대로 한 줄에 보여요.",
        evaluable = true, solfege = null, score = generateScore(61, 8, 6, "黃太仲林南潢"),
    ),
    Song(
        id = "seryeongsan", title = "세령산", level = SongLevel.Intermediate, jangdan = SampleJangdans.Seryeongsan, baseBpm = 50,
        description = "10정간 한 각이 3·2·2·3 대강으로 나뉘어요. 한 각이 한 줄에 보여요.",
        evaluable = true, solfege = null, score = generateScore(67, 6, 10, "黃太仲林南無潢"),
    ),
    Song(
        id = "gagok16", title = "가곡 (16박)", level = SongLevel.Intermediate, jangdan = SampleJangdans.Gagok16, baseBpm = 40,
        description = "16정간 한 각이 11·5 대강으로 나뉘어요. 한 각을 11정간·5정간 두 줄로 보여줘요.",
        evaluable = true, solfege = null, score = generateScore(71, 5, 16, "黃太仲林南無潢汰"),
    ),
    Song(
        id = "yeominrak", title = "여민락", level = SongLevel.Intermediate, jangdan = SampleJangdans.Yeominrak, baseBpm = 45,
        description = "20정간 한 각이 6·4·4·6 대강으로 나뉘어요. 한 각을 6·4 / 4·6 두 줄로 보여줘요.",
        evaluable = true, solfege = null, score = generateScore(79, 6, 20, "黃太仲林南無潢汰"),
    ),
)
