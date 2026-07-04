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

    val restartTriggered = stringResource(R.string.restart_triggered)
    val failed = stringResource(R.string.failed)
    val updateStarted = stringResource(R.string.update_started_msg)
    val updateFailed = stringResource(R.string.update_failed)

    LaunchedEffect(connState) {
        if (connState == ConnState.Online) vm.loadUpdateStatus()
    }

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
            SectionCard(stringResource(R.string.connection)) {
                val onlineNow = connState == ConnState.Online
                InfoRow(stringResource(R.string.status), if (onlineNow) stringResource(R.string.online) else stringResource(R.string.offline),
                    if (onlineNow) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error)
                
                activeHost?.let { 
                    val label = if (it == d.apIp) stringResource(R.string.active_conn_ap) else stringResource(R.string.active_conn_wifi)
                    InfoRow(label, it, MaterialTheme.colorScheme.primary) 
                }

                InfoRow(stringResource(R.string.mode), if (d.mode == "ap") stringResource(R.string.access_point) else stringResource(R.string.wifi))
                
                if (d.ssid.isNotEmpty()) {
                    InfoRow(stringResource(R.string.ssid_label), d.ssid)
                    InfoRow(stringResource(R.string.signal_strength), "${d.rssi} dBm")
                }

                // WLAN-IP nur zeigen, wenn sie NICHT die aktive Verbindung ist
                if (d.ip.isNotEmpty() && d.ip != "0.0.0.0" && d.ip != activeHost) {
                    InfoRow(stringResource(R.string.wifi_ip_device), d.ip)
                }
                
                // AP-IP nur zeigen, wenn sie NICHT die aktive Verbindung ist
                if (d.apIp.isNotEmpty() && d.apIp != activeHost) {
                    InfoRow(stringResource(R.string.ap_ip), d.apIp)
                }

                if (d.apClientIp.isNotEmpty()) {
                    InfoRow(stringResource(R.string.connected_device), d.apClientIp, Color(0xFF4CAF50))
                }

                if (d.apActive) {
                    val timeoutStr = if (d.apTimeoutRemaining > 0) "${d.apTimeoutRemaining}s" else stringResource(R.string.infinite)
                    InfoRow(stringResource(R.string.ap_visible), stringResource(R.string.yes_timeout, timeoutStr), Color(0xFF4CAF50))
                }
            }

            SectionCard(stringResource(R.string.ble_vesc_conn)) {
                InfoRow(stringResource(R.string.ble_connected), if (d.bleConnected) stringResource(R.string.yes) else stringResource(R.string.no),
                    if (d.bleConnected) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error)
                if (d.bleName.isNotEmpty()) InfoRow(stringResource(R.string.ble_name_label), d.bleName)
                if (d.bleMac.isNotEmpty()) InfoRow(stringResource(R.string.ble_mac), d.bleMac)
            }

            SectionCard(stringResource(R.string.vesc_telemetry)) {
                InfoRow(stringResource(R.string.vesc_connected), if (d.vescConnected) stringResource(R.string.yes) else stringResource(R.string.no),
                    if (d.vescConnected) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error)
                InfoRow(stringResource(R.string.voltage), "%.1f V".format(locale, d.vescVoltage))
                InfoRow(stringResource(R.string.erpm_label), d.vescErpm.toString())
                InfoRow(stringResource(R.string.temp_fet), "%.0f \u00b0C".format(locale, d.vescTempFet))
                InfoRow(stringResource(R.string.temp_motor), "%.0f \u00b0C".format(locale, d.vescTempMotor))
                if (d.vescFaultStr.isNotEmpty() && d.vescFaultStr != "FAULT_CODE_NONE")
                    InfoRow(stringResource(R.string.error), d.vescFaultStr, MaterialTheme.colorScheme.error)
                if (d.vescFault > 0)
                    InfoRow(stringResource(R.string.error_id), d.vescFault.toString(), MaterialTheme.colorScheme.error)
            }

            SectionCard(stringResource(R.string.firmware_update)) {
                val u = update
                if (u == null) {
                    Text(stringResource(R.string.loading_update_status), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                } else {
                    InfoRow(stringResource(R.string.version_current), u.current)
                    if (u.serverError) {
                        Text(stringResource(R.string.update_server_unreachable), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    } else if (u.latest.isNotEmpty()) {
                        InfoRow(stringResource(R.string.version_new), u.latest)
                        if (u.available || (u.latest.isNotEmpty() && u.current != u.latest)) {
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { showUpdateConfirm = true },
                                enabled = !updateBusy && progress == null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (updateBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Text(stringResource(R.string.install_update_now))
                            }
                        } else {
                            Text(stringResource(R.string.firmware_up_to_date), color = Color(0xFF4CAF50), fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = { vm.checkForUpdate() },
                        enabled = !updateBusy && progress == null,
                        modifier = Modifier.align(Alignment.End)
                    ) { Text(stringResource(R.string.check_for_updates), fontSize = 12.sp) }
                }
            }

            SectionCard(stringResource(R.string.app_update)) {
                val au = appUpdate
                if (au != null) {
                    InfoRow(stringResource(R.string.new_version_available), stringResource(R.string.version) + " ${au.latestVersionCode}")
                    Spacer(Modifier.height(8.dp))
                    val currentAppProgress = appProgress
                    if (currentAppProgress != null) {
                        Column {
                            LinearProgressIndicator(
                                progress = { currentAppProgress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("%.0f%%".format(locale, currentAppProgress * 100), fontSize = 11.sp)
                        }
                    } else {
                        Button(
                            onClick = { vm.downloadAppUpdate() },
                            enabled = !appBusy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (appBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text(stringResource(R.string.download_install_update))
                        }
                    }
                } else {
                    if (appBusy) {
                        Text(stringResource(R.string.searching_app_updates), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    } else {
                        Text(stringResource(R.string.app_up_to_date), color = Color(0xFF4CAF50), fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = { vm.checkAppUpdate() },
                        enabled = !appBusy,
                        modifier = Modifier.align(Alignment.End)
                    ) { Text(stringResource(R.string.check_app_updates), fontSize = 12.sp) }
                }
            }

            SectionCard(stringResource(R.string.system)) {
                InfoRow(stringResource(R.string.uptime), d.uptime)
                InfoRow(stringResource(R.string.free_heap), "${d.heap / 1024} kB")
                if (d.version.isNotEmpty()) InfoRow(stringResource(R.string.version), d.version)
                if (d.port != -1) InfoRow(stringResource(R.string.tcp_port), d.port.toString())
                if (d.rxPin != -1) InfoRow("UART RX", "Pin ${d.rxPin}")
                if (d.txPin != -1) InfoRow("UART TX", "Pin ${d.txPin}")
            }

            SectionCard(stringResource(R.string.diagnostics_esp32)) {
                InfoRow(stringResource(R.string.wifi_scans), d.diagScans.toString())
                InfoRow(stringResource(R.string.sta_connections), d.diagStaConn.toString())
                InfoRow(stringResource(R.string.sta_disconnects), d.diagStaDisc.toString())
                if (d.diagStaDisc > 0) InfoRow(stringResource(R.string.last_reason_sta), d.diagDiscReason.toString())
                InfoRow(stringResource(R.string.ap_connections), d.diagApConn.toString())
                InfoRow(stringResource(R.string.ap_disconnects), d.diagApDisc.toString())
                InfoRow(stringResource(R.string.probe_requests), d.diagProbeReqs.toString())
                if (d.diagProbeReqs > 0) InfoRow(stringResource(R.string.probe_rssi_avg), "${d.diagProbeRssi} dBm")
                InfoRow(stringResource(R.string.watchdog_resets), d.diagWdFires.toString())
                InfoRow(stringResource(R.string.max_loop_time), "${d.diagLoopMaxUs} \u00b5s")
                InfoRow(stringResource(R.string.loops_per_sec), d.diagLoopsPerSec.toString())
                InfoRow(stringResource(R.string.min_free_heap), "${d.diagMinHeap / 1024} kB")
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
            // Wenn die genutzte IP die AP-IP ist, Trennen-Option anbieten.
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

    if (showUpdateConfirm) {
        AlertDialog(
            onDismissRequest = { showUpdateConfirm = false },
            title = { Text(stringResource(R.string.install_firmware_update_q)) },
            text = { Text(stringResource(R.string.install_firmware_update_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    showUpdateConfirm = false
                    vm.triggerUpdate { ok ->
                        scope.launch {
                            snackbar.showSnackbar(if (ok) updateStarted else updateFailed)
                        }
                    }
                }) { Text(stringResource(R.string.install)) }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateConfirm = false }) { Text(stringResource(R.string.cancel)) }
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
