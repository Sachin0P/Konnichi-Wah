// The platform ExifInterface is used on purpose; see decodeUpright.
@file:SuppressLint("ExifInterface")

package com.osakapatnam.konnichiwah.ui.menu

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.camera.core.ImageProxy
import androidx.core.graphics.scale
import kotlin.math.max
import kotlin.math.roundToInt

// The menu reader draws translations over the photo, so the photo we show and
// the one ML Kit reads must be the same upright bitmap: then the boxes ML Kit
// returns line up with what's on screen. These helpers turn a camera frame or
// a gallery photo into that bitmap.

// The longest side we keep. Plenty for menu text, and it keeps a photo to
// about 12 MB in memory (a 12 MP original would be 48 MB).
private const val MAX_SIDE = 2048

// A camera frame, rotated upright. CameraX delivers frames as the sensor sees
// them, plus how far to turn them.
fun ImageProxy.toUprightBitmap(): Bitmap {
    val bitmap = toBitmap()
    return bitmap.rotated(imageInfo.rotationDegrees).limited()
}

// A photo from the gallery, upright and limited to MAX_SIDE.
//
// On Android 8 and 8.1 we read the rotation with the platform's
// ExifInterface. Lint prefers the androidx copy, but the platform one reads
// the orientation tag correctly on these versions, so it's not worth a
// library for two Android versions (see the file's SuppressLint).
fun decodeUpright(context: Context, uri: Uri): Bitmap {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        // ImageDecoder applies the photo's EXIF rotation itself. We ask for a
        // software bitmap because ML Kit can't read hardware ones.
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val scale = MAX_SIDE.toFloat() / max(info.size.width, info.size.height)
            if (scale < 1) decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    // Android 8 and 8.1: decode at a reduced size, then rotate by hand.
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }

    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2

    val bitmap = resolver.openInputStream(uri).use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: error("not an image")

    val orientation = resolver.openInputStream(uri).use {
        ExifInterface(checkNotNull(it)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }
    val degrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
    return bitmap.rotated(degrees).limited()
}

private fun Bitmap.rotated(degrees: Int): Bitmap {
    if (degrees == 0) return this
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

private fun Bitmap.limited(): Bitmap {
    val scale = MAX_SIDE.toFloat() / max(width, height)
    if (scale >= 1) return this
    return scale((width * scale).roundToInt(), (height * scale).roundToInt())
}
