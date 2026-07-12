package eu.benni1123.vescbridge

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource

@Composable
fun DevicesScreen(vm: MainViewModel) {
    val devices  by vm.devices.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val discoveryResults by vm.discoveryResults.collectAsStateWithLifecycle()
    val discoveryBusy by vm.discoveryBusy.collectAsStateWithLifecycle()

    var editDevice by remember { mutableStateOf<Device?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text(stringResource(R.string.saved_devices), color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))

            if (devices.isEmpty()) {
                Text(stringResource(R.string.no_devices_yet),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(devices, key = { it.id }) { d ->
                        DeviceCard(
                            device = d,
                            isSelected = d.id == selected?.id,
                            onSelect = { vm.selectDevice(d.id) },
                            onEdit = { editDevice = d },
                            onDelete = { vm.deleteDevice(d.id) },
                            onToggleAuto = { vm.toggleAutoConnect(d) }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAdd = true },
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        ) {
            Icon(Icons.Filled.Add, stringResource(R.string.add_device), tint = MaterialTheme.colorScheme.onPrimary)
        }
    }

    if (showAdd) {
        DeviceDialog(
            initial = null,
            onDismiss = { showAdd = false },
            onSave = { name, hosts, ssid, pw, apOnly, autoConn, hTo, aTo, sName ->
                vm.addDevice(
                    name = name.trim(),
                    hosts = hosts.map { it.trim() }.filter { it.isNotEmpty() },
                    apSsid = ssid.trim(),
                    apPassword = pw,
                    apOnly = apOnly,
                    autoConnect = autoConn,
                    homeTimeout = hTo,
                    apTimeout = aTo,
                    syncName = sName
                )
                showAdd = false
            },
            onScanNearbySsids = { vm.getNearbySsids() },
            discoveryResults = discoveryResults,
            discoveryBusy = discoveryBusy,
            onScanWifi = { vm.discoverBridges() }
        )
    }
    editDevice?.let { d ->
        DeviceDialog(
            initial = d,
            onDismiss = { editDevice = null },
            onSave = { name, hosts, ssid, pw, apOnly, autoConn, hTo, aTo, sName ->
                vm.updateDevice(d.copy(
                    name = name.trim(),
                    hosts = hosts.map { it.trim() }.filter { it.isNotEmpty() },
                    apSsid = ssid.trim(),
                    apPassword = pw,
                    apOnly = apOnly,
                    autoConnect = autoConn,
                    homeTimeoutMs = hTo,
                    apTimeoutMs = aTo,
                    syncName = sName
                ))
                editDevice = null
            },
            onScanNearbySsids = { vm.getNearbySsids() },
            discoveryResults = discoveryResults,
            discoveryBusy = discoveryBusy,
            onScanWifi = { vm.discoverBridges() }
        )
    }
}

@Composable
fun DeviceCard(
    device: Device,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleAuto: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth().clickable { onSelect() }
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSelected) {
                        Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(device.name, color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Text(device.hosts.joinToString(", "), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                if (device.hasAp()) {
                    Text("AP: ${device.apSsid}${if (device.apOnly) "  (" + stringResource(R.string.only_ap) + ")" else ""}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }
            IconButton(onClick = onToggleAuto) {
                Icon(
                    if (device.autoConnect) Icons.Filled.Star else Icons.Filled.StarOutline,
                    stringResource(R.string.auto_connect),
                    tint = if (device.autoConnect) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, stringResource(R.string.edit_device), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun DeviceDialog(
    initial: Device?,
    onDismiss: () -> Unit,
    onSave: (String, List<String>, String, String, Boolean, Boolean, Int, Int, Boolean) -> Unit,
    onScanNearbySsids: () -> List<String>,
    discoveryResults: List<String>,
    discoveryBusy: Boolean,
    onScanWifi: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var syncName by remember { mutableStateOf(initial?.syncName ?: true) }
    var apOnly by remember { mutableStateOf(initial?.apOnly ?: false) }
    var autoConnect by remember { mutableStateOf(initial?.autoConnect ?: false) }
    var homeTimeout by remember { mutableStateOf((initial?.homeTimeoutMs ?: 2000).toString()) }
    var apTimeout by remember { mutableStateOf((initial?.apTimeoutMs ?: 5000).toString()) }
    // Mehrere IP-Felder. Mindestens eines. Beim Bearbeiten vorhandene laden.
    val hosts = remember {
        mutableStateListOf<String>().apply {
            val init = initial?.hosts ?: emptyList()
            if (init.isEmpty()) add("") else init.forEach { add(it) }
        }
    }
    var apSsid by remember { mutableStateOf(initial?.apSsid ?: "") }
    var apPassword by remember { mutableStateOf(initial?.apPassword ?: "") }
    var showPw by remember { mutableStateOf(false) }

    // Validierung: Name ist Pflicht, außer syncName ist aktiv. 
    // IP ist Pflicht, AUSSER apOnly ist aktiv ODER eine AP-SSID wurde eingegeben.
    val valid = (name.isNotBlank() || syncName) && (apOnly || hosts.any { it.isNotBlank() } || apSsid.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) stringResource(R.string.add_device) else stringResource(R.string.edit_device)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name_hint)) },
                    singleLine = true,
                    enabled = !syncName,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = syncName, onCheckedChange = { syncName = it })
                    Text(stringResource(R.string.sync_name_with_ble), fontSize = 14.sp, modifier = Modifier.clickable { syncName = !syncName })
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ip_addresses_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    
                    if (discoveryBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(onClick = onScanWifi, contentPadding = PaddingValues(4.dp)) {
                            Icon(Icons.Filled.Search, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.scan_wifi_net), fontSize = 12.sp)
                        }
                    }
                }

                if (discoveryResults.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            Text("Found in Network:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            discoveryResults.forEach { resIp ->
                                Text(resIp, modifier = Modifier.fillMaxWidth().clickable {
                                    if (!hosts.contains(resIp)) {
                                        val emptyIdx = hosts.indexOfFirst { it.isBlank() }
                                        if (emptyIdx != -1) {
                                            // Wenn ein Feld leer ist (z.B. das erste beim Start), dieses füllen
                                            hosts[emptyIdx] = resIp
                                        } else if (hosts.size == 1) {
                                            // Wenn nur ein Feld da ist (auch wenn gefüllt), dieses bei Klick ersetzen
                                            // (verhindert das ungewollte Hinzufügen einer 2. IP bei Fehlklick)
                                            hosts[0] = resIp
                                        } else {
                                            // Sonst neues Feld hinzufügen
                                            hosts.add(resIp)
                                        }
                                    }
                                }.padding(vertical = 4.dp), fontSize = 13.sp)
                            }
                        }
                    }
                }

                hosts.forEachIndexed { idx, value ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { hosts[idx] = it },
                            label = { Text(stringResource(R.string.ip) + " ${idx + 1}") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        if (hosts.size > 1) {
                            IconButton(onClick = { hosts.removeAt(idx) }) {
                                Icon(Icons.Filled.Close, stringResource(R.string.remove), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                TextButton(onClick = { hosts.add("") }) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.add_ip))
                }

                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.access_point_optional),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                
                var showScanResults by remember { mutableStateOf(false) }
                val nearbySsids = remember { mutableStateListOf<String>() }

                Box(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = apSsid,
                        onValueChange = { apSsid = it },
                        label = { Text(stringResource(R.string.ap_ssid_hint)) },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = {
                                nearbySsids.clear()
                                nearbySsids.addAll(onScanNearbySsids())
                                showScanResults = true
                            }) {
                                Icon(Icons.Filled.Wifi, stringResource(R.string.scan))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = showScanResults && nearbySsids.isNotEmpty(),
                        onDismissRequest = { showScanResults = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        nearbySsids.forEach { ssid ->
                            DropdownMenuItem(
                                text = { Text(ssid) },
                                onClick = {
                                    apSsid = ssid
                                    showScanResults = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = apPassword,
                    onValueChange = { apPassword = it },
                    label = { Text(stringResource(R.string.ap_password_label)) },
                    singleLine = true,
                    visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                                IconButton(onClick = { showPw = !showPw }) {
                            Icon(
                                if (showPw) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                stringResource(if (showPw) R.string.hide else R.string.show), tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.ap_ip_tip),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.only_via_ap), color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                        Text(stringResource(R.string.connect_directly_ap),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                    Switch(checked = apOnly, onCheckedChange = { apOnly = it })
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.auto_connect_start), color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                        Text(stringResource(R.string.auto_connect_start_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                    Switch(checked = autoConnect, onCheckedChange = { autoConnect = it })
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.debug_timeouts), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = homeTimeout,
                        onValueChange = { homeTimeout = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.home_network)) },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = apTimeout,
                        onValueChange = { apTimeout = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.ap_mode)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (valid) {
                        onSave(
                            name,
                            hosts.toList(),
                            apSsid,
                            apPassword,
                            apOnly,
                            autoConnect,
                            homeTimeout.toIntOrNull() ?: 2000,
                            apTimeout.toIntOrNull() ?: 5000,
                            syncName
                        )
                    }
                },
                enabled = valid
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
