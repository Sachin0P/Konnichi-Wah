package com.osakapatnam.konnichiwah.data

import com.osakapatnam.konnichiwah.data.remote.ApiResult
import com.osakapatnam.konnichiwah.data.remote.ChatReplyDto
import com.osakapatnam.konnichiwah.data.remote.ChatRequestDto
import com.osakapatnam.konnichiwah.data.remote.EtiquetteRuleDto
import com.osakapatnam.konnichiwah.data.remote.HealthcheckDto
import com.osakapatnam.konnichiwah.data.remote.KonnichiApi
import com.osakapatnam.konnichiwah.data.remote.PlanDto
import com.osakapatnam.konnichiwah.data.remote.TripRequestDto
import com.osakapatnam.konnichiwah.data.remote.apiCall
import kotlinx.serialization.json.Json
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
}
