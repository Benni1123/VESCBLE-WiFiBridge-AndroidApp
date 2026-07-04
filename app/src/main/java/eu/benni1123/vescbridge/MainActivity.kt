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
                        title = { Text("\uD83D\uDEF4 VESC Bridge") },
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
                        onSelect = { vm.selectDevice(it.id) },
                        onManage = { screen = Screen.Devices }
                    )
                }
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = screen == Screen.Status,
                        onClick = { screen = Screen.Status },
                        icon = { Icon(Icons.Filled.Info, null) },
                        label = { Text("Status") }
                    )
                    if (ledsActive) {
                        NavigationBarItem(
                            selected = screen == Screen.Leds,
                            onClick = { screen = Screen.Leds; vm.loadLedConfig() },
                            icon = { Icon(Icons.Filled.Lightbulb, null) },
                            label = { Text("LED") }
                        )
                    }
                    NavigationBarItem(
                        selected = screen == Screen.Config,
                        onClick = { screen = Screen.Config; vm.loadConfig() },
                        icon = { Icon(Icons.Filled.Tune, null) },
                        label = { Text("Config") }
                    )
                    NavigationBarItem(
                        selected = screen == Screen.Devices,
                        onClick = { screen = Screen.Devices },
                        icon = { Icon(Icons.Filled.Dns, null) },
                        label = { Text("Ger\u00e4te") }
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
    onSelect: (Device) -> Unit,
    onManage: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val dotColor = when (state) {
        ConnState.Online -> Color(0xFF4CAF50)
        ConnState.Searching, ConnState.ConnectingAp, ConnState.Rebooting -> MaterialTheme.colorScheme.primary
        ConnState.Offline -> MaterialTheme.colorScheme.error
    }
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
                    val displayText = remember(selected, activeHost, state) {
                        val name = selected?.name ?: "Kein Gerät"
                        when (state) {
                            ConnState.Online -> "$name  ($activeHost)"
                            ConnState.Searching, ConnState.ConnectingAp -> "$name  (Suche...)"
                            ConnState.Rebooting -> "$name  (Neustart...)"
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
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Filled.SwapHoriz, "Wechseln", tint = MaterialTheme.colorScheme.primary)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        devices.forEach { d ->
                            val isSelected = d.id == selected?.id
                            val host = if (isSelected) (activeHost ?: d.hosts.firstOrNull()) else d.hosts.firstOrNull()
                            DropdownMenuItem(
                                text = { Text("${d.name}  (${host ?: "?"})") },
                                onClick = { onSelect(d); expanded = false }
                            )
                        }
                    }
                }
                IconButton(onClick = onManage) {
                    Icon(Icons.Filled.Settings, "Verwalten", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
}
