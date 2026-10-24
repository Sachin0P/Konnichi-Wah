package com.osakapatnam.konnichiwah.data

import com.osakapatnam.konnichiwah.data.remote.AvoidItem
import com.osakapatnam.konnichiwah.data.remote.Diet
import com.osakapatnam.konnichiwah.data.remote.PhraseCardDto
import com.osakapatnam.konnichiwah.data.remote.UserProfileDto

// The avoid items each diet's card already covers, so a vegetarian isn't
// also shown a separate "no pork" card. Keep in step with dietCovers in
// backend/internal/companion/tools.go.
private val dietCovers = mapOf(
    Diet.VEG to setOf(AvoidItem.BEEF, AvoidItem.PORK, AvoidItem.SEAFOOD, AvoidItem.EGG),
    Diet.JAIN to AvoidItem.entries.toSet(),
    Diet.EGGETARIAN to setOf(AvoidItem.BEEF, AvoidItem.PORK, AvoidItem.SEAFOOD),
    Diet.HALAL to setOf(AvoidItem.PORK, AvoidItem.ALCOHOL),
)

// The JSON strings phrases.json uses for diets and avoid items.
private val dietKey = mapOf(
    Diet.VEG to "veg", Diet.JAIN to "jain", Diet.EGGETARIAN to "eggetarian",
    Diet.HALAL to "halal", Diet.NON_VEG to "non_veg",
)
private val avoidKey = mapOf(
    AvoidItem.BEEF to "beef", AvoidItem.PORK to "pork", AvoidItem.SEAFOOD to "seafood",
    AvoidItem.EGG to "egg", AvoidItem.ONION_GARLIC to "onion_garlic",
    AvoidItem.ALCOHOL to "alcohol",
)

// The traveller's own cards, in the order to show them: their diet card,
// then a card for each thing they avoid that it doesn't cover, then (for
// anyone who can't have fish stock) the "does this contain dashi?" question.
// The same selection the companion's show_diet_card tool makes.
fun dietCardsFor(profile: UserProfileDto?, cards: List<PhraseCardDto>): List<PhraseCardDto> {
    if (profile == null) return emptyList()

    val covered = dietCovers[profile.diet].orEmpty()
    val chosen = mutableListOf<PhraseCardDto>()

    cards.firstOrNull { it.diet == dietKey[profile.diet] }?.let(chosen::add)
    for (item in profile.avoid) {
        if (item !in covered) {
            cards.firstOrNull { it.avoid == avoidKey[item] }?.let(chosen::add)
        }
    }

    val noFishStock = profile.diet in setOf(Diet.VEG, Diet.JAIN, Diet.EGGETARIAN) || AvoidItem.SEAFOOD in profile.avoid
    if (noFishStock) {
        cards.firstOrNull { it.ask == "dashi" }?.let(chosen::add)
    }

    return chosen
}
