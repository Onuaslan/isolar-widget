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
            // Unkodiert, exakt wie im Portal angezeigt: die Sungrow-Seite vergleicht den String 1:1
            // (kodiert -> "invalid_redirect_url").
            "&redirectUrl=$REDIRECT_URI"
    }

    data class PortalInfo(val region: Region, val appId: String)

    /** Liest Region und Application-ID aus der im Portal angezeigten Authorize-URL. */
    fun parsePortalUrl(url: String): PortalInfo? {
        val uri = Uri.parse(url.trim())
        val region = Region.entries.firstOrNull { it.authWebHost.equals(uri.host, ignoreCase = true) } ?: return null
        val fragmentQuery = Uri.parse("x://x?" + (uri.fragment?.substringAfter('?') ?: ""))
        val appId = fragmentQuery.getQueryParameter("applicationId") ?: return null
        return PortalInfo(region, appId)
    }

    data class Setup(val region: Region, val appKey: String, val secretKey: String, val appId: String)

    /** Einrichtungs-Link, damit Zugangsdaten nicht abgetippt werden müssen. Nichts davon steht im Repo. */
    fun parseSetupLink(text: String): Setup? {
        val uri = Uri.parse(text.trim())
        if (uri.scheme != "isolarwidget" || uri.host != "setup") return null
        val appKey = uri.getQueryParameter("appkey") ?: return null
        val secret = uri.getQueryParameter("secret") ?: return null
        val appId = uri.getQueryParameter("appid") ?: return null
        return Setup(Region.fromName(uri.getQueryParameter("region")), appKey, secret, appId)
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
