package com.osakapatnam.konnichiwah.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.stringResource
import com.osakapatnam.konnichiwah.R
import com.osakapatnam.konnichiwah.data.remote.ApiError

// Turns an ApiError into a message we can show a traveller, in the language
// on screen. Technical details (status codes, field names) stay in Logcat via
// OkHttp's logging. View models keep the ApiError rather than the message,
// so the message follows the language if the traveller switches.
@Composable
fun ApiError.userMessage(): String = when (this) {
    is ApiError.Network -> stringResource(R.string.error_network)
    is ApiError.Http -> when (status) {
        422 -> stringResource(R.string.error_rejected)
        429 -> stringResource(R.string.error_too_many)
        // The backend's 503 messages are written for travellers ("the
        // companion is busy right now, please try again in a minute"), but in
        // English, so other languages get a general "busy" message.
        503 -> if (booleanResource(R.bool.show_server_messages)) {
            message.replaceFirstChar { it.uppercase() } + "."
        } else {
            stringResource(R.string.error_busy)
        }
        else -> stringResource(R.string.error_server)
    }
    is ApiError.BadResponse -> stringResource(R.string.error_bad_response)
}
