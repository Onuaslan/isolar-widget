package de.isolarwidget.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.coerceIn
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
import androidx.glance.appwidget.LocalAppWidgetOptions
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
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
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
private val BUTTON_BG = ColorProvider(Color(0xFFF1F2F4))

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
    override val breakpoints = setOf(DpSize(110.dp, 60.dp), DpSize(110.dp, SMALL_FULL))

    @Composable
    override fun Content(s: PvSnapshot) {
        val c = LocalSize.current.height < SMALL_FULL
        Card(c) {
            Header(s, c)
            Spacer(GlanceModifier.defaultWeight())
            ValueBlock("Tagesertrag", s.values?.dailyYieldKwh, if (c) 18.sp else 23.sp, c)
            Spacer(GlanceModifier.height(if (c) 4.dp else 10.dp))
            WithRefresh(s, c) {
                ValueBlock("Tagesverbrauch", s.values?.consumptionTodayKwh, if (c) 18.sp else 23.sp, c)
            }
        }
    }
}

/**
 * „Mein Zuhause“ 4×2: je ein Tagesverlauf (0–24 Uhr) für PV-Leistung (orange) und Hauslast (blau),
 * darüber aktueller Wert und Tagesspitze in W/kW, darunter Tagesertrag und Tagesverbrauch.
 * Die Kurven bekommen die gesamte freie Höhe, die Zahlen sind echter Text (nicht gestreckt).
 */
class WideWidget : CacheWidget() {
    override val breakpoints = setOf(DpSize(250.dp, 60.dp), DpSize(250.dp, WIDE_NO_CURVE), DpSize(250.dp, WIDE_FULL))

    @Composable
    override fun Content(s: PvSnapshot) {
        val h = LocalSize.current.height
        val c = h < WIDE_FULL
        val showCurve = h >= WIDE_NO_CURVE
        val real = realSize()
        val colW = (real.width - 36.dp - COL_GAP) / 2
        // Geschätzte Kurvenhöhe nur für die Bitmap-Auflösung; die echte Höhe ergibt sich aus dem Gewicht
        val curveH = if (c) 22.dp else (real.height - 150.dp).coerceIn(28.dp, 90.dp)
        val valueSize = if (c) 18.sp else 22.sp
        val curve = s.todayCurve()
        Card(c) {
            Header(s, c)
            if (showCurve) {
                Spacer(GlanceModifier.height(if (c) 4.dp else 10.dp))
                Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    CurveColumn(curve.pv, s.values?.pvPowerW, colW, curveH, WidgetGraphics.ORANGE, fill = true, labels = !c)
                    Spacer(GlanceModifier.width(COL_GAP))
                    CurveColumn(curve.load, s.values?.loadPowerW, colW, curveH, WidgetGraphics.BLUE, fill = false, labels = !c)
                }
                Spacer(GlanceModifier.height(if (c) 4.dp else 10.dp))
            } else {
                Spacer(GlanceModifier.defaultWeight())
            }
            WithRefresh(s, c) {
                Row(GlanceModifier.fillMaxWidth()) {
                    Box(GlanceModifier.defaultWeight()) { ValueBlock("Tagesertrag", s.values?.dailyYieldKwh, valueSize, c) }
                    Spacer(GlanceModifier.width(COL_GAP))
                    Box(GlanceModifier.defaultWeight()) { ValueBlock("Tagesverbrauch", s.values?.consumptionTodayKwh, valueSize, c) }
                }
            }
        }
    }
}

private val COL_GAP = 16.dp

/**
 * Echte Widgetgröße aus den Launcher-Optionen (LocalSize liefert nur die Stufe).
 * Hochformat: min. Breite × max. Höhe, Querformat: max. Breite × min. Höhe – so wie Android sie meldet.
 */
@Composable
private fun realSize(): DpSize {
    val o = LocalAppWidgetOptions.current
    val landscape = LocalContext.current.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val w = o.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
    val h = o.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
    val fallback = LocalSize.current
    return DpSize(
        if (w > 0) w.dp.coerceAtLeast(fallback.width) else fallback.width,
        if (h > 0) h.dp.coerceAtLeast(fallback.height) else fallback.height,
    )
}

// ---------------- Bausteine ----------------

/*
 * Höhenstufen. Glance liefert in LocalSize die gewählte Stufe (nicht die echte Größe), daher
 * entspricht jede Stufe der Mindesthöhe, die das jeweilige Layout ohne Abschneiden braucht
 * (Polster + Kopfzeile + Stand + Kurve + Beschriftung + Wert).
 * Darunter (z. B. im Querformat) wird kompakt dargestellt, ganz flach ohne Kurven.
 */
private val SMALL_FULL = 172.dp
private val WIDE_FULL = 162.dp
private val WIDE_NO_CURVE = 100.dp

private fun PvSnapshot.todayCurve(): DayCurve =
    if (curve.date == LocalDate.now().toString()) curve else DayCurve.EMPTY

@Composable
private fun Card(compact: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = GlanceModifier.fillMaxSize().background(BG).cornerRadius(22.dp)
            .padding(horizontal = 18.dp, vertical = if (compact) 10.dp else 16.dp)
            .clickable(actionRunCallback<OpenIsolarAction>()),
        content = content,
    )
}

/**
 * Titel links, Batterie rechts – wie Vorlage. Der Titel bekommt das Gewicht, damit die Batterie
 * nie abgeschnitten wird. Der Aktualisieren-Knopf sitzt rechts unten (siehe [WithRefresh]).
 */
@Composable
private fun Header(s: PvSnapshot, compact: Boolean) {
    val density = LocalContext.current.resources.displayMetrics.density
    // Uhrzeit der letzten Aktualisierung, immer sichtbar; orange, wenn veraltet
    val outdated = s.isOutdated() && !s.syncing
    val stand = when {
        s.syncing -> "Aktualisiere…"
        outdated -> "Stand ${Format.time(s.lastUpdatedEpochMs)} · nicht aktuell"
        else -> "Stand ${Format.time(s.lastUpdatedEpochMs)}"
    }
    val standStyle = TextStyle(color = if (outdated) WARN else LABEL, fontSize = 11.sp)
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Mein Zuhause", style = TextStyle(color = TITLE, fontSize = 13.sp), maxLines = 1,
            modifier = if (compact) GlanceModifier else GlanceModifier.defaultWeight(),
        )
        if (compact) {
            // Kompakt: Stand-Zeile in die Kopfzeile, spart eine Zeile Höhe
            Text("  $stand", style = standStyle, maxLines = 1, modifier = GlanceModifier.defaultWeight())
        }
        Spacer(GlanceModifier.width(6.dp))
        Image(
            provider = ImageProvider(WidgetGraphics.battery(s.values?.batterySocPct, (22 * density).toInt(), (12 * density).toInt())),
            contentDescription = "Batterie ${Format.percent(s.values?.batterySocPct)}, tippen zum Aktualisieren",
            modifier = GlanceModifier.size(22.dp, 12.dp).clickable(actionRunCallback<RefreshAction>()),
        )
    }
    if (!compact) Text(stand, style = standStyle, maxLines = 1)
}

/**
 * Legt den runden Aktualisieren-Knopf rechts unten über den Inhalt (wie beim LWP-Widget),
 * damit die Beschriftungen die volle Breite behalten und nicht abgeschnitten werden.
 * Während der Aktualisierung wird das Symbol heller.
 */
@Composable
private fun WithRefresh(s: PvSnapshot, compact: Boolean, content: @Composable () -> Unit) {
    val density = LocalContext.current.resources.displayMetrics.density
    val icon = if (compact) 14.dp else 17.dp
    val touch = if (compact) 26.dp else 32.dp
    Box(GlanceModifier.fillMaxWidth(), contentAlignment = Alignment.BottomEnd) {
        content()
        Box(
            modifier = GlanceModifier.size(touch).cornerRadius(touch / 2).background(BUTTON_BG)
                .clickable(actionRunCallback<RefreshAction>()),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(
                    WidgetGraphics.refresh(
                        (icon.value * density).toInt(),
                        if (s.syncing) 0xFFB8BDC3.toInt() else 0xFF3A4048.toInt(),
                    ),
                ),
                contentDescription = "Aktualisieren",
                modifier = GlanceModifier.size(icon),
            )
        }
    }
}

@Composable
private fun ValueBlock(label: String, kwh: Double?, size: androidx.compose.ui.unit.TextUnit, compact: Boolean = false) {
    Column {
        Text(label, style = TextStyle(color = LABEL, fontSize = if (compact) 11.sp else 13.sp), maxLines = 1)
        if (!compact) Spacer(GlanceModifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Format.number(kwh), style = TextStyle(color = VALUE, fontSize = size, fontWeight = FontWeight.Bold), maxLines = 1)
            Text(" kWh", style = TextStyle(color = VALUE, fontSize = if (compact) 11.sp else 13.sp), maxLines = 1, modifier = GlanceModifier.padding(bottom = if (compact) 2.dp else 3.dp))
        }
    }
}

/**
 * Eine Kurvenspalte: oben aktueller Wert (fett, Kurvenfarbe) und Tagesspitze (grau) als echter Text,
 * darunter der Tagesverlauf 0–24 Uhr, der die restliche Höhe füllt.
 */
@Composable
private fun RowScope.CurveColumn(
    values: List<Float?>, nowW: Double?, width: Dp, height: Dp, color: Int, fill: Boolean, labels: Boolean,
) {
    val density = LocalContext.current.resources.displayMetrics.density
    Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
        if (labels) {
            val now = nowW ?: values.lastOrNull { it != null }?.toDouble()
            val peak = values.filterNotNull().maxOrNull()
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(
                    now?.let { WidgetGraphics.watt(it.toFloat()) } ?: "–",
                    style = TextStyle(color = ColorProvider(Color(color)), fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.defaultWeight())
                if (peak != null && peak > 0f) {
                    Text("max ${WidgetGraphics.watt(peak)}", style = TextStyle(color = LABEL, fontSize = 11.sp), maxLines = 1)
                }
            }
            Spacer(GlanceModifier.height(4.dp))
        }
        val bmp = WidgetGraphics.sparkline(
            values, (width.value * density).toInt(), (height.value * density).toInt(), color, fill,
            fullDay = true, strokePx = 2f * density, endDot = labels,
        )
        Image(
            provider = ImageProvider(bmp),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
        )
    }
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
