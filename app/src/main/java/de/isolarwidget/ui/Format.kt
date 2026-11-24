package de.isolarwidget.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object Format {
    fun power(w: Double?): String = when {
        w == null -> "–"
        abs(w) >= 1000 -> String.format(Locale.GERMANY, "%.2f kW", w / 1000)
        else -> String.format(Locale.GERMANY, "%.0f W", w)
    }

    /** + Bezug / − Einspeisung, mit Richtung im Text. */
    fun grid(w: Double?): String = when {
        w == null -> "–"
        w > 0 -> "+${power(w)} Bezug"
        w < 0 -> "−${power(-w)} Einsp."
        else -> "0 W"
    }

    /** Zahl ohne Einheit, z. B. "5,43". */
    fun number(k: Double?): String = k?.let { String.format(Locale.GERMANY, "%.2f", it) } ?: "–"

    fun number1(k: Double?): String = k?.let { String.format(Locale.GERMANY, "%.1f", it) } ?: "–"

    /** Kurzform fürs Widget: "+1,2 kW" (Bezug) / "−800 W" (Einspeisung). */
    fun gridShort(w: Double?): String = when {
        w == null -> "–"
        w > 0 -> "+${power(w)}"
        w < 0 -> "−${power(-w)}"
        else -> "0 W"
    }

    fun percent(p: Double?): String = p?.let { String.format(Locale.GERMANY, "%.0f %%", it) } ?: "–"

    fun kwh(k: Double?): String = k?.let { String.format(Locale.GERMANY, "%.1f kWh", it) } ?: "–"

    fun time(epochMs: Long?): String =
        epochMs?.let { SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(it)) } ?: "--:--"
}
