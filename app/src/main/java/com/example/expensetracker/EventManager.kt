package com.example.expensetracker

import android.content.Context
import android.graphics.Color

data class Event(
    val id        : String,
    val name      : String,
    val colorHex  : String,   // icon background (pastel)
    val iconTint  : String,   // dark tint for the icon/letter
    var isActive  : Boolean = true,
    var sortOrder : Int = 0
) {
    fun color()   = parseC(colorHex)
    fun tintCol() = parseC(iconTint.ifEmpty { "#666666" })
    private fun parseC(hex: String) = try { Color.parseColor(hex) } catch (e: Exception) { Color.GRAY }
}

/**
 * Mirrors CategoryManager, but for Events. Events are a separate tagging layer
 * (Option B): categories are never touched by this. Reuses CategoryIcon's
 * bold-first-letter fallback for the icon/chip rendering, since an event name
 * never matches a predefined category icon.
 */
object EventManager {

    // Color pool reserved for events, kept separate from CategoryManager.CUSTOM_COLORS
    // so an event's badge color never collides with any category's color.
    val EVENT_COLORS = listOf(
        Triple("#FBE4ED", "#D4537E", "#8A2C4E"), // rose
        Triple("#FFF1D6", "#EF9F27", "#8A5A0C"), // amber
        Triple("#E3F2EC", "#2D9D6F", "#166242"), // green
        Triple("#E6EEFD", "#3B6FE0", "#1E3F8C"), // blue
        Triple("#F1E6FB", "#8A4FE0", "#4F258C"), // violet
        Triple("#FDE8E6", "#E0593B", "#8C2E1E"), // terracotta
        Triple("#E6F7FA", "#1FA6C2", "#0F5A6B"), // teal
        Triple("#F6F0E2", "#B0842E", "#6B4E15"), // ochre
        // 12 more, chosen to stay visually distinct from every category colour
        Triple("#F6F8EF", "#C0CC8F", "#606648"),
        Triple("#F8EFF7", "#CC8FC9", "#664864"),
        Triple("#F1E4F1", "#993D99", "#4C1E4C"),
        Triple("#F8F7E2", "#CCC433", "#66621A"),
        Triple("#F8F2EF", "#CCA48F", "#665248"),
        Triple("#EDE5EA", "#80466B", "#402336"),
        Triple("#EBEDE8", "#708059", "#38402C"),
        Triple("#E0E6ED", "#205080", "#102840"),
        Triple("#F4E9E5", "#B26247", "#593124"),
        Triple("#EFF1F8", "#8F9BCC", "#484E66"),
        Triple("#F0F4E5", "#92B247", "#495924"),
        Triple("#EFF8F7", "#8FCCC6", "#486663")
    )

    var allEvents: List<Event> = emptyList()
        private set

    val activeEvents: List<Event>
        get() = allEvents.filter { it.isActive }.sortedBy { it.sortOrder }

    val archivedEvents: List<Event>
        get() = allEvents.filter { !it.isActive }.sortedBy { it.name.lowercase() }

    fun initialize(context: Context) {
        allEvents = EventDbHelper(context).getAllEvents()
    }

    fun getEventById(id: String?): Event? {
        if (id == null) return null
        return allEvents.find { it.id == id }
    }

    /**
     * Unused colour first; once the palette is exhausted, the least-used colour
     * (ties -> first in palette order). Archived events don't hold a colour.
     */
    fun getNextEventColor(): Triple<String, String, String> {
        val usage = allEvents.filter { it.isActive }.groupingBy { it.colorHex }.eachCount()
        return EVENT_COLORS.minByOrNull { usage[it.first] ?: 0 } ?: EVENT_COLORS[0]
    }

    /** Creates a new active event at the end of the sort order, returns it. */
    fun createEvent(context: Context, rawName: String): Event {
        val name = CategoryManager.normalizeName(rawName)
        val colors = getNextEventColor()
        val maxOrder = allEvents.maxOfOrNull { it.sortOrder } ?: -1
        val ev = Event(
            id        = "event_${System.currentTimeMillis()}",
            name      = name,
            colorHex  = colors.first,
            iconTint  = colors.third,
            isActive  = true,
            sortOrder = maxOrder + 1
        )
        EventDbHelper(context).insertEvent(ev)
        initialize(context)
        return allEvents.find { it.id == ev.id } ?: ev
    }

    fun archiveEvent(context: Context, id: String) {
        EventDbHelper(context).updateEventActive(id, false)
        initialize(context)
    }

    fun restoreEvent(context: Context, id: String) {
        val db = EventDbHelper(context)
        db.updateEventActive(id, true)
        val maxOrder = allEvents.filter { it.isActive }.maxOfOrNull { it.sortOrder } ?: -1
        db.updateSortOrder(id, maxOrder + 1)
        initialize(context)
    }

    fun renameEvent(context: Context, id: String, name: String) {
        EventDbHelper(context).renameEvent(id, name)
        initialize(context)
    }

    /**
     * Deletes an event. When [deleteTransactions] is false the transactions stay
     * (they just lose the event tag); when true they are permanently deleted too.
     */
    fun deleteEvent(context: Context, id: String, deleteTransactions: Boolean) {
        val expenses = ExpenseDbHelper(context)
        if (deleteTransactions) expenses.deleteExpensesForEvent(id) else expenses.clearEventTag(id)
        EventDbHelper(context).deleteEvent(id)
        initialize(context)
    }

    /** True if a category (active or archived) already uses this name. */
    fun categoryNameExists(context: Context, name: String): Boolean {
        CategoryManager.initialize(context)
        return CategoryManager.allCategories.any { it.name.equals(name, ignoreCase = true) }
    }

    /** True if an event (active or archived) already uses this name. */
    fun eventNameExists(context: Context, name: String): Boolean {
        initialize(context)
        return allEvents.any { it.name.equals(name, ignoreCase = true) }
    }

    fun nameExists(name: String, excludeId: String? = null): Boolean =
        allEvents.any { it.id != excludeId && it.name.equals(name, ignoreCase = true) }
}
