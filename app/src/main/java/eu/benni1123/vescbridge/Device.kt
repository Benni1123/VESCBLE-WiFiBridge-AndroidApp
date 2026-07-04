package eu.benni1123.vescbridge

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject

// Ein gespeichertes Geraet:
data class Device(
    val id: String,
    val name: String,
    val hosts: List<String>,
    val apSsid: String = "",
    val apPassword: String = "",
    val apOnly: Boolean = false,
    val autoConnect: Boolean = false,
    val homeTimeoutMs: Int = 2000,
    val apTimeoutMs: Int = 5000,
    val syncName: Boolean = true
) {
    fun hasAp(): Boolean = apSsid.isNotBlank()
}

private val Context.dataStore by preferencesDataStore(name = "vesc_devices")

class DeviceStore(private val context: Context) {

    private val KEY_DEVICES  = stringPreferencesKey("devices_json")
    private val KEY_SELECTED = stringPreferencesKey("selected_id")

    private val _devicesFlow = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devicesFlow.asStateFlow()

    private val _selectedIdFlow = MutableStateFlow<String?>(null)
    val selectedId: StateFlow<String?> = _selectedIdFlow.asStateFlow()

    init {
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.Main) {
            context.dataStore.data.collect { prefs ->
                _devicesFlow.value = parseDevices(prefs[KEY_DEVICES] ?: "[]")
                _selectedIdFlow.value = prefs[KEY_SELECTED]
            }
        }
    }

    suspend fun addOrUpdate(device: Device) {
        context.dataStore.edit { prefs ->
            val list = parseDevices(prefs[KEY_DEVICES] ?: "[]").toMutableList()
            val idx = list.indexOfFirst { it.id == device.id }
            if (idx >= 0) list[idx] = device else list.add(device)
            
            // Wenn das neue/aktualisierte Gerät Auto-Connect hat, alle anderen deaktivieren
            val finalList = if (device.autoConnect) {
                list.map { if (it.id == device.id) it else it.copy(autoConnect = false) }
            } else {
                list
            }
            
            // Falls noch nichts selektiert ist
            if (prefs[KEY_SELECTED] == null) prefs[KEY_SELECTED] = device.id
            
            prefs[KEY_DEVICES] = serializeDevices(finalList)
            _devicesFlow.value = finalList
        }
    }

    suspend fun delete(id: String) {
        context.dataStore.edit { prefs ->
            val list = parseDevices(prefs[KEY_DEVICES] ?: "[]").filter { it.id != id }
            prefs[KEY_DEVICES] = serializeDevices(list)
            if (prefs[KEY_SELECTED] == id) {
                // Nur ein anderes Gerät wählen, wenn es den "Stern" (autoConnect) hat.
                // Ansonsten Auswahl leeren, damit der User in der Übersicht landet.
                val next = list.firstOrNull { it.autoConnect }
                prefs[KEY_SELECTED] = next?.id ?: ""
            }
            _devicesFlow.value = list
        }
    }

    suspend fun select(id: String) {
        context.dataStore.edit { prefs -> 
            prefs[KEY_SELECTED] = id
            _selectedIdFlow.value = id
        }
    }

    suspend fun toggleAutoConnect(id: String) {
        context.dataStore.edit { prefs ->
            val list = parseDevices(prefs[KEY_DEVICES] ?: "[]")
            val targetIdx = list.indexOfFirst { it.id == id }
            if (targetIdx == -1) return@edit
            
            val isEnabling = !list[targetIdx].autoConnect
            
            val newList = list.map {
                if (it.id == id) it.copy(autoConnect = isEnabling)
                else if (isEnabling) it.copy(autoConnect = false) 
                else it
            }
            
            prefs[KEY_DEVICES] = serializeDevices(newList)
            _devicesFlow.value = newList
        }
    }

    private fun parseDevices(json: String): List<Device> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val hosts: List<String> = when {
                    o.has("hosts") -> {
                        val ha = o.getJSONArray("hosts")
                        (0 until ha.length()).map { ha.getString(it) }
                    }
                    o.has("host") -> listOf(o.getString("host"))
                    else -> emptyList()
                }.filter { it.isNotBlank() }
                Device(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    hosts = hosts,
                    apSsid = o.optString("apSsid", ""),
                    apPassword = o.optString("apPassword", ""),
                    apOnly = o.optBoolean("apOnly", false),
                    autoConnect = o.optBoolean("autoConnect", false),
                    homeTimeoutMs = o.optInt("homeTimeoutMs", 2000),
                    apTimeoutMs = o.optInt("apTimeoutMs", 5000),
                    syncName = o.optBoolean("syncName", true)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun serializeDevices(list: List<Device>): String {
        val arr = JSONArray()
        list.forEach { d ->
            arr.put(JSONObject().apply {
                put("id", d.id)
                put("name", d.name)
                put("hosts", JSONArray(d.hosts))
                put("apSsid", d.apSsid)
                put("apPassword", d.apPassword)
                put("apOnly", d.apOnly)
                put("autoConnect", d.autoConnect)
                put("homeTimeoutMs", d.homeTimeoutMs)
                put("apTimeoutMs", d.apTimeoutMs)
                put("syncName", d.syncName)
            })
        }
        return arr.toString()
    }
}
