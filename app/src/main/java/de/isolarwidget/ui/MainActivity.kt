package de.isolarwidget.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.isolarwidget.data.api.Region
import de.isolarwidget.data.auth.OAuth
import de.isolarwidget.data.cache.PvSnapshot

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    val ui by vm.ui.collectAsStateWithLifecycle()
                    val snap by vm.snapshot.collectAsStateWithLifecycle()
                    Screen(ui, snap)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        when {
            data.scheme == "isolarwidget" && data.host == "setup" -> vm.applyPortalUrl(data.toString())
            data.toString().startsWith(OAuth.REDIRECT_URI) -> vm.handleRedirect(data)
        }
    }

    private fun openLogin() {
        val url = vm.loginUrl() ?: return
        CustomTabsIntent.Builder().build().launchUrl(this, Uri.parse(url))
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Screen(ui: UiState, snap: PvSnapshot) {
        var pasted by remember { mutableStateOf("") }
        var portalUrl by remember { mutableStateOf("") }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("iSolarCloud PV-Widget", style = MaterialTheme.typography.headlineSmall)
            if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            ui.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

            StatusCard(snap)

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1. Zugang (iSolarCloud Developer-Portal)", style = MaterialTheme.typography.titleMedium)
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(
                            value = ui.region.label, onValueChange = {}, readOnly = true, label = { Text("Region") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            Region.entries.forEach { r ->
                                DropdownMenuItem(text = { Text(r.label) }, onClick = {
                                    vm.edit { it.copy(region = r) }; expanded = false
                                })
                            }
                        }
                    }
                    Text(
                        "Region muss zum Server deiner Developer-App passen. Am einfachsten die Authorize-URL " +
                            "aus dem Developer-Portal unten einfügen, dann werden Region und ID übernommen.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(portalUrl, { portalUrl = it },
                        label = { Text("Einrichtungs-Link oder Authorize-URL einfügen") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (portalUrl.isNotBlank()) TextButton(onClick = { vm.applyPortalUrl(portalUrl); portalUrl = "" }) {
                        Text("Aus URL übernehmen")
                    }
                    OutlinedTextField(ui.appKey, { v -> vm.edit { it.copy(appKey = v) } },
                        label = { Text("Appkey") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(ui.secretKey, { v -> vm.edit { it.copy(secretKey = v) } },
                        label = { Text("Secret Key (x-access-key)") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(ui.appId, { v -> vm.edit { it.copy(appId = v) } },
                        label = { Text("Application-ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Redirect-URL im Portal: ${OAuth.REDIRECT_URI}", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = ::openLogin, enabled = !ui.busy) {
                            Text(if (ui.loggedIn) "Neu verbinden" else "Bei iSolarCloud anmelden")
                        }
                        if (ui.loggedIn) OutlinedButton(onClick = { vm.logout() }) { Text("Abmelden") }
                    }
                    // Fallback, falls der Browser nicht in die App zurückspringt.
                    OutlinedTextField(pasted, { pasted = it }, label = { Text("Oder Redirect-URL einfügen") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (pasted.isNotBlank()) TextButton(onClick = { vm.handleRedirect(Uri.parse(pasted.trim())); pasted = "" }) {
                        Text("Code übernehmen")
                    }
                }
            }

            if (ui.loggedIn) Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("2. Anlage", style = MaterialTheme.typography.titleMedium)
                    Text("Ausgewählt: ${ui.selectedPlantName ?: "keine"}")
                    OutlinedButton(onClick = { vm.loadPlants() }, enabled = !ui.busy) { Text("Anlagen laden") }
                    ui.plants.forEach { p ->
                        TextButton(onClick = { vm.selectPlant(p) }) { Text("${p.name} (${p.id})") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Netz-Vorzeichen umkehren", Modifier.weight(1f))
                        Switch(checked = ui.invertGridSign, onCheckedChange = vm::setInvertGrid)
                    }
                    OutlinedButton(onClick = { vm.runDiagnostics() }, enabled = !ui.busy) { Text("Messpunkte anzeigen (Diagnose)") }
                    ui.diagnostics?.let { d ->
                        val clipboard = LocalClipboardManager.current
                        TextButton(onClick = { clipboard.setText(AnnotatedString(d)) }) { Text("Diagnose kopieren") }
                        SelectionContainer { Text(d, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }

    @Composable
    private fun StatusCard(s: PvSnapshot) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Status: ${s.status}" + if (s.syncing) " (aktualisiere…)" else "",
                    style = MaterialTheme.typography.titleMedium)
                Text("Stand: ${Format.time(s.lastUpdatedEpochMs)}  ·  letzter Versuch: ${Format.time(s.lastAttemptEpochMs)}")
                s.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                s.values?.let { v ->
                    Text("PV: ${Format.power(v.pvPowerW)}")
                    if (v.batterySocPct != null) Text("Batterie: ${Format.percent(v.batterySocPct)}")
                    Text("Netz: ${Format.grid(v.gridPowerW)}")
                    Text("Heute: ${Format.kwh(v.dailyYieldKwh)}")
                }
                OutlinedButton(onClick = { vm.syncNow() }) { Text("Jetzt aktualisieren") }
            }
        }
    }
}
