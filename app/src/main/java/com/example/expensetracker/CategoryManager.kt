package com.example.expensetracker

import android.content.Context
import android.graphics.Color

data class Category(
    val id        : String,
    val name      : String,
    val iconName  : String,
    val colorHex  : String,       // light pastel — icon background
    val chartColor: String = "",  // mid-tone — pie/bar charts
    val iconTint  : String = "",  // dark — icon tint
    var isActive  : Boolean = false,
    var sortOrder : Int = 0
) {
    fun color()     = parseC(colorHex)
    fun chartCol()  = parseC(chartColor.ifEmpty { colorHex })
    fun tintCol()   = parseC(iconTint.ifEmpty { "#666666" })
    private fun parseC(hex: String) = try { Color.parseColor(hex) } catch (e: Exception) { Color.GRAY }
}

object CategoryManager {

    const val MAX_CATEGORIES = 40

    // 30 predefined categories with three-tier colours
    val CATALOG = listOf(
        Category("cat_01", "Food",              "restaurant",     "#FDBA74", "#F59E0B", "#C2410C"),
        Category("cat_02", "Tea/Coffee",        "coffee",         "#CAA47E", "#B45309", "#78350F"),
        Category("cat_03", "Snacks",            "snacks",         "#FED7AA", "#F97316", "#EA580C"),
        Category("cat_04", "Grocery",           "shopping_cart",  "#BBF7D0", "#22C55E", "#15803D"),
        Category("cat_05", "Shopping",          "shopping_bag",   "#D6BBFA", "#8B5CF6", "#7C3AED"),
        Category("cat_06", "Entertainment",     "entertainment",  "#FBCFE8", "#EC4899", "#BE185D"),
        Category("cat_07", "Movies",            "movies",         "#E9D5FF", "#A855F7", "#7E22CE"),
        Category("cat_08", "OTT",               "subscriptions",  "#C5CAE9", "#304FFE", "#1A237E"),
        Category("cat_09", "Fuel",              "fuel",           "#5EEAD4", "#14B8A6", "#115E59"),
        Category("cat_10", "Transport",         "transport",      "#67E8F9", "#06B6D4", "#155E75"),
        Category("cat_11", "Taxi/Ride",         "taxi",           "#FFE0B2", "#FF6F00", "#E65100"),
        Category("cat_12", "Medicine",          "medicine",       "#FCA5A5", "#EF4444", "#991B1B"),
        Category("cat_13", "Mutual Funds",      "investment",     "#BBDEFB", "#2196F3", "#0D47A1"),
        Category("cat_14", "Loan EMI",          "credit_card",    "#A7F3D0", "#10B981", "#065F46"),
        Category("cat_15", "Online Order",      "package",        "#D1C4E9", "#6200EA", "#3700B3"),
        Category("cat_16", "Personal Grooming", "grooming",       "#FDA4AF", "#FB7185", "#9F1239"),
        Category("cat_17", "Internet",          "internet",       "#7DD3FC", "#0EA5E9", "#075985"),
        Category("cat_18", "Electricity",       "electricity",    "#C7D2FE", "#6366F1", "#4338CA"),
        Category("cat_19", "Gas",               "gas",            "#BAE6FD", "#38BDF8", "#0C4A6E"),
        Category("cat_20", "House Rent",        "home",           "#93C5FD", "#3B82F6", "#1E40AF"),
        Category("cat_21", "Insurance Premium", "insurance",      "#C8E6C9", "#00C853", "#00701A"),
        Category("cat_22", "Education",         "education",      "#FFE0B2", "#E65100", "#BF360C"),
        Category("cat_23", "Fitness",           "fitness",        "#BEF264", "#84CC16", "#3F6212"),
        Category("cat_24", "Savings",           "savings",        "#E1BEE7", "#AA00FF", "#6A0080"),
        Category("cat_25", "Laundry",           "laundry",        "#B2DFDB", "#00BFA5", "#00796B"),
        Category("cat_26", "Mobile Recharge",   "phone",          "#A5F3FC", "#22D3EE", "#164E63"),
        Category("cat_27", "Travel",            "travel",         "#C5CAE9", "#5C6BC0", "#303464"),
        Category("cat_28", "Gifts",             "gifts",          "#F2C4CB", "#9B2335", "#5E1520"),
        Category("cat_29", "Donation",          "donation",       "#FFE4E6", "#F43F5E", "#BE123C"),
        Category("cat_30", "Monthly Bills",     "receipt",        "#F8BBD0", "#AD1457", "#600B30")
    )

    val DEFAULT_ACTIVE_IDS = listOf(
        "cat_01", "cat_02", "cat_03", "cat_04", "cat_05", "cat_06",
        "cat_07", "cat_09", "cat_10", "cat_15", "cat_18", "cat_20"
    )

    // Custom category colour pool (for user-created categories)
    val CUSTOM_COLORS = listOf(
        Triple("#F8BBD0", "#E91E63", "#880E4F"),  // Slate
        Triple("#C8E6C9", "#4CAF50", "#2E7D32"),  // Gray
        Triple("#FCE4EC", "#F50057", "#C51162"),  // Cool gray
        Triple("#FECDD3", "#E11D48", "#881337"),  // Rose
        Triple("#FCE7F3", "#D946EF", "#A21CAF"),  // Fuchsia
        Triple("#F3E5F5", "#D500F9", "#AA00FF"),  // Yellow
        Triple("#FCD34D", "#D97706", "#92400E"),  // Amber
        Triple("#FFF8E1", "#FFB300", "#FF8F00"),  // Purple
        Triple("#D7CCC8", "#795548", "#3E2723"),  // Teal
        Triple("#F0ABFC", "#C026D3", "#86198F"),  // Pink-purple
        // 10 more distinct colors for pie chart differentiation
        Triple("#F0F4C3", "#827717", "#524C00"),  // Olive
        Triple("#E8EAF6", "#283593", "#1A237E"),  // Navy blue
        Triple("#F1F8E9", "#76FF03", "#64DD17"),  // Orange
        Triple("#FBE9E7", "#FF3D00", "#DD2C00"),  // Indigo
        Triple("#E0F7FA", "#00B8D4", "#0097A7"),  // Red
        Triple("#CFD8DC", "#546E7A", "#29373D"),  // Emerald
        Triple("#B2EBF2", "#00838F", "#004D54"),  // Violet
        Triple("#C8E6C9", "#1B5E20", "#0D3311"),  // Gold
        Triple("#E0F2F1", "#1DE9B6", "#00BFA5"),  // Cyan-teal
        Triple("#D1C4E9", "#311B92", "#1A0E52")   // Deep pink
    )

    var allCategories: List<Category> = emptyList()
        private set

    var activeCategories: List<Category> = emptyList()
        private set

    val archivedCategories: List<Category>
        get() {
            val archived = allCategories.filter { !it.isActive }
            val systemIds = CATALOG.map { it.id }.toSet()
            val system = archived.filter { it.id in systemIds }.sortedBy { it.name.lowercase() }
            val custom = archived.filter { it.id !in systemIds }.sortedBy { it.name.lowercase() }
            return system + custom
        }

    fun initialize(context: Context) {
        val dbHelper = CategoryDbHelper(context)
        val stored   = dbHelper.getAllCategories()

        if (stored.isEmpty()) {
            CATALOG.forEachIndexed { i, cat ->
                val active = cat.id in DEFAULT_ACTIVE_IDS
                dbHelper.insertCategory(cat.copy(isActive = active, sortOrder = i))
            }
            allCategories    = dbHelper.getAllCategories()
            activeCategories = allCategories.filter { it.isActive }
        } else {
            allCategories    = stored
            activeCategories = stored.filter { it.isActive }
        }
    }

    fun normalizeName(name: String): String =
        name.trim().replace(Regex("^[^a-zA-Z0-9]+|[^a-zA-Z0-9]+$"), "").replace(Regex("\\s+"), " ")

    fun getCategoryByName(name: String): Category? {
        return activeCategories.find { it.name.equals(name, ignoreCase = true) }
            ?: CATALOG.find { it.name.equals(name, ignoreCase = true) }
    }

    /**
     * Unused colour first; once the palette is exhausted, the least-used colour
     * (ties -> first in palette order). Archived categories don't hold a colour.
     */
    fun getNextCustomColor(existing: List<Category>): Triple<String, String, String> {
        // keyed on the chart colour, which is unique per palette entry
        val usage = existing.filter { it.isActive }.groupingBy { it.chartColor }.eachCount()
        return CUSTOM_COLORS.minByOrNull { usage[it.second] ?: 0 } ?: CUSTOM_COLORS[0]
    }

    fun archiveCategory(context: Context, id: String) {
        CategoryDbHelper(context).updateCategoryActive(id, false)
        initialize(context)
    }

    fun activateCategory(context: Context, id: String) {
        val db = CategoryDbHelper(context)
        db.updateCategoryActive(id, true)
        // Set sort order to last position among active categories
        val maxOrder = allCategories.filter { it.isActive }.maxOfOrNull { it.sortOrder } ?: -1
        db.updateSortOrder(id, maxOrder + 1)
        initialize(context)
    }

    fun deleteCategory(context: Context, id: String) {
        CategoryDbHelper(context).deleteCategory(id)
        initialize(context)
    }

    fun renameCategory(context: Context, id: String, name: String) {
        CategoryDbHelper(context).renameCategory(id, name)
        initialize(context)
    }

    /**
     * Returns active categories sorted by usage frequency (last 30 days).
     * Categories with no usage go to the bottom, sorted alphabetically.
     */
    fun getActiveByFrequency(context: Context): List<Category> {
        val freq = ExpenseDbHelper(context).getCategoryFrequency(30)
        val active = activeCategories.toList()
        val withCount = active.map { it to (freq[it.name] ?: 0) }
        return withCount.sortedWith(
            compareByDescending<Pair<Category, Int>> { it.second }
                .thenBy { it.first.name.lowercase() }
        ).map { it.first }
    }

    fun getAllByFrequency(context: Context): List<Category> {
        val freq = ExpenseDbHelper(context).getCategoryFrequency(30)
        val all = allCategories.toList()
        val withCount = all.map { it to (freq[it.name] ?: 0) }
        return withCount.sortedWith(
            compareByDescending<Pair<Category, Int>> { it.second }
                .thenBy { it.first.name.lowercase() }
        ).map { it.first }
    }
}
