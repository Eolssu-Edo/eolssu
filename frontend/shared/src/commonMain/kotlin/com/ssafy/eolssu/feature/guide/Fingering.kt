package com.ssafy.eolssu.feature.guide

/** 구멍 하나의 상태. 운지표 문자 `1` 막음, `0` 엶, `h` 반 막음, `?` 확인 필요. */
enum class HoleState {
    Closed, Open, Half, Unknown;

    companion object {
        fun parse(code: Char): HoleState = when (code) {
            '1' -> Closed
            '0' -> Open
            'h' -> Half
            '?' -> Unknown
            else -> error("알 수 없는 구멍 상태: $code")
        }
    }
}

/** 취법. 같은 운지에서 입김 세기로 옥타브를 나눈다. */
enum class Blow { Low, Mid, High }

/** 취법 탭 하나(`.blow button`). [enabled]가 false면 이 악기에 없는 취법이다. */
data class BlowTab(val blow: Blow, val selected: Boolean, val enabled: Boolean)

/** 운지 카드 설명 줄에서 '읽는 법' 뒤에 붙는 표시(`· 반음·그 밖의 음`, `· 운지 확인 필요`). */
enum class NoteRemark { Semitone, NeedsCheck }

/**
 * 운지표 한 줄.
 *
 * @property hanja 율명 글자(청성·탁성 포함).
 * @property reading 읽는 법. 단소는 潢·汰를 '황'·'태'로 읽는다.
 * @property holes 구멍 상태. 대금·소금은 1~6공, 단소는 뒷구멍·1·2·3·4공 순서.
 * @property basic 기본 5음(황·태·중·임·남 계열)이면 true, 반음과 그 밖의 음이면 false.
 */
data class FingeringNote(
    val hanja: String,
    val name: String,
    val reading: String,
    val holes: List<HoleState>,
    val basic: Boolean,
) {
    /** 자료가 갈려 운지를 확인하지 못한 구멍이 있다. */
    val needsCheck: Boolean get() = HoleState.Unknown in holes

    /** 설명 줄의 표시. 기초부터 배우기 카드([inLearn])는 반음 표시를 하지 않는다. */
    fun remarks(inLearn: Boolean = false): List<NoteRemark> = buildList {
        if (!inLearn && !basic) add(NoteRemark.Semitone)
        if (needsCheck) add(NoteRemark.NeedsCheck)
    }
}

/** 악기 그림 위 구멍 중심. 그림 원본 픽셀 좌표다. */
data class HolePoint(val x: Int, val y: Int)

/**
 * 운지법 보기·기초부터 배우기의 악기. 홈의 악기 선택과 따로, 와이어프레임처럼 단소에서 시작한다.
 *
 * @property id 화면 이동 인자로 쓰는 식별자(와이어프레임 `INST.id`).
 * @property hwang 황종의 높이(C에서 반음 수). 정악대금·소금·단소는 E♭(3), 산조대금은 F(5).
 * @property imageWidth 악기 그림 원본 크기. [holes]·[holeRadius]가 이 크기 기준이다.
 * @property note 운지 화면 위쪽의 악기 설명.
 * @property source 운지표 출처.
 */
enum class GuideInstrument(
    val id: String,
    val hwang: Int,
    val blows: List<Blow>,
    private val table: Map<Blow, List<FingeringNote>>,
    val imageWidth: Int,
    val imageHeight: Int,
    val holeRadius: Int,
    val holes: List<HolePoint>,
    val note: String,
    val source: String,
) {
    Danso(
        id = "danso", hwang = 3, blows = listOf(Blow.Mid, Blow.High), table = DansoTable,
        imageWidth = 2000, imageHeight = 683, holeRadius = 30,
        holes = points(935 to 209, 934 to 354, 1114 to 354, 1368 to 354, 1619 to 354),
        note = "뒷구멍은 왼손 엄지로 막아요. 역취는 평취와 같은 운지에 입김을 세게 불어요.",
        source = "출처: 국립국악원 『창작을 위한 국악기 이해와 활용1』 2장 단소 25쪽 그림, 국악사전 「단소」. 역취 汰 운지는 확인 필요.",
    ),
    Sogeum(
        id = "sogeum", hwang = 3, blows = listOf(Blow.Mid, Blow.High), table = SogeumTable,
        imageWidth = 2000, imageHeight = 668, holeRadius = 26,
        holes = points(921 to 329, 1051 to 328, 1186 to 329, 1318 to 329, 1448 to 329, 1579 to 330),
        note = "소금은 대금보다 한 옥타브 높게 소리 나요. 향악계곡 안공법 기준이에요.",
        source = "출처: 국립국악원 『창작을 위한 국악기 이해와 활용1』 3장 소금 61쪽 [표 1] 향악계곡 안공법.",
    ),
    DaegeumJeongak(
        id = "daegeum-jeongak", hwang = 3, blows = Blow.entries, table = DaegeumTable,
        imageWidth = 2000, imageHeight = 668, holeRadius = 25,
        holes = points(883 to 323, 1006 to 323, 1124 to 323, 1245 to 323, 1368 to 323, 1492 to 323),
        note = "같은 운지에서 입김 세기로 저취·평취·역취 옥타브를 나눠요. 서양 음이름은 황종=미♭(E♭) 기준이에요.",
        source = "출처: 국립국악원 『2021 대금정악보』 9쪽 대금 안공표(공공누리 4유형).",
    ),
    DaegeumSanjo(
        id = "daegeum-sanjo", hwang = 5, blows = Blow.entries, table = DaegeumTable,
        imageWidth = 2000, imageHeight = 667, holeRadius = 25,
        holes = points(848 to 332, 981 to 332, 1115 to 332, 1244 to 332, 1374 to 332, 1510 to 331),
        note = "기본 운지틀은 정악대금과 같아요. 서양 음이름은 황종=F(정악대금보다 장2도 높음) 기준이고, 유파마다 조율·세부 운지가 조금 다를 수 있어요.",
        source = "출처: 정악대금 안공표의 기본 운지틀. 유파별 차이는 선생님께 확인하세요.",
    ),
    ;

    /** 이 악기에 없는 취법(단소·소금의 저취)을 고른 채면 평취를 보여준다. */
    fun resolveBlow(blow: Blow): Blow = if (blow in blows) blow else Blow.Mid

    /** 저취·평취·역취 탭. [shown]이 고른 탭이고, 이 악기에 없는 취법은 누를 수 없다. */
    fun blowTabs(shown: Blow): List<BlowTab> = Blow.entries.map { BlowTab(it, selected = it == shown, enabled = it in blows) }

    /** [blow]의 모든 음을 낮은 음부터. 기본 5음·반음을 나누지 않는다. */
    fun notesByPitch(blow: Blow): List<FingeringNote> =
        table[resolveBlow(blow)].orEmpty().sortedBy { pitchIndexOf(it.hanja) }

    /** 저취 → 평취 → 역취 순서로 찾은 [hanja]의 운지. */
    fun noteOf(hanja: String): FingeringNote? =
        Blow.entries.firstNotNullOfOrNull { blow -> table[blow]?.firstOrNull { it.hanja == hanja } }

    fun solfegeOf(hanja: String): String = solfegeOf(hanja, hwang)

    companion object {
        val Default = Danso

        fun fromId(id: String?): GuideInstrument? = entries.firstOrNull { it.id == id }
    }
}

private fun points(vararg xy: Pair<Int, Int>) = xy.map { (x, y) -> HolePoint(x, y) }
