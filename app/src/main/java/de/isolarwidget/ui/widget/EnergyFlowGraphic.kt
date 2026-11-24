package de.isolarwidget.ui.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.util.Locale
import kotlin.math.abs

/**
 * Eigene, schlichte Energiefluss-Grafik: Haus mit Solardach, Batterie und Strommast,
 * oben Solar/Haus, unten Batterie/Netz mit Werten und Verbindungslinien.
 * Linien sind farbig, wenn gerade Energie fließt.
 */
object EnergyFlowGraphic {

    data class Values(
        val pvW: Double?, val loadW: Double?, val batteryW: Double?, val socPct: Double?, val gridW: Double?,
    )

    private const val TEXT = 0xFF15191E.toInt()
    private const val CAPTION = 0xFF7B8794.toInt()
    private const val LINE_IDLE = 0xFFC9D0D8.toInt()
    private const val WALL = 0xFFFFFFFF.toInt()
    private const val WALL_SHADE = 0xFFE3E7EC.toInt()
    private const val OUTLINE = 0xFFB8C1CB.toInt()
    private const val ROOF = 0xFFD5DBE2.toInt()
    private const val PANEL = 0xFF2E3D52.toInt()
    private const val PANEL_LINE = 0xFF50627A.toInt()
    private const val GREEN = 0xFF2ECC71.toInt()
    private const val ORANGE = 0xFFF39C12.toInt()
    private const val BLUE = 0xFF3D7BF7.toInt()

    fun render(v: Values, w: Int, h: Int, density: Float): Bitmap {
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(50), h.coerceAtLeast(50), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val d = density
        val W = bmp.width.toFloat(); val H = bmp.height.toFloat()

        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.2f * d; color = OUTLINE }

        // Bereiche: oben Labels (0..22 %), Szene (22..78 %), unten Labels (78..100 %)
        val sceneTop = H * 0.24f
        val ground = H * 0.74f

        // Boden-Schatten
        fill.shader = LinearGradient(0f, ground - 6 * d, 0f, ground + 10 * d, 0x22000000, 0x00000000, Shader.TileMode.CLAMP)
        c.drawOval(RectF(W * 0.12f, ground - 6 * d, W * 0.88f, ground + 10 * d), fill)
        fill.shader = null

        // ---- Haus ----
        val hx0 = W * 0.30f; val hx1 = W * 0.70f
        val wallTop = sceneTop + (ground - sceneTop) * 0.45f
        val roofPeak = sceneTop + (ground - sceneTop) * 0.05f
        val body = RectF(hx0, wallTop, hx1, ground)
        fill.shader = LinearGradient(0f, wallTop, 0f, ground, WALL, WALL_SHADE, Shader.TileMode.CLAMP)
        c.drawRoundRect(body, 3 * d, 3 * d, fill); fill.shader = null
        c.drawRoundRect(body, 3 * d, 3 * d, stroke)

        val roof = Path().apply {
            moveTo(hx0 - 10 * d, wallTop + 2 * d); lineTo((hx0 + hx1) / 2, roofPeak)
            lineTo(hx1 + 10 * d, wallTop + 2 * d); close()
        }
        fill.color = ROOF; c.drawPath(roof, fill); c.drawPath(roof, stroke)

        // Solarmodule auf der linken Dachhälfte (Seite des Solar-Labels), parallel zur Dachkante
        val midX = (hx0 + hx1) / 2
        val eaveX = hx0 - 10 * d; val eaveY = wallTop + 2 * d
        fun roofPt(t: Float) = (midX + (eaveX - midX) * t) to (roofPeak + (eaveY - roofPeak) * t)
        // Modulfeld: Parallelogramm mit Kanten parallel zur linken Dachschräge, waagrecht oben/unten
        val s1 = 9 * d; val s2 = s1 + (midX - hx0) * 0.45f
        val t0 = 0.30f; val t1 = 0.86f
        fun lp(t: Float, shift: Float) = roofPt(t).let { (x, y) -> (x + shift) to y }
        val panel = Path().apply {
            lp(t0, s1).let { moveTo(it.first, it.second) }; lp(t1, s1).let { lineTo(it.first, it.second) }
            lp(t1, s2).let { lineTo(it.first, it.second) }; lp(t0, s2).let { lineTo(it.first, it.second) }; close()
        }
        val panelCenter = lp((t0 + t1) / 2, (s1 + s2) / 2)
        fill.color = PANEL; c.drawPath(panel, fill)
        stroke.color = PANEL_LINE; c.drawPath(panel, stroke)
        for (k in 1..2) {   // Modulfugen
            val sh = s1 + (s2 - s1) * k / 3
            val a = lp(t0, sh); val b = lp(t1, sh)
            c.drawLine(a.first, a.second, b.first, b.second, stroke)
        }
        val tm = (t0 + t1) / 2
        lp(tm, s1).let { a -> lp(tm, s2).let { b -> c.drawLine(a.first, a.second, b.first, b.second, stroke) } }
        stroke.color = OUTLINE

        // Tür und Fenster
        fill.color = WALL_SHADE
        val door = RectF(midX + (hx1 - midX) * 0.35f, ground - (ground - wallTop) * 0.62f, midX + (hx1 - midX) * 0.75f, ground)
        c.drawRoundRect(door, 2 * d, 2 * d, fill); c.drawRoundRect(door, 2 * d, 2 * d, stroke)
        val win = RectF(hx0 + (midX - hx0) * 0.25f, wallTop + (ground - wallTop) * 0.22f, hx0 + (midX - hx0) * 0.8f, wallTop + (ground - wallTop) * 0.55f)
        fill.color = 0xFFDCE8F5.toInt(); c.drawRoundRect(win, 2 * d, 2 * d, fill); c.drawRoundRect(win, 2 * d, 2 * d, stroke)

        // Wechselrichter an der Wand
        val inv = RectF(midX - 9 * d, wallTop + (ground - wallTop) * 0.2f, midX + 2 * d, wallTop + (ground - wallTop) * 0.2f + 13 * d)
        fill.color = WALL; c.drawRoundRect(inv, 2 * d, 2 * d, fill); c.drawRoundRect(inv, 2 * d, 2 * d, stroke)
        fill.color = BLUE; c.drawCircle(inv.centerX(), inv.top + 4 * d, 1.5f * d, fill)

        // ---- Batterie (links vom Haus) ----
        val bw = 18 * d; val bh = 28 * d
        val bat = RectF(hx0 - bw - 10 * d, ground - bh, hx0 - 10 * d, ground)
        fill.color = WALL; c.drawRoundRect(bat, 3 * d, 3 * d, fill); c.drawRoundRect(bat, 3 * d, 3 * d, stroke)
        for (k in 1..2) c.drawLine(bat.left, bat.top + bh * k / 3, bat.right, bat.top + bh * k / 3, stroke)
        val soc = ((v.socPct ?: 0.0) / 100).coerceIn(0.0, 1.0).toFloat()
        fill.color = socColor(v.socPct)
        val lvl = RectF(bat.left + 3 * d, bat.bottom - 3 * d - (bh - 6 * d) * soc, bat.right - 3 * d, bat.bottom - 3 * d)
        fill.alpha = 170; c.drawRoundRect(lvl, 1.5f * d, 1.5f * d, fill); fill.alpha = 255

        // ---- Strommast (rechts) ----
        val mx = W * 0.86f; val mTop = sceneTop + (ground - sceneTop) * 0.15f
        val pole = Paint(stroke).apply { strokeWidth = 1.6f * d; color = 0xFF9AA5B1.toInt() }
        c.drawLine(mx - 9 * d, ground, mx, mTop, pole); c.drawLine(mx + 9 * d, ground, mx, mTop, pole)
        c.drawLine(mx - 12 * d, mTop + 8 * d, mx + 12 * d, mTop + 8 * d, pole)
        c.drawLine(mx - 9 * d, mTop + 18 * d, mx + 9 * d, mTop + 18 * d, pole)
        c.drawLine(mx - 6 * d, mTop + 30 * d, mx + 6 * d, ground - 10 * d, pole)
        c.drawLine(mx + 6 * d, mTop + 30 * d, mx - 6 * d, ground - 10 * d, pole)

        // ---- Leitungen + Labels ----
        val lineW = 1.4f * d
        fun leader(x: Float, y0: Float, y1: Float, active: Int?) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = if (active != null) lineW * 1.6f else lineW
                color = active ?: LINE_IDLE
            }
            c.drawLine(x, y0, x, y1, p)
            if (active != null) { fill.color = active; c.drawCircle(x, y1, 2.5f * d, fill) }
        }

        val pvActive = (v.pvW ?: 0.0) > 30
        val gridColor = when { (v.gridW ?: 0.0) > 30 -> BLUE; (v.gridW ?: 0.0) < -30 -> GREEN; else -> null }
        val battColor = when { (v.batteryW ?: 0.0) > 30 -> GREEN; (v.batteryW ?: 0.0) < -30 -> ORANGE; else -> null }
        val solarX = W * 0.14f; val houseX = W * 0.62f
        val battX = W * 0.14f; val netzX = W * 0.66f

        val topLabelBottom = H * 0.21f
        leader(solarX, topLabelBottom, panelCenter.second, if (pvActive) ORANGE else null)
        c.drawLine(solarX, panelCenter.second, panelCenter.first, panelCenter.second,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = lineW; color = if (pvActive) ORANGE else LINE_IDLE })
        if (pvActive) { fill.color = ORANGE; c.drawCircle(panelCenter.first, panelCenter.second, 2.5f * d, fill) }
        leader(houseX + 0f, topLabelBottom, wallTop + 6 * d, if ((v.loadW ?: 0.0) > 30) BLUE else null)

        val bottomLabelTop = H * 0.80f
        leader(battX, bottomLabelTop, bat.centerY(), battColor)
        c.drawLine(battX, bat.centerY(), bat.left, bat.centerY(), Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = lineW; color = battColor ?: LINE_IDLE })
        leader(netzX, bottomLabelTop, ground - 4 * d, gridColor)
        c.drawLine(netzX, ground - 4 * d, mx - 6 * d, ground - 4 * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = lineW; color = gridColor ?: LINE_IDLE })

        // Texte
        val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEXT; textSize = 20 * d; typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL) }
        val unit = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEXT; textSize = 11 * d }
        val cap = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CAPTION; textSize = 11.5f * d }

        fun label(x: Float, yBase: Float, value: Pair<String, String>, caption: String, extra: Pair<String, String>? = null, socIcon: Boolean = false) {
            val tx = x + 6 * d
            var cx = tx
            c.drawText(value.first, cx, yBase, big); cx += big.measureText(value.first) + 2 * d
            c.drawText(value.second, cx, yBase, unit); cx += unit.measureText(value.second)
            if (extra != null) {
                cx += 5 * d
                c.drawLine(cx, yBase - 14 * d, cx, yBase, Paint(cap).apply { strokeWidth = 1f * d })
                cx += 5 * d
                c.drawText(extra.first, cx, yBase, big); cx += big.measureText(extra.first) + 2 * d
                c.drawText(extra.second, cx, yBase, unit); cx += unit.measureText(extra.second) + 4 * d
            }
            if (socIcon) {
                val icon = WidgetGraphics.battery(v.socPct, (9 * d).toInt(), (16 * d).toInt(), vertical = true)
                c.drawBitmap(icon, cx, yBase - 15 * d, null)
            }
            c.drawText(caption, tx, yBase + 16 * d, cap)
        }

        label(solarX, H * 0.09f + 10 * d, powerParts(v.pvW), "Solar")
        label(houseX, H * 0.09f + 10 * d, powerParts(v.loadW), "Haus")
        val battCaption = when { battColor == GREEN -> "Batterie · lädt"; battColor == ORANGE -> "Batterie · entlädt"; else -> "Batterie" }
        label(battX, H * 0.86f + 4 * d, powerParts(v.batteryW?.let { abs(it) }), battCaption,
            extra = (v.socPct?.let { String.format(Locale.GERMANY, "%.0f", it) } ?: "–") to "%", socIcon = true)
        val netzCaption = when { gridColor == BLUE -> "Netz · Bezug"; gridColor == GREEN -> "Netz · Einspeisung"; else -> "Netz" }
        label(netzX, H * 0.86f + 4 * d, powerParts(v.gridW?.let { abs(it) }), netzCaption)
        return bmp
    }

    private fun socColor(soc: Double?) = when {
        soc == null -> LINE_IDLE; soc >= 30 -> GREEN; soc >= 15 -> ORANGE; else -> 0xFFE74C3C.toInt()
    }

    /** "1,3" + "kW" bzw. "800" + "W"; "–" ohne Wert. */
    fun powerParts(w: Double?): Pair<String, String> = when {
        w == null -> "–" to ""
        abs(w) >= 1000 -> String.format(Locale.GERMANY, "%.1f", w / 1000) to "kW"
        else -> String.format(Locale.GERMANY, "%.0f", w) to "W"
    }
}
