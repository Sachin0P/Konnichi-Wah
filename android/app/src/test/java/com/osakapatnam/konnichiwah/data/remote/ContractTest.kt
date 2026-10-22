package com.osakapatnam.konnichiwah.data.remote

import com.osakapatnam.konnichiwah.di.NetworkModule
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

// Checks that our DTOs produce exactly the JSON in docs/api.md. If one of
// these fails, the app and the backend have drifted apart.
class ContractTest {

    private val json = NetworkModule.provideJson()

    @Test
    fun tripRequestMatchesTheBackendContract() {
        val request = TripRequestDto(
            profile = UserProfileDto(
                interests = listOf(Interest.TEMPLES_SHRINES, Interest.FOOD),
                budgetBand = BudgetBand.LAKH_2_TO_3,
                pace = Pace.BALANCED,
                groupType = GroupType.FAMILY_ELDERS,
                diet = Diet.JAIN,
                avoid = listOf(AvoidItem.ONION_GARLIC),
                spiceLevel = SpiceLevel.INDIAN_SPICY,
                japaneseLevel = JapaneseLevel.NONE,
            ),
            tripDays = 7,
            travelMonth = 4,
        )

        val expected = Json.parseToJsonElement(
            """
            {
              "profile": {
                "interests": ["temples_shrines", "food"],
                "budget_band": "2-3L",
                "pace": "balanced",
                "group_type": "family_elders",
                "diet": "jain",
                "avoid": ["onion_garlic"],
                "spice_level": "indian_spicy",
                "japanese_level": "none",
                "notes": ""
              },
              "trip_days": 7,
              "travel_month": 4
            }
            """,
        )

        assertEquals(expected, json.encodeToJsonElement(TripRequestDto.serializer(), request))
    }

    @Test
    fun planResponseIgnoresUnknownKeys() {
        // A future backend may add fields; older app builds must still parse.
        val body = """
            {"plan": {"cities": [{"name": "Tokyo", "days": 3, "reason": "r", "new_field": 1}],
                      "suggestions": [{"id": "s1", "kind": "place", "name": "Senso-ji", "city": "Tokyo",
                                       "place_type": "castle", "reason": "r"}],
                      "source": "llm", "metadata": {}}}
        """

        val plan = json.decodeFromString(PlanEnvelope.serializer(), body).plan

        assertEquals("castle", plan.suggestions.single().placeType)
        assertEquals(3, plan.cities.single().days)
    }

    @Test
    fun everyEnumValueSerializesToTheBackendString() {
        // The full lists of permitted values from docs/api.md.
        fun <T> names(serializer: KSerializer<T>, values: List<T>) =
            values.map { json.encodeToJsonElement(serializer, it).jsonPrimitive.content }

        assertEquals(
            listOf("temples_shrines", "anime", "food", "nature", "shopping", "snow", "nightlife", "history"),
            names(Interest.serializer(), Interest.entries),
        )
        assertEquals(listOf("1-2L", "2-3L", "3L+"), names(BudgetBand.serializer(), BudgetBand.entries))
        assertEquals(listOf("relaxed", "balanced", "packed"), names(Pace.serializer(), Pace.entries))
        assertEquals(
            listOf("solo", "couple", "friends", "family_kids", "family_elders"),
            names(GroupType.serializer(), GroupType.entries),
        )
        assertEquals(listOf("veg", "jain", "eggetarian", "halal", "non_veg"), names(Diet.serializer(), Diet.entries))
        assertEquals(
            listOf("beef", "pork", "seafood", "egg", "onion_garlic"),
            names(AvoidItem.serializer(), AvoidItem.entries),
        )
        assertEquals(listOf("mild", "medium", "indian_spicy"), names(SpiceLevel.serializer(), SpiceLevel.entries))
        assertEquals(listOf("none", "basic"), names(JapaneseLevel.serializer(), JapaneseLevel.entries))
    }
}
