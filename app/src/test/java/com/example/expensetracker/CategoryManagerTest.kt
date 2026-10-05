package com.example.expensetracker

import android.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class CategoryManagerTest {

    @Test fun `catalog is not empty`() {
        assertTrue(CategoryManager.CATALOG.isNotEmpty())
    }

    @Test fun `catalog has 30 predefined categories`() {
        assertEquals(30, CategoryManager.CATALOG.size)
    }

    @Test fun `catalog has no duplicate IDs`() {
        val ids = CategoryManager.CATALOG.map { it.id }
        assertEquals("Duplicate category IDs", ids.size, ids.distinct().size)
    }

    @Test fun `catalog has no duplicate names`() {
        val names = CategoryManager.CATALOG.map { it.name }
        assertEquals("Duplicate category names", names.size, names.distinct().size)
    }

    @Test fun `every catalog entry has valid hex colorHex`() {
        val hex = Regex("^#[0-9A-Fa-f]{6}$")
        CategoryManager.CATALOG.forEach {
            assertTrue("Invalid colorHex '${it.colorHex}' for ${it.name}", hex.matches(it.colorHex))
        }
    }

    @Test fun `every catalog entry has valid chartColor`() {
        val hex = Regex("^#[0-9A-Fa-f]{6}$")
        CategoryManager.CATALOG.forEach {
            assertTrue("Invalid chartColor '${it.chartColor}' for ${it.name}", hex.matches(it.chartColor))
        }
    }

    @Test fun `every catalog entry has valid iconTint`() {
        val hex = Regex("^#[0-9A-Fa-f]{6}$")
        CategoryManager.CATALOG.forEach {
            assertTrue("Invalid iconTint '${it.iconTint}' for ${it.name}", hex.matches(it.iconTint))
        }
    }

    @Test fun `every catalog entry has non-blank name`() {
        CategoryManager.CATALOG.forEach {
            assertTrue("Blank name for ${it.id}", it.name.isNotBlank())
        }
    }

    @Test fun `default active IDs all exist in catalog`() {
        val ids = CategoryManager.CATALOG.map { it.id }.toSet()
        CategoryManager.DEFAULT_ACTIVE_IDS.forEach {
            assertTrue("Default '$it' not in catalog", it in ids)
        }
    }

    @Test fun `max categories is 40`() {
        assertEquals(40, CategoryManager.MAX_CATEGORIES)
    }

    @Test fun `category color returns valid int for valid hex`() {
        val cat = Category("t1", "Test", "icon", "#FF6600")
        assertNotEquals(Color.GRAY, cat.color())
    }

    @Test fun `category color returns gray for invalid hex`() {
        val cat = Category("t1", "Test", "icon", "not-a-color")
        assertEquals(Color.GRAY, cat.color())
    }

    @Test fun `food category exists in catalog`() {
        assertNotNull(CategoryManager.CATALOG.find { it.name == "Food" })
    }

    @Test fun `custom color pool has 10 entries`() {
        assertEquals(10, CategoryManager.CUSTOM_COLORS.size)
    }
}
