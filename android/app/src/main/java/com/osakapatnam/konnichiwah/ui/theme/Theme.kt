package com.osakapatnam.konnichiwah.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Placeholder brand colour (torii red) until we have a design.
private val ToriiRed = Color(0xFFC0392B)

@Composable
fun KonnichiTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current

    // Use the wallpaper-based dynamic colours on Android 12+, and our own
    // scheme on older devices (minSdk is 26).
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = ToriiRed)
        else -> lightColorScheme(primary = ToriiRed)
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
