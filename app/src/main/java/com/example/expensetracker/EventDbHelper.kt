package com.example.expensetracker

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Storage for Events. Mirrors CategoryDbHelper's structure.
 * Events are a separate tagging layer on top of transactions (Option B):
 * categories stay untouched, an event is just a nullable event_id on a transaction.
 */
class EventDbHelper(context: Context) :
    SQLiteOpenHelper(context, "events.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS events (
                id          TEXT PRIMARY KEY,
                name        TEXT NOT NULL,
                color_hex   TEXT NOT NULL,
                icon_tint   TEXT NOT NULL,
                is_active   INTEGER NOT NULL DEFAULT 1,
                sort_order  INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        // No prior versions yet.
    }

    fun insertEvent(ev: Event) {
        writableDatabase.insertWithOnConflict("events", null, ContentValues().apply {
            put("id",         ev.id)
            put("name",       ev.name)
            put("color_hex",  ev.colorHex)
            put("icon_tint",  ev.iconTint)
            put("is_active",  if (ev.isActive) 1 else 0)
            put("sort_order", ev.sortOrder)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getAllEvents(): List<Event> {
        val list = mutableListOf<Event>()
        val c = readableDatabase.query(
            "events", null, null, null, null, null, "sort_order ASC"
        )
        c.use {
            while (it.moveToNext()) {
                list.add(Event(
                    id        = it.getString(it.getColumnIndexOrThrow("id")),
                    name      = it.getString(it.getColumnIndexOrThrow("name")),
                    colorHex  = it.getString(it.getColumnIndexOrThrow("color_hex")),
                    iconTint  = it.getString(it.getColumnIndexOrThrow("icon_tint")),
                    isActive  = it.getInt(it.getColumnIndexOrThrow("is_active")) == 1,
                    sortOrder = it.getInt(it.getColumnIndexOrThrow("sort_order"))
                ))
            }
        }
        return list
    }

    fun updateEventActive(id: String, isActive: Boolean) {
        writableDatabase.update("events",
            ContentValues().apply { put("is_active", if (isActive) 1 else 0) },
            "id = ?", arrayOf(id))
    }

    fun updateSortOrder(id: String, order: Int) {
        writableDatabase.update("events",
            ContentValues().apply { put("sort_order", order) },
            "id = ?", arrayOf(id))
    }

    fun renameEvent(id: String, name: String) {
        writableDatabase.update("events",
            ContentValues().apply { put("name", name) },
            "id = ?", arrayOf(id))
    }

    fun deleteEvent(id: String) {
        writableDatabase.delete("events", "id = ?", arrayOf(id))
    }
}
