package com.osakapatnam.konnichiwah.data.remote

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.IOException

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

sealed interface ApiError {
    // The backend couldn't be reached (no network, wrong URL, server down).
    data class Network(val cause: IOException) : ApiError

    // The backend answered with an error. For 422 validation errors,
    // fieldErrors maps each field (e.g. "diet") to its message.
    data class Http(
        val status: Int,
        val message: String,
        val fieldErrors: Map<String, String> = emptyMap(),
    ) : ApiError

    // The backend answered 2xx but the body didn't match our DTOs.
    data class BadResponse(val cause: SerializationException) : ApiError
}

// apiCall runs a Retrofit call and turns every failure into an ApiError, so
// ViewModels handle one result type instead of several exception types.
suspend fun <T> apiCall(json: Json, block: suspend () -> T): ApiResult<T> =
    try {
        ApiResult.Success(block())
    } catch (e: HttpException) {
        ApiResult.Failure(parseHttpError(json, e))
    } catch (e: IOException) {
        ApiResult.Failure(ApiError.Network(e))
    } catch (e: SerializationException) {
        ApiResult.Failure(ApiError.BadResponse(e))
    }

// The backend always sends errors as {"error": ...}, where the value is either
// a string or an object of field -> message (for 422s). See docs/api.md.
private fun parseHttpError(json: Json, e: HttpException): ApiError.Http {
    val body = e.response()?.errorBody()?.string().orEmpty()
    val error = runCatching { json.parseToJsonElement(body).jsonObject["error"] }.getOrNull()

    return when (error) {
        is JsonPrimitive -> ApiError.Http(e.code(), error.content)
        is JsonObject -> ApiError.Http(
            status = e.code(),
            message = "validation failed",
            fieldErrors = error.mapValues { (_, value) -> value.jsonPrimitive.content },
        )
        else -> ApiError.Http(e.code(), e.message())
    }
}
