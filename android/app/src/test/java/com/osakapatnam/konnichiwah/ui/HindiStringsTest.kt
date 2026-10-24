package com.osakapatnam.konnichiwah.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

// Checks the Hindi strings against the English ones: every string must be
// translated, with the same placeholders (%d, %1$s…), so a Hindi screen
// never shows an English line or crashes on a missing argument.
class HindiStringsTest {

    // Strings that rightly have no Devanagari: the language switch says
    // "English", and the others are only names, numbers and symbols.
    private val noHindiNeeded = setOf("switch_language", "food_score", "it_date_range", "menu_flag")

    private fun read(dir: String): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$dir/strings.xml"))
        val out = mutableMapOf<String, String>()
        val nodes = doc.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as? Element ?: continue
            if (el.tagName == "bool") continue
            // Plurals and arrays: join their items, so placeholders and
            // Devanagari are checked across all of them.
            out[el.tagName + ":" + el.getAttribute("name")] = el.textContent.trim()
        }
        return out
    }

    private val english = read("values")
    private val hindi = read("values-hi")

    private fun placeholders(s: String) = Regex("""%(\d+\$)?(\.\d+)?[sdf]""").findAll(s).map { it.value }.toSortedSet()

    @Test
    fun everyStringIsTranslated() {
        val missing = english.keys - hindi.keys - "string:app_name"
        assertTrue("not in values-hi: $missing", missing.isEmpty())

        val extra = hindi.keys - english.keys
        assertTrue("only in values-hi: $extra", extra.isEmpty())
    }

    @Test
    fun hindiStringsAreInHindi() {
        val devanagari = Regex("[\\u0900-\\u097F]")
        val english = hindi.filter { (key, text) -> key.substringAfter(':') !in noHindiNeeded && !devanagari.containsMatchIn(text) }
        assertTrue("not in Devanagari: ${english.keys}", english.isEmpty())
    }

    @Test
    fun placeholdersMatch() {
        for ((key, text) in english) {
            val translated = hindi[key] ?: continue
            assertEquals("placeholders in $key", placeholders(text), placeholders(translated))
        }
    }
}
