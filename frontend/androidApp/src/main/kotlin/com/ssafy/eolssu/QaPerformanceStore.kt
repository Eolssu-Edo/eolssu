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
    1,
) {
    data class State(val streamId: String, val ack: Long, val end: Long, val runId: String?)

    override fun onConfigure(db: SQLiteDatabase) {
        db.execSQL("PRAGMA synchronous = FULL")
        if (db.version == 0) db.execSQL("PRAGMA auto_vacuum = FULL")
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE state (
                stream_id TEXT NOT NULL, ack INTEGER NOT NULL, last_seq INTEGER NOT NULL, run_id TEXT
            )""".trimIndent(),
        )
        db.execSQL("CREATE TABLE events (seq INTEGER PRIMARY KEY, event TEXT NOT NULL)")
        db.insertOrThrow("state", null, ContentValues().apply {
            put("stream_id", newQaPerformanceId())
            put("ack", 0L)
            put("last_seq", 0L)
        })
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("No QA performance migration from $oldVersion to $newVersion")
    }

    fun state(): State = state(readableDatabase)

    fun append(runId: String, events: List<String>) = transaction { db ->
        val state = state(db)
        var seq = state.end
        for (event in events) {
            db.insertOrThrow("events", null, ContentValues().apply {
                put("seq", ++seq)
                put("event", event)
            })
        }
        db.update("state", ContentValues().apply {
            put("last_seq", seq)
            put("run_id", runId)
        }, null, null)
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
        db.rawQuery("SELECT stream_id, ack, last_seq, run_id FROM state", null).use { row ->
            check(row.moveToFirst())
            State(row.getString(0), row.getLong(1), row.getLong(2), row.getString(3))
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
