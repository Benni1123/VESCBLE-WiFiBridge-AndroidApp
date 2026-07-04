package eu.benni1123.vescbridge

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// Einfaches Farbton-Rad (HSV-Hue-Ring). Tippen/Ziehen auf dem Ring waehlt den
// Farbton; Saettigung und Helligkeit bleiben voll (reine, kraeftige Farbe).
// Fuer Feinabstimmung dienen weiterhin die RGB-Slider darunter.
@Composable
fun ColorWheel(
    r: Int, g: Int, b: Int,
    sizeDp: Int = 180,
    onHueChanged: (Int, Int, Int) -> Unit,
    onHuePicked: (Int, Int, Int) -> Unit
) {
    Canvas(
        modifier = Modifier
            .size(sizeDp.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { pos -> 
                        handlePick(pos, size.width, size.height) { nr, ng, nb ->
                            onHueChanged(nr, ng, nb)
                            onHuePicked(nr, ng, nb)
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                var lastR = r; var lastG = g; var lastB = b
                detectDragGestures(
                    onDrag = { change, _ ->
                        handlePick(change.position, size.width, size.height) { nr, ng, nb ->
                            lastR = nr; lastG = ng; lastB = nb
                            onHueChanged(nr, ng, nb)
                        }
                        change.consume()
                    },
                    onDragEnd = { onHuePicked(lastR, lastG, lastB) },
                    onDragCancel = { onHuePicked(lastR, lastG, lastB) }
                )
            }
    ) {
        val ringWidth = size.minDimension * 0.18f
        val radius = size.minDimension / 2f
        // Farbton-Ring aus vielen Segmenten zeichnen (Sweep-Gradient-Ersatz).
        val sweep = Brush.sweepGradient(
            listOf(
                Color.Red, Color.Yellow, Color.Green, Color.Cyan,
                Color.Blue, Color.Magenta, Color.Red
            ),
            center = center
        )
        drawCircle(
            brush = sweep,
            radius = radius - ringWidth / 2f,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = ringWidth)
        )

        // Aktuelle Position markieren: aus RGB den Farbton bestimmen.
        val hue = rgbToHue(r, g, b)
        val ang = Math.toRadians(hue.toDouble())
        val markR = radius - ringWidth / 2f
        val mx = center.x + (markR * cos(ang)).toFloat()
        val my = center.y + (markR * sin(ang)).toFloat()
        drawCircle(Color.White, radius = ringWidth * 0.45f, center = Offset(mx, my),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
    }
}

private fun handlePick(pos: Offset, w: Int, h: Int, cb: (Int, Int, Int) -> Unit) {
    val cx = w / 2f
    val cy = h / 2f
    val dx = pos.x - cx
    val dy = pos.y - cy
    // Winkel -> Farbton (0..360)
    var deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
    if (deg < 0) deg += 360.0
    val rgb = hsvToRgb(deg.toFloat(), 1f, 1f)
    cb(rgb[0], rgb[1], rgb[2])
}

// HSV -> RGB (S=1, V=1 hier; allgemein gehalten).
fun hsvToRgb(h: Float, s: Float, v: Float): IntArray {
    val c = v * s
    val x = c * (1 - kotlin.math.abs((h / 60f) % 2 - 1))
    val m = v - c
    val (r1, g1, b1) = when {
        h < 60  -> Triple(c, x, 0f)
        h < 120 -> Triple(x, c, 0f)
        h < 180 -> Triple(0f, c, x)
        h < 240 -> Triple(0f, x, c)
        h < 300 -> Triple(x, 0f, c)
        else    -> Triple(c, 0f, x)
    }
    return intArrayOf(
        ((r1 + m) * 255).toInt().coerceIn(0, 255),
        ((g1 + m) * 255).toInt().coerceIn(0, 255),
        ((b1 + m) * 255).toInt().coerceIn(0, 255)
    )
}

// RGB -> Farbton (nur Hue, fuer die Markierung).
fun rgbToHue(r: Int, g: Int, b: Int): Float {
    val rf = r / 255f; val gf = g / 255f; val bf = b / 255f
    val max = maxOf(rf, gf, bf); val min = minOf(rf, gf, bf)
    val d = max - min
    if (d == 0f) return 0f
    val h = when (max) {
        rf -> 60f * (((gf - bf) / d) % 6f)
        gf -> 60f * (((bf - rf) / d) + 2f)
        else -> 60f * (((rf - gf) / d) + 4f)
    }
    return if (h < 0) h + 360f else h
}