package de.isolarwidget

import android.appwidget.AppWidgetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import org.robolectric.RuntimeEnvironment
import androidx.glance.ExperimentalGlanceApi
import de.isolarwidget.data.cache.DayCurve
import de.isolarwidget.data.cache.PvSnapshot
import de.isolarwidget.data.cache.PvValues
import de.isolarwidget.data.cache.SyncStatus
import de.isolarwidget.ui.widget.CacheWidget
import de.isolarwidget.ui.widget.SmallWidget
import de.isolarwidget.ui.widget.WideWidget
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** Rendert die kompletten Widgets mit Beispieldaten als PNG (build/previews/widget_*.png). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w400dp-h880dp-xhdpi")
@OptIn(ExperimentalGlanceApi::class)
class WidgetLayoutPreviewTest {

    private val out = File("build/previews").apply { mkdirs() }

    private val snap = PvSnapshot(
        values = PvValues(
            pvPowerW = 2140.0, batterySocPct = 62.0, gridPowerW = null, dailyYieldKwh = 5.43,
            loadPowerW = 1080.0, consumptionTodayKwh = 14.2,
        ),
        lastUpdatedEpochMs = System.currentTimeMillis(), lastAttemptEpochMs = null,
        status = SyncStatus.OK, message = null, syncing = false,
        curve = DayCurve(
            LocalDate.now().toString(),
            List(96) { i -> if (i <= 52) (if (i in 30..66) Math.sin((i - 30) / 36.0 * Math.PI) * 3000 else 0.0).toFloat() else null },
            List(96) { i -> if (i <= 52) (400 + 300 * Math.sin(i / 6.0) + if (i in 40..43) 5000 else 0).toFloat() else null },
        ),
    )

    private class Fixed(val w: CacheWidget, val s: PvSnapshot) : GlanceAppWidget() {
        override val sizeMode = SizeMode.Single
        override suspend fun provideGlance(context: android.content.Context, id: GlanceId) {
            provideContent { w.Content(s) }
        }
    }

    /** Fehler landen als Text neben den Bildern, damit man sie im Branch "previews" sieht. */
    private fun render(name: String, widget: CacheWidget, stage: DpSize, real: DpSize) {
        try {
            doRender(name, widget, stage, real)
        } catch (t: Throwable) {
            File(out, name.replace(".png", "_fehler.txt")).writeText(t.stackTraceToString())
            throw t
        }
    }

    private fun doRender(name: String, widget: CacheWidget, stage: DpSize, real: DpSize) = runBlocking {
        val ctx = RuntimeEnvironment.getApplication()
        val opts = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, real.width.value.toInt())
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, real.width.value.toInt())
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, real.height.value.toInt())
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, real.height.value.toInt())
        }
        val rv = Fixed(widget, snap).compose(ctx, options = opts, size = stage)
        val d = ctx.resources.displayMetrics.density
        val wPx = (real.width.value * d).toInt(); val hPx = (real.height.value * d).toInt()
        val parent = FrameLayout(ctx)
        val v = rv.apply(ctx, parent)
        parent.addView(v)
        parent.measure(View.MeasureSpec.makeMeasureSpec(wPx, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(hPx, View.MeasureSpec.EXACTLY))
        parent.layout(0, 0, wPx, hPx)
        val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(0xFF6B3A2A.toInt()); parent.draw(c)
        File(out, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun wideFull() = render("widget_wide.png", WideWidget(), DpSize(250.dp, 162.dp), DpSize(345.dp, 187.dp))
    @Test fun wideMin() = render("widget_wide_min.png", WideWidget(), DpSize(250.dp, 162.dp), DpSize(300.dp, 162.dp))
    @Test fun wideOneRow() = render("widget_wide_4x1.png", WideWidget(), DpSize(250.dp, 40.dp), DpSize(345.dp, 72.dp))
    @Test fun wideCompact() = render("widget_wide_compact.png", WideWidget(), DpSize(250.dp, 100.dp), DpSize(600.dp, 175.dp))
    @Test fun small() = render("widget_small.png", SmallWidget(), DpSize(110.dp, 172.dp), DpSize(170.dp, 187.dp))
}
