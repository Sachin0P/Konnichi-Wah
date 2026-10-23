package com.osakapatnam.konnichiwah.ui

import com.osakapatnam.konnichiwah.data.remote.ApiError

// Turns an ApiError into a message we can show a traveller. Technical
// details (status codes, field names) stay in Logcat via OkHttp's logging.
fun ApiError.userMessage(): String = when (this) {
    is ApiError.Network ->
        "Can't reach the Konnichi-Wah! server. Check your internet connection and try again."
    is ApiError.Http -> when (status) {
        422 -> "Some of your answers weren't accepted. Please go back and check them."
        429 -> "Too many requests. Please wait a moment and try again."
        // The backend's 503 messages are written for travellers ("the
        // companion is busy right now, please try again in a minute").
        503 -> message.replaceFirstChar { it.uppercase() } + "."
        else -> "Something went wrong on our side. Please try again."
    }
    is ApiError.BadResponse ->
        "We got an unexpected reply from the server. Please try again, or update the app."
}
