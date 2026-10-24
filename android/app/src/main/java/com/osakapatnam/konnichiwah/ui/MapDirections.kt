package com.osakapatnam.konnichiwah.ui

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/**
 * Opens turn-by-turn walking directions in Google Maps, falling back to a web
 * browser or default maps handler if Google Maps is not installed.
 */
fun openWalkingDirections(
    context: Context,
    destLat: Double,
    destLng: Double,
    destName: String? = null,
    originLat: Double? = null,
    originLng: Double? = null,
) {
    val originParam = if (
        originLat != null && !originLat.isNaN() && originLat != 0.0 &&
        originLng != null && !originLng.isNaN() && originLng != 0.0
    ) {
        "&origin=$originLat,$originLng"
    } else {
        ""
    }

    val uri = ("https://www.google.com/maps/dir/?api=1$originParam" +
        "&destination=$destLat,$destLng&travelmode=walking").toUri()

    val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
        setPackage("com.google.android.apps.maps")
    }

    runCatching {
        context.startActivity(mapIntent)
    }.recoverCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}
