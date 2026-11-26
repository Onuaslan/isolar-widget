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
    override val breakpoints = setOf(DpSize(110.dp, 60.dp), DpSize(110.dp, SMALL_FULL))

    @Composable
    override fun Content(s: PvSnapshot) {
        val c = LocalSize.current.height < SMALL_FULL
        Card(c) {
            Header(s, c)
            Spacer(GlanceModifier.defaultWeight())
            ValueBlock("Tagesertrag", s.values?.dailyYieldKwh, if (c) 18.sp else 23.sp, c)
            Spacer(GlanceModifier.height(if (c) 4.dp else 10.dp))
            BottomRow(s, c) {
                ValueBlock("Tagesverbrauch", s.values?.consumptionTodayKwh, if (c) 18.sp else 23.sp, c)
            }
        }
    }
}

/** „Mein Zuhause“ 4×2: je eine Mini-Kurve über Tagesertrag (orange) und Tagesverbrauch (blau). */
class WideWidget : CacheWidget() {
    override val breakpoints = setOf(DpSize(250.dp, 60.dp), DpSize(250.dp, WIDE_NO_CURVE), DpSize(250.dp, WIDE_FULL))

    @Composable
    override fun Content(s: PvSnapshot) {
        val h = LocalSize.current.height
        val c = h < WIDE_FULL
        val showCurve = h >= WIDE_NO_CURVE
        val colW = (realWidth() - 36.dp - 20.dp) / 2
        // Querformat (kompakt): Kurve nutzt die freie Höhe (bis 56 dp),
        // ab 28 dp mit Wattangaben
        val curveH = if (c) (realHeight() - 85.dp).coerceIn(18.dp, 56.dp) else 36.dp
        val labels = curveH >= 28.dp
        val gap = if (c) 4.dp else 10.dp
        val valueSize = if (c) 18.sp else 22.sp
        val curve = s.todayCurve()
        Card(c) {
            Header(s, c)
            Spacer(GlanceModifier.defaultWeight())
            Row(GlanceModifier.fillMaxWidth()) {
                Column(GlanceModifier.defaultWeight()) {
                    if (showCurve) {
                        Curve(curve.pv, colW, curveH, WidgetGraphics.ORANGE, fill = true, labels = labels)
                        Spacer(GlanceModifier.height(gap))
                    }
                    ValueBlock("Tagesertrag", s.values?.dailyYieldKwh, valueSize, c)
                }
                Spacer(GlanceModifier.width(20.dp))
                Column(GlanceModifier.defaultWeight()) {
                    if (showCurve) {
                        Curve(curve.load, colW, curveH, WidgetGraphics.BLUE, fill = false, labels = labels)
                        Spacer(GlanceModifier.height(gap))
                    }
                    // Knopf nur neben dem Wert, damit „Tagesverbrauch“ die volle Breite hat
                    val density = LocalContext.current.resources.displayMetrics.density
                    ValueBlock("Tagesverbrauch", s.values?.consumptionTodayKwh, valueSize, c) {
                        RefreshIcon(s, if (c) 16.dp else 18.dp, if (c) 24.dp else 28.dp, density)
                    }
                }
            }
        }
    }
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

/**
 * Echte Widgetbreite aus den Launcher-Optionen (LocalSize liefert nur die Stufe, 250 dp).
 * Damit wird die Kurve in der richtigen Breite gezeichnet und die Schrift nicht in die Breite gezogen.
 */
@Composable
private fun realWidth(): Dp {
    val o = LocalAppWidgetOptions.current
    val landscape = LocalContext.current.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val w = o.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
    val stage = LocalSize.current.width
    return if (w > 0) maxOf(w.dp, stage) else stage
}

/** Echte Widgethöhe (Hochformat: max. Höhe, Querformat: min. Höhe), sonst die Stufe. */
@Composable
private fun realHeight(): Dp {
    val o = LocalAppWidgetOptions.current
    val landscape = LocalContext.current.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val h = o.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
    val stage = LocalSize.current.height
    return if (h > 0) maxOf(h.dp, stage) else stage
}

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
 * nie abgeschnitten wird. Der Aktualisieren-Knopf sitzt rechts unten (siehe [BottomRow]).
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
 * Unterste Zeile: Inhalt links, dezenter Aktualisieren-Knopf rechts unten in der Ecke.
 * Die Tippfläche ist größer als das Symbol, damit man ihn gut trifft; während der
 * Aktualisierung wird das Symbol heller.
 */
@Composable
private fun BottomRow(s: PvSnapshot, compact: Boolean, content: @Composable () -> Unit) {
    val density = LocalContext.current.resources.displayMetrics.density
    val icon = if (compact) 16.dp else 18.dp
    val touch = if (compact) 24.dp else 28.dp
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Box(GlanceModifier.defaultWeight()) { content() }
        RefreshIcon(s, icon, touch, density)
    }
}

@Composable
private fun RefreshIcon(s: PvSnapshot, icon: Dp, touch: Dp, density: Float) {
        Box(
            modifier = GlanceModifier.size(touch).cornerRadius(touch / 2)
                .clickable(actionRunCallback<RefreshAction>()),
            // Mittig statt unten bündig: Ist das Widget minimal zu niedrig (z. B. aufgeklapptes
            // Foldable), schneidet die Karte die unterste Kante ab – dort liegt dann nur Leerraum.
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(
                    WidgetGraphics.refresh(
                        (icon.value * density).toInt(),
                        if (s.syncing) 0xFFC8CCD0.toInt() else 0xFF8E9399.toInt(),
                    ),
                ),
                contentDescription = "Aktualisieren",
                modifier = GlanceModifier.size(icon),
            )
        }
}

@Composable
private fun ValueBlock(
    label: String, kwh: Double?, size: androidx.compose.ui.unit.TextUnit, compact: Boolean = false,
    /** Optional rechts in der Wertzeile (Aktualisieren-Knopf); die Beschriftung darüber behält die volle Breite. */
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(if (trailing != null) GlanceModifier.fillMaxWidth() else GlanceModifier) {
        Text(label, style = TextStyle(color = LABEL, fontSize = if (compact) 11.sp else 13.sp), maxLines = 1)
        if (!compact) Spacer(GlanceModifier.height(2.dp))
        Row(if (trailing != null) GlanceModifier.fillMaxWidth() else GlanceModifier, verticalAlignment = Alignment.Bottom) {
            Text(Format.number(kwh), style = TextStyle(color = VALUE, fontSize = size, fontWeight = FontWeight.Bold), maxLines = 1)
            Text(" kWh", style = TextStyle(color = VALUE, fontSize = if (compact) 11.sp else 13.sp), maxLines = 1, modifier = GlanceModifier.padding(bottom = if (compact) 2.dp else 3.dp))
            if (trailing != null) {
                Spacer(GlanceModifier.defaultWeight())
                trailing()
            }
        }
    }
}

/**
 * Kurve im linken Teil, gestrichelte Grundlinie über die ganze Spalte – wie Vorlage.
 * Mit [labels] stehen Spitzenwert und aktueller Wert in W/kW an der Kurve (wie iSolarCloud-App).
 */
@Composable
private fun Curve(values: List<Float?>, width: Dp, height: Dp, color: Int, fill: Boolean, labels: Boolean = false) {
    val metrics = LocalContext.current.resources.displayMetrics
    val density = metrics.density
    val bmp = WidgetGraphics.sparkline(
        values, (width.value * density).toInt(), (height.value * density).toInt(), color, fill,
        strokePx = 2f * density, curveFraction = 0.55f,
        labelPx = if (labels) 7.5f * metrics.scaledDensity else 0f,
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
