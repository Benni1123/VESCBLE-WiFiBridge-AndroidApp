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
import androidx.compose.ui.res.stringResource

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

    if (selected == null) { EmptyHint(stringResource(R.string.no_device_selected)); return }
    val config = cfg
    if (config == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (busy) CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text(if (busy) stringResource(R.string.loading_config) else stringResource(R.string.no_config_loaded),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { vm.loadConfig() }) { Text(stringResource(R.string.load)) }
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
    var apMode  by remember(k) { mutableStateOf(config.apMode) }
    var apTimeout by remember(k) { mutableStateOf(config.apTimeout.toString()) }
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

    val savedRestarting = stringResource(R.string.saved_restarting)
    val saveFailed = stringResource(R.string.save_failed)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {

            CfgSection(stringResource(R.string.ble_section)) {
                CfgText(stringResource(R.string.ble_name_config), bleName) { bleName = it }
                CfgDropdown(stringResource(R.string.ble_mode), bleMode,
                    listOf(stringResource(R.string.ble_off), stringResource(R.string.ble_on), stringResource(R.string.ble_auto))) { bleMode = it }
                CfgNumber(stringResource(R.string.ble_auto_on_erpm), bleErpmOn) { bleErpmOn = it }
                CfgNumber(stringResource(R.string.ble_auto_off_sec_label), bleOffSec) { bleOffSec = it }
            }
            CfgSection(stringResource(R.string.access_point_section)) {
                CfgText(stringResource(R.string.ap_ssid_label), apSsid) { apSsid = it }
                CfgPassword(stringResource(R.string.ap_password_label), apPass) { apPass = it }
                CfgDropdown(stringResource(R.string.mode), apMode,
                    listOf("", stringResource(R.string.ap_on), stringResource(R.string.ap_auto)),
                    startIndex = 1) { apMode = it }
                if (apMode == 2) {
                    CfgNumber(stringResource(R.string.ap_timeout_config), apTimeout) { apTimeout = it }
                }
            }
            CfgSection(stringResource(R.string.vesc_uart_section)) {
                CfgNumber(stringResource(R.string.tcp_port), port) { port = it }
                CfgNumber(stringResource(R.string.rx_pin_label), rxPin) { rxPin = it }
                CfgNumber(stringResource(R.string.tx_pin_label), txPin) { txPin = it }
                CfgSwitch(stringResource(R.string.vesc_polling), vescPoll) { vescPoll = it }
                CfgSwitch(stringResource(R.string.auto_polling), autopollEn) { autopollEn = it }
                CfgNumber(stringResource(R.string.poll_interval_label), autopollInt) { autopollInt = it }
            }
            CfgSection(stringResource(R.string.auto_reboot_section)) {
                CfgSwitch(stringResource(R.string.auto_reboot_active), autoreboot) { autoreboot = it }
                CfgNumber(stringResource(R.string.reboot_after_s), autorebootTime) { autorebootTime = it }
                CfgSwitch(stringResource(R.string.even_on_wifi), autorebootNoWifi) { autorebootNoWifi = it }
            }
            CfgSection(stringResource(R.string.roaming_section)) {
                CfgSwitch(stringResource(R.string.roaming_active), roamEn) { roamEn = it }
                CfgNumber(stringResource(R.string.threshold_dbm), roamThr) { roamThr = it }
                CfgNumber(stringResource(R.string.hysteresis_db), roamHyst) { roamHyst = it }
            }
            CfgSection(stringResource(R.string.led_section)) {
                CfgSwitch(stringResource(R.string.led_control_active), ledsEn) { ledsEn = it }
            }
            CfgSection(stringResource(R.string.update_urls_section)) {
                CfgText(stringResource(R.string.firmware_url), updateUrl) { updateUrl = it }
                CfgText(stringResource(R.string.version_url), versionUrl) { versionUrl = it }
            }
            CfgSection(stringResource(R.string.wifi_networks_section)) {
                wifiList.forEachIndexed { idx, net ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.network_num, idx + 1), color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp, modifier = Modifier.weight(1f))
                                IconButton(onClick = { wifiList.removeAt(idx) }) {
                                    Icon(Icons.Filled.Close, stringResource(R.string.remove), tint = MaterialTheme.colorScheme.error)
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
                                    label = { Text(stringResource(R.string.ssid)) },
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
                                                Icon(Icons.Filled.Wifi, stringResource(R.string.scan))
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

                            CfgPassword(stringResource(R.string.password), net.pass) { net.pass = it }
                            CfgSwitch(stringResource(R.string.static_ip), net.static) { net.static = it }
                            if (net.static) {
                                CfgText(stringResource(R.string.ip), net.ip) {
                                    net.ip = it
                                    val parts = it.split(".")
                                    if (parts.size == 4 && parts[3].isNotEmpty()) {
                                        val base = "${parts[0]}.${parts[1]}.${parts[2]}"
                                        if (net.gateway.isBlank()) net.gateway = "$base.1"
                                        if (net.dns.isBlank()) net.dns = "$base.1"
                                        if (net.subnet.isBlank()) net.subnet = "255.255.255.0"
                                    }
                                }
                                CfgText(stringResource(R.string.gateway), net.gateway) { net.gateway = it }
                                CfgText(stringResource(R.string.subnet), net.subnet) { net.subnet = it }
                                CfgText(stringResource(R.string.dns), net.dns) { net.dns = it }
                            }
                        }
                    }
                }
                TextButton(onClick = { wifiList.add(WifiEdit()) }) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.add_wifi))
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val newCfg = BridgeConfig(
                        bleName = bleName, apSsid = apSsid, apPass = apPass,
                        apMode = apMode,
                        port = i(port, 65101), vescPoll = vescPoll, apTimeout = i(apTimeout, 0),
                        rxPin = i(rxPin, 0), txPin = i(txPin, 0),
                        autoreboot = autoreboot, autorebootTime = i(autorebootTime, 300),
                        autorebootNoWifi = autorebootNoWifi,
                        roamEnabled = roamEn, roamThreshold = i(roamThr, -75),
                        roamHysteresis = i(roamHyst, 12),
                        autopollEnabled = autopollEn, autopollInterval = i(autopollInt, 5),
                        bleMode = bleMode, bleAutoErpmOn = i(bleErpmOn, 200),
                        bleAutoOffSec = i(bleOffSec, 120),
                        ledsEnabled = ledsEn, updateUrl = updateUrl, versionUrl = versionUrl,
                        wifi = wifiList.filter { it.ssid.isNotBlank() }.map { it.toWifiNet() }
                    )
                    vm.saveConfig(newCfg, reboot = true) { ok ->
                        if (ok) vm.refreshNow() // Sofort nach Sync suchen
                        scope.launch {
                            snackbar.showSnackbar(
                                if (ok) savedRestarting else saveFailed
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
                Text(stringResource(R.string.save))
            }
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.save_reboot_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
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
                Text(if (show) stringResource(R.string.hide) else stringResource(R.string.show), fontSize = 11.sp)
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
private fun CfgDropdown(label: String, value: Int, options: List<String>, startIndex: Int = 0, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(options.getOrElse(value) { value.toString() }, color = MaterialTheme.colorScheme.onSurface)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { i, opt ->
                if (i >= startIndex && opt.isNotEmpty()) {
                    DropdownMenuItem(text = { Text(opt) }, onClick = { onChange(i); expanded = false })
                }
            }
        }
    }
}
