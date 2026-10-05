package com.example.expensetracker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class CategoryDbHelperTest {

    private lateinit var db: CategoryDbHelper

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.deleteDatabase("categories.db")
        db = CategoryDbHelper(ctx)
    }

    @After fun teardown() { db.close() }

    private fun testCategory(id: String = "test_1", name: String = "Test") =
        Category(id, name, "icon", "#FF0000", chartColor = "#CC0000", iconTint = "#990000", isActive = true, sortOrder = 0)

    @Test fun `insert and retrieve category`() {
        db.insertCategory(testCategory())
        val all = db.getAllCategories()
        assertEquals(1, all.size)
        assertEquals("Test", all[0].name)
        assertEquals("#FF0000", all[0].colorHex)
        assertTrue(all[0].isActive)
    }

    @Test fun `insert multiple categories`() {
        db.insertCategory(testCategory("a", "Alpha"))
        db.insertCategory(testCategory("b", "Beta"))
        assertEquals(2, db.getAllCategories().size)
    }

    @Test fun `categories return in sort order`() {
        db.insertCategory(Category("a", "Alpha", "i", "#000000", chartColor = "", iconTint = "", isActive = true, sortOrder = 2))
        db.insertCategory(Category("b", "Beta",  "i", "#000000", chartColor = "", iconTint = "", isActive = true, sortOrder = 0))
        db.insertCategory(Category("c", "Gamma", "i", "#000000", chartColor = "", iconTint = "", isActive = true, sortOrder = 1))
        val names = db.getAllCategories().map { it.name }
        assertEquals(listOf("Beta", "Gamma", "Alpha"), names)
    }

    @Test fun `updateSortOrder changes order`() {
        db.insertCategory(Category("a", "Alpha", "i", "#000000", chartColor = "", iconTint = "", isActive = true, sortOrder = 0))
        db.insertCategory(Category("b", "Beta",  "i", "#000000", chartColor = "", iconTint = "", isActive = true, sortOrder = 1))
        db.updateSortOrder("a", 5)
        val cats = db.getAllCategories()
        assertEquals("Beta", cats[0].name)
    }

    @Test fun `updateCategoryActive toggles state`() {
        db.insertCategory(testCategory())
        db.updateCategoryActive("test_1", false)
        assertFalse(db.getAllCategories()[0].isActive)
    }

    @Test fun `renameCategory changes name`() {
        db.insertCategory(testCategory())
        db.renameCategory("test_1", "Renamed")
        assertEquals("Renamed", db.getAllCategories()[0].name)
    }

    @Test fun `deleteCategory removes from database`() {
        db.insertCategory(testCategory())
        db.deleteCategory("test_1")
        assertTrue(db.getAllCategories().isEmpty())
    }

    @Test fun `deleteCategory with non-existent ID does not crash`() {
        db.deleteCategory("does_not_exist")
    }
}
