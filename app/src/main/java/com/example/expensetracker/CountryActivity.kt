package com.example.expensetracker

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class CountryActivity : AppCompatActivity() {

    private var selectedCountry = CountryData.countries.find { it.name == "India" }
        ?: CountryData.countries.first()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_country)

        val tvFlag     = findViewById<TextView>(R.id.tvCountryFlag)
        val tvName     = findViewById<TextView>(R.id.tvCountryName)
        val selector   = findViewById<android.view.View>(R.id.countrySelector)
        val btnGoAhead = findViewById<Button>(R.id.btnGoAhead)

        tvFlag.text = selectedCountry.flag
        tvName.text = selectedCountry.name

        selector.setOnClickListener { showCountryPicker(tvFlag, tvName) }

        btnGoAhead.setOnClickListener {
            // 1. Save country + currency
            AppPreferences.setCountry(this, selectedCountry.name)
            AppPreferences.setCurrencySymbol(this, selectedCountry.symbol)

            // 2. Navigate to Permissions page
            val intent = Intent(this, PermissionsActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                           Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun showCountryPicker(tvFlag: TextView, tvName: TextView) {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_country_picker, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        val etSearch = dialogView.findViewById<EditText>(R.id.etSearch)
        val listView = dialogView.findViewById<ListView>(R.id.countryList)
        val btnClose = dialogView.findViewById<TextView>(R.id.btnClose)

        val filtered = CountryData.countries.toMutableList()

        val adapter = object : ArrayAdapter<CountryItem>(
            this, android.R.layout.simple_list_item_1, filtered
        ) {
            override fun getView(pos: Int, cv: android.view.View?,
                                 parent: android.view.ViewGroup): android.view.View {
                val v = super.getView(pos, cv, parent) as TextView
                v.text     = "${filtered[pos].flag}  ${filtered[pos].name}"
                v.textSize = 15f
                v.setTextColor(android.graphics.Color.parseColor("#1A1A2E"))
                v.setPadding(16, 16, 16, 16)
                return v
            }
        }
        listView.adapter = adapter

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.lowercase() ?: ""
                filtered.clear()
                filtered.addAll(CountryData.countries.filter {
                    it.name.lowercase().contains(q)
                })
                adapter.notifyDataSetChanged()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        listView.setOnItemClickListener { _, _, pos, _ ->
            selectedCountry = filtered[pos]
            tvFlag.text     = selectedCountry.flag
            tvName.text     = selectedCountry.name
            dialog.dismiss()
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}
