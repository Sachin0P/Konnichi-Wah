package com.osakapatnam.konnichiwah.data.remote

import com.osakapatnam.konnichiwah.di.NetworkModule
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
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
    fun itineraryResponseParses() {
        // The example from docs/api.md, including the fields the app doesn't
        // model yet (created_at).
        val body = """
            {"itinerary": {"id": 4, "created_at": "2026-10-23T12:30:53+05:30", "title": "My 5-day Japan trip",
              "start_date": null, "version": 1,
              "days": [{"index": 1, "city": "Tokyo", "stops": [
                {"id": "st_m4bm", "time_slot": "morning", "place_name": "Senso-ji Temple, Asakusa",
                 "place_type": "temple", "city": "Tokyo", "note": "Tokyo's oldest temple...",
                 "lat": null, "lng": null, "google_place_id": null,
                 "tip_ids": ["temple-photos", "temple-shoes"]}]}]}}
        """

        val itinerary = json.decodeFromString(ItineraryEnvelope.serializer(), body).itinerary
        val stop = itinerary.days.single().stops.single()

        assertEquals(4L, itinerary.id)
        assertEquals("morning", stop.timeSlot)
        assertEquals(listOf("temple-photos", "temple-shoes"), stop.tipIds)
        assertEquals(null, stop.lat)
    }

    @Test
    fun updateRequestSendsEveryStopField() {
        // The backend's strict decoder rejects unknown keys, and we send
        // defaults too, so the PATCH body must use exactly the API's names.
        val request = UpdateItineraryRequestDto(
            days = listOf(DayDto(1, "Kyoto", listOf(StopDto("id-1", "evening", "Gion walk", "street", "Kyoto")))),
        )

        val stop = json.encodeToJsonElement(UpdateItineraryRequestDto.serializer(), request)
            .jsonObject["days"]!!.jsonArray[0].jsonObject["stops"]!!.jsonArray[0].jsonObject

        assertEquals(
            setOf("id", "time_slot", "place_name", "place_type", "city", "note", "lat", "lng", "google_place_id", "tip_ids"),
            stop.keys,
        )
    }

    @Test
    fun chatRequestAndReplyMatchTheBackendContract() {
        val request = ChatRequestDto(
            messages = listOf(ChatMessageDto("user", "Temple tips?")),
            context = ChatContextDto(city = "Kyoto", dayIndex = 3, stops = listOf(ChatStopDto("Fushimi Inari Taisha", "shrine", "morning"))),
        )

        val js = json.encodeToJsonElement(ChatRequestDto.serializer(), request).jsonObject
        val context = js["context"]!!.jsonObject

        assertEquals(setOf("city", "day_index", "stops", "profile", "location"), context.keys)
        assertEquals(setOf("place_name", "place_type", "time_slot"), context["stops"]!!.jsonArray[0].jsonObject.keys)

        val reply = json.decodeFromString(
            ChatReplyEnvelope.serializer(),
            """{"reply": {"text": "Show this card.", "phrases": [{"english": "e", "japanese": "日本語", "romaji": "r"}], "tools_used": ["show_diet_card"]}}""",
        ).reply

        assertEquals("日本語", reply.phrases.single().japanese)
        assertEquals(listOf("show_diet_card"), reply.toolsUsed)
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
