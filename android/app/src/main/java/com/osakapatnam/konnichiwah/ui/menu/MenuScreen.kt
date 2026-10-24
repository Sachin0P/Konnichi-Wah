package com.osakapatnam.konnichiwah.ui.menu

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.common.InputImage
import com.osakapatnam.konnichiwah.R
import com.osakapatnam.konnichiwah.data.MenuConcern
import com.osakapatnam.konnichiwah.ui.components.KCard
import com.osakapatnam.konnichiwah.ui.components.Pill
import com.osakapatnam.konnichiwah.ui.components.PillButton
import com.osakapatnam.konnichiwah.ui.components.PrimaryButton
import com.osakapatnam.konnichiwah.ui.components.ScreenHeader
import com.osakapatnam.konnichiwah.ui.components.StatusDot
import com.osakapatnam.konnichiwah.ui.components.Tone
import com.osakapatnam.konnichiwah.ui.theme.DisplayFont
import com.osakapatnam.konnichiwah.ui.theme.Palette
import kotlinx.coroutines.awaitCancellation

// The menu reader. It's a bottom tab, so it has no Back button; "Scan
// again" returns from the results to the camera.
@Composable
fun MenuScreen(
    onDietCards: () -> Unit,
    viewModel: MenuViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            stringResource(R.string.menu_title),
            subtitle = stringResource(R.string.menu_instructions),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
        )
        TranslationBanner(state.model, onDownload = viewModel::downloadTranslation)

        val lines = state.lines
        when {
            state.reading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = Palette.Sun, trackColor = Palette.Petal)
                    Text(stringResource(R.string.menu_reading))
                }
            }
            lines == null -> Scanner(
                error = state.error,
                onImage = viewModel::read,
                onError = viewModel::cameraError,
            )
            else -> Results(
                lines = lines,
                found = state.found,
                translating = state.model == TranslationModel.READY,
                onScanAgain = viewModel::scanAgain,
                onDietCards = onDietCards,
            )
        }
    }
}

// Offers the one-time download of the translation model. Until then, the
// reader still shows the Japanese and the warning words.
@Composable
private fun TranslationBanner(model: TranslationModel, onDownload: () -> Unit) {
    val text = when (model) {
        TranslationModel.MISSING -> stringResource(R.string.menu_model_missing)
        TranslationModel.DOWNLOADING -> stringResource(R.string.menu_model_downloading)
        TranslationModel.FAILED -> stringResource(R.string.menu_model_failed)
        TranslationModel.CHECKING, TranslationModel.READY -> return
    }

    KCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        color = Palette.Orange,
        border = BorderStroke(0.dp, Color.Transparent),
        padding = PaddingValues(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodySmall, color = Palette.OrangeInk, modifier = Modifier.weight(1f))
            when (model) {
                TranslationModel.DOWNLOADING -> CircularProgressIndicator(color = Palette.Sun, modifier = Modifier.padding(12.dp))
                else -> TextButton(onClick = onDownload) {
                    Text(stringResource(if (model == TranslationModel.FAILED) R.string.menu_retry else R.string.menu_download), color = Palette.Sun)
                }
            }
        }
    }
}

// The camera in a dark rounded viewfinder, plus a way to pick a photo
// instead (a menu photographed earlier, or when there's no camera permission).
@Composable
private fun Scanner(@StringRes error: Int?, onImage: (InputImage) -> Unit, onError: (Int) -> Unit) {
    val context = LocalContext.current
    var hasCamera by remember { mutableStateOf(hasCameraPermission(context)) }
    var asked by rememberSaveable { mutableStateOf(false) }

    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCamera = granted
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            // fromFilePath reads the photo's EXIF rotation, so it comes out
            // upright.
            runCatching { InputImage.fromFilePath(context, uri) }
                .onSuccess(onImage)
                .onFailure { onError(R.string.menu_open_failed) }
        }
    }

    // Ask for the camera once, when the screen first opens.
    LaunchedEffect(Unit) {
        if (!hasCamera && !asked) {
            asked = true
            askCamera.launch(Manifest.permission.CAMERA)
        }
    }

    // Built once and shared by the preview and the capture button.
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            // A full 12 MP photo is slow to read and isn't any more accurate
            // for a menu held at reading distance.
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(1920, 1440), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER),
                    )
                    .build(),
            )
            .build()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Palette.Night),
            contentAlignment = Alignment.Center,
        ) {
            if (hasCamera) {
                CameraPreview(imageCapture, Modifier.fillMaxSize())
                // The design's red reading frame: a hint to line up the menu.
                Box(
                    Modifier
                        .fillMaxWidth(0.84f)
                        .height(72.dp)
                        .border(2.5.dp, Palette.Sun, RoundedCornerShape(12.dp)),
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.menu_camera_off), color = Color.White, textAlign = TextAlign.Center)
                    PillButton(stringResource(R.string.menu_allow_camera), onClick = { askCamera.launch(Manifest.permission.CAMERA) })
                }
            }
        }

        if (error != null) {
            Text(stringResource(error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton(
                stringResource(R.string.menu_read),
                onClick = { capture(context, imageCapture, onImage, onError) },
                enabled = hasCamera,
                modifier = Modifier.weight(1f),
            )
            PillButton(
                stringResource(R.string.menu_choose_photo),
                onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            )
        }
        StatusDot(stringResource(R.string.menu_offline))
    }
}

// The live camera view. The camera is bound while this is on screen and
// released as soon as it leaves (after a scan, or on Back).
@Composable
private fun CameraPreview(imageCapture: ImageCapture, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }

    LaunchedEffect(lifecycleOwner) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        try {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            awaitCancellation()
        } finally {
            provider.unbindAll()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

private fun capture(context: Context, imageCapture: ImageCapture, onImage: (InputImage) -> Unit, onError: (Int) -> Unit) {
    imageCapture.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                // Copy the frame out so the camera's buffer can be released
                // straight away; ML Kit rotates it upright.
                val bitmap = image.toBitmap()
                val rotation = image.imageInfo.rotationDegrees
                image.close()
                onImage(InputImage.fromBitmap(bitmap, rotation))
            }

            override fun onError(exception: ImageCaptureException) {
                onError(R.string.menu_camera_failed)
            }
        },
    )
}

private fun hasCameraPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

@Composable
private fun Results(
    lines: List<MenuLine>,
    found: List<MenuConcern>,
    translating: Boolean,
    onScanAgain: () -> Unit,
    onDietCards: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Summary(lines.isEmpty(), found) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton(stringResource(R.string.menu_my_cards), onClick = onDietCards, modifier = Modifier.weight(1f))
                PillButton(stringResource(R.string.menu_scan_again), onClick = onScanAgain)
            }
        }

        if (lines.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.menu_translation_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(lines) { line -> LineCard(line, translating) }
    }
}

// The verdict at the top: pink with the warning words if any were found,
// otherwise a cream card that still says to check.
@Composable
private fun Summary(empty: Boolean, found: List<MenuConcern>) {
    val (title, detail) = when {
        empty -> stringResource(R.string.menu_no_text) to stringResource(R.string.menu_no_text_hint)
        found.isNotEmpty() -> stringResource(R.string.menu_may_contain, found.map { stringResource(it.label) }.joinToString()) to
            stringResource(R.string.menu_marked)
        else -> stringResource(R.string.menu_none_found) to stringResource(R.string.menu_none_found_hint)
    }

    KCard(
        modifier = Modifier.fillMaxWidth(),
        color = if (found.isNotEmpty()) Palette.Petal else Palette.Card,
        border = if (found.isNotEmpty()) BorderStroke(0.dp, Color.Transparent) else BorderStroke(1.dp, Palette.Line),
    ) {
        StatusDot(stringResource(R.string.menu_offline))
        Text(title, fontFamily = DisplayFont, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
        Text(detail, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
    }
}

// One line of the menu: the Japanese, the rough English, and a pink tag for
// each warning word found in it.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LineCard(line: MenuLine, translating: Boolean) {
    KCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(line.japanese, style = MaterialTheme.typography.titleLarge, color = Palette.Ink)
            when {
                line.english != null -> Text(line.english, style = MaterialTheme.typography.bodyMedium, color = Palette.Mute)
                translating -> Text(
                    stringResource(R.string.menu_translating),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (line.flags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    for (flag in line.flags) {
                        Pill(stringResource(R.string.menu_flag, stringResource(flag.concern.label), flag.word), Tone.PETAL)
                    }
                }
            }
        }
    }
}
