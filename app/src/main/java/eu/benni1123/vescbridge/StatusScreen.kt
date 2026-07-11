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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource

@Composable
fun StatusScreen(vm: MainViewModel) {
    val locale     = LocalConfiguration.current.locales[0]
    val info       by vm.info.collectAsStateWithLifecycle()
    val connState  by vm.state.collectAsStateWithLifecycle()
    val selected   by vm.selected.collectAsStateWithLifecycle()
    val debugMode  by vm.debugMode.collectAsStateWithLifecycle()
    val uartLog    by vm.uartLog.collectAsStateWithLifecycle()
    val bridgeFilter by vm.bridgeDebugFilter.collectAsStateWithLifecycle()

    LaunchedEffect(debugMode) {
        if (debugMode) {
            vm.loadUartLog()
        }
    }

    val snackbar = remember { SnackbarHostState() }

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
        if (info == null && connState != ConnState.Rebooting) {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (connState == ConnState.Searching || connState == ConnState.ConnectingAp || connState == ConnState.Online) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                        }
                        val msg = when (connState) {
                            ConnState.Searching     -> stringResource(R.string.searching_ip)
                            ConnState.ConnectingAp  -> stringResource(R.string.connecting_ap, currentSelected.apSsid)
                            ConnState.Online        -> stringResource(R.string.loading_config)
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

            if (debugMode) {
                SectionCard(stringResource(R.string.boot_diagnostics).uppercase()) {
                    InfoRow(stringResource(R.string.reset_reason), "${d.resetReason} (${d.resetReasonCode})")
                    if (d.plannedRestart.isNotBlank()) {
                        InfoRow(stringResource(R.string.planned_restart), d.plannedRestart)
                    }
                    if (d.resetBrownout) InfoRow(stringResource(R.string.reset_brownout), stringResource(R.string.yes), Color(0xFFF44336))
                    if (d.resetPanic) InfoRow(stringResource(R.string.reset_panic), stringResource(R.string.yes), Color(0xFFF44336))
                    if (d.resetWatchdog) InfoRow(stringResource(R.string.reset_watchdog), stringResource(R.string.yes), Color(0xFFF44336))
                }

                SectionCard(stringResource(R.string.diagnostics_esp32).uppercase()) {
                    InfoRow(stringResource(R.string.sta_scans), d.diagScans.toString())
                    InfoRow(stringResource(R.string.sta_connections), d.diagStaConn.toString())
                    val discSuffix = if (d.diagStaDisc > 0 && d.diagDiscReasonName.isNotBlank()) " (${d.diagDiscReasonName})" else ""
                    InfoRow(stringResource(R.string.sta_disconnects), "${d.diagStaDisc}$discSuffix", if (d.diagStaDisc > 0) Color(0xFFE91E63) else null)
                    InfoRow("AP client conn/disc", "${d.diagApConn} / ${d.diagApDisc}")
                    InfoRow(stringResource(R.string.watchdog_resets), d.diagWdFires.toString(), if (d.diagWdFires > 0) Color(0xFFE91E63) else null)
                    InfoRow("Loop max (ms)", "%.1f".format(locale, d.diagLoopMaxUs / 1000.0), if (d.diagLoopMaxUs > 20000) Color(0xFFE91E63) else null)
                    InfoRow("Loops/sec", d.diagLoopsPerSec.toString())
                    InfoRow(stringResource(R.string.min_free_heap), "${d.diagMinHeap / 1024} KB")
                    InfoRow("Probe requests (RSSI)", "${d.diagProbeReqs} (${d.diagProbeRssi} dBm)")
                }

                SectionCard("UART LOG") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Filter:", fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
                        DebugFilterChip("BLE", 1, bridgeFilter) { vm.setBridgeDebugFilter(it) }
                        DebugFilterChip("WiFi", 2, bridgeFilter) { vm.setBridgeDebugFilter(it) }
                        DebugFilterChip("Poll", 4, bridgeFilter) { vm.setBridgeDebugFilter(it) }
                        DebugFilterChip("Stat", 8, bridgeFilter) { vm.setBridgeDebugFilter(it) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { vm.loadUartLog() },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.refresh), fontSize = 12.sp)
                        }
                        Button(
                            onClick = { vm.clearUartLog() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(stringResource(R.string.remove), fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = Color.Black.copy(alpha = 0.05f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
                    ) {
                        Column(
                            Modifier.padding(8.dp).verticalScroll(rememberScrollState())
                        ) {
                            if (uartLog.isEmpty()) {
                                Text("No log entries", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                uartLog.forEach { line ->
                                    val color = when {
                                        line.contains("BROWNOUT") || line.contains("PANIC") || line.contains("WATCHDOG") -> Color(0xFFF44336)
                                        line.contains("[SYSTEM]") || line.contains("Software-Neustart") -> Color(0xFF4CAF50)
                                        line.contains("[BOOT]") -> Color(0xFFFF9800)
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                    Text(
                                        text = line,
                                        fontSize = 10.sp,
                                        color = color,
                                        lineHeight = 12.sp,
                                        modifier = Modifier.padding(vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
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
fun DebugFilterChip(label: String, bit: Int, currentFilter: Int, onFilterChange: (Int) -> Unit) {
    val active = (currentFilter and bit) != 0
    FilterChip(
        selected = active,
        onClick = {
            val next = if (active) currentFilter and bit.inv() else currentFilter or bit
            onFilterChange(next)
        },
        label = { Text(label, fontSize = 10.sp) },
        modifier = Modifier.padding(end = 4.dp).height(24.dp)
    )
}

@Composable
fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
