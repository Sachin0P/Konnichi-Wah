package com.osakapatnam.konnichiwah.data

import com.osakapatnam.konnichiwah.data.remote.AvoidItem
import com.osakapatnam.konnichiwah.data.remote.BudgetBand
import com.osakapatnam.konnichiwah.data.remote.Diet
import com.osakapatnam.konnichiwah.data.remote.GroupType
import com.osakapatnam.konnichiwah.data.remote.Interest
import com.osakapatnam.konnichiwah.data.remote.JapaneseLevel
import com.osakapatnam.konnichiwah.data.remote.Pace
import com.osakapatnam.konnichiwah.data.remote.PhraseCardDto
import com.osakapatnam.konnichiwah.data.remote.SpiceLevel
import com.osakapatnam.konnichiwah.data.remote.UserProfileDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

// Uses the real phrases.json, the same file the build copies into the APK.
class DietCardsTest {

    private val cards = Json.decodeFromString<List<PhraseCardDto>>(File("../../backend/rulebook/phrases.json").readText())

    private fun profile(diet: Diet, vararg avoid: AvoidItem) = UserProfileDto(
        interests = listOf(Interest.FOOD),
        budgetBand = BudgetBand.LAKH_2_TO_3,
        pace = Pace.BALANCED,
        groupType = GroupType.SOLO,
        diet = diet,
        avoid = avoid.toList(),
        spiceLevel = SpiceLevel.MEDIUM,
        japaneseLevel = JapaneseLevel.NONE,
    )

    @Test
    fun matchesTheBackendsSelection() {
        // The same cases as TestDietCardsComeFromTheCheckedFile in the
        // backend, so the app and the companion always show the same cards.
        assertEquals(listOf("diet-jain", "ask-dashi"), dietCardsFor(profile(Diet.JAIN, AvoidItem.ONION_GARLIC), cards).map { it.id })
        assertEquals(listOf("diet-veg", "ask-dashi"), dietCardsFor(profile(Diet.VEG), cards).map { it.id })
        assertEquals(listOf("avoid-beef", "avoid-pork"), dietCardsFor(profile(Diet.NON_VEG, AvoidItem.BEEF, AvoidItem.PORK), cards).map { it.id })
        assertEquals(
            listOf("diet-halal", "avoid-seafood", "ask-dashi"),
            dietCardsFor(profile(Diet.HALAL, AvoidItem.PORK, AvoidItem.SEAFOOD), cards).map { it.id },
        )
    }

    @Test
    fun noProfileMeansNoPersonalCards() {
        assertEquals(emptyList<PhraseCardDto>(), dietCardsFor(null, cards))
    }
}
