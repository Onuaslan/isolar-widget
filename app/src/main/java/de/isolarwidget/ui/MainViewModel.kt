package de.isolarwidget.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.isolarwidget.data.Plant
import de.isolarwidget.data.PvRepository
import de.isolarwidget.data.api.Region
import de.isolarwidget.data.auth.AppCredentials
import de.isolarwidget.data.cache.PvSnapshot
import de.isolarwidget.ui.widget.PvWidget
import de.isolarwidget.worker.SyncScheduler
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val region: Region,
    val appKey: String,
    val secretKey: String,
    val appId: String,
    val loggedIn: Boolean,
    val plants: List<Plant> = emptyList(),
    val selectedPlantName: String?,
    val invertGridSign: Boolean,
    val busy: Boolean = false,
    val message: String? = null,
    val diagnostics: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = PvRepository.get(app)

    private val _ui = MutableStateFlow(initialState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    val snapshot: StateFlow<PvSnapshot> =
        repo.cache.snapshot.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PvSnapshot.EMPTY)

    private fun initialState(): UiState {
        val c = repo.store.credentials
        return UiState(c.region, c.appKey, c.secretKey, c.appId, repo.isLoggedIn, emptyList(),
            repo.store.plantName, repo.store.invertGridSign)
    }

    fun edit(transform: (UiState) -> UiState) = _ui.update(transform)

    fun applyPortalUrl(url: String) {
        de.isolarwidget.data.auth.OAuth.parseSetupLink(url)?.let { setup ->
            _ui.update {
                it.copy(region = setup.region, appKey = setup.appKey, secretKey = setup.secretKey, appId = setup.appId,
                    message = "Zugangsdaten übernommen (${setup.region.label}, ID ${setup.appId}). Jetzt anmelden.")
            }
            return
        }
        val info = de.isolarwidget.data.auth.OAuth.parsePortalUrl(url)
        _ui.update {
            if (info == null) it.copy(message = "Authorize-URL nicht erkannt")
            else it.copy(region = info.region, appId = info.appId,
                message = "Übernommen: Region ${info.region.label}, Application-ID ${info.appId}")
        }
    }

    /** @return Autorisierungs-URL zum Öffnen im Browser, oder null wenn Angaben fehlen. */
    fun loginUrl(): String? {
        val s = _ui.value
        val creds = AppCredentials(s.region, s.appKey, s.secretKey, s.appId)
        if (!creds.isComplete) {
            _ui.update { it.copy(message = "Bitte Appkey, Secret Key und Application-ID eintragen") }
            return null
        }
        return repo.startLogin(creds)
    }

    fun handleRedirect(uri: Uri) = viewModelScope.launch {
        _ui.update { it.copy(busy = true, message = "Anmeldung wird abgeschlossen…") }
        val err = repo.completeLogin(uri)
        _ui.update { it.copy(busy = false, loggedIn = repo.isLoggedIn, message = err ?: "Angemeldet") }
        if (err == null) loadPlants()
    }

    fun loadPlants() = viewModelScope.launch {
        _ui.update { it.copy(busy = true) }
        val result = runCatching { repo.loadPlants() }
        _ui.update {
            it.copy(
                busy = false,
                plants = result.getOrDefault(emptyList()),
                loggedIn = repo.isLoggedIn,
                message = result.exceptionOrNull()?.let { e -> "Anlagen konnten nicht geladen werden: ${e.message}" }
                    ?: if (result.getOrNull().isNullOrEmpty()) "Keine Anlage freigegeben" else null,
            )
        }
    }

    fun selectPlant(plant: Plant) {
        repo.selectPlant(plant)
        _ui.update { it.copy(selectedPlantName = plant.name, message = "Anlage gespeichert, Widget kann jetzt hinzugefügt werden") }
        SyncScheduler.schedulePeriodic(getApplication())
        syncNow()
    }

    fun setInvertGrid(value: Boolean) {
        repo.store.invertGridSign = value
        _ui.update { it.copy(invertGridSign = value) }
        syncNow()
    }

    fun runDiagnostics() = viewModelScope.launch {
        _ui.update { it.copy(busy = true, diagnostics = null) }
        val text = repo.diagnose()
        _ui.update { it.copy(busy = false, diagnostics = text) }
    }

    fun syncNow() = viewModelScope.launch {
        repo.cache.setSyncing(true)
        PvWidget().updateAll(getApplication())
        SyncScheduler.syncNow(getApplication())
    }

    fun logout() = viewModelScope.launch {
        repo.logout()
        SyncScheduler.cancelAll(getApplication())
        repo.cache.clear()
        PvWidget().updateAll(getApplication())
        _ui.value = initialState().copy(message = "Abgemeldet")
    }
}
