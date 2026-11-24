package de.isolarwidget.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
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
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import de.isolarwidget.R
import de.isolarwidget.data.cache.PvCache
import de.isolarwidget.data.cache.PvSnapshot
import de.isolarwidget.ui.Format
import de.isolarwidget.ui.MainActivity
import de.isolarwidget.worker.SyncScheduler

class PvWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Liest ausschließlich den lokalen Cache – kein Netzwerk beim Rendern.
        val cache = PvCache(context)
        provideContent {
            val snap by cache.snapshot.collectAsState(initial = PvSnapshot.EMPTY)
            GlanceTheme { Content(snap) }
        }
    }

    @Composable
    private fun Content(s: PvSnapshot) {
        val compact = LocalSize.current.height < MEDIUM.height
        val outdated = s.isOutdated()
        val fg = GlanceTheme.colors.onSurface
        val label = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp)
        val value = TextStyle(color = fg, fontSize = if (compact) 14.sp else 16.sp, fontWeight = FontWeight.Bold)
        val v = s.values

        Column(
            modifier = GlanceModifier.fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(16.dp)
                .padding(10.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("PV ", style = label)
                Text(Format.power(v?.pvPowerW), style = value, modifier = GlanceModifier.defaultWeight())
                Image(
                    provider = ImageProvider(R.drawable.ic_refresh),
                    contentDescription = "Aktualisieren",
                    colorFilter = ColorFilter.tint(fg),
                    modifier = GlanceModifier.size(28.dp).padding(4.dp).clickable(actionRunCallback<RefreshAction>()),
                )
            }
            if (!compact) {
                Spacer(GlanceModifier.height(4.dp))
                if (v?.batterySocPct != null) Metric("Batterie", Format.percent(v.batterySocPct), label, value)
                Metric("Netz", Format.grid(v?.gridPowerW), label, value)
                Metric("Heute", Format.kwh(v?.dailyYieldKwh), label, value)
                Spacer(GlanceModifier.defaultWeight())
            }
            val stand = when {
                s.syncing -> "Aktualisiere…"
                else -> "Stand: ${Format.time(s.lastUpdatedEpochMs)}" + if (outdated) " · nicht aktuell" else ""
            }
            Text(
                stand,
                style = TextStyle(
                    color = if (outdated && !s.syncing) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp,
                ),
            )
        }
    }

    @Composable
    private fun Metric(name: String, text: String, label: TextStyle, value: TextStyle) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = label, modifier = GlanceModifier.defaultWeight())
            Text(text, style = value)
        }
    }

    companion object {
        private val SMALL = DpSize(110.dp, 40.dp)
        private val MEDIUM = DpSize(110.dp, 110.dp)
    }
}

/** Refresh-Button: markiert "syncing", rendert neu und startet einen einmaligen Sync. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        PvCache(context).setSyncing(true)
        PvWidget().updateAll(context)
        SyncScheduler.syncNow(context)
    }
}

class PvWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PvWidget()
}
