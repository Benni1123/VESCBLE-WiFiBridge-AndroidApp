package eu.benni1123.vescbridge

import android.app.Application
import android.content.Context
import android.net.Network
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

// Verbindungszustand des aktiven Geraets fuer die UI.
enum class ConnState { Searching, Online, ConnectingAp, Offline, Rebooting }

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val store = DeviceStore(app)
    private val wifi  = WifiConnector(app)

    private val wakeLock: PowerManager.WakeLock =
        (app.getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VescBridge::PollLock")
        }

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    private val _selected = MutableStateFlow<Device?>(null)
    val selected: StateFlow<Device?> = _selected.asStateFlow()

    private val _info = MutableStateFlow<BridgeInfo?>(null)
    val info: StateFlow<BridgeInfo?> = _info.asStateFlow()

    private val _state = MutableStateFlow(ConnState.Offline)
    val state: StateFlow<ConnState> = _state.asStateFlow()

    private val _activeHost = MutableStateFlow<String?>(null)
    val activeHost: StateFlow<String?> = _activeHost.asStateFlow()

    private val _ledConfig = MutableStateFlow<LedConfig?>(null)
    val ledConfig: StateFlow<LedConfig?> = _ledConfig.asStateFlow()

    private val _updateStatus = MutableStateFlow<BridgeUpdateStatus?>(null)
    val updateStatus: StateFlow<BridgeUpdateStatus?> = _updateStatus.asStateFlow()

    private val _updateBusy = MutableStateFlow(false)
    val updateBusy: StateFlow<Boolean> = _updateBusy.asStateFlow()

    private val _rebootProgress = MutableStateFlow<Float?>(null)
    val rebootProgress: StateFlow<Float?> = _rebootProgress.asStateFlow()

    private val _appUpdateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val appUpdateInfo: StateFlow<AppUpdateInfo?> = _appUpdateInfo.asStateFlow()

    private val _appUpdateBusy = MutableStateFlow(false)
    val appUpdateBusy: StateFlow<Boolean> = _appUpdateBusy.asStateFlow()

    private val _appUpdateProgress = MutableStateFlow<Float?>(null)
    val appUpdateProgress: StateFlow<Float?> = _appUpdateProgress.asStateFlow()

    private val updater = AppUpdater(app)
    private var pollJob: Job? = null

    init {
        // App-Update beim Start prüfen
        checkAppUpdate()
    }

    fun checkAppUpdate() {
        viewModelScope.launch {
            _appUpdateBusy.value = true
            _appUpdateInfo.value = updater.checkForUpdate()
            _appUpdateBusy.value = false
        }
    }

    fun downloadAppUpdate() {
        val info = _appUpdateInfo.value ?: return
        viewModelScope.launch {
            _appUpdateBusy.value = true
            _appUpdateProgress.value = 0f
            val ok = updater.downloadAndInstall(info.downloadUrl) { progress ->
                _appUpdateProgress.value = progress
            }
            if (!ok) {
                // Bei Fehler Progress zurücksetzen
                _appUpdateProgress.value = null
            }
            _appUpdateBusy.value = false
        }
    }

    private var apTried = false
    private var isRebooting = false
    private var searchCycles = 0

    // Toleranz gegen einzelne fehlgeschlagene Polls: erst nach mehreren
    // aufeinanderfolgenden Fehlern gilt die Verbindung als verloren. Verhindert,
    // dass ein einzelner Timeout (z.B. AP-Strecke aus dem Scooter mit BLE
    // nebenher) die Verbindung kappt und unnoetig neu aufbaut.
    private var consecutiveFails = 0
    private val MAX_FAILS_BEFORE_DROP = 3
    // True, solange ein AP-Connect-Versuch laeuft -> die Poll-Schleife darf in
    // dieser Zeit NICHT dazwischenfunken (kein release/Offline-Backoff).
    private var apConnecting = false
    private var isAppActive = true

    private var slowSyncJob: Job? = null

    init {
        // App-Update beim Start prüfen
        checkAppUpdate()

        // Geräteliste synchron halten
        viewModelScope.launch {
            store.devices.collect { _devices.value = it }
        }

        // Selektions-Logik
        viewModelScope.launch {
            // 1. Auf die erste Ladung der Geräteliste warten
            val list = store.devices.filter { it.isNotEmpty() }.first()

            // 2. Den aktuell gespeicherten Favoriten (Stern) finden
            val starred = list.firstOrNull { it.autoConnect }

            // 3. Bestimmen, was am Start passieren soll
            val targetId = when {
                list.size == 1 -> list.first().id
                starred != null -> starred.id
                else -> null // Mehrere Geräte, kein Stern -> Übersicht (null)
            }

            // 4. Den Store sofort überschreiben, falls er noch auf dem "letzten Gerät" steht
            // aber kein Stern gesetzt ist. Das verhindert den Auto-Connect zum falschen Gerät.
            if (targetId == null) {
                store.select("") // Selektion im Store löschen
            } else {
                store.select(targetId) // Star/Single-Device erzwingen
            }

            // 5. Erst JETZT anfangen auf Selektionen und Datenänderungen zu hören
            combine(store.devices, store.selectedId) { devs, id ->
                devs.firstOrNull { it.id == id }
            }.collect { sel ->
                val old = _selected.value
                if (old?.id != sel?.id) {
                    _selected.value = sel
                    onDeviceChanged()
                } else if (old != null && old != sel && sel != null) {
                    val wasSyncNameOff = !old.syncName && sel.syncName
                    _selected.value = sel
                    // Falls sich verbindungsrelevante Eigenschaften (z.B. apOnly)
                    // geaendert haben, muessen wir evtl. neu verbinden.
                    // ABER: nur wenn wir nicht gerade stabil online sind.
                    // Das verhindert Reconnect-Loops beim automatischen IP/Namens-Sync.
                    val isConnectionRelevantChange = old.apOnly != sel.apOnly || old.hosts != sel.hosts
                    if (isConnectionRelevantChange && _state.value != ConnState.Online) {
                        onDeviceChanged()
                    } else if (wasSyncNameOff && _info.value != null) {
                        // Wenn der Haken gerade erst aktiviert wurde: sofort synctesten
                        checkSync(_info.value!!, sel)
                    }
                }
            }
        }

        // Throttling für LED-Slider
        viewModelScope.launch {
            _ledThrottler.collect { path ->
                api()?.post(path)
                delay(250.milliseconds)
            }
        }
    }

    fun setAppActive(active: Boolean) {
        val wasInactive = !isAppActive && active
        isAppActive = active
        if (wasInactive) {
            apTried = false
            searchCycles = 0
            refreshNow()
        }
    }

    private fun api(): BridgeApi? {
        val host = _activeHost.value ?: return null
        return BridgeApi("http://$host", wifi.boundNetwork)
    }

    private fun onDeviceChanged() {
        if (isRebooting) return
        wifi.release()
        apTried = false
        searchCycles = 0
        _info.value = null
        _ledConfig.value = null
        _config.value = null
        _updateStatus.value = null
        _activeHost.value = null
        slowSyncJob?.cancel()
        restartPolling()
    }

    private suspend fun checkSync(info: BridgeInfo, dev: Device) {
        var updatedDev = dev
        var needsUpdate = false

        if (info.allIps.isNotEmpty()) {
            val combinedHosts = (dev.hosts + info.allIps).distinct().filter { it.isNotBlank() && it != "0.0.0.0" }
            if (combinedHosts.size != dev.hosts.size || !combinedHosts.containsAll(dev.hosts)) {
                updatedDev = updatedDev.copy(hosts = combinedHosts)
                needsUpdate = true
            }
        }

        if (dev.syncName && info.bleName.isNotBlank() && info.bleName != dev.name) {
            android.util.Log.d("VescDebug", "Syncing name: ${dev.name} -> ${info.bleName}")
            updatedDev = updatedDev.copy(name = info.bleName)
            needsUpdate = true
        }

        if (needsUpdate) {
            store.addOrUpdate(updatedDev)
            if (_selected.value?.id == updatedDev.id) {
                _selected.value = updatedDev
            }
        }
    }

    private fun startSlowSync() {
        slowSyncJob?.cancel()
        slowSyncJob = viewModelScope.launch {
            while (true) {
                delay(10000.milliseconds)
                if (_state.value == ConnState.Online) {
                    loadConfig()
                    loadLedConfig()
                } else {
                    break
                }
            }
        }
    }

    private fun restartPolling() {
        pollJob?.cancel()
        if (wakeLock.isHeld) try { wakeLock.release() } catch (_: Exception) {}

        pollJob = viewModelScope.launch {
            try {
                wakeLock.acquire(10 * 60 * 1000L)
                while (true) {
                    val dev = _selected.value
                    if (dev == null) {
                        _state.value = ConnState.Offline
                        delay(2000.milliseconds)
                        continue
                    }

                    // Waehrend ein AP-Connect laeuft: nicht eingreifen, kurz warten.
                    if (apConnecting) {
                        delay(500.milliseconds)
                        continue
                    }

                    if (_activeHost.value == null) {
                        _state.value = ConnState.Searching

                        val reachable = resolveHost(dev)
                        if (reachable != null) {
                            android.util.Log.d("VescDebug", "Reachable found: $reachable")
                            _activeHost.value = reachable
                            consecutiveFails = 0
                            searchCycles = 0
                            // Wenn wir die Bridge über eine andere IP als die AP-IP finden,
                            // können wir die AP-Bindung lösen.
                            if (reachable != "192.168.9.1" && wifi.boundNetwork != null) {
                                android.util.Log.d("VescDebug", "Bridge found on home IP, releasing AP bind.")
                                wifi.release()
                            }
                            if (reachable != "192.168.9.1") {
                                apTried = false
                            }
                        } else {
                            searchCycles++
                            // Wann versuchen wir den AP-Connect?
                            // 1. App muss im Vordergrund sein
                            // 2. Standort muss an sein (sonst kein Dialog)
                            // 3. Wenn das Gerät im "Nur AP" Modus ist
                            // 4. Wenn wir nicht im WLAN sind -> SOFORT
                            // 5. Wenn wir im WLAN sind, aber die Bridge nach 4 Suchläufen nicht gefunden wurde
                            val onWifi = wifi.isOnWifi()
                            val shouldTryAp = isAppActive && wifi.isLocationEnabled() &&
                                    dev.hasAp() && !apTried &&
                                    (dev.apOnly || !onWifi || searchCycles >= 4)

                            if (shouldTryAp) {
                                android.util.Log.d("VescDebug", "Triggering AP connect. Cycles: $searchCycles, WifiOn: ${wifi.isOnWifi()}")
                                if (!wifi.isWifiEnabled()) {
                                    _showWifiPanel.emit(Unit)
                                    _state.value = ConnState.Offline
                                    delay(5000.milliseconds)
                                    continue
                                }
                                apTried = true
                                tryApConnect(dev)
                            } else {
                                _state.value = ConnState.Offline
                                // Nach 6 Zyklen ohne Erfolg geben wir den AP-Versuch wieder frei
                                if (searchCycles >= 6) {
                                    apTried = false
                                    searchCycles = 0
                                    if (dev.apOnly || wifi.boundNetwork != null) {
                                        wifi.release()
                                    }
                                }
                                delay(1000.milliseconds)
                                continue
                            }
                        }
                    }

                    val a = api()
                    val onWifi = wifi.isOnWifi()
                    val isApHost = _activeHost.value == "192.168.9.1"

                    // AP-Verbindung aktiv am Leben halten: Android wuerde ein Netz
                    // ohne Internet sonst nach einer Weile verwerfen. Bekraeftigt
                    // Konnektivitaet + frischt die Prozess-Bindung auf (jeder Poll).
                    if (isApHost) wifi.keepAlive()

                    // Wenn wir im Heimnetz waren und das WLAN plötzlich weg ist:
                    // Sofort abbrechen, bevor wir in den HTTP-Timeout laufen.
                    if (!isApHost && !onWifi && _activeHost.value != null) {
                        _activeHost.value = null
                        _state.value = ConnState.Searching
                        consecutiveFails = 0
                        apTried = false
                        delay(100.milliseconds)
                        continue
                    }

                    if (a != null) {
                        try {
                            val timeout = if (isApHost) dev.apTimeoutMs else dev.homeTimeoutMs
                            val info = a.fetchInfo(timeout)
                            consecutiveFails = 0          // Erfolg -> Zaehler zuruecksetzen
                            _info.value = info
                            val wasOffline = _state.value != ConnState.Online
                            _state.value = ConnState.Online
                            
                            // Erst bei erfolgreichem Poll den AP-Versuch wieder freigeben
                            apTried = false

                            checkSync(info, dev)

                            // Config & LED Sync alle 10s im Hintergrund starten, wenn neu online
                            if (wasOffline) {
                                startSlowSync()
                            }

                            // Config Sync (bei jeder neuen Verbindung)
                            if (wasOffline || _config.value == null) {
                                loadConfigSyncToDevice(dev)
                            }
                        } catch (e: Exception) {
                            // NICHT sofort die Verbindung wegwerfen: ein einzelner
                            // Timeout ist normal. Erst nach MAX_FAILS_BEFORE_DROP
                            // aufeinanderfolgenden Fehlern wirklich neu verbinden.
                            consecutiveFails++
                            android.util.Log.d("VescDebug", "Fetch info failed ($consecutiveFails): ${e.message}")

                            // Sonderfall: Wenn WLAN komplett weg ist und wir nicht am AP hingen,
                            // sofort abbrechen und AP-Suche einleiten (spart Timeouts).
                            if (!onWifi && !isApHost && wifi.boundNetwork == null) {
                                consecutiveFails = MAX_FAILS_BEFORE_DROP
                            }

                            // Erst ab dem dritten Fehler ehrliches Feedback: nicht stur
                            // gruen bleiben, sondern "Suche..." zeigen. Die
                            // Verbindung selbst wird aber erst spaeter verworfen.
                            if (consecutiveFails > 2 && _state.value == ConnState.Online) {
                                _state.value = ConnState.Searching
                            }

                            val threshold = if (isApHost) 10 else 3
                            if (consecutiveFails >= threshold) {
                                android.util.Log.d("VescDebug", "Connection lost after $consecutiveFails fails. Resetting active host.")
                                consecutiveFails = 0
                                val oldHost = _activeHost.value
                                _activeHost.value = null
                                _state.value = ConnState.Searching
                                _config.value = null
                                // Selbstheilung: evtl. haengende WiFi-Bindung loesen,
                                // damit der naechste resolveHost frisch aufsetzt.
                                // Aber nur wenn wir vorher am AP waren.
                                if (oldHost == "192.168.9.1") {
                                    wifi.release()
                                }
                            }
                            // sonst: Verbindung halten, naechster Versuch im Loop.
                        }
                    }
                    // Adaptives Poll-Intervall: laeuft alles stabil -> ruhiger
                    // pollen (weniger Funklast, weniger Kollision mit dem AP).
                    // Sobald ein Fehlversuch da ist -> schnell pollen, um die
                    // Unterbrechung so kurz wie moeglich zu halten.
                    val nextDelayMs = if (consecutiveFails == 0 && _state.value == ConnState.Online)
                        1000L      // 1s Polling für alle Modi (stabil)
                    else
                        400L       // Problem/Erholung -> schnell nachfassen
                    delay(nextDelayMs.milliseconds)
                }
            } finally {
                if (wakeLock.isHeld) try { wakeLock.release() } catch (_: Exception) {}
            }
        }
    }

    private suspend fun resolveHost(dev: Device): String? = withContext(Dispatchers.IO) {
        val onWifi = wifi.isOnWifi()
        val bound = wifi.boundNetwork

        // Auf LTE ohne Bindung finden wir lokal eh nix. Sofort null, um AP-Connect zu beschleunigen.
        if (!onWifi && bound == null) return@withContext null

        val hosts = if (dev.apOnly) {
            listOf("192.168.9.1")
        } else {
            (dev.hosts + "192.168.9.1").distinct().filter { it.isNotBlank() }
        }

        if (hosts.isEmpty()) return@withContext null
        
        // Scope für die parallelen Pings, damit wir Kinder gezielt abbrechen können
        val result = supervisorScope {
            val channel = Channel<String?>(hosts.size * 3)
            val nets = mutableListOf<Network?>(null)
            if (bound != null) nets.add(bound)
            val current = wifi.getCurrentWifiNetwork()
            if (current != null && current != bound) nets.add(current)

            nets.forEach { net ->
                hosts.forEach { h ->
                    launch {
                        try {
                            val api = BridgeApi("http://$h", net)
                            if (api.ping()) {
                                val netType = when(net) {
                                    null -> "System"
                                    bound -> "Bound"
                                    else -> "CurrentWiFi"
                                }
                                android.util.Log.d("VescDebug", "Host $h is reachable (Net: $netType)")
                                
                                if (h == "192.168.9.1" && net == null && bound != null) {
                                    delay(300.milliseconds)
                                }
                                channel.send(h)
                            } else {
                                channel.send(null)
                            }
                        } catch (_: Exception) {
                            channel.send(null)
                        }
                    }
                }
            }

            var received = 0
            val totalExpected = hosts.size * nets.size
            var found: String? = null
            
            while (received < totalExpected) {
                val res = channel.receive()
                received++
                if (res != null) {
                    found = res
                    break 
                }
            }
            coroutineContext.cancelChildren()
            android.util.Log.d("VescDebug", "resolveHost returns: $found")
            found
        }
        result
    }

    private fun tryApConnect(dev: Device) {
        _state.value = ConnState.ConnectingAp
        apConnecting = true
        wifi.connectToAp(dev.apSsid, dev.apPassword) { ok ->
            viewModelScope.launch {
                try {
                    if (ok) {
                        // Nach dem Verbinden mit dem AP der Bridge kurz warten und ggf.
                        // mehrfach versuchen, die Bridge zu erreichen. Der Webserver
                        // auf dem ESP braucht manchmal 1-2 Sek nach WLAN-Connect.
                        var reachable: String? = null
                        for (attempt in 0..3) {
                            reachable = resolveHost(dev)
                            if (reachable != null) break
                            if (attempt < 3) delay(1000.milliseconds)
                        }

                        if (reachable != null) {
                            android.util.Log.d("VescDebug", "AP connect: host found $reachable")
                            _activeHost.value = reachable
                            consecutiveFails = 0
                            _state.value = ConnState.Online
                        } else {
                            // AP verbunden, aber Bridge auch nach Retries nicht erreichbar
                            wifi.release()
                            _state.value = ConnState.Offline
                            delay(3000.milliseconds) // Etwas längere Pause nach Fehlschlag
                        }
                    } else {
                        _state.value = ConnState.Offline
                        delay(4000.milliseconds) // Pause, falls Android die Anfrage abgelehnt hat (z.B. Hintergrund)
                    }
                } finally {
                    apConnecting = false
                }
            }
        }
    }

    fun refreshNow() {
        if (isRebooting) return
        viewModelScope.launch {
            apTried = false
            val a = api() ?: run {
                _activeHost.value = null
                restartPolling()
                return@launch
            }
            try { _info.value = a.fetchInfo(); _state.value = ConnState.Online }
            catch (_: Exception) { _activeHost.value = null }
        }
    }

    fun disconnectAp() {
        wifi.release()
        _activeHost.value = null
        apTried = false
        restartPolling()
    }

    fun addDevice(
        name: String,
        hosts: List<String>,
        apSsid: String,
        apPassword: String,
        apOnly: Boolean,
        autoConnect: Boolean,
        homeTimeout: Int = 2000,
        apTimeout: Int = 5000,
        syncName: Boolean = true
    ) {
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            val newDev = Device(
                id, name.trim().ifBlank { getApplication<Application>().getString(R.string.bridge_default_name) },
                hosts.map { it.trim() }.filter { it.isNotEmpty() },
                apSsid.trim(), apPassword, apOnly, autoConnect, homeTimeout, apTimeout, syncName
            )
            store.addOrUpdate(newDev)
        }
    }

    fun updateDevice(d: Device) { viewModelScope.launch { store.addOrUpdate(d) } }

    fun toggleAutoConnect(device: Device) {
        viewModelScope.launch {
            store.toggleAutoConnect(device.id)
        }
    }

    fun deleteDevice(id: String) { viewModelScope.launch { store.delete(id) } }
    fun selectDevice(id: String) {
        viewModelScope.launch { store.select(id) }
    }

    fun factoryReset(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val dev = _selected.value
            val ok = api()?.post("/api/factory-reset") ?: false
            if (ok) {
                if (dev != null) {
                    val updatedDev = dev.copy(
                        hosts = emptyList(),
                        apSsid = "VESC-BLE-WiFi",
                        apPassword = ""
                    )
                    store.addOrUpdate(updatedDev)
                    _selected.value = updatedDev
                }
                prepareForReboot()
                startRebootCountdown(10000L)
            }
            onResult(ok)
        }
    }

    fun restart(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = api()?.restart() ?: false
            if (ok) {
                prepareForReboot()
                startRebootCountdown(10000L)
            }
            onResult(ok)
        }
    }

    private fun prepareForReboot() {
        _activeHost.value = null
        _state.value = ConnState.Offline
        isRebooting = true
        apTried = false
        searchCycles = 0
        pollJob?.cancel()
    }

    private fun startRebootCountdown(durationMs: Long) {
        _state.value = ConnState.Rebooting
        viewModelScope.launch {
            val steps = 100
            val stepDelay = durationMs / steps

            for (i in 0..steps) {
                _rebootProgress.value = i / 100f
                delay(stepDelay.milliseconds)
            }

            _rebootProgress.value = null
            isRebooting = false
            restartPolling()
        }
    }

    private val _config = MutableStateFlow<BridgeConfig?>(null)
    val config: StateFlow<BridgeConfig?> = _config.asStateFlow()

    private val _configBusy = MutableStateFlow(false)
    val configBusy: StateFlow<Boolean> = _configBusy.asStateFlow()

    private val _showWifiPanel = MutableSharedFlow<Unit>(replay = 0)
    val showWifiPanel: SharedFlow<Unit> = _showWifiPanel.asSharedFlow()

    fun getNearbySsids(): List<String> = wifi.getNearbySsids()

    private val _ledThrottler = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    fun ledPostThrottled(path: String, optimistic: ((LedConfig) -> LedConfig)? = null) {
        val current = _ledConfig.value
        if (optimistic != null && current != null) {
            _ledConfig.value = optimistic(current)
        }
        viewModelScope.launch {
            _ledThrottler.emit(path)
        }
    }

    fun loadConfig() {
        viewModelScope.launch {
            val a = api() ?: return@launch
            _configBusy.value = true
            try {
                val cfg = BridgeConfig.parse(a.fetchConfig())
                _config.value = cfg
            }
            catch (_: Exception) { _config.value = null }
            finally { _configBusy.value = false }
        }
    }

    suspend fun scanBridgeWifi(): List<String> {
        return api()?.scanWifi() ?: emptyList()
    }

    private fun loadConfigSyncToDevice(dev: Device) {
        viewModelScope.launch {
            val a = api() ?: return@launch
            _configBusy.value = true
            try {
                val cfg = BridgeConfig.parse(a.fetchConfig())
                _config.value = cfg

                val staticIps = cfg.wifi.filter { it.static && it.ip.isNotBlank() }.map { it.ip }
                val newHosts = (dev.hosts + staticIps).distinct().filter { it.isNotBlank() }

                val needsUpdate = newHosts.size != dev.hosts.size ||
                        cfg.apSsid != dev.apSsid ||
                        cfg.apPass != dev.apPassword

                if (needsUpdate) {
                    store.addOrUpdate(dev.copy(
                        hosts = newHosts,
                        apSsid = cfg.apSsid,
                        apPassword = cfg.apPass
                    ))
                }
            } catch (_: Exception) {
            } finally {
                _configBusy.value = false
            }
        }
    }

    fun saveConfig(cfg: BridgeConfig, reboot: Boolean, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val a = api()
            val dev = _selected.value
            if (a == null || dev == null) { onResult(false); return@launch }
            _configBusy.value = true
            val ok = try { a.postConfig(cfg.toJson(reboot)) } catch (_: Exception) { false }

            if (ok) {
                val staticIps = cfg.wifi.filter { it.static && it.ip.isNotBlank() }.map { it.ip }
                val newHosts = (dev.hosts + staticIps).distinct().filter { it.isNotBlank() }

                val updatedDev = dev.copy(
                    apSsid = cfg.apSsid,
                    apPassword = cfg.apPass,
                    hosts = newHosts
                )

                _selected.value = updatedDev
                store.addOrUpdate(updatedDev)
                prepareForReboot()

                if (reboot) {
                    startRebootCountdown(10000L)
                } else {
                    isRebooting = false
                    restartPolling()
                }
            }

            _config.value = cfg
            _configBusy.value = false
            onResult(ok)
        }
    }

    fun loadLedConfig() {
        viewModelScope.launch {
            val a = api() ?: return@launch
            try { _ledConfig.value = LedConfig.parse(a.fetchLedConfig()) }
            catch (_: Exception) { _ledConfig.value = null }
        }
    }

    fun ledPost(path: String, reload: Boolean = false, optimistic: ((LedConfig) -> LedConfig)? = null) {
        val current = _ledConfig.value
        if (optimistic != null && current != null) {
            _ledConfig.value = optimistic(current)
        }
        viewModelScope.launch {
            val ok = api()?.post(path) ?: false
            if (reload || !ok || optimistic == null) {
                loadLedConfig()
            }
        }
    }

    fun loadUpdateStatus() {
        viewModelScope.launch {
            val a = api() ?: return@launch
            _updateBusy.value = true
            _updateStatus.value = a.fetchUpdateStatus()
            _updateBusy.value = false
        }
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            val a = api() ?: return@launch
            _updateBusy.value = true
            _updateStatus.value = a.checkUpdate()
            _updateBusy.value = false
        }
    }

    fun triggerUpdate(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val a = api() ?: run { onResult(false); return@launch }
            _updateBusy.value = true
            val ok = try { a.installUpdate() } catch (_: Exception) { false }
            if (ok) {
                _updateStatus.value = null
                prepareForReboot()
                startRebootCountdown(15000L)
                _updateBusy.value = false
            } else {
                _updateBusy.value = false
            }
            onResult(ok)
        }
    }

    override fun onCleared() {
        if (wakeLock.isHeld) try { wakeLock.release() } catch (_: Exception) {}
        slowSyncJob?.cancel()
        wifi.release()
    }
}