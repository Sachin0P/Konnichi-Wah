package com.osakapatnam.konnichiwah.data

import com.osakapatnam.konnichiwah.data.remote.AvoidItem
import com.osakapatnam.konnichiwah.data.remote.BudgetBand
import com.osakapatnam.konnichiwah.data.remote.Diet
import com.osakapatnam.konnichiwah.data.remote.GroupType
import com.osakapatnam.konnichiwah.data.remote.JapaneseLevel
import com.osakapatnam.konnichiwah.data.remote.Pace
import com.osakapatnam.konnichiwah.data.remote.SpiceLevel
import com.osakapatnam.konnichiwah.data.remote.UserProfileDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuWordsTest {

    private fun concerns(text: String) = menuFlags(text).map { it.concern }.toSet()

    @Test
    fun findsIngredientsWrittenOnTheMenu() {
        assertEquals(setOf(MenuConcern.PORK), concerns("豚の生姜焼き定食"))
        assertEquals(setOf(MenuConcern.BEEF), concerns("牛タン"))
        assertEquals(setOf(MenuConcern.SEAFOOD), concerns("海老フライ"))
    }

    @Test
    fun findsWhatDishesUsuallyContain() {
        // Miso soup is made with dashi, and ramen broth is usually pork.
        assertTrue(MenuConcern.DASHI in concerns("味噌汁"))
        assertTrue(MenuConcern.PORK in concerns("醤油ラーメン"))
        // Oyakodon is chicken and egg.
        assertEquals(setOf(MenuConcern.CHICKEN, MenuConcern.EGG), concerns("親子丼"))
    }

    @Test
    fun aSpecificMeatReplacesTheGeneralWarning() {
        assertEquals(setOf(MenuConcern.PORK, MenuConcern.SEAFOOD), concerns("豚骨ラーメン 海老"))
        assertEquals(setOf(MenuConcern.MEAT), concerns("野菜カレー"))
    }

    @Test
    fun misreadSoundMarksStillMatch() {
        // Text recognition read 天ぷら as 天ぶら in testing.
        assertTrue(MenuConcern.DASHI in concerns("海老天ぶら"))
    }

    @Test
    fun milkAndBurdockArentBeef() {
        assertFalse(MenuConcern.BEEF in concerns("牛乳"))
        assertFalse(MenuConcern.BEEF in concerns("牛蒡サラダ"))
        assertTrue(MenuConcern.BEEF in concerns("牛乳と牛丼"))
    }

    @Test
    fun englishWordsMatchWholeWordsOnly() {
        assertEquals(setOf(MenuConcern.EGG), concerns("Fried eggs"))
        assertFalse(MenuConcern.EGG in concerns("Grilled eggplant"))
        assertEquals(MenuFlag(MenuConcern.PORK, "pork"), menuFlags("Pork cutlet").single())
    }

    @Test
    fun aPlainVegetableDishHasNoFlags() {
        assertEquals(emptyList<MenuFlag>(), menuFlags("野菜サラダ"))
    }

    private fun profile(diet: Diet, avoid: List<AvoidItem> = emptyList()) = UserProfileDto(
        interests = emptyList(),
        budgetBand = BudgetBand.LAKH_1_TO_2,
        pace = Pace.BALANCED,
        groupType = GroupType.SOLO,
        diet = diet,
        avoid = avoid,
        spiceLevel = SpiceLevel.MEDIUM,
        japaneseLevel = JapaneseLevel.NONE,
    )

    @Test
    fun concernsFollowTheDiet() {
        assertTrue(MenuConcern.DASHI in menuConcernsFor(profile(Diet.VEG)))
        assertFalse(MenuConcern.EGG in menuConcernsFor(profile(Diet.EGGETARIAN)))
        assertTrue(MenuConcern.ONION_GARLIC in menuConcernsFor(profile(Diet.JAIN)))
        assertTrue(MenuConcern.ALCOHOL in menuConcernsFor(profile(Diet.HALAL)))
        assertEquals(emptySet<MenuConcern>(), menuConcernsFor(profile(Diet.NON_VEG)))
    }

    @Test
    fun avoidItemsAddToTheDiet() {
        assertEquals(
            setOf(MenuConcern.BEEF, MenuConcern.PORK, MenuConcern.MEAT),
            menuConcernsFor(profile(Diet.NON_VEG, listOf(AvoidItem.BEEF, AvoidItem.PORK))),
        )
    }
}
