package org.fossify.math.helpers

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Xml
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.math.extensions.config
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL

/**
 * The euro reference rates of the European Central Bank: published once per working day around 16:00 CET, about 30
 * currencies. The last downloaded ones are kept in the app's settings, so the converter works offline; a copy that
 * comes with the app covers the very first start without internet. Nothing runs in the background: the rates are
 * downloaded only when the currency tab is shown (and the kept ones are a few hours old) or from its menu.
 */
object CurrencyRates {
    private const val TAG = "CurrencyRates"
    private const val ECB_URL = "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml"
    private const val BUNDLED_FILE = "ecb_rates.xml"
    private const val TIMEOUT_MS = 10_000
    private const val REFRESH_AFTER_MS = 3 * 60 * 60 * 1000L

    /** [date] is the ECB's publication day (2026-10-13), [checkedAt] when they were last downloaded (0 = bundled). */
    class Rates(val date: String, val perEuro: Map<String, BigDecimal>, val checkedAt: Long)

    fun load(context: Context): Rates {
        val config = context.config
        val stored = config.dpadCurrencyRates
        if (stored.isNotEmpty()) {
            parseStored(stored)?.let { return Rates(config.dpadCurrencyRatesDate, it, config.dpadCurrencyRatesCheckedAt) }
        }
        return context.assets.open(BUNDLED_FILE).use { parseEcb(it) }?.let { Rates(it.first, it.second, 0) }
            ?: Rates("", emptyMap(), 0)
    }

    /** Downloaded more than a few hours ago (or never): worth trying again. */
    fun isOld(rates: Rates) = System.currentTimeMillis() - rates.checkedAt > REFRESH_AFTER_MS

    /** Downloads the current rates; [callback] gets them on the main thread, or null without internet or on an error. */
    fun refresh(context: Context, callback: (Rates?) -> Unit) {
        val appContext = context.applicationContext
        ensureBackgroundThread {
            val result = try {
                val connection = (URL(ECB_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                }
                try {
                    val code = connection.responseCode
                    if (code != HttpURLConnection.HTTP_OK) {
                        Log.w(TAG, "The ECB answered $code")
                    }
                    if (code == HttpURLConnection.HTTP_OK) connection.inputStream.use { parseEcb(it) } else null
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't download the ECB rates", e)
                null
            }

            val rates = result?.takeIf { it.second.isNotEmpty() }?.let { (date, perEuro) ->
                val now = System.currentTimeMillis()
                appContext.config.apply {
                    dpadCurrencyRates = perEuro.entries.joinToString(";") { "${it.key}=${it.value.toPlainString()}" }
                    dpadCurrencyRatesDate = date
                    dpadCurrencyRatesCheckedAt = now
                }
                Rates(date, perEuro, now)
            }
            Handler(Looper.getMainLooper()).post { callback(rates) }
        }
    }

    private fun parseStored(text: String): Map<String, BigDecimal>? = try {
        text.split(';').associate { entry ->
            val (code, rate) = entry.split('=')
            code to BigDecimal(rate)
        }
    } catch (ignored: Exception) {
        null
    }

    /** The ECB's daily XML: `<Cube time='2026-10-13'>` with `<Cube currency='USD' rate='1.1188'/>` rows. */
    private fun parseEcb(input: InputStream): Pair<String, Map<String, BigDecimal>>? {
        val parser = Xml.newPullParser()
        parser.setInput(input, null)
        var date = ""
        val perEuro = LinkedHashMap<String, BigDecimal>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG || parser.name != "Cube") {
                continue
            }
            parser.getAttributeValue(null, "time")?.let { date = it }
            val currency = parser.getAttributeValue(null, "currency")
            val rate = parser.getAttributeValue(null, "rate")
            if (currency != null && rate != null) {
                rate.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }?.let { perEuro[currency] = it }
            }
        }
        return if (date.isEmpty() || perEuro.isEmpty()) null else date to perEuro
    }
}
