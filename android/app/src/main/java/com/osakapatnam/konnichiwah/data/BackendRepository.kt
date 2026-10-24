package com.osakapatnam.konnichiwah.data

import com.osakapatnam.konnichiwah.data.remote.ApiError
import com.osakapatnam.konnichiwah.data.remote.ApiResult
import com.osakapatnam.konnichiwah.data.remote.AvoidItem
import com.osakapatnam.konnichiwah.data.remote.ChatReplyDto
import com.osakapatnam.konnichiwah.data.remote.ChatRequestDto
import com.osakapatnam.konnichiwah.data.remote.Diet
import com.osakapatnam.konnichiwah.data.remote.EtiquetteRuleDto
import com.osakapatnam.konnichiwah.data.remote.FoodDto
import com.osakapatnam.konnichiwah.data.remote.HealthcheckDto
import com.osakapatnam.konnichiwah.data.remote.KonnichiApi
import com.osakapatnam.konnichiwah.data.remote.MenuLineDto
import com.osakapatnam.konnichiwah.data.remote.MenuTranslateRequestDto
import com.osakapatnam.konnichiwah.data.remote.PlanDto
import com.osakapatnam.konnichiwah.data.remote.SpiceLevel
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

    suspend fun chat(request: ChatRequestDto): ApiResult<ChatReplyDto> =
        apiCall(json) { api.chat(request).reply }

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
