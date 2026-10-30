package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.model.Score
import com.ssafy.eolssu.core.model.ScoreBpmUnit
import com.ssafy.eolssu.core.model.ScoreCell
import com.ssafy.eolssu.core.model.ScoreEvent
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.ScoreProvenance
import com.ssafy.eolssu.core.model.ScoreSource
import com.ssafy.eolssu.core.model.ScoreTiming
import com.ssafy.eolssu.core.model.ScoreUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.math.abs

/** data/src/scores/score_data.schema.json v1. HTTP 목록/상세 응답 계약과 분리된 원본 악보 DTO. */
@Serializable
data class CommonScoreData(
    val schemaVersion: Int,
    val score: Header,
    val music: Music,
    val columns: List<Column>,
    val events: List<Event>,
    val provenance: Provenance,
) {
    @Serializable data class Header(val title: String, val instrument: String, val part: Int, val sourceId: String, val scoreVersion: Int)
    @Serializable data class Music(val hwangHz: Double, val jangdan: String, val bpmUnit: String, val ticksPerBeat: Int, val ticksPerJeonggan: Int)
    @Serializable data class Column(val index: Int, val cells: List<Cell>)
    @Serializable data class Cell(val id: String, val index: Int, val units: List<Unit>)
    @Serializable data class Unit(val row: Int, val col: Int, val symbol: String, val eventId: String)
    @Serializable data class Event(
        val id: String, val kind: String, val yul: String?, val octave: Int?,
        val startTick: Long, val durationTick: Long, val cellId: String,
        val breathAfter: Boolean, val sigimsae: List<String>,
    )
    @Serializable data class Provenance(val sourceId: String, val jgbSha256: String, val review: String, val converter: String)

    /** 누락/중복/잘못된 시간·참조를 샘플이나 빈 악보로 대체하지 않는다. */
    fun toScore(): Score {
        checkScore(schemaVersion == 1, "schemaVersion", "지원 버전은 1이다")
        checkScore(score.title.isNotEmpty() && score.part >= 0 && score.scoreVersion >= 1, "score", "제목·파트·버전을 확인해야 한다")
        checkScore(music.hwangHz.isFinite() && music.hwangHz > 0 && music.ticksPerBeat > 0 && music.ticksPerJeonggan > 0,
            "music", "황종 주파수와 tick 단위는 양수여야 한다")
        val bpmUnit = ScoreBpmUnit.entries.firstOrNull { it.name == music.bpmUnit }
            ?: throw ScoreDataException("music.bpmUnit", "지원하지 않는 BPM 단위")
        checkScore(provenance.sourceId == score.sourceId, "provenance.sourceId", "악보 출처와 다르다")
        checkScore(provenance.jgbSha256.matches(Regex("[0-9a-f]{64}")), "provenance.jgbSha256", "SHA-256 형식이 아니다")
        checkScore(provenance.review in setOf("reviewed", "inferred", "rejected"), "provenance.review", "알 수 없는 검수 상태")
        checkScore(columns.isNotEmpty() && events.isNotEmpty(), "columns/events", "표시·재생할 악보가 없다")

        val cells = linkedMapOf<String, Pair<Int, Cell>>()
        columns.forEachIndexed { c, column ->
            checkScore(column.index == c && column.cells.isNotEmpty(), "columns[$c]", "각은 0부터 순서대로 있고 정간이 필요하다")
            column.cells.forEachIndexed { j, cell ->
                checkScore(cell.index == j && cell.id == "c$c-j$j", "columns[$c].cells[$j]", "정간 ID와 위치가 다르다")
                checkScore(cell.units.isNotEmpty(), cell.id, "표시 자리가 없다")
                val rows = cell.units.groupBy { it.row }
                checkScore(rows.keys.toList() == rows.indices(), cell.id, "row는 위에서부터 연속이어야 한다")
                rows.values.forEach { units ->
                    checkScore(units.map { it.col } == units.indices.toList(), cell.id, "col은 줄 안에서 왼쪽부터 연속이어야 한다")
                }
                checkScore(cell.units == cell.units.sortedWith(compareBy<Unit> { it.row }.thenBy { it.col }), cell.id, "표시 순서가 row/col과 다르다")
                cells[cell.id] = c to cell
            }
        }
        checkScore(events.map { it.id }.distinct().size == events.size, "events.id", "중복 이벤트 ID")
        var expectedTick = 0L
        var noteIndex = 0
        val timeline = events.mapIndexed { index, event ->
            checkScore(event.startTick == expectedTick && event.durationTick > 0 && event.durationTick <= Long.MAX_VALUE - expectedTick,
                event.id, "이벤트 시간은 0부터 빈틈·겹침 없이 이어져야 한다")
            expectedTick += event.durationTick
            val (column, cell) = cells[event.cellId] ?: throw ScoreDataException(event.id, "시작 정간이 없다")
            val origin = cell.units.firstOrNull { "${cell.id}-r${it.row}-u${it.col}" == event.id && it.eventId == event.id }
                ?: throw ScoreDataException(event.id, "시작 표시 자리와 이벤트 ID가 다르다")
            val note = when (event.kind) {
                "NOTE" -> {
                    checkScore(event.yul in BaseNames && event.octave != null && event.octave in -2..2, event.id, "율명·옥타브가 잘못되었다")
                    ScoreNote.SourcePitch(requireNotNull(event.yul), requireNotNull(event.octave))
                }
                "REST" -> {
                    checkScore(event.yul == null && event.octave == null, event.id, "REST에 음높이가 있다")
                    ScoreNote.Rest
                }
                else -> throw ScoreDataException(event.id, "지원하지 않는 이벤트 종류")
            }
            ScoreEvent(index, event.id, column, cell.index, origin.row, cell.units.map { it.row }.distinct().size,
                note, event.startTick.toDouble() / music.ticksPerJeonggan, event.durationTick.toDouble() / music.ticksPerJeonggan,
                event.startTick, event.durationTick, if (event.kind == "NOTE") noteIndex++ else null,
                event.cellId, event.breathAfter, event.sigimsae)
        }
        checkScore(expectedTick == cells.size.toLong() * music.ticksPerJeonggan, "events", "전체 tick 길이가 정간 수와 다르다")
        val byId = timeline.associateBy { it.id }
        var cellOffset = 0L
        val mappedColumns = columns.map { column ->
            column.cells.map { cell ->
                val rowGroups = cell.units.groupBy { it.row }
                val notes = cell.units.map { unit ->
                    val event = byId[unit.eventId] ?: throw ScoreDataException(cell.id, "존재하지 않는 eventId: ${unit.eventId}")
                    val rowSize = rowGroups.getValue(unit.row).size
                    // 표시 분할은 유리수일 수 있다. 원본 이벤트의 정수 tick은 바꾸지 않는다(D3/D4).
                    val start = cellOffset + music.ticksPerJeonggan.toDouble() * (unit.row + unit.col.toDouble() / rowSize) / rowGroups.size
                    val duration = music.ticksPerJeonggan.toDouble() / rowGroups.size / rowSize
                    val eventStart = requireNotNull(event.startTick)
                    val eventEnd = eventStart + requireNotNull(event.durationTick)
                    checkScore(start + Epsilon >= eventStart && start + duration <= eventEnd + Epsilon, cell.id, "표시 자리가 연결된 이벤트 시간 밖이다")
                    if (event.id == "${cell.id}-r${unit.row}-u${unit.col}") {
                        checkScore(abs(start - eventStart) < Epsilon, event.id, "이벤트 시작 tick과 표시 위치가 다르다")
                    }
                    when (unit.symbol) {
                        "", "-" -> {
                            checkScore(event.note == ScoreNote.Rest || event.column == column.index, cell.id, "NOTE 이음이 각 경계를 넘었다")
                            if (unit.symbol.isEmpty()) ScoreNote.Blank else ScoreNote.Hold
                        }
                        "쉼" -> {
                            checkScore(event.note == ScoreNote.Rest, cell.id, "쉼 표기가 NOTE를 가리킨다")
                            checkScore(abs(start - eventStart) < Epsilon, cell.id, "새 쉼이 기존 이벤트 중간을 가리킨다")
                            ScoreNote.Rest
                        }
                        else -> {
                            val pitch = event.note as? ScoreNote.SourcePitch
                            checkScore(pitch != null && unit.symbol == pitch.hangul, cell.id, "표시 율명과 이벤트 음높이가 다르다")
                            checkScore(abs(start - eventStart) < Epsilon, cell.id, "새 율명이 기존 이벤트 중간을 가리킨다")
                            requireNotNull(pitch)
                        }
                    }
                }
                cellOffset += music.ticksPerJeonggan
                ScoreCell(notes, cell.id, cell.units.map { ScoreUnit(it.row, it.col, it.symbol, it.eventId) })
            }
        }
        return Score(mappedColumns,
            ScoreTiming(music.hwangHz, music.jangdan, bpmUnit, music.ticksPerBeat, music.ticksPerJeonggan),
            ScoreSource(schemaVersion, score.title, score.instrument, score.part, score.sourceId, score.scoreVersion,
                ScoreProvenance(provenance.sourceId, provenance.jgbSha256, provenance.review, provenance.converter)), timeline)
    }

    companion object {
        private val StrictJson = Json { ignoreUnknownKeys = false; isLenient = false }
        fun decode(json: String): CommonScoreData = try {
            StrictJson.decodeFromString<CommonScoreData>(json)
        } catch (e: SerializationException) {
            throw ScoreDataException("scoreData", "공통 악보 JSON 형식이 잘못되었다", e)
        }
        private const val Epsilon = 0.0000001
        private val BaseNames = setOf("황", "대", "태", "협", "고", "중", "유", "임", "이", "남", "무", "응")
    }
}

/** Repository는 이 오류를 사용자에게 재시도 가능한 악보 로드 실패로 전달한다. 원본 JSON은 로그에 넣지 않는다. */
class ScoreDataException(val location: String, reason: String, cause: Throwable? = null) : IllegalArgumentException("$location: $reason", cause)

private fun checkScore(condition: Boolean, location: String, reason: String) {
    if (!condition) throw ScoreDataException(location, reason)
}
private fun Map<Int, *>.indices(): List<Int> = (0 until size).toList()
