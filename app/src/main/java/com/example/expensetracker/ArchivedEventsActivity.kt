package com.example.expensetracker

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class ArchivedEventsActivity : AppCompatActivity() {

    private lateinit var listContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_archived_events)

        listContainer = findViewById(R.id.archivedListContainer)
        EventManager.initialize(this)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        buildList()
    }

    override fun onResume() { super.onResume(); buildList() }

    private fun buildList() {
        listContainer.removeAllViews()
        EventManager.archivedEvents.forEach { ev ->
            val row = layoutInflater.inflate(R.layout.item_archived_event_row, null)

            val iconBg = row.findViewById<FrameLayout>(R.id.iconContainer)
            CategoryIcon.applyIcon(iconBg, ev.name, ev.colorHex, ev.iconTint)

            row.findViewById<TextView>(R.id.tvEventName).text = ev.name

            row.findViewById<ImageView>(R.id.btnActivate).setOnClickListener {
                EventManager.restoreEvent(this, ev.id)
                buildList()
                Toast.makeText(this, "${ev.name} restored to active", Toast.LENGTH_SHORT).show()
            }

            row.findViewById<ImageView>(R.id.btnEdit).setOnClickListener {
                showRenameDialog(ev.id, ev.name)
            }

            row.findViewById<ImageView>(R.id.btnDelete).setOnClickListener {
                EventDialogs.showDeleteEventDialog(this, ev) { buildList() }
            }

            row.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 8) }

            listContainer.addView(row)
        }

        if (EventManager.archivedEvents.isEmpty()) {
            listContainer.addView(TextView(this).apply {
                text = "No archived events"
                textSize = 14f
                setTextColor(ThemeColors.hint(this@ArchivedEventsActivity))
                gravity = Gravity.CENTER
                setPadding(0, 48, 0, 48)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
        }
    }

    private fun showRenameDialog(id: String, currentName: String) {
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

        val dialog = android.app.AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Light_Dialog)
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
                buildList()
            }
        })
        card.addView(btnRow)

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }
}
