package com.osakapatnam.konnichiwah.ui.directions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LineColoursTest {
    // The official colours of lines a traveller is likely to ride, and what
    // the signs look like to them.
    @Test
    fun namesTheColoursOfRealLines() {
        val lines = mapOf(
            "#F62E36" to LineColour.RED, // Tokyo Metro Marunouchi
            "#FF9500" to LineColour.ORANGE, // Tokyo Metro Ginza
            "#B5B5AC" to LineColour.SILVER, // Tokyo Metro Hibiya
            "#009BBF" to LineColour.SKY_BLUE, // Tokyo Metro Tozai
            "#00BB85" to LineColour.GREEN, // Tokyo Metro Chiyoda
            "#C1A470" to LineColour.GOLD, // Tokyo Metro Yurakucho
            "#8F76D6" to LineColour.PURPLE, // Tokyo Metro Hanzomon
            "#00AC9B" to LineColour.TEAL, // Tokyo Metro Namboku
            "#9C5E31" to LineColour.BROWN, // Tokyo Metro Fukutoshin
            "#E85298" to LineColour.PINK, // Toei Asakusa
            "#0079C2" to LineColour.BLUE, // Toei Mita
            "#6CBB5A" to LineColour.LIGHT_GREEN, // Toei Shinjuku
            "#B6007A" to LineColour.MAGENTA, // Toei Oedo
            "#9ACD32" to LineColour.LIGHT_GREEN, // JR Yamanote
            "#FFD400" to LineColour.YELLOW, // JR Chuo-Sobu
            "#F15A22" to LineColour.ORANGE, // JR Chuo Rapid
            "#00B2E5" to LineColour.SKY_BLUE, // JR Keihin-Tohoku
            "#E5171F" to LineColour.RED, // Osaka Metro Midosuji
        )
        lines.forEach { (hex, want) -> assertEquals(hex, want, LineColour.nearest(hex)) }
    }

    @Test
    fun acceptsColoursWithoutTheHash() {
        assertEquals(LineColour.RED, LineColour.nearest("F62E36"))
    }

    @Test
    fun noColourIsNoName() {
        assertNull(LineColour.nearest(null))
        assertNull(LineColour.nearest(""))
        assertNull(LineColour.nearest("red"))
    }
}
