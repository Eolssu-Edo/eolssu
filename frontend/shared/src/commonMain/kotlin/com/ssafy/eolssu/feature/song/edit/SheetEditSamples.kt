package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.MySheet
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.data.song.SampleSongs

/**
 * 인식 결과에서 확인이 필요한 정간 하나(EDIT-06).
 *
 * @property index 정간 번호(연주 순서).
 * @property candidates 고를 수 있는 율명. '太·黃'처럼 가운뎃점으로 나눈 것은 한 정간을 나눈 음이다.
 */
data class ImportCheck(val index: Int, val title: String, val description: String, val candidates: List<String>)

/**
 * 악보 추가·편집(2차) 화면의 예시 데이터. 정간보 인식·오선보 변환·저장 API를 붙이기 전까지 화면 흐름만 확인한다.
 * 악보는 1차 예시 곡 아리랑([SampleSongs])의 앞 6각이고, 문구는 2차 와이어프레임(S15P31E102-401 결합본)의 예시다.
 */
internal object SheetEditSamples {
    private val arirang: Song = SampleSongs.first { it.id == "arirang" }

    /** 인식 결과 예시 악보의 각 수. */
    private const val RECOGNIZED_COLUMNS = 6

    /** 와이어프레임 EDIT-02: 정간보를 가져와 5각 넷째 정간까지 입력된 상태로 시작한다. */
    private const val IMPORT_FILLED_CELLS = 40

    /** 와이어프레임 EDIT-02 첫 선택 정간(5각 5정간). */
    const val IMPORT_FIRST_SELECTED = 40

    /** 와이어프레임의 예시 시김새: 3각 4정간 퇴성, 4각 4정간 요성. */
    private val SampleSigimsae = mapOf(21 to Sigimsae.Toeseong, 30 to Sigimsae.Yoseong)

    fun importFileName(kind: ImportKind): String = when (kind) {
        ImportKind.Jeongganbo -> "아리랑_정간보.jpg"
        ImportKind.Staff -> "아리랑_오선보.pdf"
    }

    /** 인식 결과 예시 악보의 정간(아리랑 앞 6각). */
    fun recognizedCells(): List<List<ScoreNote>> = arirang.score.columns.take(RECOGNIZED_COLUMNS).flatten().map { it.notes }

    /**
     * 확인이 필요한 정간. 정간보는 흐리게 읽힌 정간, 오선보는 국악 음계에 없는 음을 가까운 율명으로 바꾼 정간이다.
     */
    fun checks(kind: ImportKind): List<ImportCheck> = when (kind) {
        ImportKind.Jeongganbo -> listOf(
            ImportCheck(22, "太·黃 으로 읽었어요", "한 정간에 두 음이 있는데 아래 글자가 흐려요.", listOf("太·黃", "太·仲", "仲·黃")),
            ImportCheck(30, "南 으로 읽었어요", "글자 일부가 접힌 자국에 가려졌어요.", listOf("南", "林", "潢")),
            ImportCheck(41, "黃 으로 읽었어요", "곁줄 시김새가 붙어 있는지 확실하지 않아요.", listOf("黃", "太", "潢")),
        )
        ImportKind.Staff -> listOf(
            ImportCheck(2, "오선보의 G4를 太(태)로 바꿨어요", "황 = E♭4 기준으로 국악 음계에 없는 음이라 가까운 율명을 골랐어요.", listOf("太", "仲")),
            ImportCheck(20, "오선보의 B4를 林(임)으로 바꿨어요", "林(B♭4)과 南(C5) 사이 음이에요. 원래 곡에 맞는 쪽을 골라요.", listOf("林", "南")),
        )
    }

    /** 인식 결과(EDIT-06) 악보. [fixed]는 확인한 정간 번호 → 고른 후보다. */
    fun importResultDraft(kind: ImportKind, fixed: Map<Int, String>): SheetDraft = recognizedDraft(
        importTitle(kind),
        recognizedCells().mapIndexed { index, cell -> fixed[index]?.let(::candidateNotes)?.takeIf { it.isNotEmpty() } ?: cell },
    ).copy(sigimsae = emptyMap())

    private fun importTitle(kind: ImportKind): String = when (kind) {
        ImportKind.Jeongganbo -> "아리랑 (정간보 가져옴)"
        ImportKind.Staff -> "아리랑 (오선보 가져옴)"
    }

    /** 편집기로 들어온 경로에 맞는 첫 악보. 제공 곡 복사본은 '(내 편곡)'을 붙이고, 내 악보는 그 이름으로 연결된 예시 곡(없으면 아리랑 예시)을 연다. */
    fun draftFor(source: EditSource, sheets: List<MySheet>): SheetDraft = when (source) {
        is EditSource.Import -> when (source.kind) {
            ImportKind.Jeongganbo -> recognizedDraft(
                importTitle(source.kind),
                recognizedCells().mapIndexed { index, cell -> if (index < IMPORT_FILLED_CELLS) cell else emptyList() },
            )
            ImportKind.Staff -> recognizedDraft(importTitle(source.kind), recognizedCells())
        }
        is EditSource.CopySong -> songDraft(SampleSongs.firstOrNull { it.id == source.songId } ?: arirang).let { it.copy(title = copyTitle(it.title)) }
        is EditSource.CopySheet -> sheetDraft(sheets.firstOrNull { it.id == source.sheetId })
        is EditSource.Mine -> sheetDraft(sheets.firstOrNull { it.id == source.sheetId })
    }

    /** 제공 곡을 복사한 악보 이름(와이어프레임 Edit-Copy '아리랑 (내 편곡)'). 원본 곡 이름은 그대로 둔다. */
    fun copyTitle(songTitle: String): String = "$songTitle (내 편곡)"

    private fun recognizedDraft(title: String, cells: List<List<ScoreNote>>) = SheetDraft(
        title = title,
        jangdan = requireNotNull(arirang.jangdan),
        bpm = arirang.baseBpm,
        cells = cells,
        sigimsae = SampleSigimsae.filterKeys { cells[it].any(ScoreNote::isPitch) },
    )

    private fun sheetDraft(sheet: MySheet?): SheetDraft {
        val song = SampleSongs.firstOrNull { it.id == sheet?.songId }
        val title = sheet?.title ?: arirang.title
        return if (song == null || song.id == arirang.id) recognizedDraft(title, recognizedCells()) else songDraft(song).copy(title = title)
    }

    /** 제공 곡 전체를 복사한다. 각 길이가 장단과 다르면 아리랑 예시로 대신한다. */
    private fun songDraft(song: Song): SheetDraft {
        val jangdan = song.jangdan
        if (jangdan == null || song.score.columns.any { it.size != jangdan.gak }) return recognizedDraft(song.title, recognizedCells())
        return SheetDraft(song.title, jangdan, song.baseBpm, song.score.columns.flatten().map { it.notes })
    }
}

/** 후보 '太·黃'을 한 정간의 음으로 바꾼다. 율명이 아닌 글자는 버린다. */
internal fun candidateNotes(candidate: String): List<ScoreNote> = candidate.split('·').mapNotNull { ScoreNote.fromSymbol(it.trim()) }
