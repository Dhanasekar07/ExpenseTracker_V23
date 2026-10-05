package com.example.expensetracker

import org.junit.Assert.*
import org.junit.Test

class CountryDataTest {

    @Test fun `countries list is not empty`() {
        assertTrue(CountryData.countries.isNotEmpty())
    }

    @Test fun `every country has non-blank name`() {
        CountryData.countries.forEach {
            assertTrue("Country name blank: $it", it.name.isNotBlank())
        }
    }

    @Test fun `every country has non-blank flag`() {
        CountryData.countries.forEach {
            assertTrue("Flag blank for ${it.name}", it.flag.isNotBlank())
        }
    }

    @Test fun `every country has non-blank currency code`() {
        CountryData.countries.forEach {
            assertTrue("Currency blank for ${it.name}", it.currency.isNotBlank())
        }
    }

    @Test fun `every country has non-blank symbol`() {
        CountryData.countries.forEach {
            assertTrue("Symbol blank for ${it.name}", it.symbol.isNotBlank())
        }
    }

    @Test fun `no duplicate country names`() {
        val names = CountryData.countries.map { it.name }
        assertEquals("Duplicate country names found", names.size, names.distinct().size)
    }

    @Test fun `India is present with INR and rupee symbol`() {
        val india = CountryData.countries.find { it.name == "India" }
        assertNotNull("India not found", india)
        assertEquals("INR", india!!.currency)
        assertEquals("₹", india.symbol)
    }

    @Test fun `United States is present with USD`() {
        val us = CountryData.countries.find { it.name == "United States" }
        assertNotNull("US not found", us)
        assertEquals("USD", us!!.currency)
        assertEquals("$", us.symbol)
    }

    @Test fun `list is sorted alphabetically`() {
        val names = CountryData.countries.map { it.name }
        assertEquals(names.sorted(), names)
    }
}
