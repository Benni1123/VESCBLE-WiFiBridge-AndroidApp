package eu.benni1123.vescbridge

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.ui.res.stringResource

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        checkPermissions()

        lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_RESUME) vm.setAppActive(true)
                else if (event == Lifecycle.Event.ON_PAUSE) vm.setAppActive(false)
            }
        })
        
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.showWifiPanel.collect {
                    // Benutze das WLAN-Panel (wie vorher)
                    startActivity(Intent(Settings.Panel.ACTION_WIFI))
                }
            }
        }

        setContent { AppRoot(vm) }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        
        val toRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (toRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(toRequest.toTypedArray())
        }
    }
}

enum class Screen { Status, Leds, Config, Devices }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: MainViewModel) {
    VescBridgeTheme {
        var screen by remember { mutableStateOf(Screen.Status) }
        var startupRedirectDone by remember { mutableStateOf(false) }

        val devices  by vm.devices.collectAsStateWithLifecycle()
        val selected by vm.selected.collectAsStateWithLifecycle()
        val connState by vm.state.collectAsStateWithLifecycle()
        val activeHost by vm.activeHost.collectAsStateWithLifecycle()
        val progress by vm.rebootProgress.collectAsStateWithLifecycle()
        val info by vm.info.collectAsStateWithLifecycle()
        val config by vm.config.collectAsStateWithLifecycle()

        // Sichtbarkeit der LED-Steuerung
        val ledsActive = info?.ledsEnabled == true || config?.ledsEnabled == true

        // Automatisches Zurückspringen von LED, wenn LEDs deaktiviert werden
        LaunchedEffect(ledsActive) {
            if (!ledsActive && screen == Screen.Leds) {
                screen = Screen.Status
            }
        }

        // Start-up-Logik: Wenn kein Gerät (durch Stern/Single-Rule) gewählt wurde,
        // direkt zur Geräte-Übersicht springen.
        LaunchedEffect(devices, selected) {
            if (!startupRedirectDone && devices.isNotEmpty()) {
                if (selected == null) {
                    screen = Screen.Devices
                }
                startupRedirectDone = true
            }
        }

        // Automatisch zum Status springen, wenn ein Gerät manuell ausgewählt wurde
        LaunchedEffect(selected?.id) {
            if (startupRedirectDone && selected != null && screen == Screen.Devices) {
                screen = Screen.Status
            }
        }

        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text(stringResource(R.string.app_name)) },
                        colors = TopAppBarDefaults.topAppBarColors(
                            titleContentColor = MaterialTheme.colorScheme.primary
                        )
                    )
                        DeviceSelectorBar(
                            devices = devices,
                            selected = selected,
                            activeHost = activeHost,
                            state = connState,
                            progress = progress,
                            vm = vm,
                            onManageDevices = { screen = Screen.Devices }
                        )
                }
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = screen == Screen.Status,
                        onClick = { screen = Screen.Status },
                        icon = { Icon(Icons.Filled.Info, null) },
                        label = { Text(stringResource(R.string.nav_status)) }
                    )
                    if (ledsActive) {
                        NavigationBarItem(
                            selected = screen == Screen.Leds,
                            onClick = { screen = Screen.Leds; vm.loadLedConfig() },
                            icon = { Icon(Icons.Filled.Lightbulb, null) },
                            label = { Text(stringResource(R.string.nav_led)) }
                        )
                    }
                    NavigationBarItem(
                        selected = screen == Screen.Config,
                        onClick = { screen = Screen.Config; vm.loadConfig() },
                        icon = { Icon(Icons.Filled.Tune, null) },
                        label = { Text(stringResource(R.string.nav_config)) }
                    )
                    NavigationBarItem(
                        selected = screen == Screen.Devices,
                        onClick = { screen = Screen.Devices },
                        icon = { Icon(Icons.Filled.Dns, null) },
                        label = { Text(stringResource(R.string.nav_devices)) }
                    )
                }
            }
        )
{ padding ->
            Box(Modifier.padding(padding)) {
                when (screen) {
                    Screen.Status  -> StatusScreen(vm)
                    Screen.Leds    -> LedScreen(vm)
                    Screen.Config  -> ConfigScreen(vm)
                    Screen.Devices -> DevicesScreen(vm)
                }
            }
        }
    }
}

@Composable
fun DeviceSelectorBar(
    devices: List<Device>,
    selected: Device?,
    activeHost: String?,
    state: ConnState,
    progress: Float?,
    vm: MainViewModel,
    onManageDevices: () -> Unit
) {
    var expandedSelect by remember { mutableStateOf(false) }
    var expandedSettings by remember { mutableStateOf(false) }
    
    var showEspUpdateDialog by remember { mutableStateOf(false) }
    var showAppUpdateDialog by remember { mutableStateOf(false) }
    
    val dotColor = when (state) {
        ConnState.Online -> Color(0xFF4CAF50)
        ConnState.Searching, ConnState.ConnectingAp, ConnState.Rebooting -> MaterialTheme.colorScheme.primary
        ConnState.Offline -> MaterialTheme.colorScheme.error
    }

    val noDevice = stringResource(R.string.no_device)
    val searching = stringResource(R.string.searching_dots)
    val restarting = stringResource(R.string.restarting_dots)

    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    val displayText = remember(selected, activeHost, state, noDevice, searching, restarting) {
                        val name = selected?.name ?: noDevice
                        when (state) {
                            ConnState.Online -> "$name  ($activeHost)"
                            ConnState.Searching, ConnState.ConnectingAp -> "$name  ($searching)"
                            ConnState.Rebooting -> "$name  ($restarting)"
                            ConnState.Offline -> name
                        }
                    }
                    Text(
                        text = displayText,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
                if (devices.size > 1) {
                    IconButton(onClick = { expandedSelect = true }) {
                        Icon(Icons.Filled.SwapHoriz, stringResource(R.string.switch_device), tint = MaterialTheme.colorScheme.primary)
                    }
                    DropdownMenu(expanded = expandedSelect, onDismissRequest = { expandedSelect = false }) {
                        devices.forEach { d ->
                            DropdownMenuItem(
                                text = { Text("${d.name}  (${d.hosts.firstOrNull() ?: "?"})") },
                                onClick = { vm.selectDevice(d.id); expandedSelect = false }
                            )
                        }
                    }
                }
                
                Box {
                    IconButton(onClick = { expandedSettings = true }) {
                        Icon(Icons.Filled.Settings, stringResource(R.string.manage_devices), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = expandedSettings, onDismissRequest = { expandedSettings = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.nav_devices)) },
                            leadingIcon = { Icon(Icons.Filled.Dns, null) },
                            onClick = { onManageDevices(); expandedSettings = false }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.firmware_update)) },
                            leadingIcon = { Icon(Icons.Filled.SystemUpdate, null) },
                            onClick = { showEspUpdateDialog = true; expandedSettings = false }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.app_update)) },
                            leadingIcon = { Icon(Icons.Filled.BrowserUpdated, null) },
                            onClick = { showAppUpdateDialog = true; expandedSettings = false }
                        )
                    }
                }
            }
            progress?.let {
                LinearProgressIndicator(
                    progress = { it },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent
                )
            }
        }
    }

    if (showEspUpdateDialog) {
        EspUpdateDialog(vm) { showEspUpdateDialog = false }
    }
    if (showAppUpdateDialog) {
        AppUpdateDialog(vm) { showAppUpdateDialog = false }
    }
}

@Composable
fun EspUpdateDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val update by vm.updateStatus.collectAsStateWithLifecycle()
    val busy by vm.updateBusy.collectAsStateWithLifecycle()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.firmware_update)) },
        text = {
            Column {
                if (update == null) {
                    CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                } else {
                    Text(stringResource(R.string.version_current) + ": ${update?.current}")
                    Text(stringResource(R.string.version_new) + ": ${update?.latest}")
                    if (update?.available == true) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.install_firmware_update_msg))
                    } else {
                        Text(stringResource(R.string.firmware_up_to_date), color = Color(0xFF4CAF50))
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { vm.checkForUpdate() }, enabled = !busy) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp)) else Text(stringResource(R.string.check_for_updates))
                }
                if (update?.available == true) {
                    Button(onClick = { vm.triggerUpdate { onDismiss() } }, enabled = !busy) {
                        Text(stringResource(R.string.install))
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
    
    LaunchedEffect(Unit) {
        vm.loadUpdateStatus()
    }
}

@Composable
fun AppUpdateDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val update by vm.appUpdateInfo.collectAsStateWithLifecycle()
    val busy by vm.appUpdateBusy.collectAsStateWithLifecycle()
    val progress by vm.appUpdateProgress.collectAsStateWithLifecycle()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_update)) },
        text = {
            Column {
                if (update == null && busy) {
                    CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                } else if (update != null) {
                    Text(stringResource(R.string.new_version_available) + ": ${update?.latestVersionCode}")
                    if (progress != null) {
                        LinearProgressIndicator(progress = { progress!! }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                } else {
                    Text(stringResource(R.string.app_up_to_date), color = Color(0xFF4CAF50))
                }
            }
        },
        confirmButton = {
            if (update != null && progress == null) {
                Button(onClick = { vm.downloadAppUpdate() }, enabled = !busy) {
                    Text(stringResource(R.string.download_install_update))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
    
    LaunchedEffect(Unit) {
        vm.checkAppUpdate()
    }
}
