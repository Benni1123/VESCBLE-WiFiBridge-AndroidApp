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

@Composable
fun DevicesScreen(vm: MainViewModel) {
    val devices  by vm.devices.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()

    var editDevice by remember { mutableStateOf<Device?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Gespeicherte Ger\u00e4te", color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))

            if (devices.isEmpty()) {
                Text("Noch keine Ger\u00e4te. Unten rechts eins hinzuf\u00fcgen.",
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
            Icon(Icons.Filled.Add, "Hinzuf\u00fcgen", tint = MaterialTheme.colorScheme.onPrimary)
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
            onScanNearbySsids = { vm.getNearbySsids() }
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
            onScanNearbySsids = { vm.getNearbySsids() }
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
                    Text("AP: ${device.apSsid}${if (device.apOnly) "  (nur AP)" else ""}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }
            IconButton(onClick = onToggleAuto) {
                Icon(
                    if (device.autoConnect) Icons.Filled.Star else Icons.Filled.StarOutline,
                    "Auto-Connect",
                    tint = if (device.autoConnect) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, "Bearbeiten", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, "L\u00f6schen", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun DeviceDialog(
    initial: Device?,
    onDismiss: () -> Unit,
    onSave: (String, List<String>, String, String, Boolean, Boolean, Int, Int, Boolean) -> Unit,
    onScanNearbySsids: () -> List<String>
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

    // Validierung: Name ist Pflicht, außer syncName ist aktiv. IP ist Pflicht, AUSSER apOnly ist aktiv.
    val valid = (name.isNotBlank() || syncName) && (apOnly || hosts.any { it.isNotBlank() })

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Ger\u00e4t hinzuf\u00fcgen" else "Ger\u00e4t bearbeiten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (leer lassen f\u00fcr BLE-Name)") },
                    singleLine = true,
                    enabled = !syncName,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = syncName, onCheckedChange = { syncName = it })
                    Text("Namen mit BLE synchronisieren", fontSize = 14.sp, modifier = Modifier.clickable { syncName = !syncName })
                }
                Spacer(Modifier.height(8.dp))
                Text("IP-Adressen (App nutzt die erreichbare)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                hosts.forEachIndexed { idx, value ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { hosts[idx] = it },
                            label = { Text("IP ${idx + 1}") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        if (hosts.size > 1) {
                            IconButton(onClick = { hosts.removeAt(idx) }) {
                                Icon(Icons.Filled.Close, "Entfernen", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                TextButton(onClick = { hosts.add("") }) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("IP hinzuf\u00fcgen")
                }

                Spacer(Modifier.height(8.dp))
                Text("Access Point (optional, f\u00fcr Auto-Connect ohne WLAN)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                
                var showScanResults by remember { mutableStateOf(false) }
                val nearbySsids = remember { mutableStateListOf<String>() }

                Box(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = apSsid,
                        onValueChange = { apSsid = it },
                        label = { Text("AP-SSID (WLAN-Name der Bridge)") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = {
                                nearbySsids.clear()
                                nearbySsids.addAll(onScanNearbySsids())
                                showScanResults = true
                            }) {
                                Icon(Icons.Filled.Wifi, "Scannen")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (showScanResults && nearbySsids.isNotEmpty()) {
                        DropdownMenu(
                            expanded = showScanResults,
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
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = apPassword,
                    onValueChange = { apPassword = it },
                    label = { Text("AP-Passwort") },
                    singleLine = true,
                    visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                                IconButton(onClick = { showPw = !showPw }) {
                            Icon(
                                if (showPw) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                "Anzeigen", tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text("Tipp: AP-IP ist meist 192.168.9.1 \u2013 als IP mit eintragen.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Nur \u00fcber AP erreichbar", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                        Text("Direkt mit AP verbinden, auch wenn Handy im WLAN ist",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                    Switch(checked = apOnly, onCheckedChange = { apOnly = it })
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Automatisch verbinden", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                        Text("Beim App-Start automatisch suchen",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                    Switch(checked = autoConnect, onCheckedChange = { autoConnect = it })
                }
                Spacer(Modifier.height(12.dp))
                Text("Debug Timeouts (ms)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = homeTimeout,
                        onValueChange = { homeTimeout = it.filter { c -> c.isDigit() } },
                        label = { Text("Heimnetz") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = apTimeout,
                        onValueChange = { apTimeout = it.filter { c -> c.isDigit() } },
                        label = { Text("AP Mode") },
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
                Text("Speichern")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
