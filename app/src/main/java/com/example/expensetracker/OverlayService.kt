package com.example.expensetracker

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.view.inputmethod.InputMethodManager
import android.media.AudioAttributes
import android.media.SoundPool
import android.view.MotionEvent
import android.widget.*
import java.util.Locale

class OverlayService : Service() {

    private var wm          : WindowManager? = null
    private var overlayView : View?          = null
    private var db          : ExpenseDbHelper? = null
    private var timerAnim   : ObjectAnimator? = null
    private var smsObserver : SmsObserver?   = null
    private var soundPool   : SoundPool?     = null
    private var popSoundId  : Int            = 0

    private var currentAmount  = 0.0
    private var currentSource  = ""
    private var currentChannel = ""
    private var selectedEventId: String? = null
    private var eventAddRowOpen = false
    private var eventScrollX = 0
    private var eventScrollToEnd = false

    private val handler  = Handler(Looper.getMainLooper())
    private val autoHide = Runnable { dismissCurrent(); showNext() }

    // ── Minimized bubble state ──────────────────────────────────────────
    private var bubbleView: MinimizedBubbleView? = null
    private var jiggleAnim: ValueAnimator? = null
    private var bubbleDragging = false
    private var bubbleX = -1
    private var bubbleY = -1
    private var focusRunnable: Runnable? = null
    private val jiggleTick = object : Runnable {
        override fun run() {
            if (bubbleView == null) return
            playJiggle(strong = false)
            handler.postDelayed(this, JIGGLE_INTERVAL_MS)
        }
    }

    companion object {
        private const val TAG       = "OverlayService"
        private const val CH_ID     = "expense_overlay"
        private const val FG_NOTIF  = 1001
        private const val JIGGLE_FIRST_MS    = 2_000L
        private const val JIGGLE_INTERVAL_MS = 10_000L
    }

    data class PendingTxn(val amount: Double, val source: String, val channel: String)
    private val queue = ArrayDeque<PendingTxn>()

    override fun onCreate() {
        super.onCreate()
        db = ExpenseDbHelper(this)
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        goForeground()
        registerSmsObserver()
        initSound()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val amount  = intent?.getDoubleExtra("amount", 0.0) ?: 0.0
        val source  = intent?.getStringExtra("source") ?: ""
        val channel = intent?.getStringExtra("channel") ?: "unknown"

        if (amount <= 0.0) {
            Log.d(TAG, "Keep-alive start — monitoring mode")
            return START_STICKY
        }

        if (overlayView != null) {
            queue.addLast(PendingTxn(amount, source, channel))
            Log.d(TAG, "Queued: amount=$amount (queue=${queue.size})")
            // A popup is minimized: update the bubble count and jiggle harder
            if (bubbleView != null) refreshBubble(strong = true)
        } else {
            currentAmount = amount; currentSource = source; currentChannel = channel
            showOverlay()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterSmsObserver()
        soundPool?.release(); soundPool = null
        dismissCurrent(); queue.clear(); db?.close(); super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    private fun showNext() {
        if (queue.isEmpty()) {
            Log.d(TAG, "Queue empty — staying alive for SmsObserver")
            return
        }
        val next = queue.removeFirst()
        currentAmount = next.amount; currentSource = next.source; currentChannel = next.channel
        Log.d(TAG, "Next from queue: amount=$currentAmount (remaining=${queue.size})")
        showOverlay()
    }

    private fun goForeground() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CH_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH_ID, "Expense Overlay",
                    NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) }
            )
        }
        startForeground(FG_NOTIF,
            Notification.Builder(this, CH_ID)
                .setContentTitle("Expense Tracker Active")
                .setContentText("Monitoring payments...")
                .setSmallIcon(android.R.drawable.ic_menu_save)
                .setOngoing(true).build()
        )
    }

    // ═══════════════════════════════════════════════════════════════════
    // STATE 1: Small Popup (original + "+" button)
    // ═══════════════════════════════════════════════════════════════════

    private fun showOverlay(preserveEventSelection: Boolean = false, skipSound: Boolean = false) {
        if (!Settings.canDrawOverlays(this)) { return }

        try { CategoryManager.initialize(this) } catch (e: Exception) {
            Log.e(TAG, "CategoryManager init failed", e)
        }
        try { EventManager.initialize(this) } catch (e: Exception) {
            Log.e(TAG, "EventManager init failed", e)
        }
        // The selected event may have been archived or deleted meanwhile
        if (selectedEventId != null && EventManager.activeEvents.none { it.id == selectedEventId }) selectedEventId = null
        if (!preserveEventSelection) {
            selectedEventId = null
            eventAddRowOpen = false
            eventScrollX = 0
            eventScrollToEnd = false
        }

        val sym = try { AppPreferences.getCurrencySymbol(this) } catch (_: Exception) { "₹" }
        val popupMax = try { AppPreferences.getPopupMaxCategories(this) } catch (_: Exception) { 4 }
        val aliveTime = try { AppPreferences.getPopupAliveTime(this) } catch (_: Exception) { 30 }

        val sortMode = try { AppPreferences.getCategorySortMode(this) } catch (_: Exception) { "custom" }
        val categories = if (sortMode == "frequency") {
            CategoryManager.getActiveByFrequency(this).take(popupMax)
        } else {
            CategoryManager.activeCategories.take(popupMax)
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(20f)
                setColor(Color.WHITE)
            }
            setPadding(dp(20f).toInt(), dp(16f).toInt(), dp(20f).toInt(), dp(12f).toInt())
            elevation = dp(8f)
        }

        // Header: amount + "+" + "x"
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        var chipScrollForMinimize: HorizontalScrollView? = null

        val amountSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        amountSection.addView(TextView(this).apply {
            text = "PAYMENT DETECTED"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTextColor(Color.parseColor("#6B7280"))
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.05f
        })
        amountSection.addView(TextView(this).apply {
            text = "$sym${String.format("%.2f", currentAmount)}"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(Color.parseColor("#1A1A2E"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(2f).toInt(), 0, 0)
        })
        header.addView(amountSection)

        // "–" minimize button
        header.addView(createMinimizeButton {
            // remember where the event chips were and close the add-event row
            chipScrollForMinimize?.let { eventScrollX = it.scrollX }
            eventAddRowOpen = false
            minimizeToBubble()
        })

        // "+" expand button
        val expandBtn = FrameLayout(this).apply {
            val size = dp(34f).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = dp(8f).toInt()
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E0E7FF"))
            }
            setOnClickListener {
                chipScrollForMinimize?.let { eventScrollX = it.scrollX }
                eventAddRowOpen = false
                dismissCurrent()
                showExpandedOverlay()
            }
        }
        expandBtn.addView(TextView(this).apply {
            text = "+"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(Color.parseColor("#4F46E5")); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        })
        header.addView(expandBtn)

        // "x" dismiss button
        val dismissBtn = FrameLayout(this).apply {
            val size = dp(34f).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FEE2E2"))
            }
            setOnClickListener { dismissCurrent(); showNext() }
        }
        dismissBtn.addView(TextView(this).apply {
            text = "✕"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.parseColor("#EF4444")); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        })
        header.addView(dismissBtn)
        card.addView(header)

        // ── Event chip row (single-select, active events only) ──────────
        var eventNameInputRef: EditText? = null
        val activeEvents = EventManager.activeEvents

        val eventSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10f).toInt() }
        }

        // Top row: [ +/- toggle (fixed) ][ scrollable event chips ]
        val eventTopRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val eventChipScroll = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            isHorizontalScrollBarEnabled = false
        }
        val eventChipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        eventChipScroll.addView(eventChipRow)
        chipScrollForMinimize = eventChipScroll

        // "+/-" toggle — pinned at the left, never scrolls with the chips
        val toggleBtn = FrameLayout(this).apply {
            val size = dp(26f).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = dp(8f).toInt() }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(if (eventAddRowOpen) "#FEE2E2" else "#E0E7FF"))
            }
            setOnClickListener {
                eventScrollX = eventChipScroll.scrollX   // keep the chip row where it is
                eventAddRowOpen = !eventAddRowOpen
                dismissCurrent()
                showOverlay(preserveEventSelection = true, skipSound = true)
            }
        }
        toggleBtn.addView(TextView(this).apply {
            text = if (eventAddRowOpen) "–" else "+"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(Color.parseColor(if (eventAddRowOpen) "#EF4444" else "#4F46E5"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        })
        eventTopRow.addView(toggleBtn)
        eventTopRow.addView(eventChipScroll)

        // Chips: selection is restyled in place (no window rebuild), so the row
        // never jumps and the chip the user just tapped stays exactly where it is.
        val chipRefs = mutableListOf<Triple<Event, LinearLayout, TextView>>()
        fun styleEventChip(ev: Event, chip: LinearLayout, label: TextView, selected: Boolean) {
            chip.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(16f)
                setColor(Color.parseColor(if (selected) ev.colorHex else "#F3F4F6"))
            }
            label.setTextColor(Color.parseColor(if (selected) ev.iconTint else "#666b81"))
        }

        activeEvents.forEach { ev ->
            val chip = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12f).toInt(), dp(6f).toInt(), dp(12f).toInt(), dp(6f).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(6f).toInt() }
            }
            val label = TextView(this).apply {
                text = if (ev.name.length > 12) ev.name.take(11) + ".." else ev.name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
            }
            chip.addView(label)
            styleEventChip(ev, chip, label, ev.id == selectedEventId)
            chip.setOnClickListener {
                selectedEventId = if (selectedEventId == ev.id) null else ev.id
                chipRefs.forEach { (e, c, l) -> styleEventChip(e, c, l, e.id == selectedEventId) }
            }
            chipRefs.add(Triple(ev, chip, label))
            eventChipRow.addView(chip)
        }

        // Restore scroll after a rebuild (or jump to the end after a new event was added)
        eventChipScroll.post {
            if (eventScrollToEnd) {
                eventChipScroll.fullScroll(View.FOCUS_RIGHT)
                eventScrollToEnd = false
            } else {
                eventChipScroll.scrollTo(eventScrollX, 0)
            }
        }
        eventSection.addView(eventTopRow)

        if (eventAddRowOpen) {
            val addRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8f).toInt() }
            }
            val nameInput = EditText(this).apply {
                hint = "Event name"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(Color.parseColor("#1A1A2E"))
                setHintTextColor(Color.parseColor("#9CA3AF"))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE; cornerRadius = dp(10f)
                    setColor(Color.parseColor("#F3F4F6"))
                }
                setPadding(dp(10f).toInt(), dp(8f).toInt(), dp(10f).toInt(), dp(8f).toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                isSingleLine = true
            }
            eventNameInputRef = nameInput

            val addEventBtn = TextView(this).apply {
                text = "Add"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE; cornerRadius = dp(10f)
                    setColor(Color.parseColor("#2D6A4F"))
                }
                setPadding(dp(14f).toInt(), dp(8f).toInt(), dp(14f).toInt(), dp(8f).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(8f).toInt() }
                alpha = 0.4f
                isEnabled = false
                setOnClickListener {
                    val name = CategoryManager.normalizeName(nameInput.text.toString())
                    if (name.isEmpty()) return@setOnClickListener
                    if (EventManager.categoryNameExists(this@OverlayService, name)) {
                        Toast.makeText(this@OverlayService, "Category '$name' already exists", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    if (EventManager.eventNameExists(this@OverlayService, name)) {
                        Toast.makeText(this@OverlayService, "Event '$name' already exists", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val newEv = EventManager.createEvent(this@OverlayService, name)
                    selectedEventId = newEv.id
                    eventAddRowOpen = false
                    eventScrollToEnd = true
                    dismissCurrent()
                    showOverlay(preserveEventSelection = true, skipSound = true)
                }
            }
            nameInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val enabled = (s?.toString()?.trim()?.isNotEmpty()) == true
                    addEventBtn.alpha = if (enabled) 1f else 0.4f
                    addEventBtn.isEnabled = enabled
                }
            })
            addRow.addView(nameInput)
            addRow.addView(addEventBtn)
            eventSection.addView(addRow)
        }
        card.addView(eventSection)

        // Chips
        val chipBg = "#e3e8ff"; val chipIcon = "#141930"; val chipText = "#666b81"
        val chipList = if (categories.isEmpty()) listOf("Food", "Shopping", "Grocery")
        else categories.map { it.name }

        chipList.chunked(3).forEach { rowItems ->
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(10f).toInt() }
            }
            rowItems.forEach { name -> addChip(rowLayout, name, chipBg, chipIcon, chipText) }
            card.addView(rowLayout)
        }

        // Timer bar
        val timerTrack = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(6f).toInt()
            ).apply { topMargin = dp(12f).toInt() }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(3f)
                setColor(Color.parseColor("#F3F4F6"))
            }
        }
        val timerBar = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(3f)
                setColor(Color.parseColor("#2D6A4F"))
            }
        }
        timerTrack.addView(timerBar)
        if (aliveTime > 0) card.addView(timerTrack)

        overlayView = card
        Log.d(TAG, "Showing overlay: amount=$currentAmount source=$currentSource")

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        // The add-event input row needs the keyboard, so the window must be
        // focusable while it's open; otherwise keep it non-focusable as before.
        val flags = if (eventAddRowOpen) WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        val lp = WindowManager.LayoutParams(
            dp(370f).toInt(), WindowManager.LayoutParams.WRAP_CONTENT, type,
            flags, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            if (eventAddRowOpen) softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        try { wm?.addView(overlayView, lp) }
        catch (e: Exception) { Log.e(TAG, "Failed to show overlay", e); overlayView = null; return }

        // Play pop sound (skipped on an in-place rebuild, e.g. toggling an event chip)
        if (!skipSound) playPop()

        // Make draggable
        makeDraggable(card, lp)

        if (eventAddRowOpen) {
            focusRunnable = Runnable {
                eventNameInputRef?.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                eventNameInputRef?.let { imm.showSoftInput(it, InputMethodManager.SHOW_IMPLICIT) }
            }
            handler.postDelayed(focusRunnable!!, 200)
        }

        timerAnim?.cancel(); handler.removeCallbacks(autoHide)
        if (aliveTime > 0) {
            val timeMs = aliveTime * 1000L
            timerBar.pivotX = 0f
            timerAnim = ObjectAnimator.ofFloat(timerBar, "scaleX", 1f, 0f).apply {
                duration = timeMs; interpolator = LinearInterpolator(); start()
            }
            handler.postDelayed(autoHide, timeMs)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // STATE 2: Expanded Overlay (search + full category list by freq)
    // ═══════════════════════════════════════════════════════════════════

    private fun showExpandedOverlay() {
        if (!Settings.canDrawOverlays(this)) return

        try { CategoryManager.initialize(this) } catch (_: Exception) {}
        val sym = try { AppPreferences.getCurrencySymbol(this) } catch (_: Exception) { "₹" }
        val allCategories = CategoryManager.getAllByFrequency(this)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(20f)
                setColor(Color.WHITE)
            }
            setPadding(dp(20f).toInt(), dp(16f).toInt(), dp(20f).toInt(), dp(16f).toInt())
            elevation = dp(8f)
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        val amountSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        amountSection.addView(TextView(this).apply {
            text = "PAYMENT DETECTED"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTextColor(Color.parseColor("#6B7280"))
            typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.05f
        })
        amountSection.addView(TextView(this).apply {
            text = "$sym${String.format("%.2f", currentAmount)}"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(Color.parseColor("#1A1A2E"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(2f).toInt(), 0, 0)
        })
        header.addView(amountSection)

        // "–" minimize
        header.addView(createMinimizeButton { minimizeToBubble() })

        // "x" dismiss
        val dismissBtn = createDismissButton {
            dismissCurrent(); showNext()
        }
        header.addView(dismissBtn)
        card.addView(header)

        // Search row: EditText + "+" add button
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44f).toInt()
            ).apply { topMargin = dp(12f).toInt() }
        }

        val searchInput = EditText(this).apply {
            hint = "Search categories..."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.parseColor("#1A1A2E"))
            setHintTextColor(Color.parseColor("#9CA3AF"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(12f)
                setColor(Color.parseColor("#F3F4F6"))
            }
            setPadding(dp(12f).toInt(), dp(8f).toInt(), dp(12f).toInt(), dp(8f).toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            isSingleLine = true
        }
        searchRow.addView(searchInput)
        card.addView(searchRow)

        // Category list container (scrollable)
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(280f).toInt()
            ).apply { topMargin = dp(8f).toInt() }
        }
        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(listContainer)
        card.addView(scrollView)

        // Populate list
        fun populateList(filter: String = "") {
            listContainer.removeAllViews()
            val filtered = if (filter.isEmpty()) allCategories
            else allCategories.filter { it.name.contains(filter, ignoreCase = true) }

            filtered.forEach { cat ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE; cornerRadius = dp(12f)
                        setColor(Color.parseColor("#F9FAFB"))
                    }
                    setPadding(dp(12f).toInt(), dp(10f).toInt(), dp(12f).toInt(), dp(10f).toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(6f).toInt() }
                    setOnClickListener { logExpense(cat.name) }
                }

                // Category icon
                val iconBg = FrameLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(36f).toInt(), dp(36f).toInt()).apply {
                        marginEnd = dp(12f).toInt()
                    }
                }
                CategoryIcon.applyIcon(iconBg, cat.name, cat.colorHex, cat.iconTint)
                row.addView(iconBg)

                // Category name
                row.addView(TextView(this).apply {
                    text = cat.name
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    setTextColor(Color.parseColor("#1A1A2E"))
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                })
                listContainer.addView(row)
            }

            if (filtered.isEmpty()) {
                listContainer.addView(TextView(this).apply {
                    text = "No categories found"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setTextColor(Color.parseColor("#9CA3AF"))
                    gravity = Gravity.CENTER
                    setPadding(0, dp(24f).toInt(), 0, dp(24f).toInt())
                })
            }

            // Inline "+ Add Category {name}" row — shown once the typed name
            // reaches 3 characters and doesn't exactly match an existing category.
            val trimmed = filter.trim()
            val exactMatch = allCategories.any { it.name.equals(trimmed, ignoreCase = true) }
            if (trimmed.length >= 3 && !exactMatch) {
                val addRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE; cornerRadius = dp(12f)
                        setColor(Color.parseColor("#F0FDF4"))
                    }
                    setPadding(dp(12f).toInt(), dp(10f).toInt(), dp(12f).toInt(), dp(10f).toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(6f).toInt() }
                    setOnClickListener {
                        val name = CategoryManager.normalizeName(trimmed)
                        if (name.isEmpty()) return@setOnClickListener
                        if (CategoryManager.allCategories.size >= CategoryManager.MAX_CATEGORIES) {
                            Toast.makeText(this@OverlayService, "Maximum ${CategoryManager.MAX_CATEGORIES} categories", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        val existing = CategoryManager.activeCategories.find { it.name.equals(name, ignoreCase = true) }
                        if (existing != null) {
                            logExpense(existing.name, selectedEventId)
                            return@setOnClickListener
                        }
                        // archived category with the same name, or an event with that name
                        if (CategoryManager.allCategories.any { it.name.equals(name, ignoreCase = true) }) {
                            Toast.makeText(this@OverlayService, "Category '$name' already exists", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        if (EventManager.eventNameExists(this@OverlayService, name)) {
                            Toast.makeText(this@OverlayService, "Event '$name' already exists", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        val colors = CategoryManager.getNextCustomColor(CategoryManager.allCategories)
                        val newCat = Category(
                            id = "custom_${System.currentTimeMillis()}",
                            name = name,
                            iconName = "custom",
                            colorHex = colors.first,
                            chartColor = colors.second,
                            iconTint = colors.third,
                            isActive = true,
                            sortOrder = CategoryManager.allCategories.size
                        )
                        CategoryDbHelper(this@OverlayService).insertCategory(newCat)
                        CategoryManager.initialize(this@OverlayService)
                        logExpense(name, selectedEventId)
                    }
                }
                addRow.addView(TextView(this).apply {
                    text = "+  Add Category $trimmed"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setTextColor(Color.parseColor("#2D6A4F"))
                    typeface = Typeface.DEFAULT_BOLD
                })
                listContainer.addView(addRow)
            }
        }

        populateList()

        // Search filter
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                populateList(s?.toString() ?: "")
            }
        })

        overlayView = card
        Log.d(TAG, "Showing expanded overlay")

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        // Focusable so keyboard works for search
        val lp = WindowManager.LayoutParams(
            dp(370f).toInt(), WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER; softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE }

        try { wm?.addView(overlayView, lp) }
        catch (e: Exception) { Log.e(TAG, "Failed to show expanded overlay", e); overlayView = null; return }

        // Auto-focus search
        focusRunnable = Runnable {
            searchInput.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
        }
        handler.postDelayed(focusRunnable!!, 200)
    }

    // ═══════════════════════════════════════════════════════════════════
    // Shared helpers
    // ═══════════════════════════════════════════════════════════════════

    private fun createMinimizeButton(onClick: () -> Unit): FrameLayout {
        val btn = FrameLayout(this).apply {
            val size = dp(34f).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = dp(8f).toInt() }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#F3F4F6"))
            }
            setOnClickListener { onClick() }
        }
        btn.addView(TextView(this).apply {
            text = "–"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(Color.parseColor("#6B7280")); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        })
        return btn
    }

    // ═══════════════════════════════════════════════════════════════════
    // Minimized bubble
    // ═══════════════════════════════════════════════════════════════════

    /** Closes the popup (timer stops) and leaves a small floating bubble. */
    private fun minimizeToBubble() {
        eventAddRowOpen = false
        dismissCurrent()
        showBubble()
    }

    private fun showBubble() {
        if (!Settings.canDrawOverlays(this)) return

        val bv = MinimizedBubbleView(this)
        val sizePx = dp(60f).toInt()
        bv.layoutParams = FrameLayout.LayoutParams(sizePx, sizePx, Gravity.CENTER)
        bv.elevation = dp(6f)
        bv.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }

        val pad = dp(10f).toInt()
        val host = FrameLayout(this).apply {
            setPadding(pad, pad, pad, pad)
            clipChildren = false
            clipToPadding = false
            addView(bv)
        }
        bubbleView = bv
        overlayView = host        // keeps new payments queueing behind the bubble
        updateBubbleLabel()

        val dm = resources.displayMetrics
        val hostPx = sizePx + pad * 2
        if (bubbleX < 0 || bubbleY < 0) {
            bubbleX = dm.widthPixels - hostPx
            bubbleY = (dm.heightPixels * 0.30f).toInt()
        }
        // screen size may have changed (rotation): keep the saved position on screen
        bubbleX = bubbleX.coerceIn(0, (dm.widthPixels - hostPx).coerceAtLeast(0))
        bubbleY = bubbleY.coerceIn(0, (dm.heightPixels - hostPx).coerceAtLeast(0))

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bubbleX; y = bubbleY
        }

        try { wm?.addView(host, lp) }
        catch (e: Exception) {
            Log.e(TAG, "Failed to show bubble", e)
            bubbleView = null; overlayView = null
            // fall back to the normal popup so the transaction is not lost
            showOverlay(preserveEventSelection = true, skipSound = true)
            return
        }

        // Tap: back to the normal popup, timer starts again
        host.setOnClickListener {
            dismissCurrent()
            showOverlay(preserveEventSelection = true, skipSound = true)
        }

        makeDraggable(host, lp) { dragging ->
            bubbleDragging = dragging
            if (dragging) {
                jiggleAnim?.cancel()
            } else {
                // keep the bubble on screen and remember where it was left
                val maxX = (dm.widthPixels - hostPx).coerceAtLeast(0)
                val maxY = (dm.heightPixels - hostPx).coerceAtLeast(0)
                lp.x = lp.x.coerceIn(0, maxX)
                lp.y = lp.y.coerceIn(0, maxY)
                bubbleX = lp.x; bubbleY = lp.y
                try { wm?.updateViewLayout(host, lp) } catch (_: Exception) {}
            }
        }

        handler.removeCallbacks(jiggleTick)
        handler.postDelayed(jiggleTick, JIGGLE_FIRST_MS)
    }

    /** Number of payments waiting, including the minimized one. */
    private fun pendingCount() = 1 + queue.size

    private fun updateBubbleLabel() {
        val bv = bubbleView ?: return
        val count = pendingCount()
        if (count >= 2) {
            val text = if (count > 99) "99+" else count.toString()
            val units = when {
                count > 99 -> 12f
                count >= 10 -> 17f
                else -> 21f
            }
            bv.setLabel(text, units)
        } else {
            val sym = try { AppPreferences.getCurrencySymbol(this) } catch (_: Exception) { "₹" }
            val text = formatBubbleAmount(sym, currentAmount)
            val units = when {
                text.length <= 3 -> 13f
                text.length == 4 -> 11f
                text.length == 5 -> 9.6f
                else -> 8.6f
            }
            bv.setLabel(text, units)
        }
    }

    private fun refreshBubble(strong: Boolean) {
        updateBubbleLabel()
        if (strong) {
            playJiggle(strong = true)
            // restart the 10 s rhythm after the stronger jiggle
            handler.removeCallbacks(jiggleTick)
            handler.postDelayed(jiggleTick, JIGGLE_INTERVAL_MS)
        }
    }

    /** 999 -> "₹999", 12533 -> "₹12.5k"; rupees use k / L / Cr, others k / M / B. */
    private fun formatBubbleAmount(sym: String, amount: Double): String {
        val whole = Math.round(amount)
        if (whole < 1000) return "$sym$whole"
        val units: List<Pair<Double, String>> =
            if (sym == "₹") listOf(1_000.0 to "k", 100_000.0 to "L", 10_000_000.0 to "Cr")
            else listOf(1_000.0 to "k", 1_000_000.0 to "M", 1_000_000_000.0 to "B")
        fun round1(x: Double) = Math.round(x * 10.0) / 10.0
        var idx = units.indexOfLast { whole >= it.first }.coerceAtLeast(0)
        var v = round1(whole / units[idx].first)
        // e.g. 99,960 rounds to 100.0k, which should read 1L
        while (idx < units.lastIndex && v * units[idx].first >= units[idx + 1].first) {
            idx++
            v = round1(whole / units[idx].first)
        }
        val body = if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale.US, "%.1f", v)
        return "$sym$body${units[idx].second}"
    }

    /** Short wobble. Normal: 5 degrees. Strong (new payment arrived): 10 degrees plus a small pop. */
    private fun playJiggle(strong: Boolean) {
        val v = bubbleView ?: return
        if (bubbleDragging) return
        jiggleAnim?.cancel()
        val amp  = if (strong) 10f else 5f
        val dur  = if (strong) 800L else 600L
        val freq = if (strong) 4f else 3.5f
        jiggleAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = dur
            interpolator = LinearInterpolator()
            addUpdateListener { a ->
                val f = a.animatedValue as Float
                val t = f * dur / 1000f
                v.rotation = amp * Math.sin(2.0 * Math.PI * freq * t).toFloat() * (1f - f)
                if (strong) {
                    val p = Math.min(t / 0.3f, 1f)
                    val sc = 1f + 0.12f * Math.sin(Math.PI * p).toFloat()
                    v.scaleX = sc; v.scaleY = sc
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    v.rotation = 0f; v.scaleX = 1f; v.scaleY = 1f
                }
            })
            start()
        }
    }

    private fun createDismissButton(onClick: () -> Unit): FrameLayout {
        val btn = FrameLayout(this).apply {
            val size = dp(34f).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FEE2E2"))
            }
            setOnClickListener { onClick() }
        }
        btn.addView(TextView(this).apply {
            text = "✕"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.parseColor("#EF4444")); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        })
        return btn
    }

    private fun addChip(container: LinearLayout, name: String, bgHex: String, iconHex: String, textHex: String) {
        val chip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(20f)
                setColor(Color.parseColor(bgHex))
            }
            setPadding(dp(12f).toInt(), dp(8f).toInt(), dp(16f).toInt(), dp(8f).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(4f).toInt(), 0, dp(4f).toInt(), 0) }
            setOnClickListener { logExpense(name, selectedEventId) }
        }
        chip.addView(CategoryIcon.createChipIcon(this, name, Color.parseColor(iconHex), 18))
        chip.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(5f).toInt(), 1)
        })
        chip.addView(TextView(this).apply {
            text = if (name.length > 9) name.take(8) + ".." else name
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Color.parseColor(textHex))
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            maxLines = 1
        })
        container.addView(chip)
    }

    private fun logExpense(category: String, rawEventId: String? = selectedEventId) {
        // Ignore an event that was archived/deleted while the popup was open
        val eventId = rawEventId?.takeIf { id -> EventManager.activeEvents.any { it.id == id } }
        Log.d(TAG, "Category tapped: $category — amount=$currentAmount source=$currentSource channel=$currentChannel eventId=$eventId")
        db?.insertExpense(category, currentAmount, currentSource, currentChannel, eventId)
        val sym = try { AppPreferences.getCurrencySymbol(this) } catch (_: Exception) { "₹" }
        Toast.makeText(this, "✓ $category — $sym${String.format("%.2f", currentAmount)}", Toast.LENGTH_SHORT).show()
        // Hide keyboard if showing
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            overlayView?.windowToken?.let { imm.hideSoftInputFromWindow(it, 0) }
        } catch (_: Exception) {}
        dismissCurrent(); showNext()
    }

    private fun dismissCurrent() {
        Log.d(TAG, "Overlay dismissed")
        timerAnim?.cancel(); timerAnim = null
        handler.removeCallbacks(autoHide)
        // Bubble housekeeping (no-op when no bubble is showing)
        handler.removeCallbacks(jiggleTick)
        focusRunnable?.let { handler.removeCallbacks(it) }; focusRunnable = null
        jiggleAnim?.cancel(); jiggleAnim = null
        bubbleView = null
        bubbleDragging = false
        // Hide keyboard
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            overlayView?.windowToken?.let { imm.hideSoftInputFromWindow(it, 0) }
        } catch (_: Exception) {}
        overlayView?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        overlayView = null
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density

    // ── Sound ───────────────────────────────────────────────────────────
    private fun initSound() {
        try {
            val attr = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            soundPool = SoundPool.Builder().setMaxStreams(1).setAudioAttributes(attr).build()
            popSoundId = soundPool!!.load(this, R.raw.pop, 1)
        } catch (e: Exception) {
            Log.e(TAG, "SoundPool init failed", e)
        }
    }

    private fun playPop() {
        try { soundPool?.play(popSoundId, 0.7f, 0.7f, 1, 0, 1f) }
        catch (_: Exception) {}
    }

    // ── Draggable overlay ───────────────────────────────────────────────
    private fun makeDraggable(view: View, lp: WindowManager.LayoutParams, onDragState: ((Boolean) -> Unit)? = null) {
        var startX = 0f
        var startY = 0f
        var startParamX = 0
        var startParamY = 0
        var isDrag = false
        var sendingCancel = false
        val dragThreshold = dp(8f)

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    startParamX = lp.x
                    startParamY = lp.y
                    if (isDrag) onDragState?.invoke(false)
                    isDrag = false
                    false // Don't consume — let children get the DOWN
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY
                    if (!isDrag && (Math.abs(dx) > dragThreshold || Math.abs(dy) > dragThreshold)) {
                        isDrag = true
                        onDragState?.invoke(true)
                        // Cancel children's touch handling
                        val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                        sendingCancel = true
                        v.dispatchTouchEvent(cancel)
                        sendingCancel = false
                        cancel.recycle()
                    }
                    if (isDrag) {
                        lp.x = startParamX + dx.toInt()
                        lp.y = startParamY + dy.toInt()
                        try { wm?.updateViewLayout(view, lp) } catch (_: Exception) {}
                        true
                    } else false
                }
                MotionEvent.ACTION_UP -> {
                    val wasDrag = isDrag
                    isDrag = false
                    if (wasDrag) onDragState?.invoke(false)
                    wasDrag // Consume only if was dragging
                }
                MotionEvent.ACTION_CANCEL -> {
                    // A real cancel (not the one we send ourselves) also ends the drag
                    if (!sendingCancel && isDrag) {
                        isDrag = false
                        onDragState?.invoke(false)
                    }
                    false
                }
                else -> false
            }
        }
    }

    // ── SMS ContentObserver ─────────────────────────────────────────────
    private fun registerSmsObserver() {
        try {
            val observer = SmsObserver(this, handler)
            observer.seedLastId()
            contentResolver.registerContentObserver(
                SmsObserver.OBSERVE_URI, true, observer
            )
            smsObserver = observer
            Log.d(TAG, "SmsObserver registered — watching inbox")
        } catch (e: Exception) {
            Log.e(TAG, "SmsObserver registration failed", e)
        }
    }

    private fun unregisterSmsObserver() {
        smsObserver?.let {
            try {
                contentResolver.unregisterContentObserver(it)
                Log.d(TAG, "SmsObserver unregistered")
            } catch (e: Exception) {
                Log.e(TAG, "SmsObserver unregister failed", e)
            }
        }
        smsObserver = null
    }
}
