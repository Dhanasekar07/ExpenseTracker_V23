package com.example.expensetracker

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Expense(
    val id        : String = "",
    val category  : String,
    val amount    : Double,
    val source    : String,
    val channel   : String,
    val timestamp : Long,
    val eventId   : String? = null
)

class ExpenseDbHelper(context: Context) :
    SQLiteOpenHelper(context, "expenses.db", null, 4) {

    companion object { const val TABLE = "expenses" }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE (
                id        TEXT PRIMARY KEY,
                category  TEXT NOT NULL,
                amount    REAL,
                source    TEXT,
                channel   TEXT,
                timestamp INTEGER NOT NULL,
                event_id  TEXT
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        // Safe upgrade — keep data, just ensure id column exists
        try {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN id TEXT")
        } catch (e: Exception) { /* column already exists */ }
        // Update any rows missing an id
        // (one UUID per row: a single UPDATE with a subquery would give every row the same id)
        try {
            val rowIds = mutableListOf<Long>()
            db.rawQuery("SELECT rowid FROM $TABLE WHERE id IS NULL OR id = ''", null).use { c ->
                while (c.moveToNext()) rowIds.add(c.getLong(0))
            }
            rowIds.forEach { rid ->
                db.execSQL("UPDATE $TABLE SET id = ? WHERE rowid = ?",
                    arrayOf<Any>(java.util.UUID.randomUUID().toString(), rid))
            }
        } catch (e: Exception) { e.printStackTrace() }
        // v4: nullable event_id for the Events tagging feature
        if (old < 4) {
            try { db.execSQL("ALTER TABLE $TABLE ADD COLUMN event_id TEXT") }
            catch (e: Exception) { /* column already exists */ }
        }
    }

    fun insertExpense(
        category: String,
        amount  : Double,
        source  : String,
        channel : String = "unknown",
        eventId : String? = null,
        timestamp: Long = System.currentTimeMillis()
    ): Long {
        val id = java.util.UUID.randomUUID().toString()
        return try {
            writableDatabase.insert(TABLE, null, ContentValues().apply {
                put("id",        id)
                put("category",  category)
                put("amount",    amount)
                put("source",    source)
                put("channel",   channel)
                put("timestamp", timestamp)
                put("event_id",  eventId)
            })
        } catch (e: Exception) { e.printStackTrace(); -1L }
    }

    fun getExpenses(fromTs: Long = 0L, toTs: Long = Long.MAX_VALUE, eventId: String? = null): List<Expense> {
        val list = mutableListOf<Expense>()
        return try {
            val selection: String
            val args: Array<String>
            if (eventId != null) {
                selection = "timestamp >= ? AND timestamp <= ? AND event_id = ?"
                args = arrayOf(fromTs.toString(), toTs.toString(), eventId)
            } else {
                selection = "timestamp >= ? AND timestamp <= ?"
                args = arrayOf(fromTs.toString(), toTs.toString())
            }
            val c = readableDatabase.query(
                TABLE, null, selection, args, null, null, "timestamp DESC"
            )
            c.use {
                val eventIdIdx = it.getColumnIndex("event_id")
                while (it.moveToNext()) {
                    list.add(Expense(
                        id        = it.getString(it.getColumnIndexOrThrow("id")) ?: "",
                        category  = it.getString(it.getColumnIndexOrThrow("category")) ?: "",
                        amount    = it.getDouble(it.getColumnIndexOrThrow("amount")),
                        source    = it.getString(it.getColumnIndexOrThrow("source")) ?: "",
                        channel   = it.getString(it.getColumnIndexOrThrow("channel")) ?: "",
                        timestamp = it.getLong(it.getColumnIndexOrThrow("timestamp")),
                        eventId   = if (eventIdIdx >= 0) it.getString(eventIdIdx) else null
                    ))
                }
            }
            list
        } catch (e: Exception) { e.printStackTrace(); list }
    }

    fun getTotalByCategory(fromTs: Long = 0L, toTs: Long = Long.MAX_VALUE, eventId: String? = null): Map<String, Double> {
        val map = mutableMapOf<String, Double>()
        return try {
            val sql: String
            val args: Array<String>
            if (eventId != null) {
                sql = "SELECT category, SUM(amount) FROM $TABLE WHERE timestamp >= ? AND timestamp <= ? AND event_id = ? GROUP BY category"
                args = arrayOf(fromTs.toString(), toTs.toString(), eventId)
            } else {
                sql = "SELECT category, SUM(amount) FROM $TABLE WHERE timestamp >= ? AND timestamp <= ? GROUP BY category"
                args = arrayOf(fromTs.toString(), toTs.toString())
            }
            val c = readableDatabase.rawQuery(sql, args)
            c.use { while (it.moveToNext()) map[it.getString(0)] = it.getDouble(1) }
            map
        } catch (e: Exception) { e.printStackTrace(); map }
    }

    /** Running total for a single event, regardless of date range — used for its card/row. */
    fun getTotalForEvent(eventId: String): Double {
        return try {
            val c = readableDatabase.rawQuery(
                "SELECT SUM(amount) FROM $TABLE WHERE event_id = ?",
                arrayOf(eventId)
            )
            c.use { if (it.moveToFirst()) it.getDouble(0) else 0.0 }
        } catch (e: Exception) { 0.0 }
    }

    fun getTransactionCount(category: String, fromTs: Long, toTs: Long, eventId: String? = null): Int {
        return try {
            val sql = StringBuilder("SELECT COUNT(*) FROM $TABLE WHERE category=? AND timestamp>=? AND timestamp<=?")
            val args = mutableListOf(category, fromTs.toString(), toTs.toString())
            if (eventId != null) { sql.append(" AND event_id=?"); args.add(eventId) }
            val c = readableDatabase.rawQuery(sql.toString(), args.toTypedArray())
            c.use { if (it.moveToFirst()) it.getInt(0) else 0 }
        } catch (e: Exception) { 0 }
    }

    /** Number of transactions tagged to an event (all time). */
    fun getTransactionCountForEvent(eventId: String): Int {
        return try {
            val c = readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM $TABLE WHERE event_id = ?", arrayOf(eventId)
            )
            c.use { if (it.moveToFirst()) it.getInt(0) else 0 }
        } catch (e: Exception) { 0 }
    }

    /** Sets (or clears, when eventId is null) the event tag of one transaction. */
    fun updateExpenseEvent(id: String, eventId: String?) {
        updateEventForIds(listOf(id), eventId)
    }

    /** Sets (or clears, when eventId is null) the event tag of several transactions at once. */
    fun updateEventForIds(ids: List<String>, eventId: String?) {
        if (ids.isEmpty()) return
        try {
            val values = ContentValues()
            if (eventId == null) values.putNull("event_id") else values.put("event_id", eventId)
            val db = writableDatabase
            db.beginTransaction()
            try {
                ids.forEach { db.update(TABLE, values, "id = ?", arrayOf(it)) }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        } catch (e: Exception) { e.printStackTrace() }
    }

    /** Removes the event tag from every transaction of an event; the transactions stay. */
    fun clearEventTag(eventId: String) {
        try {
            val values = ContentValues().apply { putNull("event_id") }
            writableDatabase.update(TABLE, values, "event_id = ?", arrayOf(eventId))
        } catch (e: Exception) { e.printStackTrace() }
    }

    /** Permanently deletes every transaction tagged to an event. */
    fun deleteExpensesForEvent(eventId: String) {
        try { writableDatabase.delete(TABLE, "event_id = ?", arrayOf(eventId)) }
        catch (e: Exception) { e.printStackTrace() }
    }

    /** Permanently deletes the given transactions. */
    fun deleteExpenses(ids: List<String>) {
        if (ids.isEmpty()) return
        try {
            val db = writableDatabase
            db.beginTransaction()
            try {
                ids.forEach { db.delete(TABLE, "id = ?", arrayOf(it)) }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun deleteExpense(id: String) {
        try { writableDatabase.delete(TABLE, "id = ?", arrayOf(id)) }
        catch (e: Exception) { e.printStackTrace() }
    }

    fun updateExpenseCategory(id: String, newCategory: String) {
        try {
            writableDatabase.update(TABLE,
                ContentValues().apply { put("category", newCategory) },
                "id = ?", arrayOf(id))
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun clearAll() {
        try { writableDatabase.delete(TABLE, null, null) }
        catch (e: Exception) { e.printStackTrace() }
    }

    /**
     * Returns category usage counts from the last N days, sorted descending.
     */
    fun getCategoryFrequency(lastDays: Int = 30): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        val cutoff = System.currentTimeMillis() - (lastDays.toLong() * 24 * 60 * 60 * 1000)
        return try {
            val c = readableDatabase.rawQuery(
                "SELECT category, COUNT(*) as cnt FROM $TABLE WHERE timestamp >= ? GROUP BY category ORDER BY cnt DESC",
                arrayOf(cutoff.toString())
            )
            c.use { while (it.moveToNext()) map[it.getString(0)] = it.getInt(1) }
            map
        } catch (e: Exception) { e.printStackTrace(); map }
    }
}
