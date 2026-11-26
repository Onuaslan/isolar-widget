package de.isolarwidget

import android.graphics.Bitmap
import de.isolarwidget.ui.widget.WidgetGraphics
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Rendert die Widget-Grafiken als PNG (build/previews), damit man sie ohne Handy prüfen kann. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class WidgetPreviewTest {

    private val out = File("build/previews").apply { mkdirs() }

    private fun save(name: String, b: Bitmap) {
        File(out, name).outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(File(out, name).length() > 0)
    }

    @Test
    fun sparklines() {
        val pv = List(96) { i -> if (i in 30..66) (Math.sin((i - 30) / 36.0 * Math.PI) * 3000).toFloat() else null }
        val load = List(96) { i -> if (i <= 60) (400 + 300 * Math.sin(i / 6.0) + if (i in 70..75) 1500 else 0).toFloat() else null }
        save("spark_pv.png", WidgetGraphics.sparkline(pv, 400, 120, WidgetGraphics.ORANGE, fill = true, strokePx = 6f))
        save("spark_load.png", WidgetGraphics.sparkline(load, 400, 120, WidgetGraphics.BLUE, fill = false, strokePx = 6f))
        save("battery.png", WidgetGraphics.battery(48.0, 66, 33))
        save("refresh.png", WidgetGraphics.refresh(96, 0xFF8E9399.toInt()))
        save("spark_pv_left.png", WidgetGraphics.sparkline(pv, 450, 100, WidgetGraphics.ORANGE, fill = true, strokePx = 5.5f, curveFraction = 0.55f))
        save("spark_load_left.png", WidgetGraphics.sparkline(load, 450, 100, WidgetGraphics.BLUE, fill = false, strokePx = 5.5f, curveFraction = 0.55f))
        // Tagesverlauf 0–24 Uhr mit Punkt am Ende (Stand ca. 13 Uhr), ~150 × 50 dp bei 2,75 px/dp
        val pvDay = List(96) { i -> if (i in 0..52) (if (i in 30..66) Math.sin((i - 30) / 36.0 * Math.PI) * 3000 else 0.0).toFloat() else null }
        val loadDay = List(96) { i -> if (i <= 52) (400 + 300 * Math.sin(i / 6.0) + if (i in 40..43) 5000 else 0).toFloat() else null }
        save("spark_pv_day.png", WidgetGraphics.sparkline(pvDay, 412, 137, WidgetGraphics.ORANGE, fill = true, fullDay = true, strokePx = 5.5f, endDot = true))
        save("spark_load_day.png", WidgetGraphics.sparkline(loadDay, 412, 137, WidgetGraphics.BLUE, fill = false, fullDay = true, strokePx = 5.5f, endDot = true))
    }
}
