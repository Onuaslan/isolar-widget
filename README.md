# PV-Widget für Sungrow iSolarCloud (Android, Kotlin)

Schlanke App + Homescreen-Widget (Jetpack Glance). Zeigt PV-Leistung, Batterie-SoC, Netz (+ Bezug / − Einspeisung),
Tagesertrag und „Stand: HH:mm“. Das Widget liest nur den lokalen Cache (DataStore), der Sync läuft über WorkManager.

## Voraussetzung: Developer-Zugang bei Sungrow (muss der Nutzer selbst beantragen)

Die offizielle iSolarCloud Open API braucht eine eigene, von Sungrow freigegebene App:

1. Konto anlegen im **iSolarCloud Developer Portal**: https://developer-api.isolarcloud.com/
2. Unter „Applications“ → „Create“ eine App anlegen, Verwendungszweck beschreiben.
   * „Authorize with OAuth2.0“ = **Ja**
   * Redirect-URL: `isolarwidget://oauth` (exakt so; steht in `OAuth.REDIRECT_URI` und `app/build.gradle.kts`)
3. Auf Freigabe durch Sungrow warten (laut Erfahrungsberichten einige Tage).
4. Aus dem Portal übernehmen: **Appkey**, **Secret Key** (wird als Header `x-access-key` gesendet) und
   **Application-ID** (steht als Query-Parameter `applicationId` in der Authorize-URL im Portal).

**Region:** Das Portal zeigt als Authorize-URL `https://web3.isolarcloud.com.hk/#/authorized-app?cloudId=2&applicationId=…`
(International). Die App baut diese URL selbst und nimmt Host und `cloudId` aus der gewählten Region, z. B. Europa →
`web3.isolarcloud.eu`, `cloudId=3`, API `gateway.isolarcloud.eu`. Maßgeblich ist der Server, auf dem dein iSolarCloud-Konto
liegt. Anmeldeseite und API-Gateway müssen zur selben Region gehören, sonst passen Code/Token nicht zum Server.
Klappt die Anmeldung mit „Europa“ nicht (Konto unbekannt), „International“ wählen.

**RSA public key:** wird für den OAuth2-Weg nicht gebraucht (Requests gehen per HTTPS mit `x-access-key` + Bearer-Token).
Der Schlüssel dient dem alten V1-Login mit Benutzer/Passwort, den diese App nicht nutzt. Daher kein Eingabefeld.

Diese drei Werte werden in der App eingegeben und verschlüsselt (EncryptedSharedPreferences/Keystore) gespeichert.
Dein normales iSolarCloud-Passwort gibst du nur auf der Sungrow-Anmeldeseite im Browser ein, die App sieht es nie.

## API-Kurzüberblick (wie implementiert)

| Zweck | Endpunkt (POST, JSON) | Wichtige Felder |
|---|---|---|
| Code → Token | `/openapi/apiManage/token` | `appkey, code, grant_type=authorization_code, redirect_uri` |
| Token erneuern | `/openapi/apiManage/refreshToken` | `appkey, refresh_token` |
| Anlagenliste | `/openapi/platform/queryPowerStationList` | `page, size` → `result_data.pageList[].ps_id / ps_name` |
| Echtzeitwerte | `/openapi/platform/getPowerStationRealTimeData` | `ps_id_list, point_id_list` → `result_data.device_point_list[].pXXXXX` |

Immer: Header `x-access-key: <Secret Key>`, Body `appkey`, `lang`; Datenendpunkte zusätzlich `Authorization: Bearer <access_token>`.
Antworthülle: `{result_code: "1", result_msg, result_data}`.

Gateways: EU `https://gateway.isolarcloud.eu`, International `…isolarcloud.com.hk`, China `…isolarcloud.com`,
Australien `augateway.isolarcloud.com`, Indien `…isolarcloud.in` (siehe `Region.kt`).

Messpunkte (Anlagenebene, mit Fallbacks, siehe `RealtimeParser.kt`):
PV-Leistung 83067 → 83033 → 83329 (W) · SoC 83252 → 83129 → 83334 · Netz 83549 → 83328 (W) · Tagesertrag 83022 → 83331 (Wh).

Echtzeitdaten aktualisiert Sungrow etwa alle 5 Minuten; der Hintergrund-Sync läuft alle 30 Minuten.

## Offene Punkte, die erst mit echtem Zugang prüfbar sind

* **Quellen:** Endpunkte, Regionen und Messpunkt-IDs stammen aus dem Community-Client
  [pysolarcloud](https://github.com/bugjam/pysolarcloud), nicht aus der (nur nach Login sichtbaren) offiziellen Doku.
* **Netz-Vorzeichen** von 83549 ist nicht belegt. In der App gibt es den Schalter „Netz-Vorzeichen umkehren“.
* **SoC-Format** (0..1 oder %) wird automatisch erkannt.
* **Custom-Scheme-Redirect:** Falls das Portal `isolarwidget://oauth` nicht akzeptiert, eine https-URL eintragen
  und die Redirect-URL nach der Freigabe in der App ins Feld „Oder Redirect-URL einfügen“ kopieren
  (dann `OAuth.REDIRECT_URI` entsprechend ändern, sie wird beim Token-Tausch mitgeschickt).
* **Refresh-Token-Ablauf:** Läuft auch der Refresh-Token ab, verlangt Sungrow eine erneute Freigabe im Browser.
  Die App erneuert den Access-Token automatisch; nur in diesem Fall zeigt das Widget „Anmeldung abgelaufen“.
* Alternative ohne OAuth (V1-Login mit Benutzer/Passwort + RSA-Verschlüsselung) ist nicht umgesetzt.

## Aufbau

```
app/src/main/java/de/isolarwidget/
├─ IsolarApp.kt                 Plant periodischen Sync beim App-Start ein
├─ data/
│  ├─ api/  Region, IsolarApi (Retrofit), ApiClient (OkHttp-Timeouts), ApiResults (Fehlerhülle)
│  ├─ auth/ CredentialStore (verschlüsselt), OAuth (URL/Callback), TokenManager (Refresh mit Mutex)
│  ├─ cache/ PvCache (DataStore, Single Source of Truth), PvSnapshot (OK/STALE/ERROR)
│  ├─ RealtimeParser.kt         JSON → PvValues, robust bei fehlenden Feldern
│  └─ PvRepository.kt           Login, Anlagen, sync() mit einmaligem Auth-Retry
├─ worker/ SyncWorker (CoroutineWorker), SyncScheduler (30 min periodisch, CONNECTED; manuell sofort)
└─ ui/ MainActivity + MainViewModel (Compose), Format, widget/PvWidget (Glance, Refresh-Button, Receiver)
```

## Bauen

Android Studio (Ladybug oder neuer) → Projektordner öffnen → Gradle-Sync. AGP 8.7, Kotlin 2.1, compileSdk 35, minSdk 26.
Den Gradle-Wrapper erzeugt Android Studio beim ersten Öffnen (oder `gradle wrapper`).
Unit-Tests: `./gradlew :app:testDebugUnitTest`.
