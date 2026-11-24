package de.isolarwidget.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import de.isolarwidget.R
import de.isolarwidget.data.cache.DayCurve
import de.isolarwidget.data.cache.PvCache
import de.isolarwidget.data.cache.PvSnapshot
import de.isolarwidget.ui.Format
import de.isolarwidget.ui.MainActivity
import de.isolarwidget.worker.SyncScheduler
import java.time.LocalDate

// ---------------- Farben (helles Design wie Vorlage) ----------------

private val BG = ColorProvider(Color(0xFFFFFFFF))
private val BG_SOFT = ColorProvider(Color(0xFFEEF1F5))
private val TITLE = ColorProvider(Color(0xFF2C3E50))
private val LABEL = ColorProvider(Color(0xFF7B8794))
private val VALUE = ColorProvider(Color(0xFF111418))
private val WARN = ColorProvider(Color(0xFFD35400))

/** Aktualisiert alle Widget-Typen (nach Sync, Refresh, Einstellungen). */
object Widgets {
    suspend fun updateAll(context: Context) {
        SmallWidget().updateAll(context)
        WideWidget().updateAll(context)
        Widgets.updateAll(context)
    }
}

/** Basis: liest ausschließlich den lokalen Cache – kein Netzwerk beim Rendern. */
abstract class CacheWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val cache = PvCache(context)
        provideContent {
            val snap by cache.snapshot.collectAsState(initial = PvSnapshot.EMPTY)
            Content(snap)
        }
    }

    @Composable
    abstract fun Content(s: PvSnapshot)
}

// ---------------- 1) Klein: "Mein Zuhause" ----------------

class SmallWidget : CacheWidget() {
    @Composable
    override fun Content(s: PvSnapshot) {
        val size = LocalSize.current
        val tall = size.height >= 170.dp
        val curve = s.todayCurve()
        Card {
            Header(s, refresh = false)
            if (tall) {
                Spacer(GlanceModifier.height(8.dp))
                Curve(curve.pv, size.width - 28.dp, 40.dp, WidgetGraphics.ORANGE, fill = true)
                Spacer(GlanceModifier.height(4.dp))
                Curve(curve.load, size.width - 28.dp, 32.dp, WidgetGraphics.BLUE, fill = false)
            }
            Spacer(GlanceModifier.defaultWeight())
            ValueBlock("Tagesertrag", s.values?.dailyYieldKwh, 24.sp)
            Spacer(GlanceModifier.height(8.dp))
            ValueBlock("Tagesverbrauch", s.values?.consumptionTodayKwh, 24.sp)
        }
    }
}

// ---------------- 2) Breit: Mini-Kurven ----------------

class WideWidget : CacheWidget() {
    @Composable
    override fun Content(s: PvSnapshot) {
        val size = LocalSize.current
        val colW = (size.width - 28.dp - 16.dp) / 2
        val curve = s.todayCurve()
        Card {
            Header(s, refresh = false)
            Spacer(GlanceModifier.defaultWeight())
            Row(GlanceModifier.fillMaxWidth()) {
                Column(GlanceModifier.defaultWeight()) {
                    Curve(curve.pv, colW, 44.dp, WidgetGraphics.ORANGE, fill = true)
                    Spacer(GlanceModifier.height(6.dp))
                    ValueBlock("Tagesertrag", s.values?.dailyYieldKwh, 22.sp)
                }
                Spacer(GlanceModifier.width(16.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Curve(curve.load, colW, 44.dp, WidgetGraphics.BLUE, fill = false)
                    Spacer(GlanceModifier.height(6.dp))
                    ValueBlock("Tagesverbrauch", s.values?.consumptionTodayKwh, 22.sp)
                }
            }
        }
    }
}

// ---------------- 3) Groß: alle Werte + Grafik ----------------

/** Klassenname bleibt "PvWidget", damit bereits platzierte Widgets weiter funktionieren. */
class PvWidget : CacheWidget() {
    @Composable
    override fun Content(s: PvSnapshot) {
        val size = LocalSize.current
        val density = LocalContext.current.resources.displayMetrics.density
        val v = s.values
        val flowW = size.width - 20.dp
        val twoRows = size.height >= 280.dp
        // Header ~28dp, Kachelreihe ~46dp, Abstände/Padding ~28dp
        val flowH = (size.height - 28.dp - 28.dp - (if (twoRows) 98.dp else 46.dp)).coerceAtLeast(90.dp)
        val flow = EnergyFlowGraphic.render(
            EnergyFlowGraphic.Values(v?.pvPowerW, v?.loadPowerW, v?.batteryPowerW, v?.batterySocPct, v?.gridPowerW),
            (flowW.value * density).toInt(), (flowH.value * density).toInt(), density,
        )
        Column(
            modifier = GlanceModifier.fillMaxSize().background(BG_SOFT).cornerRadius(20.dp).padding(10.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Box(GlanceModifier.padding(horizontal = 4.dp)) { Header(s, refresh = true) }
            Image(
                provider = ImageProvider(flow),
                contentDescription = "Energiefluss",
                modifier = GlanceModifier.fillMaxWidth().height(flowH),
            )
            Spacer(GlanceModifier.defaultWeight())
            Row(GlanceModifier.fillMaxWidth()) {
                KwhTile("PV-Erzeugung", v?.dailyYieldKwh)
                Spacer(GlanceModifier.width(6.dp))
                KwhTile("Hausverbrauch", v?.consumptionTodayKwh)
                Spacer(GlanceModifier.width(6.dp))
                KwhTile("Netzbezug", v?.gridBuyTodayKwh)
            }
            if (twoRows) Spacer(GlanceModifier.height(6.dp))
            if (twoRows) Row(GlanceModifier.fillMaxWidth()) {
                KwhTile("Batt. geladen", v?.batteryChargeTodayKwh)
                Spacer(GlanceModifier.width(6.dp))
                KwhTile("Batt. entladen", v?.batteryDischargeTodayKwh)
                Spacer(GlanceModifier.width(6.dp))
                KwhTile("Eingespeist", v?.feedInTodayKwh)
            }
        }
    }
}

@Composable
private fun androidx.glance.layout.RowScope.KwhTile(label: String, kwh: Double?) {
    Column(GlanceModifier.defaultWeight().background(BG).cornerRadius(12.dp).padding(horizontal = 8.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Format.number1(kwh), style = TextStyle(color = VALUE, fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Text(" kWh", style = TextStyle(color = VALUE, fontSize = 10.sp), maxLines = 1, modifier = GlanceModifier.padding(bottom = 2.dp))
        }
        Text(label, style = TextStyle(color = LABEL, fontSize = 11.sp), maxLines = 1)
    }
}

// ---------------- Gemeinsame Bausteine ----------------

private fun PvSnapshot.todayCurve(): DayCurve =
    if (curve.date == LocalDate.now().toString()) curve else DayCurve.EMPTY

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = GlanceModifier.fillMaxSize().background(BG).cornerRadius(20.dp).padding(14.dp)
            .clickable(actionStartActivity<MainActivity>()),
        content = content,
    )
}

@Composable
private fun Header(s: PvSnapshot, refresh: Boolean) {
    val density = LocalContext.current.resources.displayMetrics.density
    val outdated = s.isOutdated() && !s.syncing
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Mein Zuhause", style = TextStyle(color = TITLE, fontSize = 13.sp, fontWeight = FontWeight.Medium))
        Spacer(GlanceModifier.width(6.dp))
        Text(
            when {
                s.syncing -> "…"
                else -> Format.time(s.lastUpdatedEpochMs) + if (outdated) " !" else ""
            },
            style = TextStyle(color = if (outdated) WARN else LABEL, fontSize = 11.sp),
            modifier = GlanceModifier.defaultWeight(),
        )
        if (refresh) {
            Image(
                provider = ImageProvider(R.drawable.ic_refresh),
                contentDescription = "Aktualisieren",
                colorFilter = ColorFilter.tint(LABEL),
                modifier = GlanceModifier.size(26.dp).padding(3.dp).clickable(actionRunCallback<RefreshAction>()),
            )
            Spacer(GlanceModifier.width(6.dp))
        }
        Image(
            provider = ImageProvider(WidgetGraphics.battery(s.values?.batterySocPct, (24 * density).toInt(), (12 * density).toInt())),
            contentDescription = "Batterie ${Format.percent(s.values?.batterySocPct)}",
            modifier = GlanceModifier.size(24.dp, 12.dp),
        )
    }
}

@Composable
private fun ValueBlock(label: String, kwh: Double?, size: TextUnit) {
    Column {
        Text(label, style = TextStyle(color = LABEL, fontSize = 14.sp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Format.number(kwh), style = TextStyle(color = VALUE, fontSize = size, fontWeight = FontWeight.Bold))
            Text(" kWh", style = TextStyle(color = VALUE, fontSize = 13.sp), modifier = GlanceModifier.padding(bottom = 3.dp))
        }
    }
}

@Composable
private fun Curve(values: List<Float?>, width: Dp, height: Dp, color: Int, fill: Boolean, fullDay: Boolean = false) {
    val density = LocalContext.current.resources.displayMetrics.density
    val bmp = WidgetGraphics.sparkline(
        values, (width.value * density).toInt(), (height.value * density).toInt(), color, fill, fullDay,
        strokePx = 2.2f * density,
    )
    Image(
        provider = ImageProvider(bmp),
        contentDescription = null,
        modifier = GlanceModifier.fillMaxWidth().height(height),
    )
}

// ---------------- Aktionen & Receiver ----------------

/** Refresh-Button: markiert "syncing", rendert neu und startet einen einmaligen Sync. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        PvCache(context).setSyncing(true)
        Widgets.updateAll(context)
        SyncScheduler.syncNow(context)
    }
}

class PvWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PvWidget()
}

class SmallWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SmallWidget()
}

class WideWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WideWidget()
}

/** Gleiche Widgets in weiteren Startgrößen, damit der Launcher sie getrennt anbietet. */
class SmallTallWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SmallWidget()
}

class PvMediumWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PvWidget()
}
