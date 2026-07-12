package eu.benni1123.vescbridge

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.location.LocationManager
import androidx.core.location.LocationManagerCompat
import java.net.Inet4Address

// Verbindet das Handy gezielt mit dem Access Point der Bridge und bindet den
// App-Datenverkehr an dieses Netz. Da minSdk nun 32 (Android 12L) ist, nutzen
// wir moderne Capabilities fuer maximale Stabilitaet.
class WifiConnector(private val context: Context) {

    private val cm = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wm = context.applicationContext
        .getSystemService(Context.WIFI_SERVICE) as WifiManager

    private var callback: ConnectivityManager.NetworkCallback? = null
    @Volatile var boundNetwork: Network? = null
        private set

    // Ist WLAN generell eingeschaltet (unabhängig von einer Verbindung)?
    fun isWifiEnabled(): Boolean = wm.isWifiEnabled

    // Prüft, ob der globale Standort-Schalter an ist (wichtig für den WLAN-Dialog)
    fun isLocationEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return LocationManagerCompat.isLocationEnabled(lm)
    }

    // Liefert das aktuelle WLAN-Netzwerk, falls das Handy in einem eingeloggt ist.
    // Hilft uns, die Bridge zu finden, wenn der User sich manuell verbunden hat.
    fun getCurrentWifiNetwork(): Network? {
        val active = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(active)
        return if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) active else null
    }

    fun getLocalIpAddress(): String? {
        val net = getCurrentWifiNetwork() ?: return null
        val lp = cm.getLinkProperties(net) ?: return null
        return lp.linkAddresses.firstOrNull { it.address is Inet4Address }?.address?.hostAddress
    }

    // Ist das Handy aktuell in einem WLAN? Mobile Daten zaehlen bewusst NICHT.
    fun isOnWifi(): Boolean = getCurrentWifiNetwork() != null

    // Liefert eine Liste sichtbarer SSIDs (Voraussetzung: Standortberechtigung erteilt).
    fun getNearbySsids(): List<String> {
        return try {
            wm.scanResults
                .map {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        it.wifiSsid?.toString()?.removeSurrounding("\"") ?: ""
                    } else {
                        @Suppress("DEPRECATION")
                        it.SSID ?: ""
                    }
                }
                .filter { it.isNotBlank() && it != "<unknown ssid>" }
                .distinct()
                .sortedBy { !it.contains("VescBridge", ignoreCase = true) } // VescBridges nach oben
        } catch (e: SecurityException) {
            android.util.Log.e("VescDebug", "Permission missing for scanResults", e)
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    // Mit dem AP verbinden. onResult(true) sobald verbunden, gebunden UND IP vorhanden.
    fun connectToAp(ssid: String, password: String, onResult: (Boolean) -> Unit) {
        release()

        val specBuilder = WifiNetworkSpecifier.Builder().setSsid(ssid)
        if (password.isNotBlank()) {
            specBuilder.setWpa2Passphrase(password)
        }
        val specifier = specBuilder.build()

        @Suppress("WrongConstant")
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            // Bridge hat kein Internet
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            // Wichtig: System soll die Verbindung nicht als "eingeschraenkt" verwerfen
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .setNetworkSpecifier(specifier)
            .build()

        val startTime = System.currentTimeMillis()
        val cb = object : ConnectivityManager.NetworkCallback() {
            private var resultSent = false

            override fun onAvailable(network: Network) {
                android.util.Log.d("VescDebug", "AP Network available after ${System.currentTimeMillis() - startTime}ms: $network")
                // Zuerst den Prozess binden, damit DHCP-Traffic etc. über das richtige Interface läuft.
                cm.bindProcessToNetwork(network)
                boundNetwork = network
            }

            override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
                val hasIpV4 = lp.linkAddresses.any { it.address is Inet4Address }
                android.util.Log.d("VescDebug", "LinkProperties changed. Has IPv4: $hasIpV4")

                if (hasIpV4 && !resultSent) {
                    resultSent = true
                    try {
                        cm.reportNetworkConnectivity(network, true)
                    } catch (e: Exception) {
                        android.util.Log.e("VescDebug", "Error reporting connectivity", e)
                    }
                    onResult(true)
                }
            }

            override fun onLost(network: Network) {
                android.util.Log.d("VescDebug", "AP Network lost! $network")
                if (boundNetwork == network) {
                    try { cm.bindProcessToNetwork(null) } catch (_: Exception) {}
                    boundNetwork = null
                    if (callback == this) callback = null
                    onResult(false)
                }
            }

            override fun onUnavailable() {
                val duration = System.currentTimeMillis() - startTime
                android.util.Log.d("VescDebug", "AP Network unavailable after ${duration}ms (Rejected or Timeout)")
                if (callback == this) callback = null
                boundNetwork = null
                onResult(false)
            }
        }

        callback = cb
        // Timeout auf 15s gesetzt, damit der User nicht zu lange im "Verbinde..." feststeckt
        cm.requestNetwork(request, cb, 15000)
    }

    // Haelt die AP-Verbindung aktiv am Leben. Android neigt dazu, ein Netz OHNE
    // Internet nach einer Weile zu verwerfen oder zu deprioritisieren (vor allem
    // wenn der Bildschirm ausgeht oder ein anderes Netz auftaucht). Diese Methode
    // bekraeftigt periodisch, dass das Netz in Ordnung ist, und stellt die
    // Prozess-Bindung sicher (falls Android sie geloest hat). Regelmaessig aus
    // der Poll-Schleife aufrufen, solange wir am AP haengen.
    // Gibt true zurueck, wenn weiterhin an den AP gebunden.
    fun keepAlive(): Boolean {
        val net = boundNetwork ?: return false
        try { cm.reportNetworkConnectivity(net, true) } catch (_: Exception) {}
        try { cm.bindProcessToNetwork(net) } catch (_: Exception) {}
        return true
    }

    fun release() {
        try { cm.bindProcessToNetwork(null) } catch (_: Exception) {}
        val cb = callback
        callback = null
        if (cb != null) {
            try { cm.unregisterNetworkCallback(cb) } catch (_: Exception) {}
        }
        boundNetwork = null
    }
}