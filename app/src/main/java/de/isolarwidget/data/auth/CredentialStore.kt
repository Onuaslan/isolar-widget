package de.isolarwidget.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import de.isolarwidget.data.api.Region

/** App-Zugangsdaten und Tokens, verschlüsselt über Android Keystore. */
data class AppCredentials(
    val region: Region,
    val appKey: String,
    val secretKey: String,
    val appId: String,
) {
    val isComplete get() = appKey.isNotBlank() && secretKey.isNotBlank() && appId.isNotBlank()
}

data class Tokens(val accessToken: String, val refreshToken: String, val expiresAtEpochMs: Long)

class CredentialStore(context: Context) {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var credentials: AppCredentials
        get() = AppCredentials(
            region = Region.fromName(prefs.getString(K_REGION, null)),
            appKey = prefs.getString(K_APPKEY, "").orEmpty(),
            secretKey = prefs.getString(K_SECRET, "").orEmpty(),
            appId = prefs.getString(K_APPID, "").orEmpty(),
        )
        set(v) = prefs.edit {
            putString(K_REGION, v.region.name)
            putString(K_APPKEY, clean(v.appKey))
            putString(K_SECRET, clean(v.secretKey))
            putString(K_APPID, clean(v.appId))
        }

    var tokens: Tokens?
        get() {
            val a = prefs.getString(K_ACCESS, null) ?: return null
            val r = prefs.getString(K_REFRESH, null) ?: return null
            return Tokens(a, r, prefs.getLong(K_EXPIRES, 0L))
        }
        set(v) = prefs.edit {
            if (v == null) {
                remove(K_ACCESS); remove(K_REFRESH); remove(K_EXPIRES)
            } else {
                putString(K_ACCESS, v.accessToken)
                putString(K_REFRESH, v.refreshToken)
                putLong(K_EXPIRES, v.expiresAtEpochMs)
            }
        }

    var pendingOAuthState: String?
        get() = prefs.getString(K_STATE, null)
        set(v) = prefs.edit { if (v == null) remove(K_STATE) else putString(K_STATE, v) }

    var plantId: String?
        get() = prefs.getString(K_PLANT_ID, null)
        set(v) = prefs.edit { if (v == null) remove(K_PLANT_ID) else putString(K_PLANT_ID, v) }

    var plantName: String?
        get() = prefs.getString(K_PLANT_NAME, null)
        set(v) = prefs.edit { if (v == null) remove(K_PLANT_NAME) else putString(K_PLANT_NAME, v) }

    /** ps_key + device_type des Geräts für Netzleistung (Cache aus getDeviceListByPsId). */
    var deviceKey: String?
        get() = prefs.getString(K_DEVICE_KEY, null)
        set(v) = prefs.edit { if (v == null) remove(K_DEVICE_KEY) else putString(K_DEVICE_KEY, v) }

    var deviceType: Int
        get() = prefs.getInt(K_DEVICE_TYPE, 0)
        set(v) = prefs.edit { putInt(K_DEVICE_TYPE, v) }

    /** true: Netzwert der API wird negiert (falls Vorzeichen der Anlage andersherum ist). */
    var invertGridSign: Boolean
        get() = prefs.getBoolean(K_INVERT_GRID, false)
        set(v) = prefs.edit { putBoolean(K_INVERT_GRID, v) }

    private companion object {
        /** Entfernt alle Leer- und unsichtbaren Zeichen, die beim Kopieren aus Portal/Chat mitkommen. */
        fun clean(s: String) = s.filterNot { it.isWhitespace() || it.isISOControl() || it in "\u200B\u200C\u200D\uFEFF\u00A0" }

        const val K_REGION = "region"
        const val K_APPKEY = "appkey"
        const val K_SECRET = "secret_key"
        const val K_APPID = "app_id"
        const val K_ACCESS = "access_token"
        const val K_REFRESH = "refresh_token"
        const val K_EXPIRES = "expires_at"
        const val K_STATE = "oauth_state"
        const val K_PLANT_ID = "plant_id"
        const val K_PLANT_NAME = "plant_name"
        const val K_INVERT_GRID = "invert_grid"
        const val K_DEVICE_KEY = "device_ps_key"
        const val K_DEVICE_TYPE = "device_type"
    }
}
