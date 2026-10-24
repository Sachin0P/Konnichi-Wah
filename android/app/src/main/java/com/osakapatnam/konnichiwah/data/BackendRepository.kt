package com.osakapatnam.konnichiwah.data

import com.osakapatnam.konnichiwah.data.remote.ApiError
import com.osakapatnam.konnichiwah.data.remote.ApiResult
import com.osakapatnam.konnichiwah.data.remote.AvoidItem
import com.osakapatnam.konnichiwah.data.remote.BudgetBand
import com.osakapatnam.konnichiwah.data.remote.ChatReplyDto
import com.osakapatnam.konnichiwah.data.remote.ChatRequestDto
import com.osakapatnam.konnichiwah.data.remote.Diet
import com.osakapatnam.konnichiwah.data.remote.DirectionsDto
import com.osakapatnam.konnichiwah.data.remote.EtiquetteRuleDto
import com.osakapatnam.konnichiwah.data.remote.FoodDto
import com.osakapatnam.konnichiwah.data.remote.GroupType
import com.osakapatnam.konnichiwah.data.remote.HealthcheckDto
import com.osakapatnam.konnichiwah.data.remote.HotelRecommendationDto
import com.osakapatnam.konnichiwah.data.remote.HotelRequestDto
import com.osakapatnam.konnichiwah.data.remote.KonnichiApi
import com.osakapatnam.konnichiwah.data.remote.MenuLineDto
import com.osakapatnam.konnichiwah.data.remote.MenuTranslateRequestDto
import com.osakapatnam.konnichiwah.data.remote.PlaceSuggestionDto
import com.osakapatnam.konnichiwah.data.remote.PlacesDto
import com.osakapatnam.konnichiwah.data.remote.PlanDto
import com.osakapatnam.konnichiwah.data.remote.SpiceLevel
import com.osakapatnam.konnichiwah.data.remote.TimetableDto
import com.osakapatnam.konnichiwah.data.remote.TripRequestDto
import com.osakapatnam.konnichiwah.data.remote.UserProfileDto
import com.osakapatnam.konnichiwah.data.remote.apiCall
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

// The single place ViewModels go to for backend data. It unwraps the response
// envelopes and turns failures into ApiResult. When we add Room, this is where
// offline caching will live, so ViewModels won't need to change.
@Singleton
class BackendRepository @Inject constructor(
    private val api: KonnichiApi,
    private val json: Json,
) {
    suspend fun healthcheck(): ApiResult<HealthcheckDto> =
        apiCall(json) { api.healthcheck() }

    suspend fun etiquette(placeType: String? = null): ApiResult<List<EtiquetteRuleDto>> =
        apiCall(json) { api.etiquette(placeType).rules }

    suspend fun suggest(request: TripRequestDto): ApiResult<PlanDto> =
        apiCall(json) { api.suggest(request).plan }

    suspend fun recommendHotels(
        cities: List<String>,
        budgetBand: BudgetBand? = null,
        groupType: GroupType? = null,
        notes: String? = null,
        itineraryId: Long? = null,
    ): ApiResult<List<HotelRecommendationDto>> =
        apiCall(json) {
            api.recommendHotels(
                HotelRequestDto(
                    cities = cities,
                    budgetBand = budgetBand,
                    groupType = groupType,
                    notes = notes,
                    itineraryId = itineraryId,
                )
            ).hotels
        }

    suspend fun chat(request: ChatRequestDto): ApiResult<ChatReplyDto> =
        apiCall(json) { api.chat(request).reply }

    // Directions between two places. A Point end can carry a label (a stop's
    // name), which the backend uses instead of "Your location".
    suspend fun directions(from: RouteEnd, to: RouteEnd, city: String, depart: String? = null): ApiResult<DirectionsDto> =
        apiCall(json) {
            api.directions(
                from = from.name, fromLat = from.lat, fromLng = from.lng,
                to = to.name, toLat = to.lat, toLng = to.lng,
                city = city, depart = depart,
            ).directions
        }

    // Help nearby (kind) or anything else (query), around a point or a place.
    suspend fun places(kind: String?, query: String?, near: RouteEnd, city: String): ApiResult<PlacesDto> =
        apiCall(json) {
            api.places(kind = kind, query = query, lat = near.lat, lng = near.lng, near = near.name.takeIf { near is RouteEnd.Named }, city = city).places
        }

    suspend fun suggestPlaces(query: String, city: String, lat: Double?, lng: Double?): ApiResult<List<PlaceSuggestionDto>> =
        apiCall(json) { api.suggestPlaces(query, city, lat, lng).suggestions }

    // Fetches live departures from a station by name or coordinates.
    suspend fun timetable(station: String?, lat: Double?, lng: Double?, city: String?, n: Int = 10): ApiResult<TimetableDto> =
        apiCall(json) {
            api.timetable(station = station, lat = lat, lng = lng, city = city, n = n).timetable
        }

    // The AI translation of a menu's lines, one for each line sent, in order.
    // Any failure (no internet, the AI busy) is the caller's cue to translate
    // on the phone instead.
    suspend fun translateMenu(lines: List<String>, language: AppLanguage): ApiResult<List<MenuLineDto>> {
        val result = apiCall(json) { api.translateMenu(MenuTranslateRequestDto(lines, language.tag)).menu.lines }

        // The backend checks this too, but a mismatch would put translations
        // on the wrong lines of the photo, so we never use one.
        if (result is ApiResult.Success && result.value.size != lines.size) {
            return ApiResult.Failure(ApiError.BadResponse(SerializationException("expected ${lines.size} lines, got ${result.value.size}")))
        }
        return result
    }

    // Searches by the stop's stored coordinates when it has them (set by the
    // backend when the trip was built), otherwise by its name. The query
    // parameters use the same strings as the profile's JSON, so we reuse the
    // serializer rather than spelling them out again.
    suspend fun food(near: String, city: String, lat: Double?, lng: Double?, profile: UserProfileDto?, language: AppLanguage): ApiResult<FoodDto> =
        apiCall(json) {
            val diet = profile?.diet?.let { json.encodeToJsonElement(Diet.serializer(), it).jsonPrimitive.content } ?: "non_veg"
            val avoid = profile?.avoid.orEmpty()
                .map { json.encodeToJsonElement(AvoidItem.serializer(), it).jsonPrimitive.content }
                .joinToString(",")
                .ifEmpty { null }
            val spice = profile?.spiceLevel?.let { json.encodeToJsonElement(SpiceLevel.serializer(), it).jsonPrimitive.content } ?: "medium"
            val hasPoint = lat != null && lng != null
            api.food(
                lat = lat.takeIf { hasPoint },
                lng = lng.takeIf { hasPoint },
                near = near.takeUnless { hasPoint },
                city = city,
                diet = diet,
                language = language.tag,
                avoid = avoid,
                spice = spice,
            ).food
        }
}

// One end of a journey, or the centre of a search: a place name the backend
// looks up, or a point (the traveller's location, or a stop that has
// coordinates).
sealed interface RouteEnd {
    val name: String?
    val lat: Double?
    val lng: Double?

    data class Named(override val name: String) : RouteEnd {
        override val lat: Double? get() = null
        override val lng: Double? get() = null
    }

    data class Point(override val lat: Double, override val lng: Double, val label: String? = null) : RouteEnd {
        override val name: String? get() = label
    }
}
