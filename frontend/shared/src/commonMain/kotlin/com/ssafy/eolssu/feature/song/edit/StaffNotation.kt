package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.Yul
import com.ssafy.eolssu.core.model.fingeringYul
import kotlin.math.abs

/** EDIT-03에서 위·아래 버튼으로 옮겨 다니는 율명 순서(낮은 음 → 높은 음). 편집 자판의 율명과 같다. */
val StaffPitchOrder: List<Yul> = listOf(
    Yul.Hwangjong, Yul.Taeju, Yul.Jungnyeo, Yul.Imjong, Yul.Namnyeo, Yul.Muyeok, Yul.CheongHwangjong, Yul.CheongTaeju,
)

/** 오선보 음표 하나를 가리키는 키: 시작 정간 번호와 그 정간 안의 순서. */
data class StaffNoteKey(val start: Int, val sub: Int)

/**
 * 정간보를 오선보로 옮긴 음표 하나.
 *
 * @property length 정간 단위 길이. 한 정간 = 8분음표, 나눈 정간의 음은 그만큼 짧고 지속(ㅡ)은 앞 음을 늘린다.
 * @property span 이 음이 걸친 정간 수(시작 정간 포함). 같은 위치의 정간보를 칠할 때 쓴다.
 * @property yul 율명. null이면 쉼표다.
 * @property system 오선보 줄 번호. 한 줄에 각 두 개를 적는다.
 */
data class StaffNote(
    val key: StaffNoteKey,
    val subCount: Int,
    val length: Double,
    val span: Int,
    val yul: Yul?,
    val system: Int,
) {
    val isRest: Boolean get() = yul == null

    fun covers(index: Int): Boolean = index >= key.start && index < key.start + span
}

/**
 * 정간보를 연주 순서대로 오선보 음표로 옮긴다(와이어프레임 EDIT-03). 오선보 한 줄은 [cellsPerSystem] 정간이다.
 * 지속(ㅡ)은 같은 줄의 앞 음을 늘리고, 줄이 바뀐 뒤의 지속은 앞 음과 같은 높이의 새 음표로 적는다. 빈 정간은 건너뛴다.
 */
fun staffNotes(draft: SheetDraft, cellsPerSystem: Int = draft.gak * 2): List<StaffNote> {
    val notes = mutableListOf<StaffNote>()
    draft.cells.forEachIndexed { index, cell ->
        val system = index / cellsPerSystem
        cell.forEachIndexed { sub, note ->
            val length = 1.0 / cell.size
            val last = notes.lastOrNull()
            when {
                note == ScoreNote.Blank -> Unit
                note == ScoreNote.Hold && last != null && last.system == system ->
                    notes[notes.lastIndex] = last.copy(length = last.length + length, span = last.span + if (sub == 0) 1 else 0)
                note == ScoreNote.Hold -> notes += StaffNote(StaffNoteKey(index, sub), cell.size, length, 1, last?.yul, system)
                note == ScoreNote.Rest -> notes += StaffNote(StaffNoteKey(index, sub), cell.size, length, 1, null, system)
                else -> notes += StaffNote(StaffNoteKey(index, sub), cell.size, length, 1, note.fingeringYul, system)
            }
        }
    }
    return notes
}

/** 오선보에 한 번에 보이는 줄 수(와이어프레임 EDIT-03: 두 줄 = 네 각). */
const val STAFF_SYSTEMS_SHOWN = 2

/**
 * [cell] 정간이 든 오선보 줄과 함께 보일 줄 번호. 줄을 [STAFF_SYSTEMS_SHOWN]개씩 묶어 넘긴다
 * (한 줄이 두 각이면 1–2각·3–4각, 5–6각·7–8각 …). 마지막 묶음은 남은 줄만 보인다.
 */
fun visibleStaffSystems(cell: Int, cellsPerSystem: Int, systemCount: Int): IntRange {
    if (systemCount <= 0) return IntRange.EMPTY
    val system = (cell / cellsPerSystem).coerceIn(0, systemCount - 1)
    val first = system / STAFF_SYSTEMS_SHOWN * STAFF_SYSTEMS_SHOWN
    return first until minOf(first + STAFF_SYSTEMS_SHOWN, systemCount)
}

/** 고른 키의 음표. 없으면 그 정간을 덮는 음표, 그것도 없으면 첫 음표다. */
fun List<StaffNote>.pick(key: StaffNoteKey?): StaffNote? {
    if (key == null) return firstOrNull()
    return firstOrNull { it.key == key } ?: firstOrNull { it.covers(key.start) } ?: firstOrNull()
}

/**
 * 오선보 위 음 높이. 플랫 표기로 적는다(와이어프레임: 황 = E♭4이면 林은 B♭4).
 *
 * @property letter 음이름 번호(C=0 … B=6).
 * @property step 오선 위치 비교용 번호(`letter + 7 × octave`).
 */
data class StaffPitch(val letter: Int, val octave: Int, val flat: Boolean) {
    val step: Int get() = letter + 7 * octave

    /** 'E♭4'. */
    val name: String get() = LetterNames[letter] + (if (flat) FLAT else "") + octave

    /** '미♭(E♭4)'. 계이름은 고정도 이름이다. */
    val solfegeName: String get() = SolfegeNames[letter] + (if (flat) FLAT else "") + "($name)"

    /** 아래 덧줄 없이 오선 맨 아래 줄(E4)에서 몇 칸(선·칸 하나가 1) 위인지. */
    val stepsAboveBottomLine: Int get() = step - BottomLineStep

    companion object {
        private const val FLAT = "♭"
        private val LetterNames = listOf("C", "D", "E", "F", "G", "A", "B")

        /** 고정도 계이름(도 = C). 국악 율명과 함께 적는 서양 음 이름이라 리소스가 아닌 표기 규칙으로 둔다. */
        private val SolfegeNames = listOf("도", "레", "미", "파", "솔", "라", "시")

        /** 높은음자리표 오선 맨 아래 줄 E4. */
        private val BottomLineStep = 2 + 7 * 4
    }
}

/** 황을 [hwang]으로 놓았을 때 [yul]의 오선보 음 높이. */
fun staffPitch(yul: Yul, hwang: HwangPitch): StaffPitch = staffPitch(hwang.midi + yulSemitone(yul))

internal fun staffPitch(midi: Int): StaffPitch {
    val (letter, flat) = FlatSpelling[midi % 12]
    return StaffPitch(letter, midi / 12 - 1, flat)
}

/** 황종에서 몇 반음 위인지. 기본 12율은 선언 순서, 청성은 한 옥타브(12반음) 위다. */
internal fun yulSemitone(yul: Yul): Int = when (yul) {
    Yul.CheongHwangjong -> 12
    Yul.CheongTaeju -> 14
    Yul.CheongJungnyeo -> 17
    Yul.CheongImjong -> 19
    Yul.CheongMuyeok -> 22
    else -> yul.ordinal
}

/** 음 높이(C=0 … B=11) → (음이름 번호, 플랫 여부). 검은 건반은 플랫으로 적는다. */
private val FlatSpelling = listOf(
    0 to false, 1 to true, 1 to false, 2 to true, 2 to false, 3 to false,
    4 to true, 4 to false, 5 to true, 5 to false, 6 to true, 6 to false,
)

/** 정간 길이로 정한 음표 모양. 와이어프레임 길이 이름(16분 · 8분 · 4분 · 점4분)에 2분 계열을 더했다. */
enum class StaffDuration { Sixteenth, Eighth, DottedEighth, Quarter, DottedQuarter, Half, DottedHalf, Other }

fun staffDuration(length: Double): StaffDuration {
    fun near(value: Double) = abs(length - value) < 0.01
    return when {
        near(0.5) -> StaffDuration.Sixteenth
        near(1.0) -> StaffDuration.Eighth
        near(1.5) -> StaffDuration.DottedEighth
        near(2.0) -> StaffDuration.Quarter
        near(3.0) -> StaffDuration.DottedQuarter
        near(4.0) -> StaffDuration.Half
        near(6.0) -> StaffDuration.DottedHalf
        length < 1.0 -> StaffDuration.Sixteenth
        else -> StaffDuration.Other
    }
}
