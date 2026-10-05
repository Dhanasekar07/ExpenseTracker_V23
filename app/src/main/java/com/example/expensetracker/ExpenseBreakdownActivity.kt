package com.example.expensetracker

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class ExpenseBreakdownActivity : AppCompatActivity() {

    private lateinit var db            : ExpenseDbHelper
    private lateinit var tvTotalSpent  : TextView
    private lateinit var tvPeriodLabel : TextView
    private lateinit var pieChart      : PieChartView
    private lateinit var barChart      : BarChartView
    private lateinit var breakdownContainer: LinearLayout
    private lateinit var dotsContainer : LinearLayout

    private var showingPie = true
    private var currentEventId: String? = null

    private var currentFilter = "month"
    private var customFrom    = 0L
    private var customTo      = 0L
    private val currency get() = AppPreferences.getCurrencySymbol(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_expense_breakdown)

        db                  = ExpenseDbHelper(this)
        tvTotalSpent        = findViewById(R.id.tvTotalSpent)
        tvPeriodLabel       = findViewById(R.id.tvPeriodLabel)
        pieChart            = findViewById(R.id.pieChart)
        barChart            = findViewById(R.id.barChart)
        breakdownContainer  = findViewById(R.id.breakdownContainer)
        dotsContainer       = findViewById(R.id.dotsContainer)

        // Get filter from intent
        currentFilter = intent.getStringExtra("filter") ?: "month"
        customFrom    = intent.getLongExtra("from", 0L)
        customTo      = intent.getLongExtra("to", 0L)

        currentEventId = intent.getStringExtra("eventId")

        CategoryManager.initialize(this)
        try { EventManager.initialize(this) } catch (e: Exception) { e.printStackTrace() }
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        setupFilterTabs()
        setupEventFilter()
        setupChartPager()
        setupBottomNav()
        refreshData()
    }

    override fun onResume() {
        super.onResume()
        try { refreshData() } catch (e: Exception) { e.printStackTrace() }
    }

    private fun setupFilterTabs() {
        val tabs = mapOf(
            R.id.tabDay to "day", R.id.tabWeek to "week",
            R.id.tabMonth to "month", R.id.tabCustom to "custom"
        )
        tabs.forEach { (id, filter) ->
            findViewById<TextView>(id).setOnClickListener {
                if (filter == "custom") showDatePicker()
                else { currentFilter = filter; updateTabUI(); refreshData() }
            }
        }
        updateTabUI()
    }

    private fun setupEventFilter() {
        val pill = findViewById<LinearLayout>(R.id.btnEventFilter)
        EventDialogs.styleEventPill(this, pill, currentEventId)
        pill.setOnClickListener {
            EventDialogs.showEventFilterMenu(this, pill) { id ->
                currentEventId = id
                EventDialogs.styleEventPill(this, pill, currentEventId)
                refreshData()
            }
        }
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
        tvPeriodLabel.text = when (currentFilter) {
            "day"    -> "Today"
            "week"   -> "This Week"
            "month"  -> "This Month"
            "custom" -> "Custom Range"
            else     -> "This Month"
        }
    }

    private fun setupChartPager() {
        barChart.visibility = View.GONE
        pieChart.visibility = View.VISIBLE
        showingPie = true
        updateDots()

        fun switchTo(pie: Boolean) {
            if (showingPie == pie) return
            showingPie = pie
            pieChart.visibility = if (showingPie) View.VISIBLE else View.GONE
            barChart.visibility = if (showingPie) View.GONE else View.VISIBLE
            updateDots()
        }

        // Swipe charts to switch between pie and bar; tap-to-toggle is removed.
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val dx = e2.x - (e1?.x ?: e2.x)
                if (Math.abs(dx) < Math.abs(e2.y - (e1?.y ?: e2.y))) return false
                if (Math.abs(velocityX) < 300) return false
                if (dx < 0) switchTo(false) else switchTo(true)
                return true
            }
        })
        val touchListener = View.OnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)
            v.onTouchEvent(event)
            true
        }
        pieChart.setOnTouchListener(touchListener)
        barChart.setOnTouchListener(touchListener)
    }

    private fun updateDots() {
        dotsContainer.removeAllViews()
        repeat(2) { i ->
            val isActive = (i == 0 && showingPie) || (i == 1 && !showingPie)
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    if (isActive) 20 else 16,
                    if (isActive) 20 else 16
                ).apply { setMargins(6, 0, 6, 0) }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(if (isActive) ThemeColors.brand(this@ExpenseBreakdownActivity)
                             else Color.parseColor("#D1D5DB"))
                }
            }
            dotsContainer.addView(dot)
        }
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

    private fun getToTs() = if (currentFilter=="custom") customTo else System.currentTimeMillis()

    private fun refreshData() {
        // Selected event may have been deleted
        if (currentEventId != null && EventManager.getEventById(currentEventId) == null) {
            currentEventId = null
            EventDialogs.styleEventPill(this, findViewById(R.id.btnEventFilter), null)
        }
        val catTotals = db.getTotalByCategory(getFromTs(), getToTs(), currentEventId)
        val total     = catTotals.values.sum()

        tvTotalSpent.text = "$currency${String.format("%.0f", total)}"

        // 6+1 chart data
        val chartData = ChartDataHelper.buildChartData(catTotals) { CategoryManager.getCategoryByName(it) }
        val slices = chartData.map { PieSlice(it.name, it.amount.toFloat(), it.chartColor) }
        pieChart.setData(slices, currency)

        // Bar chart data
        val barData = chartData.map { BarEntry(it.name, it.amount.toFloat(), it.chartColor) }
        barChart.setData(barData, currency)

        // Breakdown list: top 6 first
        breakdownContainer.removeAllViews()
        val sorted = catTotals.entries.sortedByDescending { it.value }
        val top6 = sorted.take(6)

        top6.forEach { (name, amt) ->
            val cat   = CategoryManager.getCategoryByName(name)
            val count = db.getTransactionCount(name, getFromTs(), getToTs(), currentEventId)
            addBreakdownRow(name, amt, count, cat?.colorHex ?: "#9CA3AF")
        }

        // Others section if more than 6
        if (sorted.size > 6) {
            val othersTotal = sorted.drop(6).sumOf { it.value }
            // Others header
            breakdownContainer.addView(TextView(this).apply {
                text = "Others — $currency${String.format("%.0f", othersTotal)}"
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#6B7280"))
                setPadding(4, 24, 0, 10)
            })
            // Remaining categories
            sorted.drop(6).forEach { (name, amt) ->
                val cat   = CategoryManager.getCategoryByName(name)
                val count = db.getTransactionCount(name, getFromTs(), getToTs(), currentEventId)
                addBreakdownRow(name, amt, count, cat?.colorHex ?: "#9CA3AF")
            }
        }
    }

    private fun addBreakdownRow(name: String, amount: Double, count: Int, colorHex: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = android.view.Gravity.CENTER_VERTICAL
            background  = getDrawable(R.drawable.bg_normal_row)
            setPadding(16, 14, 16, 14)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 8) }
        }

        val iconBg = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(48, 48).apply { setMargins(0, 0, 14, 0) }
        }
        val catObj = CategoryManager.getCategoryByName(name)
        CategoryIcon.applyIcon(iconBg, name,
            catObj?.colorHex ?: colorHex,
            catObj?.iconTint ?: "#475569"
        )

        val info = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        info.addView(TextView(this).apply {
            text = name; textSize = 15f
            setTextColor(ThemeColors.primary(this))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        info.addView(TextView(this).apply {
            text = "$count Transactions"; textSize = 12f
            setTextColor(ThemeColors.hint(this))
        })

        val amt = TextView(this).apply {
            text = "$currency${String.format("%.2f", amount)}"
            textSize = 15f
            setTextColor(ThemeColors.amountNeg(this))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        row.addView(iconBg); row.addView(info); row.addView(amt)
        breakdownContainer.addView(row)
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            val from = Calendar.getInstance().apply {
                set(y,m,d,0,0,0); set(Calendar.MILLISECOND,0)
            }.timeInMillis
            DatePickerDialog(this, { _, y2, m2, d2 ->
                val to = Calendar.getInstance().apply {
                    set(y2,m2,d2,23,59,59); set(Calendar.MILLISECOND,999)
                }.timeInMillis
                customFrom=from; customTo=to; currentFilter="custom"
                updateTabUI(); refreshData()
            }, y,m,d).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
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
        // Home highlighted (breakdown is a sub-screen of home)
        findViewById<LinearLayout>(R.id.navHomePill).setBackgroundResource(R.drawable.bg_nav_active_pill)
        findViewById<ImageView>(R.id.navHomeIcon).apply { alpha = 1f; setColorFilter(ThemeColors.brand(this)) }
        findViewById<TextView>(R.id.navHomeLabel).setTextColor(ThemeColors.brand(this))

        findViewById<LinearLayout>(R.id.navHome).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java)); finish()
        }
        findViewById<LinearLayout>(R.id.navTransactions).setOnClickListener {
            startActivity(Intent(this, TransactionsActivity::class.java)); finish()
        }
        findViewById<LinearLayout>(R.id.navCategory).setOnClickListener {
            startActivity(Intent(this, ManageCategoriesActivity::class.java)); finish()
        }
        findViewById<LinearLayout>(R.id.navSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java)); finish()
        }
    }
}
