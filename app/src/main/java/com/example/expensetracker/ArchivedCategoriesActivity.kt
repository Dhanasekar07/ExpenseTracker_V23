package com.example.expensetracker

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class ArchivedCategoriesActivity : AppCompatActivity() {

    private lateinit var listContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_archived_categories)

        listContainer = findViewById(R.id.archivedListContainer)
        CategoryManager.initialize(this)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        buildList()
    }

    override fun onResume() { super.onResume(); buildList() }

    private fun buildList() {
        listContainer.removeAllViews()
        CategoryManager.archivedCategories.forEach { cat ->
            val row = layoutInflater.inflate(R.layout.item_archived_row, null)

            val iconBg = row.findViewById<android.widget.FrameLayout>(R.id.iconContainer)
            CategoryIcon.applyIcon(iconBg, cat.name, cat.colorHex, cat.iconTint)

            row.findViewById<TextView>(R.id.tvCategoryName).text = cat.name

            row.findViewById<ImageView>(R.id.btnActivate).setOnClickListener {
                CategoryManager.activateCategory(this, cat.id)
                buildList()
                Toast.makeText(this, "${cat.name} added to active", Toast.LENGTH_SHORT).show()
            }

            row.findViewById<ImageView>(R.id.btnEdit).setOnClickListener {
                showRenameDialog(cat.id, cat.name)
            }

            row.findViewById<ImageView>(R.id.btnDelete).setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Delete Category")
                    .setMessage(getString(R.string.delete_confirmation))
                    .setPositiveButton("Delete") { _, _ ->
                        CategoryManager.deleteCategory(this, cat.id)
                        buildList()
                    }
                    .setNegativeButton("Cancel", null)
                    .create().apply {
                        setOnShowListener {
                            getButton(AlertDialog.BUTTON_POSITIVE)
                                .setTextColor(ThemeColors.amountNeg(this@ArchivedCategoriesActivity))
                        }
                    }.show()
            }

            row.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 8) }

            listContainer.addView(row)
        }

        // Storage management card at bottom
        if (CategoryManager.archivedCategories.isEmpty()) {
            listContainer.addView(TextView(this).apply {
                text = "No archived categories"
                textSize = 14f
                setTextColor(ThemeColors.hint(this))
                gravity = android.view.Gravity.CENTER
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
        val cat = CategoryManager.allCategories.find { it.id == id }

        val card = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 20 * d
                setColor(android.graphics.Color.WHITE)
            }
            setPadding((24*d).toInt(), (24*d).toInt(), (24*d).toInt(), (24*d).toInt())
        }

        card.addView(android.widget.TextView(ctx).apply {
            text = "Rename Category"
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(android.graphics.Color.parseColor("#1A1A2E"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, (20*d).toInt())
        })

        if (cat != null) {
            val previewRow = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER
                setPadding(0, 0, 0, (16*d).toInt())
            }
            val iconBg = android.widget.FrameLayout(ctx).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams((36*d).toInt(), (36*d).toInt()).apply {
                    marginEnd = (10*d).toInt()
                }
            }
            CategoryIcon.applyIcon(iconBg, cat.name, cat.colorHex, cat.iconTint, 18)
            previewRow.addView(iconBg)
            previewRow.addView(android.widget.TextView(ctx).apply {
                text = currentName
                textSize = 15f
                setTextColor(android.graphics.Color.parseColor("#6B7280"))
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            })
            card.addView(previewRow)
        }

        card.addView(android.widget.TextView(ctx).apply {
            text = "New Name"
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(android.graphics.Color.parseColor("#6B7280"))
            setPadding((4*d).toInt(), 0, 0, (6*d).toInt())
        })

        val input = android.widget.EditText(ctx).apply {
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

        val btnRow = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (20*d).toInt() }
        }

        btnRow.addView(android.widget.TextView(ctx).apply {
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
            layoutParams = android.widget.LinearLayout.LayoutParams(0, (48*d).toInt(), 1f).apply {
                marginEnd = (8*d).toInt()
            }
            setOnClickListener { dialog.dismiss() }
        })

        btnRow.addView(android.widget.TextView(ctx).apply {
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
            layoutParams = android.widget.LinearLayout.LayoutParams(0, (48*d).toInt(), 1f)
            setOnClickListener {
                val newName = CategoryManager.normalizeName(input.text.toString())
                if (newName.isEmpty()) {
                    android.widget.Toast.makeText(ctx, "Enter a category name", android.widget.Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val exists = CategoryManager.allCategories.any {
                    it.id != id && it.name.equals(newName, ignoreCase = true)
                }
                if (exists) {
                    android.widget.Toast.makeText(ctx, "Category '$newName' already exists", android.widget.Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (EventManager.eventNameExists(ctx, newName)) {
                    android.widget.Toast.makeText(ctx, "Event '$newName' already exists", android.widget.Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                CategoryManager.renameCategory(ctx, id, newName)
                dialog.dismiss()
                buildList()
            }
        })
        card.addView(btnRow)

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )
    }
}
