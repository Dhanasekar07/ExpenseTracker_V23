package com.example.expensetracker

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Collections

class ManageCategoriesActivity : AppCompatActivity() {

    private lateinit var previewContainer: LinearLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CategoryAdapter
    private lateinit var eventAdapter: EventAdapter
    private var touchHelper: ItemTouchHelper? = null
    private var eventTouchHelper: ItemTouchHelper? = null
    private var currentTab = "categories"

    @Suppress("DEPRECATION")
    override fun onBackPressed() { finishAffinity() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_categories)

        try { CategoryManager.initialize(this) } catch (e: Exception) { e.printStackTrace() }
        try { EventManager.initialize(this) } catch (e: Exception) { e.printStackTrace() }

        previewContainer = findViewById(R.id.previewContainer)
        recyclerView     = findViewById(R.id.recyclerView)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.btnArchive).setOnClickListener {
            if (currentTab == "events") {
                startActivity(Intent(this, ArchivedEventsActivity::class.java))
            } else {
                startActivity(Intent(this, ArchivedCategoriesActivity::class.java))
            }
        }
        findViewById<ImageView>(R.id.btnMenu).setOnClickListener { showSortMenu(it) }
        findViewById<TextView>(R.id.btnAddNew).setOnClickListener {
            if (currentTab == "events") showAddEventDialog() else showAddCategoryDialog()
        }

        findViewById<TextView>(R.id.tabCategories).setOnClickListener { selectTab("categories") }
        findViewById<TextView>(R.id.tabEvents).setOnClickListener { selectTab("events") }

        setupRecyclerView()
        setupBottomNav()
        selectTab("categories")
    }

    override fun onResume() {
        super.onResume()
        try {
            CategoryManager.initialize(this)
            EventManager.initialize(this)
            refreshUI()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun selectTab(tab: String) {
        currentTab = tab
        val tabCats = findViewById<TextView>(R.id.tabCategories)
        val tabEvts = findViewById<TextView>(R.id.tabEvents)

        if (tab == "categories") {
            tabCats.setBackgroundResource(R.drawable.bg_nav_active_pill)
            tabCats.setTextColor(ThemeColors.primary(this))
            tabEvts.background = null
            tabEvts.setTextColor(ThemeColors.secondary(this))

            recyclerView.adapter = adapter
            eventTouchHelper?.attachToRecyclerView(null)
            touchHelper?.attachToRecyclerView(recyclerView)

            findViewById<ImageView>(R.id.btnMenu).visibility = View.VISIBLE
            findViewById<TextView>(R.id.btnAddNew).text = "＋ Add Category"
        } else {
            tabEvts.setBackgroundResource(R.drawable.bg_nav_active_pill)
            tabEvts.setTextColor(ThemeColors.primary(this))
            tabCats.background = null
            tabCats.setTextColor(ThemeColors.secondary(this))

            recyclerView.adapter = eventAdapter
            touchHelper?.attachToRecyclerView(null)
            eventTouchHelper?.attachToRecyclerView(recyclerView)

            // INVISIBLE (not GONE): the archive icon is anchored to the 3-dot menu,
            // so keeping its slot makes the archive icon sit exactly where it does on Categories.
            findViewById<ImageView>(R.id.btnMenu).visibility = View.INVISIBLE
            findViewById<TextView>(R.id.btnAddNew).text = "＋ Add Event"
        }
        refreshUI()
    }

    private fun setupRecyclerView() {
        adapter = CategoryAdapter(
            mutableListOf(),
            onArchive = { cat ->
                CategoryManager.archiveCategory(this, cat.id)
                refreshUI()
                Toast.makeText(this, "${cat.name} archived", Toast.LENGTH_SHORT).show()
            },
            onRename = { cat -> showRenameDialog(cat.id, cat.name) },
            onDelete = { cat -> showDeleteConfirmation(cat.id, cat.name) },
            onStartDrag = { holder ->
                if (AppPreferences.getCategorySortMode(this) == "custom") {
                    touchHelper?.startDrag(holder)
                }
            }
        )

        eventAdapter = EventAdapter(
            mutableListOf(),
            onArchive = { ev ->
                EventManager.archiveEvent(this, ev.id)
                refreshUI()
                Toast.makeText(this, "${ev.name} archived", Toast.LENGTH_SHORT).show()
            },
            onEdit = { ev -> showEventRenameDialog(ev.id, ev.name) },
            onDelete = { ev -> EventDialogs.showDeleteEventDialog(this, ev) { refreshUI() } },
            onStartDrag = { holder -> eventTouchHelper?.startDrag(holder) }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun isLongPressDragEnabled(): Boolean = false

            override fun getMovementFlags(rv: RecyclerView, holder: RecyclerView.ViewHolder): Int {
                if (AppPreferences.getCategorySortMode(this@ManageCategoriesActivity) != "custom") {
                    return makeMovementFlags(0, 0)
                }
                return super.getMovementFlags(rv, holder)
            }

            override fun onMove(rv: RecyclerView, from: RecyclerView.ViewHolder, to: RecyclerView.ViewHolder): Boolean {
                val fromPos = from.adapterPosition
                val toPos = to.adapterPosition
                adapter.moveItem(fromPos, toPos)
                return true
            }
            override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) {}
            override fun clearView(rv: RecyclerView, holder: RecyclerView.ViewHolder) {
                super.clearView(rv, holder)
                adapter.getItems().forEachIndexed { index, cat ->
                    CategoryDbHelper(this@ManageCategoriesActivity).updateSortOrder(cat.id, index)
                }
                CategoryManager.initialize(this@ManageCategoriesActivity)
                adapter.notifyDataSetChanged()
                buildPreview()
            }
        }
        touchHelper = ItemTouchHelper(callback)
        touchHelper!!.attachToRecyclerView(recyclerView)

        val eventCallback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun isLongPressDragEnabled(): Boolean = false

            override fun onMove(rv: RecyclerView, from: RecyclerView.ViewHolder, to: RecyclerView.ViewHolder): Boolean {
                eventAdapter.moveItem(from.adapterPosition, to.adapterPosition)
                return true
            }
            override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) {}
            override fun clearView(rv: RecyclerView, holder: RecyclerView.ViewHolder) {
                super.clearView(rv, holder)
                eventAdapter.getItems().forEachIndexed { index, ev ->
                    EventDbHelper(this@ManageCategoriesActivity).updateSortOrder(ev.id, index)
                }
                EventManager.initialize(this@ManageCategoriesActivity)
                eventAdapter.notifyDataSetChanged()
                buildPreview()
            }
        }
        eventTouchHelper = ItemTouchHelper(eventCallback)
    }

    private fun showSortMenu(anchor: View) {
        val popup = android.widget.PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "Custom")
        popup.menu.add(0, 2, 1, "Frequently Used")

        val current = AppPreferences.getCategorySortMode(this)
        popup.menu.findItem(if (current == "custom") 1 else 2)?.isChecked = true

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    AppPreferences.setCategorySortMode(this, "custom")
                    refreshUI()
                    true
                }
                2 -> {
                    AppPreferences.setCategorySortMode(this, "frequency")
                    refreshUI()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun refreshUI() {
        if (currentTab == "events") {
            findViewById<TextView>(R.id.tvSectionTitle).text = "Active Events"
            eventAdapter.updateItems(EventManager.activeEvents.toMutableList())
        } else {
            findViewById<TextView>(R.id.tvSectionTitle).text = "Active Categories"
            val sortMode = AppPreferences.getCategorySortMode(this)
            val active = if (sortMode == "frequency") {
                CategoryManager.getActiveByFrequency(this).toMutableList()
            } else {
                CategoryManager.activeCategories.toMutableList()
            }
            adapter.updateItems(active)
            adapter.setDragEnabled(sortMode == "custom")
        }
        buildPreview()
    }

    private fun buildPreview() {
        previewContainer.removeAllViews()
        previewContainer.orientation = LinearLayout.VERTICAL
        val maxPopup = AppPreferences.getPopupMaxCategories(this)
        val sortMode = AppPreferences.getCategorySortMode(this)
        val cats = if (sortMode == "frequency") {
            CategoryManager.getActiveByFrequency(this).take(maxPopup)
        } else {
            CategoryManager.activeCategories.take(maxPopup)
        }

        // Event row — same as the real popup: fixed "+" then the active events in order
        val eventRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10f).toInt() }
        }
        eventRow.addView(FrameLayout(this).apply {
            val size = dp(26f).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = dp(8f).toInt() }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E0E7FF"))
            }
            addView(TextView(this@ManageCategoriesActivity).apply {
                text = "+"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(Color.parseColor("#4F46E5"))
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
                )
            })
        })
        val eventScroll = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            isHorizontalScrollBarEnabled = false
        }
        val eventChips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        EventManager.activeEvents.forEach { ev ->
            eventChips.addView(TextView(this).apply {
                text = if (ev.name.length > 12) ev.name.take(11) + ".." else ev.name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(Color.parseColor("#666b81"))
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(16f)
                    setColor(Color.parseColor("#F3F4F6"))
                }
                setPadding(dp(12f).toInt(), dp(6f).toInt(), dp(12f).toInt(), dp(6f).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(6f).toInt() }
            })
        }
        eventScroll.addView(eventChips)
        eventRow.addView(eventScroll)
        previewContainer.addView(eventRow)

        val chipsPerRow = 3
        var currentRow: LinearLayout? = null

        cats.forEachIndexed { index, cat ->
            if (index % chipsPerRow == 0) {
                currentRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { if (index > 0) topMargin = dp(6f).toInt() }
                }
                previewContainer.addView(currentRow)
            }

            val chip = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(20f)
                    setColor(Color.parseColor("#e3e8ff"))
                }
                setPadding(dp(12f).toInt(), dp(8f).toInt(), dp(14f).toInt(), dp(8f).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(dp(4f).toInt(), 0, dp(4f).toInt(), 0) }
            }
            chip.addView(CategoryIcon.createChipIcon(this, cat.name, Color.parseColor("#141930"), 16))
            chip.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(4f).toInt(), 1)
            })
            chip.addView(TextView(this).apply {
                text = if (cat.name.length > 9) cat.name.take(8) + ".." else cat.name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(Color.parseColor("#666b81"))
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            })
            currentRow?.addView(chip)
        }
    }

    private fun showDeleteConfirmation(id: String, name: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete \"$name\"?")
            .setMessage(getString(R.string.delete_confirmation))
            .setPositiveButton("Delete") { _, _ ->
                CategoryManager.deleteCategory(this, id)
                refreshUI()
            }
            .setNegativeButton("Cancel", null)
            .create().apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE)
                        .setTextColor(ThemeColors.amountNeg(this@ManageCategoriesActivity))
                }
            }.show()
    }

    private fun showRenameDialog(id: String, currentName: String) {
        val ctx = this
        val d = resources.displayMetrics.density

        // Find the category to show its icon
        val cat = CategoryManager.allCategories.find { it.id == id }

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 20 * d
                setColor(android.graphics.Color.WHITE)
            }
            setPadding((24*d).toInt(), (24*d).toInt(), (24*d).toInt(), (24*d).toInt())
        }

        // Title
        card.addView(TextView(ctx).apply {
            text = "Rename Category"
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(android.graphics.Color.parseColor("#1A1A2E"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, (20*d).toInt())
        })

        // Category icon + current name preview
        if (cat != null) {
            val previewRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER
                setPadding(0, 0, 0, (16*d).toInt())
            }
            val iconBg = FrameLayout(ctx).apply {
                layoutParams = LinearLayout.LayoutParams((36*d).toInt(), (36*d).toInt()).apply {
                    marginEnd = (10*d).toInt()
                }
            }
            CategoryIcon.applyIcon(iconBg, cat.name, cat.colorHex, cat.iconTint, 18)
            previewRow.addView(iconBg)
            previewRow.addView(TextView(ctx).apply {
                text = currentName
                textSize = 15f
                setTextColor(android.graphics.Color.parseColor("#6B7280"))
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            })
            card.addView(previewRow)
        }

        // New name label
        card.addView(TextView(ctx).apply {
            text = "New Name"
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(android.graphics.Color.parseColor("#6B7280"))
            setPadding((4*d).toInt(), 0, 0, (6*d).toInt())
        })

        val input = EditText(ctx).apply {
            setText(currentName)
            textSize = 15f
            setTextColor(android.graphics.Color.parseColor("#1A1A2E"))
            setHintTextColor(android.graphics.Color.parseColor("#9CA3AF"))
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 12 * d
                setColor(android.graphics.Color.parseColor("#F3F4F6"))
            }
            setPadding((14*d).toInt(), (14*d).toInt(), (14*d).toInt(), (14*d).toInt())
            isSingleLine = true
            setSelectAllOnFocus(true)
        }
        card.addView(input)

        val dialog = AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Light_Dialog)
            .setView(card)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Buttons row
        val btnRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (20*d).toInt() }
        }

        // Cancel button
        btnRow.addView(TextView(ctx).apply {
            text = "Cancel"
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(android.graphics.Color.parseColor("#6B7280"))
            gravity = android.view.Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 14 * d
                setColor(android.graphics.Color.parseColor("#F3F4F6"))
            }
            layoutParams = LinearLayout.LayoutParams(0, (48*d).toInt(), 1f).apply {
                marginEnd = (8*d).toInt()
            }
            setOnClickListener { dialog.dismiss() }
        })

        // Save button
        btnRow.addView(TextView(ctx).apply {
            text = "Save"
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(android.graphics.Color.WHITE)
            gravity = android.view.Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 14 * d
                setColor(android.graphics.Color.parseColor("#2D6A4F"))
            }
            layoutParams = LinearLayout.LayoutParams(0, (48*d).toInt(), 1f)
            setOnClickListener {
                val newName = CategoryManager.normalizeName(input.text.toString())
                if (newName.isNotEmpty()) {
                    // Check for duplicate name (excluding current category)
                    val exists = CategoryManager.allCategories.any {
                        it.id != id && it.name.equals(newName, ignoreCase = true)
                    }
                    if (exists) {
                        Toast.makeText(ctx, "Category '$newName' already exists", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    if (EventManager.eventNameExists(ctx, newName)) {
                        Toast.makeText(ctx, "Event '$newName' already exists", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    CategoryManager.renameCategory(ctx, id, newName)
                    dialog.dismiss()
                    refreshUI()
                } else {
                    Toast.makeText(ctx, "Enter a category name", Toast.LENGTH_SHORT).show()
                }
            }
        })
        card.addView(btnRow)

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    private fun showAddCategoryDialog() {
        val ctx = this
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = (20 * resources.displayMetrics.density)
                setColor(android.graphics.Color.WHITE)
            }
            setPadding(
                (24 * resources.displayMetrics.density).toInt(),
                (24 * resources.displayMetrics.density).toInt(),
                (24 * resources.displayMetrics.density).toInt(),
                (24 * resources.displayMetrics.density).toInt()
            )
        }

        card.addView(TextView(ctx).apply {
            text = "Add New Category"
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(android.graphics.Color.parseColor("#1A1A2E"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, (20 * resources.displayMetrics.density).toInt())
        })

        card.addView(TextView(ctx).apply {
            text = "Category Name"
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(android.graphics.Color.parseColor("#6B7280"))
            setPadding((4 * resources.displayMetrics.density).toInt(), 0, 0, (6 * resources.displayMetrics.density).toInt())
        })

        val input = EditText(ctx).apply {
            hint = "Enter category name"
            textSize = 15f
            setTextColor(android.graphics.Color.parseColor("#1A1A2E"))
            setHintTextColor(android.graphics.Color.parseColor("#9CA3AF"))
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = (12 * resources.displayMetrics.density)
                setColor(android.graphics.Color.parseColor("#F3F4F6"))
            }
            setPadding(
                (14 * resources.displayMetrics.density).toInt(),
                (14 * resources.displayMetrics.density).toInt(),
                (14 * resources.displayMetrics.density).toInt(),
                (14 * resources.displayMetrics.density).toInt()
            )
            isSingleLine = true
        }
        card.addView(input)

        val dialog = AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Light_Dialog)
            .setView(card)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val addBtn = TextView(ctx).apply {
            text = "Add Category"
            textSize = 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(android.graphics.Color.WHITE)
            gravity = android.view.Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = (14 * resources.displayMetrics.density)
                setColor(android.graphics.Color.parseColor("#2D6A4F"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (50 * resources.displayMetrics.density).toInt()
            ).apply { topMargin = (20 * resources.displayMetrics.density).toInt() }
            setOnClickListener {
                val name = CategoryManager.normalizeName(input.text.toString())
                if (name.isEmpty()) {
                    Toast.makeText(ctx, "Enter a category name", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                // Check for duplicate name
                val exists = CategoryManager.allCategories.any { it.name.equals(name, ignoreCase = true) }
                if (exists) {
                    Toast.makeText(ctx, "Category '$name' already exists", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (EventManager.eventNameExists(ctx, name)) {
                    Toast.makeText(ctx, "Event '$name' already exists", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (CategoryManager.allCategories.size >= CategoryManager.MAX_CATEGORIES) {
                    Toast.makeText(ctx, "Maximum ${CategoryManager.MAX_CATEGORIES} categories", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val colors = CategoryManager.getNextCustomColor(CategoryManager.allCategories)
                val newCat = Category(
                    id         = "custom_${System.currentTimeMillis()}",
                    name       = name,
                    iconName   = "custom",
                    colorHex   = colors.first,
                    chartColor = colors.second,
                    iconTint   = colors.third,
                    isActive   = true,
                    sortOrder  = CategoryManager.allCategories.size
                )
                CategoryDbHelper(ctx).insertCategory(newCat)
                CategoryManager.initialize(ctx)
                dialog.dismiss()
                refreshUI()
            }
        }
        card.addView(addBtn)

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    private fun showAddEventDialog() {
        val ctx = this
        val d = resources.displayMetrics.density
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20 * d
                setColor(Color.WHITE)
            }
            setPadding((24*d).toInt(), (24*d).toInt(), (24*d).toInt(), (24*d).toInt())
        }

        card.addView(TextView(ctx).apply {
            text = "Add New Event"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#1A1A2E"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (20*d).toInt())
        })

        card.addView(TextView(ctx).apply {
            text = "Event Name"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#6B7280"))
            setPadding((4*d).toInt(), 0, 0, (6*d).toInt())
        })

        val input = EditText(ctx).apply {
            hint = "Enter event name"
            textSize = 15f
            setTextColor(Color.parseColor("#1A1A2E"))
            setHintTextColor(Color.parseColor("#9CA3AF"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12 * d
                setColor(Color.parseColor("#F3F4F6"))
            }
            setPadding((14*d).toInt(), (14*d).toInt(), (14*d).toInt(), (14*d).toInt())
            isSingleLine = true
        }
        card.addView(input)

        val dialog = AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Light_Dialog)
            .setView(card)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val addBtn = TextView(ctx).apply {
            text = "Add Event"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 14 * d
                setColor(Color.parseColor("#2D6A4F"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (50 * d).toInt()
            ).apply { topMargin = (20 * d).toInt() }
            setOnClickListener {
                val name = CategoryManager.normalizeName(input.text.toString())
                if (name.isEmpty()) {
                    Toast.makeText(ctx, "Enter an event name", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (EventManager.categoryNameExists(ctx, name)) {
                    Toast.makeText(ctx, "Category '$name' already exists", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (EventManager.nameExists(name)) {
                    Toast.makeText(ctx, "Event '$name' already exists", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                EventManager.createEvent(ctx, name)
                dialog.dismiss()
                refreshUI()
            }
        }
        card.addView(addBtn)

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    private fun showEventRenameDialog(id: String, currentName: String) {
        val ctx = this
        val d = resources.displayMetrics.density
        val ev = EventManager.allEvents.find { it.id == id }

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20 * d
                setColor(Color.WHITE)
            }
            setPadding((24*d).toInt(), (24*d).toInt(), (24*d).toInt(), (24*d).toInt())
        }

        card.addView(TextView(ctx).apply {
            text = "Rename Event"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#1A1A2E"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (20*d).toInt())
        })

        if (ev != null) {
            val previewRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, (16*d).toInt())
            }
            val iconBg = FrameLayout(ctx).apply {
                layoutParams = LinearLayout.LayoutParams((36*d).toInt(), (36*d).toInt()).apply {
                    marginEnd = (10*d).toInt()
                }
            }
            CategoryIcon.applyIcon(iconBg, ev.name, ev.colorHex, ev.iconTint, 18)
            previewRow.addView(iconBg)
            previewRow.addView(TextView(ctx).apply {
                text = currentName
                textSize = 15f
                setTextColor(Color.parseColor("#6B7280"))
                typeface = Typeface.DEFAULT_BOLD
            })
            card.addView(previewRow)
        }

        card.addView(TextView(ctx).apply {
            text = "New Name"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#6B7280"))
            setPadding((4*d).toInt(), 0, 0, (6*d).toInt())
        })

        val input = EditText(ctx).apply {
            setText(currentName)
            textSize = 15f
            setTextColor(Color.parseColor("#1A1A2E"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12 * d
                setColor(Color.parseColor("#F3F4F6"))
            }
            setPadding((14*d).toInt(), (14*d).toInt(), (14*d).toInt(), (14*d).toInt())
            isSingleLine = true
            setSelectAllOnFocus(true)
        }
        card.addView(input)

        val dialog = AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Light_Dialog)
            .setView(card)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (20*d).toInt() }
        }

        btnRow.addView(TextView(ctx).apply {
            text = "Cancel"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#6B7280"))
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 14 * d
                setColor(Color.parseColor("#F3F4F6"))
            }
            layoutParams = LinearLayout.LayoutParams(0, (48*d).toInt(), 1f).apply {
                marginEnd = (8*d).toInt()
            }
            setOnClickListener { dialog.dismiss() }
        })

        btnRow.addView(TextView(ctx).apply {
            text = "Save"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 14 * d
                setColor(Color.parseColor("#2D6A4F"))
            }
            layoutParams = LinearLayout.LayoutParams(0, (48*d).toInt(), 1f)
            setOnClickListener {
                val newName = CategoryManager.normalizeName(input.text.toString())
                if (newName.isEmpty()) {
                    Toast.makeText(ctx, "Enter an event name", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (EventManager.categoryNameExists(ctx, newName)) {
                    Toast.makeText(ctx, "Category '$newName' already exists", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (EventManager.nameExists(newName, excludeId = id)) {
                    Toast.makeText(ctx, "Event '$newName' already exists", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                EventManager.renameEvent(ctx, id, newName)
                dialog.dismiss()
                refreshUI()
            }
        })
        card.addView(btnRow)

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )
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
        findViewById<LinearLayout>(R.id.navCategoryPill).setBackgroundResource(R.drawable.bg_nav_active_pill)
        findViewById<ImageView>(R.id.navCategoryIcon).apply { alpha = 1f; setColorFilter(ThemeColors.brand(this)) }
        findViewById<TextView>(R.id.navCategoryLabel).setTextColor(ThemeColors.brand(this))

        findViewById<LinearLayout>(R.id.navHome).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java)); finish(); overridePendingTransition(0, 0)
        }
        findViewById<LinearLayout>(R.id.navTransactions).setOnClickListener {
            startActivity(Intent(this, TransactionsActivity::class.java)); finish(); overridePendingTransition(0, 0)
        }
        findViewById<LinearLayout>(R.id.navCategory).setOnClickListener { }
        findViewById<LinearLayout>(R.id.navSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java)); finish(); overridePendingTransition(0, 0)
        }
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density

    // ── RecyclerView Adapter ─────────────────────────────────────────────
    inner class CategoryAdapter(
        private val items: MutableList<Category>,
        private val onArchive: (Category) -> Unit,
        private val onRename: (Category) -> Unit,
        private val onDelete: (Category) -> Unit,
        private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
    ) : RecyclerView.Adapter<CategoryAdapter.VH>() {

        private var dragEnabled = true

        fun setDragEnabled(enabled: Boolean) {
            dragEnabled = enabled
            notifyDataSetChanged()
        }

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val dragHandle  = view.findViewById<ImageView>(R.id.imgDrag)
            val iconBg      = view.findViewById<FrameLayout>(R.id.iconContainer)
            val tvName      = view.findViewById<TextView>(R.id.tvCategoryName)
            val btnMinus    = view.findViewById<ImageView>(R.id.btnMinus)
            val btnEdit     = view.findViewById<ImageView>(R.id.btnEdit)
            val btnDelete   = view.findViewById<ImageView>(R.id.btnDelete)
        }

        fun updateItems(newItems: List<Category>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        fun moveItem(from: Int, to: Int) {
            Collections.swap(items, from, to)
            notifyItemMoved(from, to)
        }

        fun getItems(): List<Category> = items

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = layoutInflater.inflate(R.layout.item_category_row, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val cat = items[position]
            val maxPopup = AppPreferences.getPopupMaxCategories(this@ManageCategoriesActivity)

            // Green highlight for top N categories
            if (position < maxPopup) {
                holder.itemView.setBackgroundResource(R.drawable.bg_top4_highlight)
            } else {
                holder.itemView.setBackgroundResource(R.drawable.bg_normal_row)
            }

            CategoryIcon.applyIcon(holder.iconBg, cat.name, cat.colorHex, cat.iconTint)
            holder.tvName.text = cat.name

            // Minus shows as + for inactive (inventory items)
            holder.btnMinus.setImageResource(R.drawable.ic_minus)

            holder.btnMinus.setOnClickListener { onArchive(cat) }
            holder.btnEdit.setOnClickListener { onRename(cat) }
            holder.btnDelete.setOnClickListener { onDelete(cat) }

            // Drag handle — visible only in Custom mode
            holder.dragHandle.visibility = if (dragEnabled) View.VISIBLE else View.GONE
            holder.dragHandle.setOnTouchListener { _, event ->
                if (dragEnabled && event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onStartDrag(holder)
                }
                false
            }
        }

        override fun getItemCount() = items.size
    }

    // ── Event RecyclerView Adapter ───────────────────────────────────────
    inner class EventAdapter(
        private val items: MutableList<Event>,
        private val onArchive: (Event) -> Unit,
        private val onEdit: (Event) -> Unit,
        private val onDelete: (Event) -> Unit,
        private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
    ) : RecyclerView.Adapter<EventAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val dragHandle = view.findViewById<ImageView>(R.id.imgDrag)
            val iconBg     = view.findViewById<FrameLayout>(R.id.iconContainer)
            val tvName     = view.findViewById<TextView>(R.id.tvEventName)
            val btnMinus   = view.findViewById<ImageView>(R.id.btnMinus)
            val btnEdit    = view.findViewById<ImageView>(R.id.btnEdit)
            val btnDelete  = view.findViewById<ImageView>(R.id.btnDelete)
        }

        fun updateItems(newItems: List<Event>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        fun moveItem(from: Int, to: Int) {
            Collections.swap(items, from, to)
            notifyItemMoved(from, to)
        }

        fun getItems(): List<Event> = items

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = layoutInflater.inflate(R.layout.item_event_row, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val ev = items[position]
            CategoryIcon.applyIcon(holder.iconBg, ev.name, ev.colorHex, ev.iconTint)
            holder.tvName.text = ev.name
            holder.btnMinus.setOnClickListener { onArchive(ev) }
            holder.btnEdit.setOnClickListener { onEdit(ev) }
            holder.btnDelete.setOnClickListener { onDelete(ev) }

            holder.dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onStartDrag(holder)
                }
                false
            }
        }

        override fun getItemCount() = items.size
    }
}
