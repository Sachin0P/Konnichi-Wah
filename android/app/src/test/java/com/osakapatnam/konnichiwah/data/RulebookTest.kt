package com.osakapatnam.konnichiwah.data

import com.osakapatnam.konnichiwah.data.remote.EtiquetteRuleDto
import com.osakapatnam.konnichiwah.data.remote.StopDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RulebookTest {

    private fun rule(id: String, severity: String, vararg placeTypes: String) =
        EtiquetteRuleDto(id = id, placeTypes = placeTypes.toList(), title = id, tip = "", severity = severity)

    private fun stop(placeType: String, tipIds: List<String> = emptyList()) =
        StopDto(id = "s", timeSlot = "morning", placeName = "x", placeType = placeType, city = "Tokyo", tipIds = tipIds)

    private val rulebook = Rulebook(
        listOf(
            rule("temple-quiet", "helpful", "temple"),
            rule("ryokan-tatami", "important", "ryokan", "temple"),
            rule("temple-shoes", "important", "temple"),
            rule("street-rubbish", "important", "street", "temple"),
        ),
    )

    @Test
    fun serverTipIdsWinAndUnknownIdsAreSkipped() {
        val tips = rulebook.tipsFor(stop("temple", tipIds = listOf("temple-quiet", "made-up", "temple-shoes")))

        assertEquals(listOf("temple-quiet", "temple-shoes"), tips.map { it.id })
    }

    @Test
    fun withoutTipIdsFallBackToTheBackendsOrdering() {
        // Important first; then rules written mainly for temples; then by id.
        val tips = rulebook.tipsFor(stop("temple"))

        assertEquals(listOf("temple-shoes", "ryokan-tatami", "street-rubbish", "temple-quiet"), tips.map { it.id })
    }

    @Test
    fun cityRulesOnlyApplyInTheirCity() {
        val parks = Rulebook(
            listOf(
                EtiquetteRuleDto(id = "park-nara-deer", placeTypes = listOf("park"), cities = listOf("Nara"), title = "", tip = "", severity = "important"),
                rule("park-trails", "helpful", "park"),
            ),
        )

        val inTokyo = StopDto(id = "s", timeSlot = "morning", placeName = "Shinjuku Gyoen", placeType = "park", city = "Tokyo")
        val inNara = inTokyo.copy(placeName = "Nara Park", city = "nara")

        assertEquals(listOf("park-trails"), parks.tipsFor(inTokyo).map { it.id })
        assertEquals(listOf("park-nara-deer", "park-trails"), parks.tipsFor(inNara).map { it.id })

        // Old server tip ids (from before the rule had a city) are filtered too.
        assertEquals(listOf("park-trails"), parks.tipsFor(inTokyo.copy(tipIds = listOf("park-nara-deer", "park-trails"))).map { it.id })
    }

    @Test
    fun anUnknownPlaceTypeHasNoTips() {
        assertEquals(emptyList<EtiquetteRuleDto>(), rulebook.tipsFor(stop("castle")))
    }

    @Test
    fun theRealRulebookGivesEveryAddablePlaceTypeSomeTips() {
        // Reads the same file the build copies into the APK.
        val text = File("../../backend/rulebook/etiquette.json").readText()
        val real = Rulebook(Json.decodeFromString<List<EtiquetteRuleDto>>(text))

        for (placeType in com.osakapatnam.konnichiwah.ui.itinerary.ADDABLE_PLACE_TYPES) {
            val tips = real.tipsFor(stop(placeType))
            assertTrue("no tips for $placeType", tips.isNotEmpty())
            assertTrue("too many tips for $placeType", tips.size <= MAX_TIPS_PER_STOP)
        }
    }
}
