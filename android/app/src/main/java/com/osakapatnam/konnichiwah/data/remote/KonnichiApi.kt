package com.osakapatnam.konnichiwah.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

// One method per backend endpoint (see docs/api.md). Each response is wrapped
// in an envelope type matching the backend's top-level key.
interface KonnichiApi {

    @GET("v1/healthcheck")
    suspend fun healthcheck(): HealthcheckDto

    // Leave placeType null to get the whole rulebook.
    @GET("v1/etiquette")
    suspend fun etiquette(@Query("place_type") placeType: String? = null): EtiquetteEnvelope

    // Can take 10-20 seconds while the backend waits for the LLM.
    @POST("v1/plan/suggestions")
    suspend fun suggest(@Body request: TripRequestDto): PlanEnvelope
}
