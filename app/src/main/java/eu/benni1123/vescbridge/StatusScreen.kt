package eu.benni1123.vescbridge

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalConfiguration

@Composable
fun StatusScreen(vm: MainViewModel) {
    val locale     = LocalConfiguration.current.locales[0]
    val info       by vm.info.collectAsStateWithLifecycle()
    val connState  by vm.state.collectAsStateWithLifecycle()
    val activeHost by vm.activeHost.collectAsStateWithLifecycle()
    val selected   by vm.selected.collectAsStateWithLifecycle()
    val update     by vm.updateStatus.collectAsStateWithLifecycle()
    val updateBusy by vm.updateBusy.collectAsStateWithLifecycle()
    val appUpdate  by vm.appUpdateInfo.collectAsStateWithLifecycle()
    val appBusy    by vm.appUpdateBusy.collectAsStateWithLifecycle()
    val appProgress by vm.appUpdateProgress.collectAsStateWithLifecycle()
    val progress   by vm.rebootProgress.collectAsStateWithLifecycle()

    var showRestartConfirm by remember { mutableStateOf(false) }
    var showUpdateConfirm by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(connState) {
        if (connState == ConnState.Online) vm.loadUpdateStatus()
    }

    val currentSelected = selected
    if (currentSelected == null) {
        EmptyHint("Kein Gerät ausgewählt.\nÜber „Geräte“ eins hinzufügen.")
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        if (connState != ConnState.Online && info == null && connState != ConnState.Rebooting) {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (connState == ConnState.Searching || connState == ConnState.ConnectingAp) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                        }
                        val msg = when (connState) {
                            ConnState.Searching     -> "Suche erreichbare IP \u2026"
                            ConnState.ConnectingAp  -> "Verbinde mit AP ${currentSelected.apSsid} \u2026"
                            ConnState.Offline       -> "Nicht erreichbar. Pr\u00fcfe IPs / WLAN."
                            else -> ""
                        }
                        Text(msg, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (connState == ConnState.ConnectingAp) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Tipp: Da der ESP manchmal tr\u00e4ge beim DHCP ist, kann die Verbindung fehlschlagen und ein App Neustart w\u00e4re ratsam.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            lineHeight = 14.sp
                        )
                    }
                    if (connState == ConnState.Offline) {
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { vm.refreshNow() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Refresh, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Erneut versuchen")
                        }
                    }
                }
            }
        }

        info?.let { d ->
            SectionCard("Verbindung") {
                val onlineNow = connState == ConnState.Online
                InfoRow("Status", if (onlineNow) "Online" else "Offline",
                    if (onlineNow) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error)
                
                activeHost?.let { 
                    val label = if (it == d.apIp) "Aktive Verbindung (AP)" else "Aktive Verbindung (WLAN)"
                    InfoRow(label, it, MaterialTheme.colorScheme.primary) 
                }

                InfoRow("Modus", if (d.mode == "ap") "Access Point" else "WLAN")
                
                if (d.ssid.isNotEmpty()) {
                    InfoRow("SSID", d.ssid)
                    InfoRow("Signalstärke (RSSI)", "${d.rssi} dBm")
                }

                // WLAN-IP nur zeigen, wenn sie NICHT die aktive Verbindung ist
                if (d.ip.isNotEmpty() && d.ip != "0.0.0.0" && d.ip != activeHost) {
                    InfoRow("WLAN-IP (Gerät)", d.ip)
                }
                
                // AP-IP nur zeigen, wenn sie NICHT die aktive Verbindung ist
                if (d.apIp.isNotEmpty() && d.apIp != activeHost) {
                    InfoRow("AP-IP", d.apIp)
                }

                if (d.apClientIp.isNotEmpty()) {
                    InfoRow("Verbundenes Gerät", d.apClientIp, Color(0xFF4CAF50))
                }

                if (d.apActive) {
                    val timeoutStr = if (d.apTimeoutRemaining > 0) "${d.apTimeoutRemaining}s" else "Unendlich"
                    InfoRow("AP Sichtbar", "Ja (Timeout: $timeoutStr)", Color(0xFF4CAF50))
                }
            }

            SectionCard("BLE (VESC-Verbindung)") {
                InfoRow("BLE verbunden", if (d.bleConnected) "Ja" else "Nein",
                    if (d.bleConnected) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error)
                if (d.bleName.isNotEmpty()) InfoRow("BLE-Name", d.bleName)
                if (d.bleMac.isNotEmpty()) InfoRow("BLE-MAC", d.bleMac)
            }

            SectionCard("VESC-Telemetrie") {
                InfoRow("VESC verbunden", if (d.vescConnected) "Ja" else "Nein",
                    if (d.vescConnected) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error)
                InfoRow("Spannung", "%.1f V".format(locale, d.vescVoltage))
                InfoRow("ERPM", d.vescErpm.toString())
                InfoRow("Temp FET", "%.0f \u00b0C".format(locale, d.vescTempFet))
                InfoRow("Temp Motor", "%.0f \u00b0C".format(locale, d.vescTempMotor))
                if (d.vescFaultStr.isNotEmpty() && d.vescFaultStr != "FAULT_CODE_NONE")
                    InfoRow("Fehler", d.vescFaultStr, MaterialTheme.colorScheme.error)
                if (d.vescFault > 0)
                    InfoRow("Fehler-ID", d.vescFault.toString(), MaterialTheme.colorScheme.error)
            }

            SectionCard("Firmware-Update") {
                val u = update
                if (u == null) {
                    Text("Lade Update-Status...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                } else {
                    InfoRow("Version (aktuell)", u.current)
                    if (u.serverError) {
                        Text("Update-Server nicht erreichbar", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    } else if (u.latest.isNotEmpty()) {
                        InfoRow("Version (neu)", u.latest)
                        if (u.available || (u.latest.isNotEmpty() && u.current != u.latest)) {
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { showUpdateConfirm = true },
                                enabled = !updateBusy && progress == null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (updateBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Text("Update jetzt installieren")
                            }
                        } else {
                            Text("Deine Firmware ist aktuell.", color = Color(0xFF4CAF50), fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = { vm.checkForUpdate() },
                        enabled = !updateBusy && progress == null,
                        modifier = Modifier.align(Alignment.End)
                    ) { Text("Nach Updates suchen", fontSize = 12.sp) }
                }
            }

            SectionCard("App-Update") {
                val au = appUpdate
                if (au != null) {
                    InfoRow("Neue Version verfügbar", "Build ${au.latestVersionCode}")
                    Spacer(Modifier.height(8.dp))
                    if (appProgress != null) {
                        Column {
                            LinearProgressIndicator(
                                progress = { appProgress!! },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("%.0f%%".format(locale, appProgress!! * 100), fontSize = 11.sp)
                        }
                    } else {
                        Button(
                            onClick = { vm.downloadAppUpdate() },
                            enabled = !appBusy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (appBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text("Update herunterladen & installieren")
                        }
                    }
                } else {
                    if (appBusy) {
                        Text("Suche nach App-Updates...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    } else {
                        Text("Deine App ist aktuell.", color = Color(0xFF4CAF50), fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = { vm.checkAppUpdate() },
                        enabled = !appBusy,
                        modifier = Modifier.align(Alignment.End)
                    ) { Text("Nach App-Updates suchen", fontSize = 12.sp) }
                }
            }

            SectionCard("System") {
                InfoRow("Uptime", d.uptime)
                InfoRow("Freier Heap", "${d.heap / 1024} kB")
                if (d.build.isNotEmpty()) InfoRow("Build", d.build)
                if (d.port != -1) InfoRow("TCP-Port", d.port.toString())
                if (d.rxPin != -1) InfoRow("UART RX", "Pin ${d.rxPin}")
                if (d.txPin != -1) InfoRow("UART TX", "Pin ${d.txPin}")
            }

            SectionCard("Diagnose (ESP32)") {
                InfoRow("WLAN Scans", d.diagScans.toString())
                InfoRow("STA Verbindungen", d.diagStaConn.toString())
                InfoRow("STA Trennungen", d.diagStaDisc.toString())
                if (d.diagStaDisc > 0) InfoRow("Letzter Grund (STA)", d.diagDiscReason.toString())
                InfoRow("AP Verbindungen", d.diagApConn.toString())
                InfoRow("AP Trennungen", d.diagApDisc.toString())
                InfoRow("Probe Requests", d.diagProbeReqs.toString())
                if (d.diagProbeReqs > 0) InfoRow("Probe RSSI (Avg)", "${d.diagProbeRssi} dBm")
                InfoRow("Watchdog Resets", d.diagWdFires.toString())
                InfoRow("Max Loop Zeit", "${d.diagLoopMaxUs} \u00b5s")
                InfoRow("Loops pro Sek.", d.diagLoopsPerSec.toString())
                InfoRow("Min. freier Heap", "${d.diagMinHeap / 1024} kB")
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { vm.refreshNow() },
                    modifier = Modifier.weight(1f)
                ) { Text("Aktualisieren") }
                Button(
                    onClick = { showRestartConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                    modifier = Modifier.weight(1f)
                ) { Text("Neustart") }
            }
            // Wenn die genutzte IP die AP-IP ist, Trennen-Option anbieten.
            if (activeHost == "192.168.9.1") {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { vm.disconnectAp() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Vom AP trennen") }
            }
        }
    }

    if (showRestartConfirm) {
        AlertDialog(
            onDismissRequest = { showRestartConfirm = false },
            title = { Text("Bridge neu starten?") },
            text = { Text("Die Bridge wird neu gestartet. Die Verbindung bricht kurz ab.") },
            confirmButton = {
                TextButton(onClick = {
                    showRestartConfirm = false
                    vm.restart { ok ->
                        scope.launch {
                            snackbar.showSnackbar(if (ok) "Neustart ausgel\u00f6st" else "Fehlgeschlagen")
                        }
                    }
                }) { Text("Neustart", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showRestartConfirm = false }) { Text("Abbrechen") }
            }
        )
    }

    if (showUpdateConfirm) {
        AlertDialog(
            onDismissRequest = { showUpdateConfirm = false },
            title = { Text("Firmware-Update installieren?") },
            text = { Text("Die Bridge l\u00e4dt die neue Firmware herunter und installiert sie. Dies dauert ca. 30-60 Sekunden.") },
            confirmButton = {
                TextButton(onClick = {
                    showUpdateConfirm = false
                    vm.triggerUpdate { ok ->
                        scope.launch {
                            snackbar.showSnackbar(if (ok) "Update gestartet - Bridge startet gleich neu" else "Update fehlgeschlagen")
                        }
                    }
                }) { Text("Installieren") }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateConfirm = false }) { Text("Abbrechen") }
            }
        )
    }

    SnackbarHost(snackbar)
}

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, valueColor: Color? = null) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(value, color = valueColor ?: MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
