package com.ssafy.eolssu

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.util.UUID

/** Accessed only from the diagnostics worker. Sequence allocation and deletion survive process death. */
internal class QaPerformanceStore(context: Context) : SQLiteOpenHelper(
    context,
    File(context.noBackupFilesDir, "qa-performance.db").path,
    null,
    2,
) {
    data class State(val streamId: String, val ack: Long, val end: Long, val runId: String?, val retainedAfter: Long = 0, val missingRetention: Long = 0)

    override fun onConfigure(db: SQLiteDatabase) {
        db.execSQL("PRAGMA synchronous = FULL")
        if (db.version == 0) db.execSQL("PRAGMA auto_vacuum = FULL")
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE state (
                stream_id TEXT NOT NULL, ack INTEGER NOT NULL, last_seq INTEGER NOT NULL, run_id TEXT, retained_after INTEGER NOT NULL DEFAULT 0, missing_retention INTEGER NOT NULL DEFAULT 0
            )""".trimIndent(),
        )
        db.execSQL("CREATE TABLE events (seq INTEGER PRIMARY KEY, event TEXT NOT NULL, recorded_at INTEGER NOT NULL DEFAULT 0)")
        db.insertOrThrow("state", null, ContentValues().apply {
            put("stream_id", newQaPerformanceId())
            put("ack", 0L)
            put("last_seq", 0L)
        })
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion == 1 && newVersion == 2) {
            db.execSQL("ALTER TABLE state ADD COLUMN retained_after INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE state ADD COLUMN missing_retention INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE events ADD COLUMN recorded_at INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE events SET recorded_at = ?", arrayOf(System.currentTimeMillis()))
        } else error("Unsupported QA performance migration")
    }

    fun state(): State = state(readableDatabase)

    fun append(runId: String, events: List<String>) = transaction { db ->
        val state = state(db)
        var seq = state.end
        for (event in events) {
            db.insertOrThrow("events", null, ContentValues().apply {
                put("seq", ++seq)
                put("event", event)
                put("recorded_at", System.currentTimeMillis())
            })
        }
        db.update("state", ContentValues().apply {
            put("last_seq", seq)
            put("run_id", runId)
        }, null, null)
        // Count and time bounds keep the read-only relay snapshot below its established 16 MiB cap.
        val ageFloor = db.rawQuery("SELECT MAX(seq) FROM events WHERE recorded_at < ?", arrayOf((System.currentTimeMillis() - RETENTION_MS).toString())).use {
            it.moveToFirst(); if (it.isNull(0)) 0L else it.getLong(0)
        }
        val floor = maxOf(state.retainedAfter, seq - MAX_EVENTS, ageFloor)
        if (floor > state.retainedAfter) {
            val removed = db.delete("events", "seq <= ?", arrayOf(floor.toString()))
            db.update("state", ContentValues().apply {
                put("retained_after", floor)
                put("missing_retention", state.missingRetention + removed)
            }, null, null)
        }
    }

    /** Returns a contiguous prefix of (after, through] bounded by both the wire size and row count. */
    fun read(after: Long, through: Long, accept: (Long, String) -> Boolean): Long {
        var cursor = after
        readableDatabase.rawQuery(
            "SELECT seq, event FROM events WHERE seq > ? AND seq <= ? ORDER BY seq LIMIT 128",
            arrayOf(after.toString(), through.toString()),
        ).use { rows ->
            while (rows.moveToNext()) {
                val seq = rows.getLong(0)
                check(seq == cursor + 1) { "Missing QA performance sequence" }
                if (!accept(seq, rows.getString(1))) break
                cursor = seq
            }
        }
        return cursor
    }

    fun acknowledge(through: Long) = transaction { db ->
        val state = state(db)
        require(through in state.ack..state.end)
        if (through > state.ack) {
            db.update("state", ContentValues().apply { put("ack", through) }, null, null)
            db.delete("events", "seq <= ?", arrayOf(through.toString()))
        }
    }

    private fun state(db: SQLiteDatabase): State =
        db.rawQuery("SELECT stream_id, ack, last_seq, run_id, retained_after, missing_retention FROM state", null).use { row ->
            check(row.moveToFirst())
            State(row.getString(0), row.getLong(1), row.getLong(2), row.getString(3), row.getLong(4), row.getLong(5))
        }

    companion object {
        const val MAX_EVENTS = 4096L
        const val RETENTION_MS = 24L * 60 * 60 * 1000
    }

    private inline fun <T> transaction(block: (SQLiteDatabase) -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val result = block(db)
            db.setTransactionSuccessful()
            return result
        } finally {
            db.endTransaction()
        }
    }
}

internal fun newQaPerformanceId(): String = UUID.randomUUID().toString().replace("-", "")
