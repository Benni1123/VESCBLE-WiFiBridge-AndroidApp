package eu.benni1123.vescbridge

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

// Editierbarer WLAN-Eintrag (lokaler Zustand im Editor).
private class WifiEdit(
    ssid: String = "", pass: String = "", static: Boolean = false,
    ip: String = "", gateway: String = "", subnet: String = "", dns: String = ""
) {
    var ssid by mutableStateOf(ssid)
    var pass by mutableStateOf(pass)
    var static by mutableStateOf(static)
    var ip by mutableStateOf(ip)
    var gateway by mutableStateOf(gateway)
    var subnet by mutableStateOf(subnet)
    var dns by mutableStateOf(dns)
    fun toWifiNet() = WifiNet(ssid, pass, static, ip, gateway, subnet, dns)
}

@Composable
fun ConfigScreen(vm: MainViewModel) {
    val cfg      by vm.config.collectAsStateWithLifecycle()
    val busy     by vm.configBusy.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val state    by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(selected?.id, state) {
        if (state == ConnState.Online && cfg == null) vm.loadConfig()
    }

    if (selected == null) { EmptyHint("Kein Ger\u00e4t ausgew\u00e4hlt."); return }
    val config = cfg
    if (config == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (busy) CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text(if (busy) "Lade Konfiguration \u2026" else "Keine Konfiguration geladen",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { vm.loadConfig() }) { Text("Laden") }
            }
        }
        return
    }

    ConfigEditor(config, busy, vm)
}

@Composable
private fun ConfigEditor(config: BridgeConfig, busy: Boolean, vm: MainViewModel) {
    val progress by vm.rebootProgress.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Schluessel sorgt dafuer, dass beim Neuladen die Felder neu initialisiert werden.
    val k = config

    var bleName by remember(k) { mutableStateOf(config.bleName) }
    var bleMode by remember(k) { mutableStateOf(config.bleMode) }
    var bleErpmOn by remember(k) { mutableStateOf(config.bleAutoErpmOn.toString()) }
    var bleOffSec by remember(k) { mutableStateOf(config.bleAutoOffSec.toString()) }
    var apSsid  by remember(k) { mutableStateOf(config.apSsid) }
    var apPass  by remember(k) { mutableStateOf(config.apPass) }
    var apTimeout by remember(k) { mutableStateOf(config.apTimeout.toString()) }
    var apWake  by remember(k) { mutableStateOf(config.apWakeOnMove) }
    var port    by remember(k) { mutableStateOf(config.port.toString()) }
    var rxPin   by remember(k) { mutableStateOf(config.rxPin.toString()) }
    var txPin   by remember(k) { mutableStateOf(config.txPin.toString()) }
    var vescPoll by remember(k) { mutableStateOf(config.vescPoll) }
    var autopollEn by remember(k) { mutableStateOf(config.autopollEnabled) }
    var autopollInt by remember(k) { mutableStateOf(config.autopollInterval.toString()) }
    var autoreboot by remember(k) { mutableStateOf(config.autoreboot) }
    var autorebootTime by remember(k) { mutableStateOf(config.autorebootTime.toString()) }
    var autorebootNoWifi by remember(k) { mutableStateOf(config.autorebootNoWifi) }
    var roamEn  by remember(k) { mutableStateOf(config.roamEnabled) }
    var roamThr by remember(k) { mutableStateOf(config.roamThreshold.toString()) }
    var roamHyst by remember(k) { mutableStateOf(config.roamHysteresis.toString()) }
    var ledsEn  by remember(k) { mutableStateOf(config.ledsEnabled) }
    var updateUrl by remember(k) { mutableStateOf(config.updateUrl) }
    var versionUrl by remember(k) { mutableStateOf(config.versionUrl) }

    val wifiList = remember(k) {
        mutableStateListOf<WifiEdit>().apply {
            config.wifi.forEach { w ->
                add(WifiEdit(w.ssid, w.pass, w.static, w.ip, w.gateway, w.subnet, w.dns))
            }
        }
    }

    fun i(s: String, d: Int) = s.toIntOrNull() ?: d

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {

            CfgSection("BLE") {
                CfgText("BLE-Name", bleName) { bleName = it }
                CfgDropdown("BLE-Modus", bleMode,
                    listOf("0 = Aus", "1 = Immer an", "2 = Auto")) { bleMode = it }
                CfgNumber("Auto-AN ab ERPM", bleErpmOn) { bleErpmOn = it }
                CfgNumber("Auto-AUS nach (s)", bleOffSec) { bleOffSec = it }
            }
            CfgSection("Access Point") {
                CfgText("AP-SSID", apSsid) { apSsid = it }
                CfgPassword("AP-Passwort", apPass) { apPass = it }
                CfgNumber("AP-Timeout (s, 0=aus)", apTimeout) { apTimeout = it }
                CfgSwitch("AP bei Bewegung wecken", apWake) { apWake = it }
            }
            CfgSection("VESC / UART") {
                CfgNumber("TCP-Port", port) { port = it }
                CfgNumber("RX-Pin", rxPin) { rxPin = it }
                CfgNumber("TX-Pin", txPin) { txPin = it }
                CfgSwitch("VESC-Polling", vescPoll) { vescPoll = it }
                CfgSwitch("Auto-Polling", autopollEn) { autopollEn = it }
                CfgNumber("Poll-Intervall (s)", autopollInt) { autopollInt = it }
            }
            CfgSection("Auto-Reboot") {
                CfgSwitch("Auto-Reboot aktiv", autoreboot) { autoreboot = it }
                CfgNumber("Reboot nach (s)", autorebootTime) { autorebootTime = it }
                CfgSwitch("Auch wenn im WLAN", autorebootNoWifi) { autorebootNoWifi = it }
            }
            CfgSection("Roaming") {
                CfgSwitch("Roaming aktiv", roamEn) { roamEn = it }
                CfgNumber("Schwelle (dBm)", roamThr) { roamThr = it }
                CfgNumber("Hysterese (dB)", roamHyst) { roamHyst = it }
            }
            CfgSection("LED") {
                CfgSwitch("WS28XX-Steuerung aktiv", ledsEn) { ledsEn = it }
            }
            CfgSection("Update-URLs") {
                CfgText("Firmware-URL", updateUrl) { updateUrl = it }
                CfgText("Versions-URL", versionUrl) { versionUrl = it }
            }
            CfgSection("WLAN-Netze") {
                wifiList.forEachIndexed { idx, net ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Netz ${idx + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp, modifier = Modifier.weight(1f))
                                IconButton(onClick = { wifiList.removeAt(idx) }) {
                                    Icon(Icons.Filled.Close, "Entfernen", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            
                            var showScan by remember { mutableStateOf(false) }
                            var scanning by remember { mutableStateOf(false) }
                            val scope = rememberCoroutineScope()
                            val nearby = remember { mutableStateListOf<String>() }

                            Box(Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = net.ssid,
                                    onValueChange = { net.ssid = it },
                                    label = { Text("SSID") },
                                    singleLine = true,
                                    trailingIcon = {
                                        if (scanning) {
                                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                        } else {
                                            IconButton(onClick = {
                                                scope.launch {
                                                    scanning = true
                                                    nearby.clear()
                                                    val results = vm.scanBridgeWifi()
                                                    nearby.addAll(results)
                                                    scanning = false
                                                    showScan = true
                                                }
                                            }) {
                                                Icon(Icons.Filled.Wifi, "Scannen")
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                )
                                if (showScan && !scanning) {
                                    DropdownMenu(
                                        expanded = showScan && nearby.isNotEmpty(),
                                        onDismissRequest = { showScan = false },
                                        modifier = Modifier.fillMaxWidth(0.8f)
                                    ) {
                                        nearby.forEach { ssid ->
                                            DropdownMenuItem(
                                                text = { Text(ssid) },
                                                onClick = {
                                                    net.ssid = ssid
                                                    showScan = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            CfgPassword("Passwort", net.pass) { net.pass = it }
                            CfgSwitch("Statische IP", net.static) { net.static = it }
                            if (net.static) {
                                CfgText("IP", net.ip) {
                                    net.ip = it
                                    val parts = it.split(".")
                                    if (parts.size == 4 && parts[3].isNotEmpty()) {
                                        val base = "${parts[0]}.${parts[1]}.${parts[2]}"
                                        if (net.gateway.isBlank()) net.gateway = "$base.1"
                                        if (net.dns.isBlank()) net.dns = "$base.1"
                                        if (net.subnet.isBlank()) net.subnet = "255.255.255.0"
                                    }
                                }
                                CfgText("Gateway", net.gateway) { net.gateway = it }
                                CfgText("Subnetz", net.subnet) { net.subnet = it }
                                CfgText("DNS", net.dns) { net.dns = it }
                            }
                        }
                    }
                }
                TextButton(onClick = { wifiList.add(WifiEdit()) }) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("WLAN hinzuf\u00fcgen")
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val newCfg = BridgeConfig(
                        bleName = bleName, apSsid = apSsid, apPass = apPass,
                        port = i(port, 65101), vescPoll = vescPoll, apTimeout = i(apTimeout, 0),
                        rxPin = i(rxPin, 0), txPin = i(txPin, 0),
                        autoreboot = autoreboot, autorebootTime = i(autorebootTime, 300),
                        autorebootNoWifi = autorebootNoWifi,
                        roamEnabled = roamEn, roamThreshold = i(roamThr, -75),
                        roamHysteresis = i(roamHyst, 12),
                        autopollEnabled = autopollEn, autopollInterval = i(autopollInt, 5),
                        bleMode = bleMode, bleAutoErpmOn = i(bleErpmOn, 200),
                        apWakeOnMove = apWake, bleAutoOffSec = i(bleOffSec, 120),
                        ledsEnabled = ledsEn, updateUrl = updateUrl, versionUrl = versionUrl,
                        wifi = wifiList.filter { it.ssid.isNotBlank() }.map { it.toWifiNet() }
                    )
                    vm.saveConfig(newCfg, reboot = true) { ok ->
                        if (ok) vm.refreshNow() // Sofort nach Sync suchen
                        scope.launch {
                            snackbar.showSnackbar(
                                if (ok) "Gespeichert \u2013 Bridge startet neu" else "Speichern fehlgeschlagen"
                            )
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Speichern")
            }
            Spacer(Modifier.height(6.dp))
            Text("Nach dem Speichern startet die Bridge neu. AP-SSID/Passwort werden " +
                    "auch im App-Ger\u00e4t aktualisiert.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Spacer(Modifier.height(40.dp))
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun CfgSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun CfgText(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
}

@Composable
private fun CfgNumber(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, { onChange(it.filter { c -> c.isDigit() || c == '-' }) },
        label = { Text(label) }, singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
}

@Composable
private fun CfgPassword(label: String, value: String, onChange: (String) -> Unit) {
    var show by remember { mutableStateOf(false) }
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true,
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = { show = !show }) {
                Text(if (show) "verbergen" else "zeigen", fontSize = 11.sp)
            }
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
}

@Composable
private fun CfgSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f), fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun CfgDropdown(label: String, value: Int, options: List<String>, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(options.getOrElse(value) { value.toString() }, color = MaterialTheme.colorScheme.onSurface)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { i, opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { onChange(i); expanded = false })
            }
        }
    }
}
