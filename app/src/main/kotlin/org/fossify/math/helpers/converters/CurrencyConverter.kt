package org.fossify.math.helpers.converters

import android.content.Context
import org.fossify.math.R
import org.fossify.math.helpers.MATH_CONTEXT
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency
import java.util.Locale

/**
 * Currencies at the ECB's euro reference rates ([perEuro]: how much of each currency one euro buys). The euro is the
 * base, so a currency's factor is 1 / its rate. Names come from the phone's language ("americký dolár"), the symbol
 * pill shows the code (USD).
 */
class CurrencyConverter(perEuro: Map<String, BigDecimal>) : Converter {
    override val nameResId = R.string.dpad_currency_converter
    override val imageResId = R.drawable.ic_currency_vector
    override val key = KEY

    override val units: List<Converter.Unit> = buildList {
        add(CurrencyUnit(EURO, BigDecimal.ONE))
        perEuro.filterKeys { it != EURO }.forEach { (code, rate) -> add(CurrencyUnit(code, rate)) }
    }.sortedWith(compareBy<Converter.Unit> { FIRST.indexOf(it.key).let { index -> if (index < 0) FIRST.size else index } }.thenBy { it.key })

    override val defaultTopUnit: Converter.Unit = units.first { it.key == EURO }
    override val defaultBottomUnit: Converter.Unit = units.firstOrNull { it.key == "USD" } ?: units.last()

    /** Money: cents for amounts of 1 and more, a few significant digits for less (1 JPY = 0.00565 EUR). */
    override fun roundResult(value: BigDecimal): BigDecimal {
        return if (value.abs() >= BigDecimal.ONE || value.signum() == 0) {
            value.setScale(2, RoundingMode.HALF_UP)
        } else {
            value.round(java.math.MathContext(SMALL_AMOUNT_DIGITS, RoundingMode.HALF_UP))
        }
    }

    private class CurrencyUnit(code: String, perEuro: BigDecimal) :
        Converter.Unit(0, 0, BigDecimal.ONE.divide(perEuro, MATH_CONTEXT), code) {
        override fun displayName(context: Context): String {
            val name = try {
                Currency.getInstance(key).getDisplayName(Locale.getDefault())
            } catch (ignored: IllegalArgumentException) {
                key
            }
            return name.replaceFirstChar { it.titlecase(Locale.getDefault()) }
        }

        override fun displaySymbol(context: Context) = key
    }

    companion object {
        const val KEY = "currency"
        const val EURO = "EUR"
        private const val SMALL_AMOUNT_DIGITS = 3

        /** The ones used most around here come first, the rest by their code. */
        private val FIRST = listOf("EUR", "USD", "GBP", "CHF", "CZK", "PLN", "HUF")
    }
}
