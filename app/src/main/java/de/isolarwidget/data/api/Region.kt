package de.isolarwidget.data.api

/**
 * iSolarCloud-Server je Region. Gateway = API-Basis, authWebHost/cloudId = OAuth2-Autorisierungsseite.
 * Quelle: Community-Client pysolarcloud (bugjam/pysolarcloud), nicht offiziell dokumentiert.
 */
enum class Region(
    val label: String,
    val gatewayUrl: String,
    val authWebHost: String,
    val cloudId: Int,
) {
    EUROPE("Europa (.eu)", "https://gateway.isolarcloud.eu/", "web3.isolarcloud.eu", 3),
    INTERNATIONAL("International (.com.hk)", "https://gateway.isolarcloud.com.hk/", "web3.isolarcloud.com.hk", 2),
    CHINA("China (.com)", "https://gateway.isolarcloud.com/", "web3.isolarcloud.com", 1),
    AUSTRALIA("Australien", "https://augateway.isolarcloud.com/", "auweb3.isolarcloud.com", 7),
    INDIA("Indien", "https://gateway.isolarcloud.in/", "web3.isolarcloud.in", 9);

    companion object {
        fun fromName(name: String?): Region = entries.firstOrNull { it.name == name } ?: EUROPE
    }
}
