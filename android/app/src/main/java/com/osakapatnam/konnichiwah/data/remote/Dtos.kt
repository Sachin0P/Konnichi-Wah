package com.osakapatnam.konnichiwah.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// These types mirror the JSON contract in docs/api.md. The enum values must
// match the backend's permitted values (backend/internal/data/profiles.go)
// exactly, because the backend rejects anything else with a 422.

@Serializable
enum class Interest {
    @SerialName("temples_shrines") TEMPLES_SHRINES,
    @SerialName("anime") ANIME,
    @SerialName("food") FOOD,
    @SerialName("nature") NATURE,
    @SerialName("shopping") SHOPPING,
    @SerialName("snow") SNOW,
    @SerialName("nightlife") NIGHTLIFE,
    @SerialName("history") HISTORY,
}

@Serializable
enum class BudgetBand {
    @SerialName("1-2L") LAKH_1_TO_2,
    @SerialName("2-3L") LAKH_2_TO_3,
    @SerialName("3L+") LAKH_3_PLUS,
}

@Serializable
enum class Pace {
    @SerialName("relaxed") RELAXED,
    @SerialName("balanced") BALANCED,
    @SerialName("packed") PACKED,
}

@Serializable
enum class GroupType {
    @SerialName("solo") SOLO,
    @SerialName("couple") COUPLE,
    @SerialName("friends") FRIENDS,
    @SerialName("family_kids") FAMILY_KIDS,
    @SerialName("family_elders") FAMILY_ELDERS,
}

@Serializable
enum class Diet {
    @SerialName("veg") VEG,
    @SerialName("jain") JAIN,
    @SerialName("eggetarian") EGGETARIAN,
    @SerialName("halal") HALAL,
    @SerialName("non_veg") NON_VEG,
}

@Serializable
enum class AvoidItem {
    @SerialName("beef") BEEF,
    @SerialName("pork") PORK,
    @SerialName("seafood") SEAFOOD,
    @SerialName("egg") EGG,
    @SerialName("onion_garlic") ONION_GARLIC,
}

@Serializable
enum class SpiceLevel {
    @SerialName("mild") MILD,
    @SerialName("medium") MEDIUM,
    @SerialName("indian_spicy") INDIAN_SPICY,
}

@Serializable
enum class JapaneseLevel {
    @SerialName("none") NONE,
    @SerialName("basic") BASIC,
}

@Serializable
data class UserProfileDto(
    val interests: List<Interest>,
    @SerialName("budget_band") val budgetBand: BudgetBand,
    val pace: Pace,
    @SerialName("group_type") val groupType: GroupType,
    val diet: Diet,
    val avoid: List<AvoidItem> = emptyList(),
    @SerialName("spice_level") val spiceLevel: SpiceLevel,
    @SerialName("japanese_level") val japaneseLevel: JapaneseLevel,
    val notes: String = "",
)

@Serializable
data class TripRequestDto(
    val profile: UserProfileDto,
    @SerialName("trip_days") val tripDays: Int,
    @SerialName("travel_month") val travelMonth: Int,
)

// Response types. Values the backend may add to over time (place_type, kind,
// severity, source) are plain Strings, so a new value doesn't crash older
// app builds.

@Serializable
data class HealthcheckDto(
    val status: String,
    @SerialName("system_info") val systemInfo: Map<String, String>,
)

// cities is only set for rules that apply in specific places (e.g. Nara's
// deer); an empty list means everywhere.
@Serializable
data class EtiquetteRuleDto(
    val id: String,
    @SerialName("place_types") val placeTypes: List<String>,
    val cities: List<String> = emptyList(),
    val title: String,
    val tip: String,
    val severity: String,
) {
    fun appliesIn(city: String): Boolean = cities.isEmpty() || cities.any { it.equals(city, ignoreCase = true) }
}

@Serializable
data class EtiquetteEnvelope(
    @SerialName("etiquette_rules") val rules: List<EtiquetteRuleDto>,
)

@Serializable
data class CityPlanDto(
    val name: String,
    val days: Int,
    val reason: String,
)

@Serializable
data class SuggestionDto(
    val id: String,
    val kind: String,
    val name: String,
    val city: String,
    @SerialName("place_type") val placeType: String,
    val reason: String,
)

@Serializable
data class PlanDto(
    val cities: List<CityPlanDto>,
    val suggestions: List<SuggestionDto>,
    val source: String,
)

@Serializable
data class PlanEnvelope(val plan: PlanDto)

// Itineraries (POST/GET/PATCH /v1/itineraries).

// Stop order is the position in the list; there's no separate order field.
// lat, lng and googlePlaceId stay null until the backend adds Google Places.
// tipIds are always set by the server from placeType.
@Serializable
data class StopDto(
    val id: String,
    @SerialName("time_slot") val timeSlot: String,
    @SerialName("place_name") val placeName: String,
    @SerialName("place_type") val placeType: String,
    val city: String,
    val note: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    @SerialName("google_place_id") val googlePlaceId: String? = null,
    @SerialName("tip_ids") val tipIds: List<String> = emptyList(),
)

@Serializable
data class DayDto(
    val index: Int,
    val city: String,
    val stops: List<StopDto>,
)

@Serializable
data class ItineraryDto(
    val id: Long,
    val title: String,
    @SerialName("start_date") val startDate: String? = null,
    val days: List<DayDto>,
    val version: Int,
)

@Serializable
data class ItineraryEnvelope(val itinerary: ItineraryDto)

@Serializable
data class CreateItineraryRequestDto(
    val title: String,
    @SerialName("start_date") val startDate: String? = null,
    val cities: List<CityPlanDto>,
    val suggestions: List<SuggestionDto>,
)

// A partial update of the days and the start date. A null start date is sent
// as null, which the server reads as "not changing it".
@Serializable
data class UpdateItineraryRequestDto(
    val days: List<DayDto>,
    @SerialName("start_date") val startDate: String? = null,
)

// Companion chat (POST /v1/companion/chat). The backend keeps no state: we
// send the conversation so far plus context about the day.

@Serializable
data class ChatMessageDto(val role: String, val text: String)

@Serializable
data class ChatStopDto(
    @SerialName("place_name") val placeName: String,
    @SerialName("place_type") val placeType: String,
    @SerialName("time_slot") val timeSlot: String,
)

@Serializable
data class ChatContextDto(
    val city: String = "",
    @SerialName("day_index") val dayIndex: Int = 0,
    val stops: List<ChatStopDto> = emptyList(),
    val profile: UserProfileDto? = null,
)

// language is "en" or "hi": the companion answers in it.
@Serializable
data class ChatRequestDto(
    val messages: List<ChatMessageDto>,
    val context: ChatContextDto,
    val language: String = "en",
)

// A Japanese phrase card to show staff.
@Serializable
data class PhraseDto(val english: String, val japanese: String, val romaji: String)

@Serializable
data class ChatReplyDto(
    val text: String,
    val phrases: List<PhraseDto> = emptyList(),
    @SerialName("tools_used") val toolsUsed: List<String> = emptyList(),
)

@Serializable
data class ChatReplyEnvelope(val reply: ChatReplyDto)

// A checked phrase card from phrases.json (bundled in the APK). Exactly one of
// diet, avoid or ask says what it's for.
@Serializable
data class PhraseCardDto(
    val id: String,
    val diet: String? = null,
    val avoid: String? = null,
    val ask: String? = null,
    val english: String,
    val japanese: String,
    val romaji: String,
) {
    fun phrase() = PhraseDto(english = english, japanese = japanese, romaji = romaji)
}

// Restaurant search (GET /v1/food), ranked with Desi Match.

@Serializable
data class DesiMatchDto(
    val score: Int,
    val reasons: List<String> = emptyList(),
    val caution: String? = null,
)

@Serializable
data class FoodResultDto(
    val id: String,
    val name: String,
    @SerialName("name_en") val nameEn: String? = null,
    val lat: Double,
    val lng: Double,
    val tags: Map<String, String> = emptyMap(),
    @SerialName("distance_m") val distanceM: Int,
    @SerialName("desi_match") val desiMatch: DesiMatchDto,
) {
    // English name first, with the Japanese one so it matches the sign.
    val displayName: String
        get() = if (!nameEn.isNullOrBlank() && nameEn != name) "$nameEn ($name)" else name
}

@Serializable
data class FoodNearDto(val name: String = "", val lat: Double, val lng: Double)

@Serializable
data class FoodDto(
    val near: FoodNearDto,
    val results: List<FoodResultDto>,
    val attribution: String = "",
)

@Serializable
data class FoodEnvelope(val food: FoodDto)

// Menu translation (POST /v1/menu/translate). Only the text the phone read is
// sent, never the photo.

@Serializable
data class MenuTranslateRequestDto(
    val lines: List<String>,
    val language: String = "en",
)

// japanese is the AI's best reading of the line (it fixes recognition
// mistakes), text the line in the traveller's language, and note what a dish
// is, or "".
@Serializable
data class MenuLineDto(
    val japanese: String,
    val text: String,
    val note: String = "",
)

@Serializable
data class MenuDto(val lines: List<MenuLineDto>)

@Serializable
data class MenuEnvelope(val menu: MenuDto)
