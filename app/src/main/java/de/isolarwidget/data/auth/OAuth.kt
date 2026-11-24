package de.isolarwidget.data.auth

import android.net.Uri
import de.isolarwidget.data.api.Region
import java.net.URLEncoder

object OAuth {
    /** Muss 1:1 als Redirect-URL der App im iSolarCloud-Developer-Portal eingetragen sein. */
    const val REDIRECT_URI = "isolarwidget://oauth"

    /**
     * Die Autorisierungsseite ist eine Single-Page-App: "state" muss VOR dem '#' stehen,
     * die übrigen Parameter dahinter (Verhalten laut pysolarcloud).
     */
    fun authorizeUrl(region: Region, appId: String, state: String): String {
        val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
        return "https://${region.authWebHost}/?state=${enc(state)}" +
            "#/authorized-app?cloudId=${region.cloudId}&applicationId=${enc(appId)}" +
            "&redirectUrl=${enc(REDIRECT_URI)}"
    }

    data class Callback(val code: String, val state: String?)

    /** Liest code/state aus der Redirect-URL (auch wenn der Nutzer die URL manuell einfügt). */
    fun parseCallback(uri: Uri): Callback? {
        val code = uri.getQueryParameter("code")
            ?: Uri.parse("x://x?" + (uri.fragment?.substringAfter('?') ?: "")).getQueryParameter("code")
            ?: return null
        return Callback(code, uri.getQueryParameter("state"))
    }
}
