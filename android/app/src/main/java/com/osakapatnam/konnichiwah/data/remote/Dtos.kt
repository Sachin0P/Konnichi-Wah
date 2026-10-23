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

@Serializable
data class EtiquetteRuleDto(
    val id: String,
    @SerialName("place_types") val placeTypes: List<String>,
    val title: String,
    val tip: String,
    val severity: String,
)

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

// A partial update: we only ever send the days.
@Serializable
data class UpdateItineraryRequestDto(val days: List<DayDto>)
