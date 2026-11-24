package de.isolarwidget

import android.graphics.Bitmap
import de.isolarwidget.ui.widget.EnergyFlowGraphic
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
    fun energyFlow() {
        val d = 2.75f
        save("energyflow_charging.png", EnergyFlowGraphic.render(
            EnergyFlowGraphic.Values(pvW = 2600.0, loadW = 1300.0, batteryW = 1300.0, socPct = 48.0, gridW = 0.0),
            (330 * d).toInt(), (210 * d).toInt(), d))
        save("energyflow_import.png", EnergyFlowGraphic.render(
            EnergyFlowGraphic.Values(pvW = 0.0, loadW = 850.0, batteryW = -400.0, socPct = 12.0, gridW = 450.0),
            (330 * d).toInt(), (210 * d).toInt(), d))
    }

    @Test
    fun sparklines() {
        val pv = List(96) { i -> if (i in 30..66) (Math.sin((i - 30) / 36.0 * Math.PI) * 3000).toFloat() else null }
        val load = List(96) { i -> if (i <= 60) (400 + 300 * Math.sin(i / 6.0) + if (i in 70..75) 1500 else 0).toFloat() else null }
        save("spark_pv.png", WidgetGraphics.sparkline(pv, 400, 120, WidgetGraphics.ORANGE, fill = true, strokePx = 6f))
        save("spark_load.png", WidgetGraphics.sparkline(load, 400, 120, WidgetGraphics.BLUE, fill = false, strokePx = 6f))
        save("battery.png", WidgetGraphics.battery(48.0, 66, 33))
        save("daychart.png", de.isolarwidget.ui.widget.DayChart.render(pv, load, (310 * 2.75f).toInt(), (130 * 2.75f).toInt(), 2.75f))
    }
}
