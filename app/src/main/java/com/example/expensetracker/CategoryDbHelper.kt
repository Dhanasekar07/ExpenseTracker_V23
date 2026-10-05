package com.example.expensetracker

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class CategoryDbHelper(context: Context) :
    SQLiteOpenHelper(context, "categories.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE categories (
                id          TEXT PRIMARY KEY,
                name        TEXT NOT NULL,
                icon_name   TEXT NOT NULL,
                color_hex   TEXT NOT NULL,
                chart_color TEXT NOT NULL DEFAULT '',
                icon_tint   TEXT NOT NULL DEFAULT '',
                is_active   INTEGER NOT NULL DEFAULT 0,
                sort_order  INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        if (old < 2) {
            db.execSQL("ALTER TABLE categories ADD COLUMN chart_color TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE categories ADD COLUMN icon_tint TEXT NOT NULL DEFAULT ''")
        }
    }

    fun insertCategory(cat: Category) {
        writableDatabase.insertWithOnConflict("categories", null, ContentValues().apply {
            put("id",          cat.id)
            put("name",        cat.name)
            put("icon_name",   cat.iconName)
            put("color_hex",   cat.colorHex)
            put("chart_color", cat.chartColor)
            put("icon_tint",   cat.iconTint)
            put("is_active",   if (cat.isActive) 1 else 0)
            put("sort_order",  cat.sortOrder)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getAllCategories(): List<Category> {
        val list = mutableListOf<Category>()
        val c = readableDatabase.query(
            "categories", null, null, null, null, null, "sort_order ASC"
        )
        c.use {
            while (it.moveToNext()) {
                val ccIdx = it.getColumnIndex("chart_color")
                val itIdx = it.getColumnIndex("icon_tint")
                list.add(Category(
                    id         = it.getString(it.getColumnIndexOrThrow("id")),
                    name       = it.getString(it.getColumnIndexOrThrow("name")),
                    iconName   = it.getString(it.getColumnIndexOrThrow("icon_name")),
                    colorHex   = it.getString(it.getColumnIndexOrThrow("color_hex")),
                    chartColor = if (ccIdx >= 0) it.getString(ccIdx) ?: "" else "",
                    iconTint   = if (itIdx >= 0) it.getString(itIdx) ?: "" else "",
                    isActive   = it.getInt(it.getColumnIndexOrThrow("is_active")) == 1,
                    sortOrder  = it.getInt(it.getColumnIndexOrThrow("sort_order"))
                ))
            }
        }
        return list
    }

    fun updateCategoryActive(id: String, isActive: Boolean) {
        writableDatabase.update("categories",
            ContentValues().apply { put("is_active", if (isActive) 1 else 0) },
            "id = ?", arrayOf(id))
    }

    fun updateSortOrder(id: String, order: Int) {
        writableDatabase.update("categories",
            ContentValues().apply { put("sort_order", order) },
            "id = ?", arrayOf(id))
    }

    fun renameCategory(id: String, name: String) {
        writableDatabase.update("categories",
            ContentValues().apply { put("name", name) },
            "id = ?", arrayOf(id))
    }

    fun deleteCategory(id: String) {
        writableDatabase.delete("categories", "id = ?", arrayOf(id))
    }
}
