package com.osakapatnam.konnichiwah.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
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

    @POST("v1/itineraries")
    suspend fun createItinerary(@Body request: CreateItineraryRequestDto): ItineraryEnvelope

    @GET("v1/itineraries/{id}")
    suspend fun getItinerary(@Path("id") id: Long): ItineraryEnvelope

    @PATCH("v1/itineraries/{id}")
    suspend fun updateItinerary(@Path("id") id: Long, @Body request: UpdateItineraryRequestDto): ItineraryEnvelope

    // Usually 1-6 seconds; the backend caps it at 25.
    @POST("v1/companion/chat")
    suspend fun chat(@Body request: ChatRequestDto): ChatReplyEnvelope

    // Search either near a point (lat and lng) or a place name (near and
    // city); Retrofit leaves out null parameters. avoid is comma separated,
    // e.g. "pork,beef".
    @GET("v1/food")
    suspend fun food(
        @Query("lat") lat: Double?,
        @Query("lng") lng: Double?,
        @Query("near") near: String?,
        @Query("city") city: String,
        @Query("diet") diet: String,
        @Query("avoid") avoid: String?,
        @Query("spice") spice: String,
    ): FoodEnvelope
}
