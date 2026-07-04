package eu.benni1123.vescbridge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource

@Composable
fun LedScreen(vm: MainViewModel) {
    val cfg      by vm.ledConfig.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()

    LaunchedEffect(selected?.id) { vm.loadLedConfig() }

    if (selected == null) { EmptyHint(stringResource(R.string.no_device_selected)); return }
    val config = cfg
    if (config == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.loading_led_config), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.led_enable_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 12.sp)
            }
        }
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        // Kanal-Anzahl steuern
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.active_channels, config.count),
                        color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    OutlinedButton(
                        onClick = {
                            if (config.count > 1) {
                                val nextCount = config.count - 1
                                vm.ledPost("/api/led/channels?n=$nextCount", optimistic = { it.copy(count = nextCount) })
                            }
                        },
                        enabled = config.count > 1,
                        contentPadding = PaddingValues(horizontal = 14.dp)
                    ) { Text("\u2212") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (config.count < 4) {
                                val nextCount = config.count + 1
                                vm.ledPost("/api/led/channels?n=$nextCount", optimistic = { it.copy(count = nextCount) })
                            }
                        },
                        enabled = config.count < 4,
                        contentPadding = PaddingValues(horizontal = 14.dp)
                    ) { Text("+") }
                }
            }
        }

        // Synchronisierte Kanaele: ein gemeinsamer Block
        val synced = (0 until config.count).filter { config.channels[it].synced }
        if (synced.isNotEmpty()) {
            LedControlBlock(
                title = stringResource(R.string.synced_channels, synced.size),
                ch = config.channels[synced.first()],
                target = "sync=1",
                vm = vm
            )
        }

        // Nicht-synchronisierte Kanaele: je ein eigener Block
        for (i in 0 until config.count) {
            val c = config.channels[i]
            // Hardware-Zeile + Sync-Schalter immer pro Kanal
            ChannelHardwareCard(index = i, ch = c, vm = vm)
            if (!c.synced) {
                LedControlBlock(
                    title = stringResource(R.string.channel_num, i + 1),
                    ch = c,
                    target = "ch=$i",
                    vm = vm
                )
            }
        }
    }
}

@Composable
fun ChannelHardwareCard(index: Int, ch: LedChannel, vm: MainViewModel) {
    var pin by remember(ch.pin) { mutableStateOf(ch.pin.toString()) }
    var count by remember(ch.count) { mutableStateOf(ch.count.toString()) }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(stringResource(R.string.channel_num, index + 1) + " \u2013 " + stringResource(R.string.hardware), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = pin, onValueChange = { pin = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.gpio_label)) }, singleLine = true, modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = count, onValueChange = { count = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.led_count)) }, singleLine = true, modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        val p = pin.toIntOrNull() ?: 4
                        val n = count.toIntOrNull() ?: 30
                        // hw-Endpoint erwartet p<i>/n<i> fuer alle Kanaele; hier nur diesen Kanal
                        vm.ledPost("/api/led/hw?p$index=$p&n$index=$n", optimistic = { old ->
                            val newChannels = old.channels.toMutableList()
                            if (index < newChannels.size) {
                                newChannels[index] = newChannels[index].copy(pin = p, count = n)
                            }
                            old.copy(channels = newChannels)
                        })
                    }
                ) { Text(stringResource(R.string.apply)) }
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.sync), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Switch(
                    checked = ch.synced,
                    onCheckedChange = { on ->
                        vm.ledPost("/api/led/sync?ch=$index&on=${if (on) 1 else 0}", optimistic = { old ->
                            val newChannels = old.channels.toMutableList()
                            if (index < newChannels.size) {
                                newChannels[index] = newChannels[index].copy(synced = on)
                            }
                            old.copy(channels = newChannels)
                        })
                    }
                )
            }
        }
    }
}

// Steuerblock: Effekt, Farbe (RGB), Helligkeit, Knight Rider, Police
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LedControlBlock(title: String, ch: LedChannel, target: String, vm: MainViewModel) {
    // Hilfsfunktion fuer optimistisches Update der Config
    fun updateOptimistic(block: (LedChannel) -> LedChannel): (LedConfig) -> LedConfig = { old ->
        val isSync = target.startsWith("sync")
        val chIdx = target.substringAfter("ch=").toIntOrNull() ?: 0
        
        val newChannels = old.channels.mapIndexed { idx, channel ->
            if (isSync) {
                if (channel.synced) block(channel) else channel
            } else if (idx == chIdx) {
                block(channel)
            } else {
                channel
            }
        }
        old.copy(channels = newChannels)
    }

    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))

            // Effekt-Auswahl
            Text(stringResource(R.string.effect), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            var expanded by remember { mutableStateOf(false) }
            val effects = listOf(
                stringResource(R.string.off),
                stringResource(R.string.solid_color),
                "Knight Rider",
                stringResource(R.string.police_eu),
                stringResource(R.string.police_us_white),
                stringResource(R.string.police_us_rb),
                stringResource(R.string.rainbow_wave),
                stringResource(R.string.breathing),
                stringResource(R.string.sparkle),
                stringResource(R.string.meteor_rain),
                stringResource(R.string.hellfire)
            )
            
            Box {
                OutlinedCard(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            effects.getOrElse(ch.effect) { stringResource(R.string.unknown) },
                            Modifier.weight(1f),
                            fontSize = 14.sp
                        )
                        Icon(
                            if (expanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                            null
                        )
                    }
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    effects.forEachIndexed { index, name ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                expanded = false
                                if (index == 3) { // Police EU -> Auto Blue
                                    vm.ledPost("/api/led/effect?$target&e=$index", optimistic = updateOptimistic { it.copy(effect = index, r = 0, g = 0, b = 255) })
                                    vm.ledPost("/api/led/color?$target&r=0&g=0&b=255", optimistic = updateOptimistic { it.copy(r = 0, g = 0, b = 255) })
                                } else {
                                    vm.ledPost("/api/led/effect?$target&e=$index", optimistic = updateOptimistic { it.copy(effect = index) })
                                }
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Farbe zeigen bei: Solid, KR, Pol(EU), Atmen, Sparkle, Meteor
            if (ch.effect == 1 || ch.effect == 2 || ch.effect == 3 || ch.effect == 7 || ch.effect == 8 || ch.effect == 9) {
                // Farb-Zustand (lokal). Farbrad UND Slider schreiben hierein.
                var r by remember(ch.r) { mutableStateOf(ch.r.toFloat()) }
                var g by remember(ch.g) { mutableStateOf(ch.g.toFloat()) }
                var b by remember(ch.b) { mutableStateOf(ch.b.toFloat()) }

                fun sendColor(throttled: Boolean = false) {
                    val nr = r.roundToInt(); val ng = g.roundToInt(); val nb = b.roundToInt()
                    val path = "/api/led/color?$target&r=$nr&g=$ng&b=$nb"
                    val opt = updateOptimistic { it.copy(r = nr, g = ng, b = nb) }
                    if (throttled) vm.ledPostThrottled(path, opt)
                    else vm.ledPost(path, optimistic = opt)
                }

                Text(stringResource(R.string.color), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)

                // Farbton-Rad: setzt R/G/B auf den reinen Farbton.
                Box(Modifier.fillMaxWidth().padding(top = 8.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center) {
                    ColorWheel(
                        r = r.roundToInt(), g = g.roundToInt(), b = b.roundToInt(),
                        onHueChanged = { nr, ng, nb ->
                            r = nr.toFloat(); g = ng.toFloat(); b = nb.toFloat()
                            sendColor(throttled = true)
                        },
                        onHuePicked = { nr, ng, nb ->
                            r = nr.toFloat(); g = ng.toFloat(); b = nb.toFloat()
                            sendColor(throttled = false)
                        }
                    )
                }

                // Vorschau
                Box(
                    Modifier.fillMaxWidth().height(36.dp).padding(top = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(r.roundToInt(), g.roundToInt(), b.roundToInt()))
                )

                // RGB-Slider (Feinkorrektur)
                ColorSlider("R", r, Color(0xFFE53935), 
                    onChange = { r = it; sendColor(throttled = true) },
                    onFinished = { sendColor(throttled = false) }
                )
                ColorSlider("G", g, Color(0xFF43A047),
                    onChange = { g = it; sendColor(throttled = true) },
                    onFinished = { sendColor(throttled = false) }
                )
                ColorSlider("B", b, Color(0xFF1E88E5),
                    onChange = { b = it; sendColor(throttled = true) },
                    onFinished = { sendColor(throttled = false) }
                )
                Spacer(Modifier.height(10.dp))
            }

            if (ch.effect >= 1) {
                // Helligkeit
                var bright by remember(ch.bright) { mutableStateOf(ch.bright.toFloat()) }
                LabeledSlider(stringResource(R.string.brightness), bright, 0f, 255f,
                    onChange = { 
                        bright = it
                        val v = bright.roundToInt()
                        vm.ledPostThrottled("/api/led/bright?$target&v=$v", optimistic = updateOptimistic { it.copy(bright = v) })
                    },
                    onFinished = {
                        val v = bright.roundToInt()
                        vm.ledPost("/api/led/bright?$target&v=$v", optimistic = updateOptimistic { it.copy(bright = v) })
                    }
                )
                Spacer(Modifier.height(10.dp))
            }

            // Animation-Optionen (Speed/Width) für KR, Rainbow, Breath, Sparkle, Meteor, Fire
            if (ch.effect == 2 || ch.effect == 6 || ch.effect == 7 || ch.effect == 8 || ch.effect == 9 || ch.effect == 10) {
                val spdLabel = when(ch.effect) {
                    2 -> stringResource(R.string.speed_kr)
                    6 -> stringResource(R.string.speed_rainbow)
                    7 -> stringResource(R.string.speed_breath)
                    8 -> stringResource(R.string.speed_sparkle)
                    9 -> stringResource(R.string.speed_meteor)
                    10 -> stringResource(R.string.speed_fire)
                    else -> stringResource(R.string.speed_animation)
                }
                var spd by remember(ch.krspeed) { mutableStateOf(maxOf(1f, ch.krspeed.toFloat())) }
                LabeledSlider(spdLabel, spd, 1f, 200f,
                    onChange = { 
                        spd = it
                        val v = spd.roundToInt()
                        vm.ledPostThrottled("/api/led/krspeed?$target&v=$v", optimistic = updateOptimistic { it.copy(krspeed = v) })
                    },
                    onFinished = {
                        val v = spd.roundToInt()
                        vm.ledPost("/api/led/krspeed?$target&v=$v", optimistic = updateOptimistic { it.copy(krspeed = v) })
                    }
                )
                
                // Breath (7) hat keine Breite/Width
                if (ch.effect != 7) {
                    val widLabel = when(ch.effect) {
                        2 -> stringResource(R.string.width_kr)
                        6 -> stringResource(R.string.density_color)
                        8 -> stringResource(R.string.stars_amount)
                        9 -> stringResource(R.string.trail_length)
                        10 -> stringResource(R.string.fire_intensity)
                        else -> stringResource(R.string.width_amount)
                    }
                    var wid by remember(ch.krwidth) { mutableStateOf(maxOf(1f, ch.krwidth.toFloat())) }
                    LabeledSlider(widLabel, wid, 1f, 50f,
                        onChange = { 
                            wid = it
                            val v = wid.roundToInt()
                            vm.ledPostThrottled("/api/led/krwidth?$target&v=$v", optimistic = updateOptimistic { it.copy(krwidth = v) })
                        },
                        onFinished = {
                            val v = wid.roundToInt()
                            vm.ledPost("/api/led/krwidth?$target&v=$v", optimistic = updateOptimistic { it.copy(krwidth = v) })
                        }
                    )
                    
                    if (ch.effect == 2) {
                        Text(
                            stringResource(R.string.kitt_tip),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // Police-Optionen bei Effekt 3-5
            if (ch.effect in 3..5) {
                var phz by remember(ch.polhz) { mutableStateOf(maxOf(1f, ch.polhz.toFloat())) }
                LabeledSlider(stringResource(R.string.police_freq), phz, 1f, 10f,
                    onChange = { 
                        phz = it
                        val v = phz.roundToInt()
                        vm.ledPostThrottled("/api/led/polhz?$target&v=$v", optimistic = updateOptimistic { it.copy(polhz = v) })
                    },
                    onFinished = {
                        val v = phz.roundToInt()
                        vm.ledPost("/api/led/polhz?$target&v=$v", optimistic = updateOptimistic { it.copy(polhz = v) })
                    }
                )
                var pwid by remember(ch.krwidth) { mutableStateOf(maxOf(1f, ch.krwidth.toFloat())) }
                LabeledSlider(stringResource(R.string.flashes_per_burst), pwid, 1f, 10f,
                    onChange = { 
                        pwid = it
                        val v = pwid.roundToInt()
                        vm.ledPostThrottled("/api/led/krwidth?$target&v=$v", optimistic = updateOptimistic { it.copy(krwidth = v) })
                    },
                    onFinished = {
                        val v = pwid.roundToInt()
                        vm.ledPost("/api/led/krwidth?$target&v=$v", optimistic = updateOptimistic { it.copy(krwidth = v) })
                    }
                )
                
                // Swap Colors nur bei US-Police (4 und 5)
                if (ch.effect == 4 || ch.effect == 5) {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.swap_colors_rb), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = ch.swapColors,
                            onCheckedChange = { on ->
                                vm.ledPost("/api/led/swapcol?$target&v=${if (on) 1 else 0}", optimistic = updateOptimistic { it.copy(swapColors = on) })
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EffectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 13.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

@Composable
fun ColorSlider(label: String, value: Float, color: Color, onChange: (Float) -> Unit, onFinished: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = color, fontSize = 13.sp, modifier = Modifier.width(20.dp))
        Slider(
            value = value, onValueChange = onChange,
            onValueChangeFinished = onFinished,
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color),
            modifier = Modifier.weight(1f)
        )
        Text(value.roundToInt().toString(), color = MaterialTheme.colorScheme.primary,
            fontSize = 12.sp, modifier = Modifier.width(36.dp))
    }
}

@Composable
fun LabeledSlider(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit, onFinished: () -> Unit) {
    Column(Modifier.padding(top = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Text(value.roundToInt().toString(), color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
        }
        Slider(
            value = value, onValueChange = onChange, 
            onValueChangeFinished = onFinished,
            valueRange = min..max
        )
    }
}
