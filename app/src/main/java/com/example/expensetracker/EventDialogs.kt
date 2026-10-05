package com.example.expensetracker

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView

/**
 * Shared dialogs used by the Events feature.
 * Both dialogs follow the same layout: title + "✕" cancel at the top right,
 * a short explanation, and two clearly different choices.
 */
object EventDialogs {

    /** Dialog header: [title] on the left, round "✕" cancel button at the top right. */
    fun addHeader(activity: Activity, card: LinearLayout, title: String, dialog: AlertDialog) {
        val d = activity.resources.displayMetrics.density
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(activity).apply {
            text = title
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#1A1A2E"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(FrameLayout(activity).apply {
            val size = (32 * d).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#F3F4F6"))
            }
            addView(TextView(activity).apply {
                text = "✕"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#6B7280"))
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
                )
            })
            setOnClickListener { dialog.dismiss() }
        })
        card.addView(header)

    }

    /** Styles the Home/Breakdown "Event ▾" pill: outlined when empty, green with the name when selected. */
    fun styleEventPill(activity: Activity, pill: LinearLayout, eventId: String?) {
        val label = pill.findViewById<TextView>(R.id.tvEventFilterLabel)
        val arrow = pill.findViewById<ImageView>(R.id.ivEventFilterArrow)
        val ev = EventManager.getEventById(eventId)
        if (ev == null) {
            label.text = "Event"
            label.setTextColor(ThemeColors.secondary(activity))
            pill.setBackgroundResource(R.drawable.bg_filter_inactive)
            arrow.clearColorFilter()
        } else {
            label.text = ev.name
            label.setTextColor(Color.WHITE)
            pill.setBackgroundResource(R.drawable.bg_filter_active)
            arrow.setColorFilter(Color.WHITE)
        }
    }

    /** Dropdown of "No Event" + all events (archived marked). */
    fun showEventFilterMenu(activity: Activity, anchor: android.view.View, onPicked: (String?) -> Unit) {
        val sorted = EventManager.allEvents.sortedBy { it.name.lowercase() }
        val popup = PopupMenu(activity, anchor)
        popup.menu.add(0, -1, 0, "No Event")
        sorted.forEachIndexed { idx, ev ->
            popup.menu.add(0, idx, idx + 1, if (ev.isActive) ev.name else "${ev.name} (Archived)")
        }
        popup.setOnMenuItemClickListener { item ->
            onPicked(if (item.itemId == -1) null else sorted.getOrNull(item.itemId)?.id)
            true
        }
        popup.show()
    }

    /**
     * Two-choice dialog with a cancel "✕" at the top right.
     * The second choice is styled as the destructive one.
     */
    fun showTwoOptionDialog(
        activity: Activity,
        title: String,
        message: String,
        firstTitle: String,
        firstSub: String,
        secondTitle: String,
        secondSub: String,
        onFirst: () -> Unit,
        onSecond: () -> Unit
    ) {
        val d = activity.resources.displayMetrics.density

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20 * d
                setColor(Color.WHITE)
            }
            setPadding((22 * d).toInt(), (18 * d).toInt(), (22 * d).toInt(), (22 * d).toInt())
        }

        val dialog = AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Light_Dialog)
            .setView(card)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        addHeader(activity, card, title, dialog)

        card.addView(TextView(activity).apply {
            text = message
            textSize = 13.5f
            setTextColor(Color.parseColor("#6B7280"))
            setPadding(0, (10 * d).toInt(), 0, (14 * d).toInt())
        })

        fun option(titleText: String, subText: String, bg: String, titleColor: String, onClick: () -> Unit) {
            val box = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 14 * d
                    setColor(Color.parseColor(bg))
                }
                setPadding((16 * d).toInt(), (12 * d).toInt(), (16 * d).toInt(), (12 * d).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (8 * d).toInt() }
                setOnClickListener { dialog.dismiss(); onClick() }
            }
            box.addView(TextView(activity).apply {
                text = titleText
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor(titleColor))
            })
            box.addView(TextView(activity).apply {
                text = subText
                textSize = 12.5f
                setTextColor(Color.parseColor("#6B7280"))
                setPadding(0, (2 * d).toInt(), 0, 0)
            })
            card.addView(box)
        }

        option(firstTitle, firstSub, "#F0FDF4", "#2D6A4F", onFirst)
        option(secondTitle, secondSub, "#FEF2F2", "#B91C1C", onSecond)

        dialog.show()
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.90).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    /**
     * Delete an event: "Event only" keeps its transactions (they just lose the tag),
     * "Event and Transactions" deletes everything. ✕ cancels.
     */
    fun showDeleteEventDialog(activity: Activity, ev: Event, onDone: () -> Unit) {
        val count = ExpenseDbHelper(activity).getTransactionCountForEvent(ev.id)
        val countText = when (count) {
            0 -> "It has no transactions."
            1 -> "It has 1 transaction."
            else -> "It has $count transactions."
        }
        showTwoOptionDialog(
            activity,
            title = "Delete \"${ev.name}\"?",
            message = "$countText Choose what should happen to them.",
            firstTitle = "Event only",
            firstSub = if (count == 0) "Delete the event" else "Delete the event, keep the transactions (they just lose the event tag)",
            secondTitle = "Event and Transactions",
            secondSub = if (count == 0) "Delete the event" else "Permanently delete the event and its $count transaction${if (count == 1) "" else "s"}",
            onFirst = {
                EventManager.deleteEvent(activity, ev.id, deleteTransactions = false)
                onDone()
            },
            onSecond = {
                EventManager.deleteEvent(activity, ev.id, deleteTransactions = true)
                onDone()
            }
        )
    }
}
