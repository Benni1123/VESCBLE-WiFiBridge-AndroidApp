package eu.benni1123.vescbridge

import android.net.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

// Antwort eines Status-Abrufs (/api/info), auf das Wesentliche reduziert.
data class BridgeInfo(
    val mode: String,
    val ssid: String,
    val ip: String,
    val rssi: Int = -999,
    val bleConnected: Boolean,
    val bleName: String,
    val bleMac: String,
    val wifiClientConnected: Boolean,
    val apActive: Boolean,
    val apTimeoutRemaining: Int,
    val apIp: String,
    val apClientIp: String = "",
    val vescConnected: Boolean,
    val vescVoltage: Double,
    val vescErpm: Long,
    val vescTempFet: Double,
    val vescTempMotor: Double,
    val vescFault: Int,
    val vescFaultStr: String,
    val uptime: String,
    val heap: Long,
    val version: String,
    val port: Int,
    val rxPin: Int,
    val txPin: Int,
    val ledsEnabled: Boolean = false,
    val allIps: List<String> = emptyList(),
    // Diagnose-Daten
    val diagScans: Int = -1,
    val diagStaConn: Int = -1,
    val diagStaDisc: Int = -1,
    val diagDiscReason: Int = -1,
    val diagApConn: Int = -1,
    val diagApDisc: Int = -1,
    val diagWdFires: Int = -1,
    val diagLoopMaxUs: Int = -1,
    val diagLoopsPerSec: Int = -1,
    val diagMinHeap: Long = -1,
    val diagProbeReqs: Int = -1,
    val diagProbeRssi: Int = -999
)

// Info ueber den Firmware-Stand
data class BridgeUpdateStatus(
    val current: String = "",
    val latest: String = "",
    val available: Boolean = false,
    val serverError: Boolean = false
)

// Kapselt alle HTTP-Aufrufe gegen eine Bridge.
class BridgeApi(private val baseUrl: String, private val network: Network? = null) {

    // Hilfsfunktion fuer automatische Retries bei Netzwerkfehlern (ESP32-Traegheit)
    private suspend fun <T> withRetry(retries: Int = 1, block: () -> T): T {
        var lastEx: Exception? = null
        repeat(retries + 1) { i ->
            try {
                return block()
            } catch (e: Exception) {
                lastEx = e
                if (i <= retries) delay(150) // Kurz warten vor dem naechsten Versuch
            }
        }
        throw lastEx ?: IOException("Unknown error")
    }

    suspend fun fetchInfo(timeout: Int = 2000): BridgeInfo = withContext(Dispatchers.IO) {
        withRetry(retries = 0) {
            val json = httpGet("/api/info", timeout = timeout)
            val o = JSONObject(json)
            val ips = mutableListOf<String>()
            o.optJSONArray("all_ips")?.let { arr ->
                for (i in 0 until arr.length()) ips.add(arr.getString(i))
            } ?: run {
                val ip = o.optString("ip", o.optString("ap_ip", ""))
                if (ip.isNotBlank()) ips.add(ip)
            }
            BridgeInfo(
                mode          = o.optString("mode", ""),
                ssid          = o.optString("ssid", ""),
                ip            = o.optString("ip", ""),
                rssi          = if (o.has("rssi")) o.getInt("rssi") else -999,
                bleConnected  = o.optBoolean("ble_connected", false),
                bleName       = o.optString("ble_name", ""),
                bleMac        = o.optString("ble_mac", ""),
                wifiClientConnected = o.optBoolean("wifi_client_connected", o.optBoolean("wifi_sta_connected", false)),
                apActive      = o.optBoolean("ap_active", false),
                apTimeoutRemaining = if (o.has("ap_timeout_remaining")) o.getInt("ap_timeout_remaining") else -1,
                apIp          = o.optString("ap_ip", ""),
                apClientIp    = o.optString("ap_client_ip", ""),
                vescConnected = o.optBoolean("vesc_connected", false),
                vescVoltage   = o.optDouble("vesc_voltage", 0.0),
                vescErpm      = o.optLong("vesc_erpm", 0),
                vescTempFet   = o.optDouble("vesc_temp_fet", 0.0),
                vescTempMotor = o.optDouble("vesc_temp_motor", 0.0),
                vescFault     = if (o.has("vesc_fault")) o.getInt("vesc_fault") else -1,
                vescFaultStr  = o.optString("vesc_fault_str", ""),
                uptime        = o.optString("uptime", ""),
                heap          = o.optLong("heap", 0),
                version       = o.optString("build", ""),
                port          = if (o.has("port")) o.getInt("port") else -1,
                rxPin         = if (o.has("rx_pin")) o.getInt("rx_pin") else -1,
                txPin         = if (o.has("tx_pin")) o.getInt("tx_pin") else -1,
                ledsEnabled   = o.optBoolean("leds_enabled", o.optBoolean("leds", false)),
                allIps        = ips.filter { it != "0.0.0.0" },
                diagScans     = if (o.has("diag_scans")) o.getInt("diag_scans") else -1,
                diagStaConn   = if (o.has("diag_sta_conn")) o.getInt("diag_sta_conn") else -1,
                diagStaDisc   = if (o.has("diag_sta_disc")) o.getInt("diag_sta_disc") else -1,
                diagDiscReason = if (o.has("diag_disc_reason")) o.getInt("diag_disc_reason") else -1,
                diagApConn    = if (o.has("diag_ap_conn")) o.getInt("diag_ap_conn") else -1,
                diagApDisc    = if (o.has("diag_ap_disc")) o.getInt("diag_ap_disc") else -1,
                diagWdFires   = if (o.has("diag_wd_fires")) o.getInt("diag_wd_fires") else -1,
                diagLoopMaxUs = if (o.has("diag_loop_max_us")) o.getInt("diag_loop_max_us") else -1,
                diagLoopsPerSec = if (o.has("diag_loops_per_sec")) o.getInt("diag_loops_per_sec") else -1,
                diagMinHeap   = if (o.has("diag_min_heap")) o.optLong("diag_min_heap", -1L) else -1L,
                diagProbeReqs = if (o.has("diag_probe_reqs")) o.getInt("diag_probe_reqs") else -1,
                diagProbeRssi = if (o.has("diag_probe_rssi")) o.getInt("diag_probe_rssi") else -999
            )
        }
    }

    suspend fun fetchLedConfig(): String = withContext(Dispatchers.IO) {
        withRetry { httpGet("/api/led/config") }
    }

    suspend fun fetchConfig(): String = withContext(Dispatchers.IO) {
        withRetry { httpGet("/api/config") }
    }

    suspend fun postConfig(jsonBody: String): Boolean = withContext(Dispatchers.IO) {
        try { withRetry { httpPostBody("/api/config", jsonBody) } } catch (e: Exception) { false }
    }

    suspend fun scanWifi(): List<String> = withContext(Dispatchers.IO) {
        try {
            val json = withRetry { httpGet("/api/wifi/scan", timeout = 10000) }
            val list = mutableListOf<String>()
            try {
                // Versuche es als Array (wie im Screenshot)
                val arr = org.json.JSONArray(json)
                for (i in 0 until arr.length()) {
                    val net = arr.getJSONObject(i)
                    list.add(net.getString("ssid"))
                }
            } catch (e: Exception) {
                // Fallback auf Objekt mit "networks" Key
                val o = JSONObject(json)
                val arr = o.getJSONArray("networks")
                for (i in 0 until arr.length()) {
                    val net = arr.getJSONObject(i)
                    list.add(net.getString("ssid"))
                }
            }
            list.distinct().filter { it.isNotBlank() }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun post(path: String): Boolean = withContext(Dispatchers.IO) {
        try { withRetry { httpPost(path) } } catch (e: Exception) { false }
    }

    suspend fun restart(): Boolean = withContext(Dispatchers.IO) {
        try { withRetry { httpPost("/api/restart") } } catch (e: Exception) { false }
    }

    suspend fun ping(): Boolean = withContext(Dispatchers.IO) {
        try { withRetry(retries = 0) { httpGet("/api/ping", timeout = 1000); true } } catch (e: Exception) { false }
    }

    suspend fun fetchUpdateStatus(): BridgeUpdateStatus = withContext(Dispatchers.IO) {
        try {
            val json = withRetry { httpGet("/api/update/status") }
            val o = JSONObject(json)
            BridgeUpdateStatus(
                current = o.optString("current", ""),
                latest = o.optString("available", ""),
                available = o.optBoolean("update_available", false)
            )
        } catch (e: Exception) { BridgeUpdateStatus(serverError = true) }
    }

    suspend fun checkUpdate(): BridgeUpdateStatus = withContext(Dispatchers.IO) {
        try {
            val json = withRetry { httpGet("/api/update/check") }
            val o = JSONObject(json)
            BridgeUpdateStatus(
                current = o.optString("current", ""),
                latest = o.optString("available", ""),
                available = o.optBoolean("update_available", false)
            )
        } catch (e: Exception) { BridgeUpdateStatus(serverError = true) }
    }

    suspend fun installUpdate(): Boolean = withContext(Dispatchers.IO) {
        try { withRetry { httpPost("/api/update/install") } } catch (e: Exception) { false }
    }

    private fun httpGet(path: String, timeout: Int = 5000): String {
        val url = URL(baseUrl + path)
        val conn = (if (network != null) network.openConnection(url) else url.openConnection()) as HttpURLConnection
        conn.apply {
            requestMethod = "GET"
            connectTimeout = timeout
            readTimeout = timeout
            setRequestProperty("Connection", "close") // ESP32 Sockets sofort freigeben
        }
        try {
            if (conn.responseCode in 200..299) {
                return conn.inputStream.bufferedReader().use { it.readText() }
            }
            throw IOException("HTTP ${conn.responseCode}")
        } finally {
            conn.disconnect()
        }
    }

    private fun httpPost(path: String): Boolean {
        val url = URL(baseUrl + path)
        val conn = (if (network != null) network.openConnection(url) else url.openConnection()) as HttpURLConnection
        conn.apply {
            requestMethod = "POST"
            connectTimeout = 4000
            readTimeout = 4000
            doOutput = true
            setRequestProperty("Connection", "close")
        }
        try {
            conn.outputStream.use { it.write(ByteArray(0)) }
            return conn.responseCode in 200..299
        } catch (e: Exception) {
            throw e
        } finally {
            conn.disconnect()
        }
    }

    private fun httpPostBody(path: String, body: String): Boolean {
        val url = URL(baseUrl + path)
        val conn = (if (network != null) network.openConnection(url) else url.openConnection()) as HttpURLConnection
        conn.apply {
            requestMethod = "POST"
            connectTimeout = 5000
            readTimeout = 6000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Connection", "close")
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            return conn.responseCode in 200..299
        } catch (e: Exception) {
            throw e
        } finally {
            conn.disconnect()
        }
    }
}
