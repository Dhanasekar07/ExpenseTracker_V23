package com.example.expensetracker

data class CountryItem(
    val name     : String,
    val flag     : String,
    val currency : String,
    val symbol   : String
)

object CountryData {
    val countries = listOf(
        CountryItem("Afghanistan",    "🇦🇫", "AFN", "؋"),
        CountryItem("Australia",      "🇦🇺", "AUD", "$"),
        CountryItem("Bangladesh",     "🇧🇩", "BDT", "৳"),
        CountryItem("Brazil",         "🇧🇷", "BRL", "R$"),
        CountryItem("Canada",         "🇨🇦", "CAD", "$"),
        CountryItem("China",          "🇨🇳", "CNY", "¥"),
        CountryItem("France",         "🇫🇷", "EUR", "€"),
        CountryItem("Germany",        "🇩🇪", "EUR", "€"),
        CountryItem("India",          "🇮🇳", "INR", "₹"),
        CountryItem("Indonesia",      "🇮🇩", "IDR", "Rp"),
        CountryItem("Italy",          "🇮🇹", "EUR", "€"),
        CountryItem("Japan",          "🇯🇵", "JPY", "¥"),
        CountryItem("Malaysia",       "🇲🇾", "MYR", "RM"),
        CountryItem("Mexico",         "🇲🇽", "MXN", "$"),
        CountryItem("Nepal",          "🇳🇵", "NPR", "Rs"),
        CountryItem("Netherlands",    "🇳🇱", "EUR", "€"),
        CountryItem("New Zealand",    "🇳🇿", "NZD", "$"),
        CountryItem("Nigeria",        "🇳🇬", "NGN", "₦"),
        CountryItem("Pakistan",       "🇵🇰", "PKR", "Rs"),
        CountryItem("Philippines",    "🇵🇭", "PHP", "₱"),
        CountryItem("Saudi Arabia",   "🇸🇦", "SAR", "﷼"),
        CountryItem("Singapore",      "🇸🇬", "SGD", "$"),
        CountryItem("South Africa",   "🇿🇦", "ZAR", "R"),
        CountryItem("South Korea",    "🇰🇷", "KRW", "₩"),
        CountryItem("Spain",          "🇪🇸", "EUR", "€"),
        CountryItem("Sri Lanka",      "🇱🇰", "LKR", "Rs"),
        CountryItem("Sweden",         "🇸🇪", "SEK", "kr"),
        CountryItem("Switzerland",    "🇨🇭", "CHF", "Fr"),
        CountryItem("Thailand",       "🇹🇭", "THB", "฿"),
        CountryItem("Turkey",         "🇹🇷", "TRY", "₺"),
        CountryItem("UAE",            "🇦🇪", "AED", "د.إ"),
        CountryItem("United Kingdom", "🇬🇧", "GBP", "£"),
        CountryItem("United States",  "🇺🇸", "USD", "$"),
        CountryItem("Vietnam",        "🇻🇳", "VND", "₫")
    ).sortedBy { it.name }
}
