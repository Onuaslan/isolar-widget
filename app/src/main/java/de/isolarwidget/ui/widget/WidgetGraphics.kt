package de.isolarwidget.ui.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils

/** Zeichnet Grafiken als Bitmap, weil Glance-Widgets keine Canvas-Zeichnung unterstützen. */
object WidgetGraphics {

    const val ORANGE = 0xFFF39C12.toInt()
    const val BLUE = 0xFF3D7BF7.toInt()
    private const val DASH = 0xFFD5D8DC.toInt()

    /** Kreispfeil zum Aktualisieren (ca. 300° Bogen mit Pfeilspitze). */
    fun refresh(sizePx: Int, color: Int): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val stroke = sizePx * 0.11f
        val r = sizePx / 2f - stroke * 1.6f
        val cx = sizePx / 2f
        val cy = sizePx / 2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND
        }
        // Bogen von 300° gegen 0° (rechts oben offen), im Uhrzeigersinn
        c.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), -60f, 300f, false, p)
        // Pfeilspitze am Bogenende (240°), zeigt in Laufrichtung (im Uhrzeigersinn)
        val t = Math.toRadians(240.0)
        val ex = cx + r * Math.cos(t).toFloat()
        val ey = cy + r * Math.sin(t).toFloat()
        val dx = -Math.sin(t).toFloat()   // Tangente im Uhrzeigersinn (y nach unten)
        val dy = Math.cos(t).toFloat()
        val nx = Math.cos(t).toFloat()    // Normale (nach außen)
        val ny = Math.sin(t).toFloat()
        val h = stroke * 2.2f
        val head = Path().apply {
            moveTo(ex + dx * h, ey + dy * h)
            lineTo(ex + nx * h * 0.85f, ey + ny * h * 0.85f)
            lineTo(ex - nx * h * 0.85f, ey - ny * h * 0.85f)
            close()
        }
        c.drawPath(head, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL })
        return bmp
    }

    /**
     * Glatte Kurve wie in der Vorlage. [fullDay] = x-Achse über 0–24 Uhr, sonst nur über den Bereich mit Daten.
     * Gibt bei fehlenden Daten nur die gestrichelte Grundlinie zurück.
     */
    fun sparkline(
        values: List<Float?>, widthPx: Int, heightPx: Int, color: Int,
        fill: Boolean, fullDay: Boolean = false, strokePx: Float = 4f,
        /** Anteil der Breite, den die Kurve nutzt (Rest: nur gestrichelte Linie). */
        curveFraction: Float = 1f,
        /** Punkt am Kurvenende (aktueller Wert). */
        endDot: Boolean = false,
    ): Bitmap {
        val w = widthPx.coerceAtLeast(10); val h = heightPx.coerceAtLeast(10)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val baseY = h - strokePx

        val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; this.color = DASH; strokeWidth = strokePx / 2
            pathEffect = DashPathEffect(floatArrayOf(strokePx * 2, strokePx * 2), 0f)
        }
        c.drawLine(0f, baseY, w.toFloat(), baseY, dash)
        if (fullDay) {
            // Feine Markierungen bei 6, 12 und 18 Uhr als Orientierung
            val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = DASH; strokeWidth = strokePx / 2 }
            for (hour in intArrayOf(6, 12, 18)) {
                val x = hour / 24f * (w - strokePx * 2) + strokePx
                c.drawLine(x, baseY - strokePx * 1.5f, x, baseY, tick)
            }
        }

        val idx = values.indices.filter { values[it] != null }
        if (idx.size < 2) return bmp
        val first = if (fullDay) 0 else idx.first()
        val last = if (fullDay) values.size - 1 else idx.last()
        val max = idx.maxOf { values[it]!! }.coerceAtLeast(1f)
        val span = (last - first).coerceAtLeast(1)
        val top = if (endDot) strokePx * 2.5f else strokePx * 2
        val pts = idx.map { i ->
            val x = (i - first).toFloat() / span * (w * curveFraction - strokePx * 2) + strokePx
            val y = baseY - (values[i]!!.coerceAtLeast(0f) / max) * (baseY - top)
            x to y
        }

        val path = Path().apply {
            moveTo(pts[0].first, pts[0].second)
            for (k in 1 until pts.size) {
                val (x0, y0) = pts[k - 1]; val (x1, y1) = pts[k]
                val mx = (x0 + x1) / 2
                cubicTo(mx, y0, mx, y1, x1, y1)
            }
        }
        if (fill) {
            val area = Path(path).apply {
                lineTo(pts.last().first, baseY); lineTo(pts.first().first, baseY); close()
            }
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, top, 0f, baseY,
                    ColorUtils.setAlphaComponent(color, 110), ColorUtils.setAlphaComponent(color, 10), Shader.TileMode.CLAMP)
            }
            c.drawPath(area, fillPaint)
        }
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; this.color = color; strokeWidth = strokePx
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        })
        if (endDot) {
            val (ex, ey) = pts.last()
            c.drawCircle(ex, ey, strokePx * 1.6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
            c.drawCircle(ex, ey, strokePx * 0.7f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFFFFFFF.toInt() })
        }
        return bmp
    }

    /** "850 W" bzw. "3,2 kW" – kurz genug fürs Widget. */
    fun watt(w: Float): String = when {
        w >= 1000f -> String.format(java.util.Locale.GERMANY, "%.1f kW", w / 1000f)
        else -> String.format(java.util.Locale.GERMANY, "%.0f W", w.coerceAtLeast(0f))
    }

    /** Batterie-Symbol mit Füllstand: grün ≥ 30 %, orange ≥ 15 %, sonst rot; grau ohne Wert. */
    fun battery(socPct: Double?, widthPx: Int, heightPx: Int, vertical: Boolean = false): Bitmap {
        if (vertical) {
            // Horizontal zeichnen und um 90° drehen (Pol nach oben)
            val horiz = battery(socPct, heightPx, widthPx, vertical = false)
            val m = android.graphics.Matrix().apply { postRotate(-90f) }
            return Bitmap.createBitmap(horiz, 0, 0, horiz.width, horiz.height, m, true)
        }
        val w = widthPx.coerceAtLeast(12); val h = heightPx.coerceAtLeast(6)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val stroke = h * 0.12f
        val capW = w * 0.08f
        val body = RectF(stroke / 2, stroke / 2, w - capW - stroke / 2, h - stroke / 2)
        val r = h * 0.22f
        val color = when {
            socPct == null -> 0xFFB0B4B9.toInt()
            socPct >= 30 -> 0xFF2ECC71.toInt()
            socPct >= 15 -> ORANGE
            else -> 0xFFE74C3C.toInt()
        }
        c.drawRoundRect(body, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = stroke; this.color = color
        })
        val pad = stroke * 1.6f
        val frac = ((socPct ?: 0.0) / 100.0).coerceIn(0.0, 1.0).toFloat()
        val inner = RectF(body.left + pad, body.top + pad, body.left + pad + (body.width() - 2 * pad) * frac, body.bottom - pad)
        if (frac > 0f) c.drawRoundRect(inner, r / 2, r / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        c.drawRoundRect(RectF(w - capW, h * 0.32f, w.toFloat(), h * 0.68f), capW / 2, capW / 2,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        return bmp
    }
}

