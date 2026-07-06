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
import androidx.compose.ui.res.stringResource

@Composable
fun StatusScreen(vm: MainViewModel) {
    val locale     = LocalConfiguration.current.locales[0]
    val info       by vm.info.collectAsStateWithLifecycle()
    val connState  by vm.state.collectAsStateWithLifecycle()
    val activeHost by vm.activeHost.collectAsStateWithLifecycle()
    val selected   by vm.selected.collectAsStateWithLifecycle()

    var showRestartConfirm by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val restartTriggered = stringResource(R.string.restart_triggered)
    val failed = stringResource(R.string.failed)

    val currentSelected = selected
    if (currentSelected == null) {
        EmptyHint(stringResource(R.string.no_device_selected))
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
                            ConnState.Searching     -> stringResource(R.string.searching_ip)
                            ConnState.ConnectingAp  -> stringResource(R.string.connecting_ap, currentSelected.apSsid)
                            ConnState.Offline       -> stringResource(R.string.not_reachable)
                            else -> ""
                        }
                        Text(msg, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (connState == ConnState.ConnectingAp) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.dhcp_tip),
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
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }
        }

        info?.let { d ->
            SectionCard(stringResource(R.string.status)) {
                InfoRow(stringResource(R.string.ble_name_label), d.bleName)
                InfoRow(stringResource(R.string.ble_mac), d.bleMac)
                InfoRow(stringResource(R.string.ble_connected), if (d.bleConnected) stringResource(R.string.online) else stringResource(R.string.offline),
                    if (d.bleConnected) Color(0xFF4CAF50) else Color(0xFFF44336))
                
                InfoRow(stringResource(R.string.wifi_client_label), if (d.wifiClientConnected) stringResource(R.string.online) else stringResource(R.string.offline),
                    if (d.wifiClientConnected) Color(0xFF4CAF50) else Color(0xFFF44336))

                InfoRow(stringResource(R.string.ip), d.ip)
                if (d.ssid.isNotEmpty()) {
                    InfoRow(stringResource(R.string.ssid_label), d.ssid)
                    InfoRow(stringResource(R.string.signal_strength), "${d.rssi} dBm")
                }
                
                InfoRow(stringResource(R.string.free_ram), "%.1f KB".format(locale, d.heap / 1024.0))
                
                val apStatusText = if (d.apActive) "Active (${d.apIp})" else "Off"
                InfoRow(stringResource(R.string.access_point), apStatusText, if (d.apActive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface)

                InfoRow(stringResource(R.string.tcp_port), d.port.toString())
                InfoRow("UART", "RX=GPIO${d.rxPin.toString().padStart(2, '0')} TX=GPIO${d.txPin.toString().padStart(2, '0')}")
            }

            SectionCard("VESC") {
                InfoRow("VESC", if (d.vescConnected) stringResource(R.string.online) else stringResource(R.string.offline),
                    if (d.vescConnected) Color(0xFF4CAF50) else Color(0xFFF44336))
                
                InfoRow(stringResource(R.string.voltage), "%.1f V".format(locale, d.vescVoltage))
                InfoRow(stringResource(R.string.temp_fet), "%.0f \u00b0C".format(locale, d.vescTempFet))
                InfoRow(stringResource(R.string.temp_motor), "%.0f \u00b0C".format(locale, d.vescTempMotor))
                InfoRow(stringResource(R.string.error), d.vescFaultStr.ifEmpty { "OK" }, if (d.vescFault > 0) Color(0xFFF44336) else Color(0xFF4CAF50))
                InfoRow(stringResource(R.string.erpm_label), d.vescErpm.toString())
                
                InfoRow(stringResource(R.string.uptime), d.uptime)
                InfoRow("Build", d.version)
            }

            SectionCard(stringResource(R.string.diagnostics_esp32).uppercase()) {
                InfoRow(stringResource(R.string.sta_scans), d.diagScans.toString())
                InfoRow(stringResource(R.string.sta_connections), d.diagStaConn.toString())
                InfoRow(stringResource(R.string.sta_disconnects), d.diagStaDisc.toString())
                InfoRow("AP client conn/disc", "${d.diagApConn} / ${d.diagApDisc}")
                InfoRow(stringResource(R.string.watchdog_resets), d.diagWdFires.toString())
                InfoRow("Loop max (ms)", "%.1f".format(locale, d.diagLoopMaxUs / 1000.0))
                InfoRow("Loops/sec", d.diagLoopsPerSec.toString())
                InfoRow(stringResource(R.string.min_free_heap), "${d.diagMinHeap / 1024} KB")
                InfoRow("Probe requests (RSSI)", "${d.diagProbeReqs} (${d.diagProbeRssi} dBm)")
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { vm.refreshNow() },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.refresh)) }
                Button(
                    onClick = { showRestartConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.restart)) }
            }
            if (activeHost == "192.168.9.1") {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { vm.disconnectAp() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.disconnect_ap)) }
            }
        }
    }

    if (showRestartConfirm) {
        AlertDialog(
            onDismissRequest = { showRestartConfirm = false },
            title = { Text(stringResource(R.string.restart_bridge_q)) },
            text = { Text(stringResource(R.string.restart_bridge_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    showRestartConfirm = false
                    vm.restart { ok ->
                        scope.launch {
                            snackbar.showSnackbar(if (ok) restartTriggered else failed)
                        }
                    }
                }) { Text(stringResource(R.string.restart), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showRestartConfirm = false }) { Text(stringResource(R.string.cancel)) }
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
