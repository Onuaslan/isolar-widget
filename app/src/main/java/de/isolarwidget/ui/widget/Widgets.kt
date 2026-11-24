package de.isolarwidget.ui.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
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
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.ContentScale
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
import de.isolarwidget.data.cache.DayCurve
import de.isolarwidget.data.cache.PvCache
import de.isolarwidget.data.cache.PvSnapshot
import de.isolarwidget.ui.Format
import de.isolarwidget.ui.MainActivity
import de.isolarwidget.worker.SyncScheduler
import java.time.LocalDate

// Farben wie Vorlage: weiße Karte, dunkler Titel, graue Beschriftung, schwarze Werte
private val BG = ColorProvider(Color(0xFFFFFFFF))
private val TITLE = ColorProvider(Color(0xFF3A4048))
private val LABEL = ColorProvider(Color(0xFF7A828C))
private val VALUE = ColorProvider(Color(0xFF111418))
private val WARN = ColorProvider(Color(0xFFD35400))

/** Aktualisiert beide Widget-Typen (nach Sync, Refresh, Einstellungen). */
object Widgets {
    suspend fun updateAll(context: Context) {
        SmallWidget().updateAll(context)
        WideWidget().updateAll(context)
    }
}

/** Basis: liest ausschließlich den lokalen Cache – kein Netzwerk beim Rendern. */
abstract class CacheWidget : GlanceAppWidget() {
    /** Feste Layout-Stufen; manche Launcher melden bei SizeMode.Exact falsche Höhen. */
    abstract val breakpoints: Set<DpSize>
    override val sizeMode: SizeMode get() = SizeMode.Responsive(breakpoints)

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

/** „Mein Zuhause“ 2×2: Tagesertrag und Tagesverbrauch. */
class SmallWidget : CacheWidget() {
    override val breakpoints = setOf(DpSize(110.dp, 110.dp))

    @Composable
    override fun Content(s: PvSnapshot) {
        Card {
            Header(s)
            Spacer(GlanceModifier.defaultWeight())
            ValueBlock("Tagesertrag", s.values?.dailyYieldKwh, 23.sp)
            Spacer(GlanceModifier.height(10.dp))
            ValueBlock("Tagesverbrauch", s.values?.consumptionTodayKwh, 23.sp)
        }
    }
}

/** „Mein Zuhause“ 4×2: je eine Mini-Kurve über Tagesertrag (orange) und Tagesverbrauch (blau). */
class WideWidget : CacheWidget() {
    override val breakpoints = setOf(DpSize(250.dp, 110.dp))

    @Composable
    override fun Content(s: PvSnapshot) {
        val colW = (LocalSize.current.width - 36.dp - 20.dp) / 2
        val curve = s.todayCurve()
        Card {
            Header(s)
            Spacer(GlanceModifier.defaultWeight())
            Row(GlanceModifier.fillMaxWidth()) {
                Column(GlanceModifier.defaultWeight()) {
                    Curve(curve.pv, colW, 36.dp, WidgetGraphics.ORANGE, fill = true)
                    Spacer(GlanceModifier.height(10.dp))
                    ValueBlock("Tagesertrag", s.values?.dailyYieldKwh, 22.sp)
                }
                Spacer(GlanceModifier.width(20.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Curve(curve.load, colW, 36.dp, WidgetGraphics.BLUE, fill = false)
                    Spacer(GlanceModifier.height(10.dp))
                    ValueBlock("Tagesverbrauch", s.values?.consumptionTodayKwh, 22.sp)
                }
            }
        }
    }
}

// ---------------- Bausteine ----------------

private fun PvSnapshot.todayCurve(): DayCurve =
    if (curve.date == LocalDate.now().toString()) curve else DayCurve.EMPTY

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = GlanceModifier.fillMaxSize().background(BG).cornerRadius(22.dp)
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .clickable(actionRunCallback<OpenIsolarAction>()),
        content = content,
    )
}

/**
 * Titel links, Batterie rechts – wie Vorlage. Der Titel bekommt das Gewicht, damit die Batterie
 * nie abgeschnitten wird. Tipp auf die Batterie startet eine Aktualisierung.
 */
@Composable
private fun Header(s: PvSnapshot) {
    val density = LocalContext.current.resources.displayMetrics.density
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Mein Zuhause", style = TextStyle(color = TITLE, fontSize = 13.sp), maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        Spacer(GlanceModifier.width(6.dp))
        Image(
            provider = ImageProvider(WidgetGraphics.battery(s.values?.batterySocPct, (22 * density).toInt(), (12 * density).toInt())),
            contentDescription = "Batterie ${Format.percent(s.values?.batterySocPct)}, tippen zum Aktualisieren",
            modifier = GlanceModifier.size(22.dp, 12.dp).clickable(actionRunCallback<RefreshAction>()),
        )
    }
    // Uhrzeit der letzten Aktualisierung, immer sichtbar; orange, wenn veraltet
    val outdated = s.isOutdated() && !s.syncing
    Text(
        when {
            s.syncing -> "Aktualisiere…"
            outdated -> "Stand ${Format.time(s.lastUpdatedEpochMs)} · nicht aktuell"
            else -> "Stand ${Format.time(s.lastUpdatedEpochMs)}"
        },
        style = TextStyle(color = if (outdated) WARN else LABEL, fontSize = 11.sp), maxLines = 1,
    )
}

@Composable
private fun ValueBlock(label: String, kwh: Double?, size: androidx.compose.ui.unit.TextUnit) {
    Column {
        Text(label, style = TextStyle(color = LABEL, fontSize = 13.sp), maxLines = 1)
        Spacer(GlanceModifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Format.number(kwh), style = TextStyle(color = VALUE, fontSize = size, fontWeight = FontWeight.Bold), maxLines = 1)
            Text(" kWh", style = TextStyle(color = VALUE, fontSize = 13.sp), maxLines = 1, modifier = GlanceModifier.padding(bottom = 3.dp))
        }
    }
}

/** Kurve im linken Teil, gestrichelte Grundlinie über die ganze Spalte – wie Vorlage. */
@Composable
private fun Curve(values: List<Float?>, width: Dp, height: Dp, color: Int, fill: Boolean) {
    val density = LocalContext.current.resources.displayMetrics.density
    val bmp = WidgetGraphics.sparkline(
        values, (width.value * density).toInt(), (height.value * density).toInt(), color, fill,
        strokePx = 2f * density, curveFraction = 0.55f,
    )
    Image(
        provider = ImageProvider(bmp),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = GlanceModifier.fillMaxWidth().height(height),
    )
}

/**
 * Tipp aufs Widget: öffnet die Sungrow-App iSolarCloud. Gesucht wird unter den installierten Apps
 * nach dem Namen, weil die Paket-ID nicht offiziell dokumentiert ist. Fehlt sie, öffnet sich diese App.
 */
class OpenIsolarAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val target = pm.queryIntentActivities(launcher, 0).firstOrNull { info ->
            val pkg = info.activityInfo.packageName
            pkg != context.packageName && (
                info.loadLabel(pm).toString().contains("isolarcloud", ignoreCase = true) ||
                    pkg.contains("isolarcloud", ignoreCase = true))
        }
        val intent = target?.let { pm.getLaunchIntentForPackage(it.activityInfo.packageName) }
            ?: Intent(context, MainActivity::class.java)
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Startet einen einmaligen Sync und zeigt solange "…". */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        PvCache(context).setSyncing(true)
        Widgets.updateAll(context)
        SyncScheduler.syncNow(context)
    }
}

class SmallWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SmallWidget()
}

class WideWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WideWidget()
}
