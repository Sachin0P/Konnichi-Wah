package com.osakapatnam.konnichiwah.ui.directions

import androidx.annotation.StringRes
import com.osakapatnam.konnichiwah.R

// The colours a traveller would call a line's signs, with a typical shade of
// each. In Japan every line has its own colour on the signs, maps and
// platforms (the Marunouchi Line is red, the Ginza Line orange), and "follow
// the red signs" is easier to act on in a crowded station than a line name.
enum class LineColour(val rgb: Int, @StringRes val label: Int) {
    RED(0xE60012, R.string.colour_red),
    ORANGE(0xF39700, R.string.colour_orange),
    YELLOW(0xFFD400, R.string.colour_yellow),
    LIGHT_GREEN(0x9ACD32, R.string.colour_light_green),
    GREEN(0x00B26B, R.string.colour_green),
    TEAL(0x00AC9B, R.string.colour_teal),
    SKY_BLUE(0x00A7DB, R.string.colour_sky_blue),
    BLUE(0x0068B7, R.string.colour_blue),
    NAVY(0x1D2088, R.string.colour_navy),
    PURPLE(0x8F76D6, R.string.colour_purple),
    PINK(0xE85298, R.string.colour_pink),
    MAGENTA(0xB6007A, R.string.colour_magenta),
    BROWN(0x9C5E31, R.string.colour_brown),
    GOLD(0xC1A470, R.string.colour_gold),
    SILVER(0xB5B5AC, R.string.colour_silver),
    ;

    companion object {
        // The named colour nearest to a line's colour ("#F62E36" is red), or
        // null when there's no colour or it isn't a hex colour. Distance is
        // the "redmean" approximation, which weighs red, green and blue
        // roughly as the eye does, so yellows and greens don't swap.
        fun nearest(hex: String?): LineColour? {
            val rgb = hex?.removePrefix("#")?.takeIf { it.length == 6 }?.toIntOrNull(16) ?: return null
            return entries.minBy { distance(rgb, it.rgb) }
        }

        private fun distance(a: Int, b: Int): Double {
            val r1 = a shr 16 and 0xFF
            val r2 = b shr 16 and 0xFF
            val rMean = (r1 + r2) / 2.0
            val dr = (r1 - r2).toDouble()
            val dg = ((a shr 8 and 0xFF) - (b shr 8 and 0xFF)).toDouble()
            val db = ((a and 0xFF) - (b and 0xFF)).toDouble()
            return (2 + rMean / 256) * dr * dr + 4 * dg * dg + (2 + (255 - rMean) / 256) * db * db
        }
    }
}
