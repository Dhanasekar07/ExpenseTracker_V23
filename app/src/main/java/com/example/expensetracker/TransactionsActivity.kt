package com.example.expensetracker

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class TransactionsActivity : AppCompatActivity() {

    private lateinit var db       : ExpenseDbHelper
    private lateinit var container: LinearLayout

    private var currentFilter = "day"
    private var customFrom    = 0L
    private var customTo      = 0L
    private var searchQuery   = ""
    private var isStackEntry  = false
    private var currentCategoryFilter: String? = null
    private val expandedEventKeys = mutableSetOf<String>()
    private var rangeAll: List<Expense> = emptyList()   // unfiltered rows of the current period
    private val currency get() = AppPreferences.getCurrencySymbol(this)

    @Suppress("DEPRECATION")
    override fun onBackPressed() { finishAffinity() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transactions)

        db           = ExpenseDbHelper(this)
        container    = findViewById(R.id.transactionContainer)
        isStackEntry = intent.getStringExtra("entry") == "stack"

        try { CategoryManager.initialize(this) } catch (e: Exception) { e.printStackTrace() }
        try { EventManager.initialize(this) } catch (e: Exception) { e.printStackTrace() }

        val btnBack   = findViewById<ImageView>(R.id.btnBack)
        val btnSearch = findViewById<ImageView>(R.id.btnSearch)
        val searchBar = findViewById<LinearLayout>(R.id.searchBar)
        val etSearch  = searchBar.findViewById<EditText>(R.id.etSearch)

        btnBack.visibility = if (isStackEntry) View.VISIBLE else View.GONE
        btnBack.setOnClickListener { finish() }

        findViewById<ImageView>(R.id.btnAddTransaction).setOnClickListener {
            showAddTransactionDialog()
        }

        findViewById<ImageView>(R.id.btnCategoryFilter).setOnClickListener {
            showCategoryFilterDialog()
        }

        btnSearch.setOnClickListener {
            searchBar.visibility =
                if (searchBar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString()?.lowercase() ?: ""
                refreshList()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        setupFilterTabs()
        setupBottomNav()
        refreshList()
    }

    override fun onResume() {
        super.onResume()
        try { EventManager.initialize(this) } catch (e: Exception) { e.printStackTrace() }
        refreshList()
    }

    // ── Shared pieces for the picker dialogs ─────────────────────────────
    private fun newPickerCard(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(20).toFloat()
            setColor(Color.WHITE)
        }
        setPadding(dp(20), dp(18), dp(20), dp(20))
    }

    private fun newDialog(content: View): AlertDialog {
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog)
            .setView(content)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dialog
    }

    private fun showSized(dialog: AlertDialog) {
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    private fun hintText(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(Color.parseColor("#6B7280"))
        setPadding(0, dp(8), 0, dp(12))
    }

    private fun glyphIcon(glyph: String, bgHex: String, fgHex: String): FrameLayout {
        val box = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(10) }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor(bgHex))
            }
        }
        box.addView(TextView(this).apply {
            text = glyph
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor(fgHex))
            gravity = android.view.Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        })
        return box
    }

    private fun categoryIconView(cat: Category): FrameLayout {
        val box = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(10) }
        }
        CategoryIcon.applyIcon(box, cat.name, cat.colorHex, cat.iconTint, 16)
        return box
    }

    private fun eventIconView(ev: Event): FrameLayout {
        val box = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(10) }
        }
        CategoryIcon.applyIcon(box, ev.name, ev.colorHex, ev.iconTint, 16)
        return box
    }

    /** One tappable row in a picker: [icon] name ........ ✓ (when it is the current choice). */
    private fun pickRow(icon: View, name: String, selected: Boolean, onClick: () -> Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor(if (selected) "#E8F5E9" else "#F9FAFB"))
            }
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
            setOnClickListener { onClick() }
        }
        row.addView(icon)
        row.addView(TextView(this).apply {
            text = name
            textSize = 14f
            setTextColor(Color.parseColor("#1A1A2E"))
            typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        if (selected) {
            row.addView(TextView(this).apply {
                text = "✓"
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#2D6A4F"))
            })
        }
        return row
    }

    // ── Category filter (filter icon in the app bar) ─────────────────────
    private fun showCategoryFilterDialog() {
        val card = newPickerCard()
        val dialog = newDialog(card)
        EventDialogs.addHeader(this, card, "Filter by category", dialog)
        card.addView(hintText(
            currentCategoryFilter?.let { "Showing only $it" } ?: "Pick a category to see only its transactions"
        ))

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(320))
        }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        card.addView(scroll)

        fun choose(name: String?) {
            currentCategoryFilter = name
            val icon = findViewById<ImageView>(R.id.btnCategoryFilter)
            if (name != null) icon.setColorFilter(ThemeColors.brand(this)) else icon.clearColorFilter()
            dialog.dismiss()
            refreshList()
        }

        list.addView(pickRow(glyphIcon("All", "#EEF2FF", "#4F46E5"), "All Categories", currentCategoryFilter == null) { choose(null) })
        CategoryManager.activeCategories.sortedBy { it.name.lowercase() }.forEach { cat ->
            list.addView(pickRow(
                categoryIconView(cat), cat.name, cat.name.equals(currentCategoryFilter, ignoreCase = true)
            ) { choose(cat.name) })
        }
        showSized(dialog)
    }

    private fun setupFilterTabs() {
        mapOf(
            R.id.tabDay to "day", R.id.tabWeek to "week",
            R.id.tabMonth to "month", R.id.tabCustom to "custom"
        ).forEach { (id, filter) ->
            findViewById<TextView>(id).setOnClickListener {
                if (filter == "custom") showDatePicker()
                else { currentFilter = filter; updateTabUI(); refreshList() }
            }
        }
        updateTabUI()
    }

    private fun updateTabUI() {
        listOf(
            R.id.tabDay to "day", R.id.tabWeek to "week",
            R.id.tabMonth to "month", R.id.tabCustom to "custom"
        ).forEach { (id, f) ->
            val tv = findViewById<TextView>(id)
            if (f == currentFilter) {
                tv.setBackgroundResource(R.drawable.bg_filter_active)
                tv.setTextColor(Color.WHITE)
            } else {
                tv.setBackgroundResource(R.drawable.bg_filter_inactive)
                tv.setTextColor(ThemeColors.secondary(this))
            }
        }
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            val from = Calendar.getInstance().apply {
                set(y, m, d, 0, 0, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            DatePickerDialog(this, { _, y2, m2, d2 ->
                val to = Calendar.getInstance().apply {
                    set(y2, m2, d2, 23, 59, 59); set(Calendar.MILLISECOND, 999)
                }.timeInMillis
                customFrom = from; customTo = to; currentFilter = "custom"
                updateTabUI(); refreshList()
            }, y, m, d).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun getFromTs(): Long {
        val cal = Calendar.getInstance()
        return when (currentFilter) {
            "day"    -> { cal.set(Calendar.HOUR_OF_DAY,0); cal.set(Calendar.MINUTE,0); cal.set(Calendar.SECOND,0); cal.set(Calendar.MILLISECOND,0); cal.timeInMillis }
            "week"   -> { cal.add(Calendar.DAY_OF_YEAR,-7); cal.timeInMillis }
            "month"  -> { cal.set(Calendar.DAY_OF_MONTH,1); cal.set(Calendar.HOUR_OF_DAY,0); cal.set(Calendar.MINUTE,0); cal.set(Calendar.SECOND,0); cal.set(Calendar.MILLISECOND,0); cal.timeInMillis }
            "custom" -> customFrom
            else     -> 0L
        }
    }

    private fun getToTs() =
        if (currentFilter == "custom") customTo else System.currentTimeMillis()

    private fun refreshList() {
        container.removeAllViews()

        var expenses = db.getExpenses(getFromTs(), getToTs())
        rangeAll = expenses

        val categoryFilterActive = currentCategoryFilter != null
        if (categoryFilterActive) {
            expenses = expenses.filter { it.category.equals(currentCategoryFilter, ignoreCase = true) }
        }

        if (searchQuery.isNotEmpty()) {
            expenses = expenses.filter {
                it.category.lowercase().contains(searchQuery) ||
                it.source.lowercase().contains(searchQuery)
            }
        }

        if (expenses.isEmpty()) {
            container.addView(TextView(this).apply {
                text     = "No transactions found"
                textSize = 14f
                setTextColor(ThemeColors.hint(this))
                gravity  = android.view.Gravity.CENTER
                setPadding(0, 48, 0, 48)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
            return
        }

        val dateFmt   = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today     = dateFmt.format(Date())
        val yesterday = dateFmt.format(Date(System.currentTimeMillis() - 86_400_000L))
        val grouped   = expenses.groupBy { dateFmt.format(Date(it.timestamp)) }

        grouped.forEach { (dateKey, txns) ->
            val headerText = when (dateKey) {
                today     -> "TODAY"
                yesterday -> "YESTERDAY"
                else -> {
                    val parsed = dateFmt.parse(dateKey)
                    SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                        .format(parsed ?: Date()).uppercase()
                }
            }

            // Day header — pill badge
            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dp(12), 0, dp(8)) }
            }

            val pill = TextView(this).apply {
                text = headerText
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#2D6A4F"))
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = dp(13).toFloat()
                    setColor(Color.parseColor("#E8F5E9"))
                }
                setPadding(dp(14), dp(6), dp(14), dp(6))
            }
            headerRow.addView(pill)

            container.addView(headerRow)

            if (categoryFilterActive) {
                // Flattened view — category filter is active, no per-day event collapse.
                txns.forEach { expense -> addTransactionRow(container, expense) }
            } else {
                val plain  = txns.filter { it.eventId == null }
                val tagged = txns.filter { it.eventId != null }

                plain.forEach { expense -> addTransactionRow(container, expense) }

                tagged.groupBy { it.eventId }.forEach { (eventId, evTxns) ->
                    addEventGroupRow(container, dateKey, headerText, eventId!!, evTxns)
                }
            }
        }
    }

    /** Collapsible row for a day's transactions tagged to one event, nested by category. */
    private fun addEventGroupRow(
        parent: LinearLayout, dateKey: String, dayLabel: String, eventId: String, txns: List<Expense>
    ) {
        val ev = EventManager.getEventById(eventId)
        val evName = ev?.name ?: "Event"
        val key = "$dateKey|$eventId"
        val total = txns.sumOf { it.amount }
        val count = txns.size
        val plural = if (count == 1) "" else "s"

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = android.view.Gravity.CENTER_VERTICAL
            background  = getDrawable(R.drawable.bg_normal_row)
            setPadding(dp(10), dp(0), dp(10), dp(0))
            elevation   = 1f * resources.displayMetrics.density
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56)
            ).apply { setMargins(0, 0, 0, dp(6)) }
        }

        val iconBg = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(35), dp(35)).apply { setMargins(0, 0, dp(10), 0) }
        }
        CategoryIcon.applyIcon(iconBg, evName, ev?.colorHex ?: "#E2E8F0", ev?.iconTint ?: "#475569")

        val info = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        info.addView(TextView(this).apply {
            text = evName; textSize = 15f
            setTextColor(ThemeColors.primary(this))
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        info.addView(TextView(this).apply {
            text = "$count transaction$plural"; textSize = 12f
            setTextColor(ThemeColors.hint(this))
        })

        val totalText = "$currency${String.format("%.0f", total)}"
        val amt = TextView(this).apply {
            text = totalText
            textSize = 15f
            setTextColor(ThemeColors.amountNeg(this))
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(4), 0, dp(6), 0) }
        }

        // Edit = reassign this whole group to another event
        val btnEdit = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply { setMargins(0, 0, dp(4), 0) }
            setImageResource(R.drawable.ic_edit)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        // Edit / delete always act on the WHOLE day-group, even while a search hides some rows
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val fullGroup = rangeAll.filter {
            it.eventId == eventId && dayFmt.format(Date(it.timestamp)) == dateKey
        }.ifEmpty { txns }
        btnEdit.setOnClickListener { showReassignEventForGroup(evName, fullGroup) }

        // Delete = remove from event, or delete the transactions (user chooses)
        val btnDelete = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply { setMargins(0, 0, dp(4), 0) }
            setImageResource(R.drawable.ic_delete)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        btnDelete.setOnClickListener {
            val ids = fullGroup.map { it.id }
            val fCount = fullGroup.size
            val fPlural = if (fCount == 1) "" else "s"
            val fTotalText = "$currency${String.format("%.0f", fullGroup.sumOf { it.amount })}"
            EventDialogs.showTwoOptionDialog(
                this,
                title = "$evName · $dayLabel",
                message = "$fCount transaction$fPlural · $fTotalText. What should happen to them?",
                firstTitle = "Delete Event Only",
                firstSub = "Keep the transaction$fPlural, just take them out of $evName",
                secondTitle = "Delete Transactions",
                secondSub = "Permanently delete $fCount transaction$fPlural ($fTotalText)",
                onFirst = { db.updateEventForIds(ids, null); refreshList() },
                onSecond = { db.deleteExpenses(ids); refreshList() }
            )
        }

        val chevron = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
            setImageResource(R.drawable.ic_chevron_down)
            rotation = if (key in expandedEventKeys) 180f else 0f
        }

        header.addView(iconBg); header.addView(info); header.addView(amt)
        header.addView(btnEdit); header.addView(btnDelete); header.addView(chevron)
        parent.addView(header)

        val childContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, 0, 0)
            visibility = if (key in expandedEventKeys) View.VISIBLE else View.GONE
        }
        txns.groupBy { it.category }.forEach { (catName, catTxns) ->
            val cat = try { CategoryManager.getCategoryByName(catName) } catch (e: Exception) { null }
            val tint = try { Color.parseColor(cat?.iconTint ?: "#6B7280") } catch (e: Exception) { Color.parseColor("#6B7280") }
            val catTotal = catTxns.sumOf { it.amount }

            // Category sub-header: NAME ............ total for that category
            val subHeader = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(4), dp(8), dp(6), dp(4))
            }
            subHeader.addView(TextView(this).apply {
                text = catName.uppercase()
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tint)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            subHeader.addView(TextView(this).apply {
                text = "$currency${String.format("%.0f", catTotal)}"
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tint)
            })
            childContainer.addView(subHeader)

            catTxns.forEach { expense -> addTransactionRow(childContainer, expense) }
        }
        parent.addView(childContainer)

        header.setOnClickListener {
            if (key in expandedEventKeys) {
                expandedEventKeys.remove(key)
                childContainer.visibility = View.GONE
                chevron.rotation = 0f
            } else {
                expandedEventKeys.add(key)
                childContainer.visibility = View.VISIBLE
                chevron.rotation = 180f
            }
        }
    }

    /** Move every transaction of a day's event group to another event (or to "No Event"). */
    private fun showReassignEventForGroup(currentName: String, txns: List<Expense>) {
        val card = newPickerCard()
        val dialog = newDialog(card)
        EventDialogs.addHeader(this, card, "Reassign Event", dialog)
        val n = txns.size
        card.addView(hintText("Move $n transaction${if (n == 1) "" else "s"} from $currentName to:"))

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(280))
        }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        card.addView(scroll)

        val ids = txns.map { it.id }
        val currentId = txns.firstOrNull()?.eventId
        fun move(target: String?) {
            db.updateEventForIds(ids, target)
            dialog.dismiss()
            refreshList()
        }
        list.addView(pickRow(glyphIcon("–", "#F3F4F6", "#6B7280"), "No Event", false) { move(null) })
        EventManager.activeEvents.filter { it.id != currentId }.forEach { ev ->
            list.addView(pickRow(eventIconView(ev), ev.name, false) { move(ev.id) })
        }
        showSized(dialog)
    }

    private fun addTransactionRow(target: LinearLayout, expense: Expense) {
        val cat      = try { CategoryManager.getCategoryByName(expense.category) } catch (e: Exception) { null }
        val colorHex = cat?.colorHex ?: "#F0F2F5"
        val sdf      = SimpleDateFormat("hh:mm a", Locale.getDefault())

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = android.view.Gravity.CENTER_VERTICAL
            background  = getDrawable(R.drawable.bg_normal_row)
            setPadding(dp(10), dp(0), dp(10), dp(0))
            elevation   = 1f * resources.displayMetrics.density
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
            ).apply { setMargins(0, 0, 0, dp(6)) }
        }

        val iconBg = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(35), dp(35)).apply { setMargins(0, 0, dp(10), 0) }
        }
        CategoryIcon.applyIcon(iconBg, expense.category,
            cat?.colorHex ?: "#E2E8F0",
            cat?.iconTint ?: "#475569"
        )

        val info = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        info.addView(TextView(this).apply {
            text = expense.category; textSize = 15f
            setTextColor(ThemeColors.primary(this))
            typeface = Typeface.DEFAULT_BOLD
        })
        info.addView(TextView(this).apply {
            text = sdf.format(Date(expense.timestamp)); textSize = 12f
            setTextColor(ThemeColors.hint(this))
        })

        val amt = TextView(this).apply {
            text = if (expense.amount > 0)
                "$currency${String.format("%.0f", expense.amount)}" else "$currency-"
            textSize = 15f
            setTextColor(ThemeColors.amountNeg(this))
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, dp(6), 0) }
        }

        val btnEdit = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply { setMargins(0, 0, dp(4), 0) }
            setImageResource(R.drawable.ic_edit)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        btnEdit.setOnClickListener { showReassignDialog(expense) }

        val btnDelete = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
            setImageResource(R.drawable.ic_delete)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        btnDelete.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete Transaction")
                .setMessage(getString(R.string.delete_transaction_confirmation))
                .setPositiveButton("Delete") { _, _ ->
                    db.deleteExpense(expense.id); refreshList()
                }
                .setNegativeButton("Cancel", null)
                .create().apply {
                    setOnShowListener {
                        getButton(AlertDialog.BUTTON_POSITIVE)
                            .setTextColor(ThemeColors.amountNeg(this@TransactionsActivity))
                    }
                }.show()
        }

        row.addView(iconBg); row.addView(info)
        row.addView(amt); row.addView(btnEdit); row.addView(btnDelete)
        target.addView(row)
    }

    /**
     * Edit icon on a transaction row.
     * Opens on "Reassign Category" (category list). The "Reassign Event" bar sits below it;
     * tapping it swaps the category list for the event list, and tapping "Reassign Category" swaps back.
     */
    private fun showReassignDialog(expense: Expense) {
        val card = newPickerCard()
        val dialog = newDialog(card)

        val sym = try { AppPreferences.getCurrencySymbol(this) } catch (_: Exception) { "₹" }
        EventDialogs.addHeader(this, card, "$sym${String.format("%.2f", expense.amount)}", dialog)
        card.addView(hintText("Choose what to change for this transaction"))

        fun sectionBar(label: String): TextView = TextView(this).apply {
            text = label
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44)
            ).apply { bottomMargin = dp(8) }
        }
        fun listScroll(): ScrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(250))
        }

        val catBar = sectionBar("Reassign Category")
        val catScroll = listScroll()
        val catList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        catScroll.addView(catList)

        val evtBar = sectionBar("Reassign Event")
        val evtScroll = listScroll()
        val evtList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        evtScroll.addView(evtList)

        card.addView(catBar); card.addView(catScroll)
        card.addView(evtBar); card.addView(evtScroll)

        fun showSection(categories: Boolean) {
            catScroll.visibility = if (categories) View.VISIBLE else View.GONE
            evtScroll.visibility = if (categories) View.GONE else View.VISIBLE
            fun style(bar: TextView, active: Boolean, label: String) {
                bar.text = (if (active) "▾  " else "▸  ") + label
                bar.setTextColor(Color.parseColor(if (active) "#2D6A4F" else "#6B7280"))
                bar.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(12).toFloat()
                    setColor(Color.parseColor(if (active) "#E8F5E9" else "#F3F4F6"))
                }
            }
            style(catBar, categories, "Reassign Category")
            style(evtBar, !categories, "Reassign Event")
        }
        catBar.setOnClickListener { showSection(true) }
        evtBar.setOnClickListener { showSection(false) }

        // Categories (active only)
        CategoryManager.activeCategories.forEach { cat ->
            catList.addView(pickRow(
                categoryIconView(cat), cat.name, cat.name.equals(expense.category, ignoreCase = true)
            ) {
                db.updateExpenseCategory(expense.id, cat.name)
                dialog.dismiss()
                refreshList()
            })
        }

        // Events: "No Event" first, then the active events
        fun setEvent(target: String?) {
            db.updateExpenseEvent(expense.id, target)
            dialog.dismiss()
            refreshList()
        }
        evtList.addView(pickRow(
            glyphIcon("–", "#F3F4F6", "#6B7280"), "No Event", expense.eventId == null
        ) { setEvent(null) })
        val activeEvents = EventManager.activeEvents
        activeEvents.forEach { ev ->
            evtList.addView(pickRow(eventIconView(ev), ev.name, ev.id == expense.eventId) { setEvent(ev.id) })
        }
        if (activeEvents.isEmpty()) {
            evtList.addView(hintText("No events yet. Create one from the Categories page or the popup."))
        }

        showSection(true)
        showSized(dialog)
    }


    private fun setupBottomNav() {
        listOf(
            Triple(R.id.navHomePill, R.id.navHomeIcon, R.id.navHomeLabel),
            Triple(R.id.navTransactionsPill, R.id.navTransactionsIcon, R.id.navTransactionsLabel),
            Triple(R.id.navCategoryPill, R.id.navCategoryIcon, R.id.navCategoryLabel),
            Triple(R.id.navSettingsPill, R.id.navSettingsIcon, R.id.navSettingsLabel)
        ).forEach { (p, i, l) ->
            findViewById<LinearLayout>(p).background = null
            findViewById<ImageView>(i).apply { alpha = 0.5f; clearColorFilter() }
            findViewById<TextView>(l).setTextColor(ThemeColors.hint(this))
        }

        // Highlight Transactions if root nav
        if (!isStackEntry) {
            findViewById<LinearLayout>(R.id.navTransactionsPill)
                .setBackgroundResource(R.drawable.bg_nav_active_pill)
            findViewById<ImageView>(R.id.navTransactionsIcon).apply {
                alpha = 1f; setColorFilter(ThemeColors.brand(this))
            }
            findViewById<TextView>(R.id.navTransactionsLabel)
                .setTextColor(ThemeColors.brand(this))
        }

        findViewById<LinearLayout>(R.id.navHome).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java)); finish(); overridePendingTransition(0, 0)
        }
        findViewById<LinearLayout>(R.id.navTransactions).setOnClickListener {}
        findViewById<LinearLayout>(R.id.navCategory).setOnClickListener {
            startActivity(Intent(this, ManageCategoriesActivity::class.java)); finish(); overridePendingTransition(0, 0)
        }
        findViewById<LinearLayout>(R.id.navSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java)); finish(); overridePendingTransition(0, 0)
        }
    }

    private fun showAddTransactionDialog() {
        val ctx = this
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(20).toFloat()
                setColor(android.graphics.Color.WHITE)
            }
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }

        // Title
        card.addView(TextView(ctx).apply {
            text = "Add Transaction"
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(android.graphics.Color.parseColor("#1A1A2E"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, dp(20))
        })

        // Amount label + input
        card.addView(TextView(ctx).apply {
            text = "Amount"
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(android.graphics.Color.parseColor("#6B7280"))
            setPadding(dp(4), 0, 0, dp(6))
        })
        val amountInput = EditText(ctx).apply {
            hint = "Enter amount"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            textSize = 15f
            setTextColor(android.graphics.Color.parseColor("#1A1A2E"))
            setHintTextColor(android.graphics.Color.parseColor("#9CA3AF"))
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(android.graphics.Color.parseColor("#F3F4F6"))
            }
            setPadding(dp(14), dp(14), dp(14), dp(14))
            isSingleLine = true
        }
        card.addView(amountInput)

        // Category label + input
        card.addView(TextView(ctx).apply {
            text = "Category"
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(android.graphics.Color.parseColor("#6B7280"))
            setPadding(dp(4), dp(16), 0, dp(6))
        })

        // Custom autocomplete with icons
        val categoryInput = EditText(ctx).apply {
            hint = "Search category"
            textSize = 15f
            setTextColor(android.graphics.Color.parseColor("#1A1A2E"))
            setHintTextColor(android.graphics.Color.parseColor("#9CA3AF"))
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(android.graphics.Color.parseColor("#F3F4F6"))
            }
            setPadding(dp(14), dp(14), dp(14), dp(14))
            isSingleLine = true
        }
        card.addView(categoryInput)

        // Category suggestion list with icons — fixed height, no overlay resize
        val suggestionContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        val scrollSuggestions = ScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(160)
            )
            addView(suggestionContainer)
        }
        card.addView(scrollSuggestions)

        fun populateSuggestions(filter: String) {
            suggestionContainer.removeAllViews()
            val filtered = CategoryManager.activeCategories.filter {
                it.name.contains(filter, ignoreCase = true)
            }
            if (filter.isEmpty()) {
                // Show all categories by default
                CategoryManager.activeCategories.take(8).forEach { cat ->
                    addSuggestionRow(suggestionContainer, cat, categoryInput, scrollSuggestions)
                }
                return
            }
            filtered.take(8).forEach { cat ->
                addSuggestionRow(suggestionContainer, cat, categoryInput, scrollSuggestions)
            }
            if (filtered.isEmpty()) {
                suggestionContainer.addView(TextView(ctx).apply {
                    text = "No categories found"
                    textSize = 13f
                    setTextColor(android.graphics.Color.parseColor("#9CA3AF"))
                    gravity = android.view.Gravity.CENTER
                    setPadding(0, dp(20), 0, dp(20))
                })
            }
        }

        categoryInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                populateSuggestions(s?.toString() ?: "")
            }
        })

        // Show all categories initially
        populateSuggestions("")

        // ── Event (optional) ─────────────────────────────────────────────
        var selectedEventId: String? = null
        card.addView(TextView(ctx).apply {
            text = "Event (optional)"
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(android.graphics.Color.parseColor("#6B7280"))
            setPadding(dp(4), dp(16), 0, dp(6))
        })

        val eventTopRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val eventScroll = HorizontalScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            isHorizontalScrollBarEnabled = false
        }
        val eventChipRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        eventScroll.addView(eventChipRow)

        // "+" / "–" toggle pinned at the left (same as the payment popup)
        val toggleLabel = TextView(ctx).apply {
            text = "+"
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(android.graphics.Color.parseColor("#4F46E5"))
            gravity = android.view.Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        val eventToggle = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(8) }
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(android.graphics.Color.parseColor("#E0E7FF"))
            }
            addView(toggleLabel)
        }
        eventTopRow.addView(eventToggle)
        eventTopRow.addView(eventScroll)
        card.addView(eventTopRow)

        // Inline "new event" row (hidden until "+" is tapped)
        val eventAddRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }
        val eventNameInput = EditText(ctx).apply {
            hint = "New event name"
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#1A1A2E"))
            setHintTextColor(android.graphics.Color.parseColor("#9CA3AF"))
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(10).toFloat()
                setColor(android.graphics.Color.parseColor("#F3F4F6"))
            }
            setPadding(dp(12), dp(10), dp(12), dp(10))
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val eventAddBtn = TextView(ctx).apply {
            text = "Add"
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(android.graphics.Color.WHITE)
            gravity = android.view.Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(10).toFloat()
                setColor(android.graphics.Color.parseColor("#2D6A4F"))
            }
            setPadding(dp(16), dp(10), dp(16), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(8) }
            alpha = 0.4f
            isEnabled = false
        }
        eventNameInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val ok = (s?.toString()?.trim()?.isNotEmpty()) == true
                eventAddBtn.alpha = if (ok) 1f else 0.4f
                eventAddBtn.isEnabled = ok
            }
        })
        eventAddRow.addView(eventNameInput)
        eventAddRow.addView(eventAddBtn)
        card.addView(eventAddRow)

        // Chips: tap to select, tap again to clear. Selected chip shows a ✓ and its event color.
        val chipRefs = mutableListOf<Triple<Event, LinearLayout, TextView>>()
        fun styleChip(ev: Event, chip: LinearLayout, label: TextView, selected: Boolean) {
            chip.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(android.graphics.Color.parseColor(if (selected) ev.colorHex else "#F3F4F6"))
            }
            label.text = (if (selected) "✓ " else "") + (if (ev.name.length > 14) ev.name.take(13) + ".." else ev.name)
            label.setTextColor(android.graphics.Color.parseColor(if (selected) ev.iconTint else "#666b81"))
        }
        fun renderEventChips() {
            eventChipRow.removeAllViews()
            chipRefs.clear()
            val events = EventManager.activeEvents
            if (events.isEmpty()) {
                eventChipRow.addView(TextView(ctx).apply {
                    text = "No events yet — tap + to create one"
                    textSize = 13f
                    setTextColor(android.graphics.Color.parseColor("#9CA3AF"))
                })
                return
            }
            events.forEach { ev ->
                val chip = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(7), dp(12), dp(7))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = dp(6) }
                }
                val label = TextView(ctx).apply {
                    textSize = 13f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    maxLines = 1
                }
                chip.addView(label)
                styleChip(ev, chip, label, ev.id == selectedEventId)
                chip.setOnClickListener {
                    selectedEventId = if (selectedEventId == ev.id) null else ev.id
                    chipRefs.forEach { (e, c, l) -> styleChip(e, c, l, e.id == selectedEventId) }
                }
                chipRefs.add(Triple(ev, chip, label))
                eventChipRow.addView(chip)
            }
        }
        renderEventChips()

        fun setAddRowOpen(open: Boolean) {
            eventAddRow.visibility = if (open) View.VISIBLE else View.GONE
            toggleLabel.text = if (open) "–" else "+"
            toggleLabel.setTextColor(android.graphics.Color.parseColor(if (open) "#EF4444" else "#4F46E5"))
            eventToggle.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(android.graphics.Color.parseColor(if (open) "#FEE2E2" else "#E0E7FF"))
            }
            if (open) eventNameInput.requestFocus()
        }
        eventToggle.setOnClickListener { setAddRowOpen(eventAddRow.visibility != View.VISIBLE) }
        eventAddBtn.setOnClickListener {
            val name = CategoryManager.normalizeName(eventNameInput.text.toString())
            if (name.isEmpty()) return@setOnClickListener
            if (EventManager.categoryNameExists(ctx, name)) {
                android.widget.Toast.makeText(ctx, "Category '$name' already exists", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (EventManager.eventNameExists(ctx, name)) {
                android.widget.Toast.makeText(ctx, "Event '$name' already exists", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val created = EventManager.createEvent(ctx, name)
            selectedEventId = created.id
            eventNameInput.setText("")
            setAddRowOpen(false)
            renderEventChips()
            eventScroll.post { eventScroll.fullScroll(View.FOCUS_RIGHT) }
        }

        // ── Date (defaults to today) ─────────────────────────────────────
        var selectedDateMillis: Long? = null
        val dateFmtUi = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
        card.addView(TextView(ctx).apply {
            text = "Date"
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(android.graphics.Color.parseColor("#6B7280"))
            setPadding(dp(4), dp(16), 0, dp(6))
        })
        val dateBox = TextView(ctx).apply {
            textSize = 15f
            setTextColor(android.graphics.Color.parseColor("#1A1A2E"))
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(android.graphics.Color.parseColor("#F3F4F6"))
            }
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        fun refreshDateLabel() {
            val millis = selectedDateMillis
            dateBox.text = if (millis == null) "Today · ${dateFmtUi.format(Date())}  ▾"
                           else "${dateFmtUi.format(Date(millis))}  ▾"
        }
        refreshDateLabel()
        dateBox.setOnClickListener {
            val start = Calendar.getInstance().apply { timeInMillis = selectedDateMillis ?: System.currentTimeMillis() }
            DatePickerDialog(ctx, { _, y, m, dd ->
                // keeps the current time of day so same-day ordering still makes sense
                val picked = Calendar.getInstance().apply {
                    set(Calendar.YEAR, y); set(Calendar.MONTH, m); set(Calendar.DAY_OF_MONTH, dd)
                }
                val now = Calendar.getInstance()
                val isToday = picked.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                              picked.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
                selectedDateMillis = if (isToday) null else picked.timeInMillis
                refreshDateLabel()
            }, start.get(Calendar.YEAR), start.get(Calendar.MONTH), start.get(Calendar.DAY_OF_MONTH))
                .apply { datePicker.maxDate = System.currentTimeMillis() }
                .show()
        }
        card.addView(dateBox)

        val dialog = android.app.AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Light_Dialog)
            .setView(ScrollView(ctx).apply { addView(card) })
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Add button
        val addBtn = TextView(ctx).apply {
            text = "Add Transaction"
            textSize = 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(android.graphics.Color.WHITE)
            gravity = android.view.Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(android.graphics.Color.parseColor("#2D6A4F"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50)
            ).apply { topMargin = dp(20) }
            setOnClickListener {
                val amountStr = amountInput.text.toString().trim()
                val category = categoryInput.text.toString().trim()

                if (amountStr.isEmpty() || category.isEmpty()) {
                    android.widget.Toast.makeText(ctx, "Enter amount and category", android.widget.Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val amount = amountStr.toDoubleOrNull()
                if (amount == null || amount <= 0) {
                    android.widget.Toast.makeText(ctx, "Enter a valid amount", android.widget.Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val validCat = CategoryManager.activeCategories.find {
                    it.name.equals(category, ignoreCase = true)
                }
                if (validCat == null) {
                    android.widget.Toast.makeText(ctx, "Select a valid category", android.widget.Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                ExpenseDbHelper(ctx).insertExpense(
                    validCat.name, amount, "manual", "manual",
                    selectedEventId, selectedDateMillis ?: System.currentTimeMillis()
                )
                val sym = try { AppPreferences.getCurrencySymbol(ctx) } catch (_: Exception) { "₹" }
                val evSuffix = selectedEventId?.let { EventManager.getEventById(it)?.name }?.let { " · $it" } ?: ""
                android.widget.Toast.makeText(ctx, "✓ ${validCat.name} — $sym${String.format("%.2f", amount)}$evSuffix", android.widget.Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                refreshList()
            }
        }
        card.addView(addBtn)

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    private fun addSuggestionRow(container: LinearLayout, cat: Category, input: EditText, scroll: ScrollView) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(10), dp(8), dp(10))
            setOnClickListener {
                input.setText(cat.name)
                input.clearFocus()
            }
        }
        val iconBg = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginEnd = dp(10) }
        }
        CategoryIcon.applyIcon(iconBg, cat.name, cat.colorHex, cat.iconTint, 16)
        row.addView(iconBg)
        row.addView(TextView(this).apply {
            text = cat.name
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#1A1A2E"))
        })
        container.addView(row)
        container.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(android.graphics.Color.parseColor("#F0F0F0"))
        })
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
