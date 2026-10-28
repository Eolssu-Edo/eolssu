package com.ssafy.eolssu.feature.guide.learn

import com.ssafy.eolssu.feature.guide.Blow
import com.ssafy.eolssu.feature.guide.GuideInstrument

/*
 * 기초부터 배우기 9단계(와이어프레임 LEARN_STAGES·LEARN_BY_INST·LEARN_SONGS).
 * 출처: docs/knowledge/domain/wind-beginner-curriculum.md (S15P31E102-118). 공통 9단계는 모든 악기가 같고,
 * 악기별 내용은 1~6단계와 8단계 기본 5음에서만 쓴다. 통과 기준 수치는 화면 구성을 보여주기 위한 예시이며 팀 확정 사항이 아니다.
 */

/** 단계의 판정 방식. */
enum class LearnJudge { Read, Pitch, Beat, Listen }

/** 할 일 한 줄. [detail]이 null이면 제목만 보인다. */
data class LearnTask(val title: String, val detail: String? = null)

/** 통과 기준. [rule]을 굵게, [note]를 회색으로 쓴다. */
data class LearnPass(val rule: String, val note: String)

/**
 * 단계 하나.
 *
 * @property tasks 공통 할 일. 4·6단계는 악기별 내용으로 화면이 만들고, 2단계의 '악기 잡기'는 악기별 설명으로 채운다.
 * @property optional 진행률에 넣지 않는 선택 단계(9단계).
 */
data class LearnStage(
    val number: Int,
    val title: String,
    val goal: String,
    val judge: LearnJudge,
    val why: String,
    val tasks: List<LearnTask> = emptyList(),
    val pass: LearnPass? = null,
    val source: String,
    val optional: Boolean = false,
)

/** 6단계에서 새로 내는 음역. [first]부터 낸다. */
data class LearnRange(val blow: Blow, val label: String, val description: String, val first: String)

/**
 * 악기별 내용.
 *
 * @property basic 4단계에서 배우는 순서. 앞 두 음의 운지를 보여준다.
 * @property scale 기본 5음을 낮은 음부터(5·8단계).
 */
data class LearnInstrumentContent(
    val parts: List<String>,
    val hold: String,
    val basic: List<String>,
    val basicNote: String,
    val basicCaution: String? = null,
    val scale: List<String>,
    val ranges: List<LearnRange>,
)

/** 8단계 연습할 곡 후보. 같은 제목의 평가 가능한 예시 곡이 있으면 곡 상세로 간다. */
data class LearnSong(val title: String, val meta: String)

/** 2단계에서 악기별 설명으로 바꾸는 할 일 제목. */
internal const val HOLD_TASK_TITLE = "악기 잡기"

val LearnStages: List<LearnStage> = listOf(
    LearnStage(
        1, "악기 알기·감상", "소리와 악기 구조를 안다", LearnJudge.Read,
        why = "지도서는 악기를 보고 모범 연주를 먼저 듣는 범주 학습을 가장 앞에 둬요.",
        tasks = listOf(
            LearnTask("악기 구조와 이름 보기", "취구·지공 같은 부분 이름을 알아 둬요."),
            LearnTask("모범 연주 듣기", "이 악기로 연주한 소리를 먼저 들어요."),
            LearnTask("이 악기 소리의 특징 느끼기", "높낮이·음색을 귀로 익혀요."),
        ),
        source = "단소 지도서 기본원칙 ①·④, 과정안 기초단계 1·2항. 모든 강좌의 1차시",
    ),
    LearnStage(
        2, "자세·호흡", "바르게 잡고 숨을 고르게 쓴다", LearnJudge.Read,
        why = "지도서 연주법 장의 순서(자세 → 안공법 → 발음법 → 호흡법)를 따라요.",
        tasks = listOf(
            LearnTask("앉은 자세", "허리를 펴고 어깨 힘을 빼요."),
            LearnTask(HOLD_TASK_TITLE),
            LearnTask("복식호흡", "배로 숨을 들이쉬고 길고 고르게 내쉬어요."),
        ),
        source = "단소 지도서 연주법 장. 소금 교실 2·3차시, 대금 기초편 8차시, 단소 교실 2차시",
    ),
    LearnStage(
        3, "첫 소리", "안정된 소리 하나를 낸다", LearnJudge.Pitch,
        why = "첫 소리는 음높이보다 소리가 끊기지 않고 고르게 나는지가 중요해요.",
        tasks = listOf(
            LearnTask("취구에 입술 대기", "아랫입술로 취구를 반쯤 덮어요."),
            LearnTask("입김 방향·세기 찾기", "음료수병을 불듯 가늘고 곧게 불어요(대금 기초편 7차시)."),
            LearnTask("한 소리 길게 내기", "같은 소리를 끊지 않고 이어 봐요."),
        ),
        pass = LearnPass("한 소리를 2초 이상 끊김 없이 3번", "음높이는 묻지 않고 소리가 났는지·흔들림만 봐요."),
        source = "단소 지도서 발음법 1·2항, 대금 기초편 7차시",
    ),
    LearnStage(
        4, "기본음", "쉬운 음부터 율명과 운지를 익힌다", LearnJudge.Pitch,
        why = "소리 내기 쉬운 음부터 배워요. 율명 구음을 소리 내어 읽으며 함께 익혀요.",
        pass = LearnPass("음마다 2초 이상 유지", "목표 음과 ±25센트 이내면 통과(예시)."),
        source = "단소 지도서 발음법 3항, 소금 교실 5~7차시, 대금 교실 2~4차시",
    ),
    LearnStage(
        5, "음 잇기", "이웃 음과 건너뛴 음을 이어 분다", LearnJudge.Pitch,
        why = "기본음이 되면 순차 진행 → 건너뛰기 → 세 음 동요 순서로 넓혀요.",
        pass = LearnPass("패턴의 8음 중 7음 이상 맞음", "음 사이 끊김은 박으로 세지 않아요(예시)."),
        source = "단소 지도서 발음법 3항·과정안 발전단계, 소금 교실 5차시(3음 동요)",
    ),
    LearnStage(
        6, "음역 넓히기", "높은·낮은 음역을 낸다", LearnJudge.Pitch,
        why = "같은 운지에서 입김 세기로 옥타브를 바꾸는 연습이에요.",
        pass = LearnPass("새 음역의 음을 각각 2초 이상 유지", "평취 음과 옥타브 차이가 나는지 봐요(예시)."),
        source = "단소 지도서 발음법 4·5항, 대금 교실 5~7차시, 대금 기초편 10~13차시, 소금 교실 13차시",
    ),
    LearnStage(
        7, "혀치기·박 나누기", "같은 음 반복과 정간 안 박 나누기를 한다", LearnJudge.Beat,
        why = "같은 음을 여러 번 낼 때 혀로 끊는 혀치기와, 한 정간을 둘·셋으로 나누는 연습이에요.",
        tasks = listOf(
            LearnTask("혀치기", "같은 음을 \"투·투·투\" 혀로 끊어 4번 반복해요."),
            LearnTask("1정간을 2로 나누기", "한 칸 안에 두 음을 고르게 넣어요."),
            LearnTask("1정간을 3으로 나누기", "한 칸 안에 세 음을 고르게 넣어요."),
        ),
        pass = LearnPass("혀치기 4번 반복, 박 어긋남 ±15% 이내", "2분·3분은 각 음 길이가 고른지 봐요(예시)."),
        source = "단소 지도서 발음법 6항, 소금 교실 8차시, 대금 기초편 14~18차시",
    ),
    LearnStage(
        8, "악보와 곡", "정간보를 보고 장단에 맞춰 곡을 분다", LearnJudge.Beat,
        why = "지도서는 평취로 된 쉬운 곡부터, 정간보로 먼저 가르친 뒤 오선보를 보게 해요. 장단 반주를 함께 틀어요.",
        tasks = listOf(
            LearnTask("정간보 읽는 순서 확인", "위에서 아래로, 오른쪽에서 왼쪽으로."),
            LearnTask("율명 보며 천천히 불기", "한 각씩 끊어 연습해요."),
            LearnTask("장단 반주에 맞춰 곡 전체 불기", "연습곡 화면에서 속도를 낮춰 시작해요."),
        ),
        pass = LearnPass("곡 전체 음정·박자 각 70점 이상", "AI 연주 평가 결과 화면의 점수를 그대로 써요(예시)."),
        source = "단소 지도서 과정안 통합단계·기본원칙 ⑧, 대금 교실 8~10차시, 소금 교실 9·10차시",
    ),
    LearnStage(
        9, "장식음·시김새", "꾸밈음과 요성·퇴성을 맛본다", LearnJudge.Listen,
        why = "모든 자료가 마지막 단계로 둬요. 지도서는 잘 되지 않으면 무리하게 지도하지 않고 감상으로 소개하는 선에서 끝내도 된다고 해요. 그래서 판정 없이 듣고 따라 해요.",
        tasks = listOf(
            LearnTask("앞·뒤 꾸밈음 듣고 따라 하기"),
            LearnTask("굴리기"),
            LearnTask("요성(떠는 소리)·퇴성(흘러내리는 소리)", "모범 연주를 듣고 흉내 내 봐요."),
        ),
        source = "단소 지도서 과정안 심화단계·발음법 8항, 소금 교실 11·14차시, 대금 교실 12·13차시",
        optional = true,
    ),
)

/** 진행률에 넣는 필수 단계(1~8). */
val RequiredLearnStages: List<LearnStage> = LearnStages.filterNot { it.optional }

fun learnStageOf(number: Int): LearnStage? = LearnStages.getOrNull(number - 1)

/** 단소 지도서 통합단계의 곡 목록. 악보·음원 이용 조건은 곡마다 확인 필요. */
val LearnSongs: List<LearnSong> = listOf(
    LearnSong("비행기", "3음 · 평취"),
    LearnSong("새야새야", "평취"),
    LearnSong("아리랑", "세마치장단"),
    LearnSong("도라지타령", "세마치장단"),
    LearnSong("천안삼거리", "굿거리장단"),
    LearnSong("풍년가", "굿거리장단"),
)

private val DaegeumRanges = listOf(
    LearnRange(Blow.High, "역취 · 높은 단", "입김을 세게 불어 한 옥타브 올려요.", "淋"),
    LearnRange(Blow.Low, "저취 · 낮은 단", "입김을 느슨하게 해 한 옥타브 내려요.", "㑣"),
)

private val DaegeumParts = listOf("취구", "청공(갈대 속청)", "지공 6개", "칠성공")

val GuideInstrument.learnContent: LearnInstrumentContent
    get() = when (this) {
        GuideInstrument.Danso -> LearnInstrumentContent(
            parts = listOf("취구(U자 홈)", "뒷구멍 1개 · 왼손 엄지", "앞 지공 4개"),
            hold = "세로로 세워 잡아요. 뒷구멍은 왼손 엄지로, 앞 지공은 왼손 검지·중지와 오른손 검지·중지로 막아요.",
            basic = listOf("汰", "潢", "無", "林", "仲"),
            basicNote = "구멍을 다 연 汰(태)부터 하나씩 막으며 내려가요. 仲·林 같은 낮은 음은 어려워서 뒤에 해요. 반음 夾·南은 다섯 음이 익은 뒤에 배워요.",
            basicCaution = "정일영(2014)은 汰와 같은 입김으로 나는 가운데 소리를 먼저 익히자고 하고, 단소 교실은 낮은 소리 → 높은 소리 순이라 자료마다 첫 음이 달라요.",
            scale = listOf("仲", "林", "無", "潢", "汰"),
            ranges = listOf(LearnRange(Blow.High, "역취 · 한 옥타브 위", "평취와 같은 운지에 입김을 세게 불어요.", "㳞")),
        )
        GuideInstrument.Sogeum -> LearnInstrumentContent(
            parts = listOf("취구", "지공 6개"),
            hold = "가로로 오른쪽을 향해 잡아요. 왼손이 취구 쪽 세 구멍, 오른손이 아래 세 구멍을 막아요.",
            basic = listOf("㴌", "汰", "潢", "無", "南", "林", "㳞"),
            basicNote = "고·태·황 → 무·남·임 → 중 순서예요(소금 교실 5~7차시). 역취 황은 6단계에서 해요.",
            scale = listOf("林", "南", "潢", "汰", "㳞"),
            ranges = listOf(LearnRange(Blow.High, "역취 · 한 옥타브 위", "같은 운지에 입김을 세게 불어요. 소금은 대금보다 한 옥타브 높아요.", "淋")),
        )
        GuideInstrument.DaegeumJeongak -> LearnInstrumentContent(
            parts = DaegeumParts,
            hold = "가로로 오른쪽을 향해 잡아요. 왼손이 취구 쪽 세 구멍, 오른손이 아래 세 구멍을 막아요.",
            basic = listOf("㳞", "汰", "潢", "南", "林"),
            basicNote = "지공을 모두 연 음 → 평취 왼손 음 → 평취 오른손 음 순서예요(대금 교실 2~4차시).",
            basicCaution = "강좌는 차시 제목만 확인해서 \"모든 지공을 연 음\"이 어느 율명인지는 확인이 필요해요. 대금 기초편은 중간 → 높은 → 낮은 음역 순이에요.",
            scale = listOf("林", "南", "潢", "汰", "㳞"),
            ranges = DaegeumRanges,
        )
        GuideInstrument.DaegeumSanjo -> LearnInstrumentContent(
            parts = DaegeumParts,
            hold = "가로로 오른쪽을 향해 잡아요. 기본 운지틀은 정악대금과 같아요.",
            basic = listOf("㳞", "汰", "潢", "南", "林"),
            basicNote = "정악대금과 같은 순서로 해요. 산조대금은 황종이 F라 같은 율명이라도 소리는 정악대금보다 장2도 높아요.",
            basicCaution = "유파마다 조율·세부 운지가 조금 다를 수 있어요.",
            scale = listOf("林", "南", "潢", "汰", "㳞"),
            ranges = DaegeumRanges,
        )
    }

/** 5단계 연습 패턴. */
enum class LearnPattern { Ascending, Descending, SkipOne, SkipTwo, ThreeNoteSong }

/** [scale](기본 5음, 낮은 음부터)로 만든 패턴의 음 순서. */
fun LearnPattern.notes(scale: List<String>): List<String> = when (this) {
    LearnPattern.Ascending -> scale
    LearnPattern.Descending -> scale.reversed()
    LearnPattern.SkipOne -> listOf(scale[0], scale[2], scale[4], scale[2], scale[0])
    LearnPattern.SkipTwo -> listOf(scale[0], scale[3], scale[1], scale[4], scale[1])
    LearnPattern.ThreeNoteSong -> listOf(scale[2], scale[1], scale[0], scale[1], scale[2])
}
