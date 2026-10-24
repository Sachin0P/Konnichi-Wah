package com.osakapatnam.konnichiwah.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JapaneseTransliteratorTest {

    @Test
    fun detectsJapaneseRunesProperly() {
        assertTrue(JapaneseTransliterator.hasJapanese("神田"))
        assertTrue(JapaneseTransliterator.hasJapanese("とうきょう"))
        assertTrue(JapaneseTransliterator.hasJapanese("クリニック"))
        assertFalse(JapaneseTransliterator.hasJapanese("Tokyo Station"))
        assertFalse(JapaneseTransliterator.hasJapanese("St. Luke's Hospital"))
    }

    @Test
    fun cleansStationMetadata() {
        assertEquals("神田", JapaneseTransliterator.cleanStation("神田(東京都)"))
        assertEquals("新宿", JapaneseTransliterator.cleanStation("新宿(ＪＲ・小田急)"))
        assertEquals("なんば", JapaneseTransliterator.cleanStation("なんば(南海)"))
        assertEquals("大崎", JapaneseTransliterator.cleanStation("大崎 行き"))
        assertEquals("京都", JapaneseTransliterator.cleanStation("JR京都駅"))
        assertEquals("銀座", JapaneseTransliterator.cleanStation("東京メトロ銀座駅"))
    }

    @Test
    fun romanizesStations() {
        assertEquals("Kanda", JapaneseTransliterator.romanizeStation("神田(東京都)"))
        assertEquals("Shinjuku", JapaneseTransliterator.romanizeStation("新宿"))
        assertEquals("Tokyo", JapaneseTransliterator.romanizeStation("東京"))
        assertEquals("Todai-mae", JapaneseTransliterator.romanizeStation("東大前"))
        assertEquals("Shin-Osaka", JapaneseTransliterator.romanizeStation("新大阪"))
    }

    @Test
    fun translatesHospitalsAndClinics() {
        assertEquals("Yamamoto Hospital", JapaneseTransliterator.englishPlaceName("山元病院", "hospital"))
        assertEquals("Matsuoka Eye Clinic", JapaneseTransliterator.englishPlaceName("松岡眼科", "hospital"))
        assertEquals("Saikyo Clinic", JapaneseTransliterator.englishPlaceName("さいきょうクリニック", "clinic"))
        assertEquals("Kitamura Clinic", JapaneseTransliterator.englishPlaceName("北村医院", "clinic"))
        assertEquals("Tokyo Tama Hospital", JapaneseTransliterator.englishPlaceName("東京多摩病院", "hospital"))
        assertEquals("Ishikawajima Memorial Hospital", JapaneseTransliterator.englishPlaceName("医療法人社団健育会 石川島記念病院", "hospital"))
    }

    @Test
    fun resolvesStopForWayfinding() {
        val (en1, ja1) = JapaneseTransliterator.resolveStop("神田(東京都)", "")
        assertEquals("Kanda", en1)
        assertEquals("神田", ja1)

        val (en2, ja2) = JapaneseTransliterator.resolveStop("Tokyo", "東京")
        assertEquals("Tokyo", en2)
        assertEquals("東京", ja2)
    }
}
