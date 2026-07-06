package eu.benni1123.vescbridge

import org.json.JSONArray
import org.json.JSONObject

// Hilfsfunktion um Booleans sowohl als echte JSON-Booleans (true/false) als auch
// als Integers (1/0) zu lesen. Macht die API robuster gegen Firmware-Aenderungen.
private fun JSONObject.optBool(key: String, fallback: Boolean): Boolean {
    if (!has(key)) return fallback
    return try {
        getBoolean(key)
    } catch (_: Exception) {
        optInt(key, if (fallback) 1 else 0) != 0
    }
}

// Ein WLAN-Netz (Heimnetz-Zugangsdaten der Bridge).
data class WifiNet(
    val ssid: String = "",
    val pass: String = "",
    val static: Boolean = false,
    val ip: String = "",
    val gateway: String = "",
    val subnet: String = "",
    val dns: String = ""
)

// Komplette Geraete-Config (Spiegel von /api/config).
data class BridgeConfig(
    val bleName: String = "",
    val apSsid: String = "",
    val apPass: String = "",
    val apMode: Int = 1,
    val port: Int = 65101,
    val vescPoll: Boolean = false,
    val apTimeout: Int = 0,
    val rxPin: Int = 0,
    val txPin: Int = 0,
    val autoreboot: Boolean = false,
    val autorebootTime: Int = 0,
    val autorebootNoWifi: Boolean = false,
    val roamEnabled: Boolean = false,
    val roamThreshold: Int = -75,
    val roamHysteresis: Int = 5,
    val autopollEnabled: Boolean = false,
    val autopollInterval: Int = 5,
    val bleMode: Int = 1,
    val blePinEnabled: Boolean = false,
    val blePin: Int = 123456,
    val bleAutoErpmOn: Int = 200,
    val bleAutoOffSec: Int = 120,
    val ledsEnabled: Boolean = false,
    val updateUrl: String = "",
    val versionUrl: String = "",
    val wifi: List<WifiNet> = emptyList()
) {
    companion object {
        fun parse(json: String): BridgeConfig {
            val o = JSONObject(json)
            val wifiList = mutableListOf<WifiNet>()
            o.optJSONArray("wifi")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val w = arr.getJSONObject(i)
                    wifiList.add(WifiNet(
                        ssid = w.optString("ssid", ""),
                        pass = w.optString("pass", ""),
                        static = w.optBool("static", false),
                        ip = w.optString("ip", ""),
                        gateway = w.optString("gateway", ""),
                        subnet = w.optString("subnet", ""),
                        dns = w.optString("dns", "")
                    ))
                }
            }
            return BridgeConfig(
                bleName = o.optString("ble_name", ""),
                apSsid = o.optString("ap_ssid", ""),
                apPass = o.optString("ap_pass", ""),
                apMode = o.optInt("ap_mode", 1),
                port = o.optInt("port", 65101),
                vescPoll = o.optBool("vesc_poll", false),
                apTimeout = o.optInt("ap_timeout", 0),
                rxPin = o.optInt("rx_pin", 0),
                txPin = o.optInt("tx_pin", 0),
                autoreboot = o.optBool("autoreboot", false),
                autorebootTime = o.optInt("autoreboot_time", 0),
                autorebootNoWifi = o.optBool("autoreboot_no_wifi", false),
                roamEnabled = o.optBool("roam_enabled", false),
                roamThreshold = o.optInt("roam_threshold", -75),
                roamHysteresis = o.optInt("roam_hysteresis", 5),
                autopollEnabled = o.optBool("autopoll_enabled", false),
                autopollInterval = o.optInt("autopoll_interval", 5),
                bleMode = if (o.has("ble_mode")) o.getInt("ble_mode") else if (o.has("bleMode")) o.getInt("bleMode") else 1,
                blePinEnabled = o.optBool("ble_pin_enabled", false),
                blePin = o.optInt("ble_pin", 123456),
                bleAutoErpmOn = o.optInt("ble_auto_erpm_on", 200),
                bleAutoOffSec = o.optInt("ble_auto_off_sec", 120),
                ledsEnabled = o.optBool("leds_enabled", false),
                updateUrl = o.optString("update_url", ""),
                versionUrl = o.optString("version_url", ""),
                wifi = wifiList
            )
        }
    }

    // Als JSON-Body fuer den POST an /api/config (gleiches Format wie die Web-UI).
    fun toJson(reboot: Boolean): String {
        val o = JSONObject()
        o.put("ble_name", bleName)
        o.put("ap_ssid", apSsid)
        o.put("ap_pass", apPass)
        o.put("ap_mode", apMode)
        o.put("port", port)
        o.put("vesc_poll", vescPoll)
        o.put("ap_timeout", apTimeout)
        o.put("rx_pin", rxPin)
        o.put("tx_pin", txPin)
        o.put("autoreboot", autoreboot)
        o.put("autoreboot_time", autorebootTime)
        o.put("autoreboot_no_wifi", autorebootNoWifi)
        o.put("roam_enabled", roamEnabled)
        o.put("roam_threshold", roamThreshold)
        o.put("roam_hysteresis", roamHysteresis)
        o.put("autopoll_enabled", autopollEnabled)
        o.put("autopoll_interval", autopollInterval)
        o.put("ble_mode", bleMode)
        o.put("ble_pin_enabled", blePinEnabled)
        o.put("ble_pin", blePin)
        o.put("ble_auto_erpm_on", bleAutoErpmOn)
        o.put("ble_auto_off_sec", bleAutoOffSec)
        o.put("leds_enabled", ledsEnabled)
        o.put("update_url", updateUrl)
        o.put("version_url", versionUrl)
        val arr = JSONArray()
        wifi.forEach { w ->
            arr.put(JSONObject().apply {
                put("ssid", w.ssid); put("pass", w.pass)
                put("static", w.static); put("ip", w.ip)
                put("gateway", w.gateway); put("subnet", w.subnet); put("dns", w.dns)
            })
        }
        o.put("wifi", arr)
        if (!reboot) o.put("noreboot", 1)
        return o.toString()
    }
}