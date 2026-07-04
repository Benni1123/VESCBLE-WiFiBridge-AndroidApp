package eu.benni1123.vescbridge

import org.json.JSONObject

// Einstellungen eines LED-Kanals (Spiegel der Bridge-Struktur).
data class LedChannel(
    val pin: Int,
    val count: Int,
    val synced: Boolean,
    val effect: Int,     // 0=Aus, 1=Solid, 2=Knight Rider
    val r: Int,
    val g: Int,
    val b: Int,
    val bright: Int,
    val krspeed: Int,
    val krwidth: Int,
    val polhz: Int,
    val swapColors: Boolean
)

// Gesamte LED-Config: Anzahl aktiver Kanaele + alle 4 Kanaele.
data class LedConfig(
    val count: Int,
    val channels: List<LedChannel>
) {
    companion object {
        fun parse(json: String): LedConfig {
            val o = JSONObject(json)
            val arr = o.getJSONArray("channels")
            val list = (0 until arr.length()).map { i ->
                val c = arr.getJSONObject(i)
                LedChannel(
                    pin     = c.optInt("pin", 4),
                    count   = c.optInt("count", 30),
                    synced  = c.optBoolean("synced", false),
                    effect  = c.optInt("effect", 0),
                    r       = c.optInt("r", 0),
                    g       = c.optInt("g", 0),
                    b       = c.optInt("b", 255),
                    bright  = c.optInt("bright", 128),
                    krspeed = c.optInt("krspeed", 30),
                    krwidth = c.optInt("krwidth", 3),
                    polhz   = c.optInt("polhz", 4),
                    swapColors = c.optBoolean("swapcolors", false)
                )
            }
            return LedConfig(o.optInt("count", 1), list)
        }
    }
}
