package com.osakapatnam.konnichiwah.ui.menu

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.osakapatnam.konnichiwah.R
import com.osakapatnam.konnichiwah.data.MenuConcern
import com.osakapatnam.konnichiwah.data.hasJapanese
import com.osakapatnam.konnichiwah.ui.components.KCard
import com.osakapatnam.konnichiwah.ui.components.Pill
import com.osakapatnam.konnichiwah.ui.components.PillButton
import com.osakapatnam.konnichiwah.ui.components.PrimaryButton
import com.osakapatnam.konnichiwah.ui.components.ScreenHeader
import com.osakapatnam.konnichiwah.ui.components.StatusDot
import com.osakapatnam.konnichiwah.ui.components.Tone
import com.osakapatnam.konnichiwah.ui.theme.Palette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// The menu reader. In scanning mode, it displays the camera viewfinder and
// controls. In results mode, the menu photo is given maximum screen space
// with clean, clutter-free translation badges.
@Composable
fun MenuScreen(
    onDietCards: () -> Unit,
    viewModel: MenuViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lines = state.lines

    when {
        state.reading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(color = Palette.Sun, trackColor = Palette.Petal)
                Text(stringResource(R.string.menu_reading))
            }
        }
        lines == null -> {
            Column(modifier = Modifier.fillMaxSize()) {
                ScreenHeader(
                    stringResource(R.string.menu_title),
                    subtitle = stringResource(R.string.menu_instructions),
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
                )
                TranslationBanner(state.model, onDownload = viewModel::downloadTranslation)
                Scanner(
                    error = state.error,
                    onImage = viewModel::read,
                    onError = viewModel::cameraError,
                )
            }
        }
        else -> Results(
            photo = state.photo,
            lines = lines,
            found = state.found,
            translating = state.translating,
            translatedBy = state.translatedBy,
            onScanAgain = viewModel::scanAgain,
            onDietCards = onDietCards,
        )
    }
}

// Offers the one-time download of the translation model.
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

// The camera in a dark rounded viewfinder, plus a way to pick a photo instead.
@Composable
private fun Scanner(@StringRes error: Int?, onImage: (Bitmap) -> Unit, onError: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var hasCamera by remember { mutableStateOf(hasCameraPermission(context)) }
    var asked by rememberSaveable { mutableStateOf(false) }

    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCamera = granted
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { withContext(Dispatchers.Default) { decodeUpright(context, uri) } }
                    .onSuccess(onImage)
                    .onFailure { onError(R.string.menu_open_failed) }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCamera && !asked) {
            asked = true
            askCamera.launch(Manifest.permission.CAMERA)
        }
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
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
                onClick = { capture(context, scope, imageCapture, onImage, onError) },
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

private fun capture(
    context: Context,
    scope: CoroutineScope,
    imageCapture: ImageCapture,
    onImage: (Bitmap) -> Unit,
    onError: (Int) -> Unit,
) {
    imageCapture.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                scope.launch {
                    val photo = withContext(Dispatchers.Default) {
                        try {
                            image.toUprightBitmap()
                        } finally {
                            image.close()
                        }
                    }
                    onImage(photo)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                onError(R.string.menu_camera_failed)
            }
        },
    )
}

private fun hasCameraPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

// The scan's results. Fullscreen photo view with clean, uncluttered overlays,
// or an organized list view.
@Composable
private fun Results(
    photo: Bitmap?,
    lines: List<MenuLine>,
    found: List<MenuConcern>,
    translating: Boolean,
    translatedBy: TranslatedBy?,
    onScanAgain: () -> Unit,
    onDietCards: () -> Unit,
) {
    val canShowPhoto = photo != null && lines.any { it.box != null }
    var showList by rememberSaveable { mutableStateOf(!canShowPhoto) }
    var showWarningsOnly by rememberSaveable { mutableStateOf(false) }
    var opened by remember { mutableStateOf<MenuLine?>(null) }
    val onPhoto = canShowPhoto && !showList

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Compact, non-intrusive top control bar
            Surface(
                color = Palette.Cream,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PillButton(
                            "‹ " + stringResource(R.string.menu_retake),
                            onClick = onScanAgain,
                        )

                        if (found.isNotEmpty()) {
                            Pill(
                                stringResource(R.string.menu_may_contain, found.map { stringResource(it.label) }.joinToString()),
                                Tone.PETAL,
                            )
                        } else {
                            Pill(
                                "✓ " + stringResource(R.string.menu_safe),
                                Tone.GREEN,
                            )
                        }

                        if (canShowPhoto) {
                            PillButton(
                                stringResource(if (showList) R.string.menu_view_photo else R.string.menu_view_list),
                                onClick = { showList = !showList },
                            )
                        }
                    }

                    // Sub-row: Quick filters & Diet cards link
                    if (onPhoto) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (found.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Pill(
                                        stringResource(R.string.menu_filter_all),
                                        tone = if (!showWarningsOnly) Tone.MAROON else Tone.PETAL,
                                        modifier = Modifier.clickable { showWarningsOnly = false },
                                    )
                                    Pill(
                                        stringResource(R.string.menu_filter_warnings, found.size),
                                        tone = if (showWarningsOnly) Tone.MAROON else Tone.PETAL,
                                        modifier = Modifier.clickable { showWarningsOnly = true },
                                    )
                                }
                            } else {
                                Text(
                                    stringResource(R.string.menu_zoom_hint),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Palette.Mute,
                                )
                            }

                            TextButton(
                                onClick = onDietCards,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    stringResource(R.string.menu_my_cards) + " →",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Palette.Maroon,
                                )
                            }
                        }
                    }
                }
            }

            // Main View Area: Photo with sleek overlays, or clean list
            if (onPhoto) {
                MenuPhotoView(
                    photo = photo!!,
                    lines = lines,
                    warningsOnly = showWarningsOnly,
                    onTap = { opened = it },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            } else {
                val relevantLines = remember(lines) {
                    lines.filter { it.flags.isNotEmpty() || (hasJapanese(it.japanese) && it.english != null) }
                        .ifEmpty { lines }
                }
                val warningLines = remember(relevantLines) { relevantLines.filter { it.flags.isNotEmpty() } }
                val otherLines = remember(relevantLines) { relevantLines.filter { it.flags.isEmpty() } }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (warningLines.isNotEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.menu_marked),
                                fontWeight = FontWeight.Bold,
                                color = Palette.Maroon,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                        items(warningLines) { line -> LineCard(line, translating) }
                    }

                    if (otherLines.isNotEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.menu_title),
                                fontWeight = FontWeight.Bold,
                                color = Palette.Ink,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                            )
                        }
                        items(otherLines) { line -> LineCard(line, translating) }
                    }
                }
            }
        }

        // Elegant bottom sheet when a menu item is tapped
        opened?.let { line ->
            val current = lines.firstOrNull { it.box == line.box && it.japanese == line.japanese } ?: line
            ItemDetailSheet(
                line = current,
                translating = translating,
                onDismiss = { opened = null },
                onDietCards = onDietCards,
            )
        }
    }
}

// Sleek detail card when tapping an item on the menu photo.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemDetailSheet(
    line: MenuLine,
    translating: Boolean,
    onDismiss: () -> Unit,
    onDietCards: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = Palette.Card,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false) {},
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.menu_item_detail),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Palette.Mute,
                    )
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.card_close), color = Palette.Mute)
                    }
                }

                Text(
                    text = line.english ?: stringResource(if (translating) R.string.menu_translating else R.string.menu_translation_note),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (line.flags.isNotEmpty()) Palette.Maroon else Palette.Ink,
                )

                Text(
                    text = line.japanese,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.Mute,
                )

                if (!line.note.isNullOrBlank()) {
                    Text(
                        text = line.note,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = Palette.Ink,
                    )
                }

                if (line.flags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        for (flag in line.flags) {
                            Pill(
                                stringResource(R.string.menu_flag, stringResource(flag.concern.label), flag.word),
                                Tone.PETAL,
                            )
                        }
                    }

                    PrimaryButton(
                        text = stringResource(R.string.menu_show_card),
                        onClick = {
                            onDismiss()
                            onDietCards()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

// One line of the menu in the list view.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LineCard(line: MenuLine, translating: Boolean) {
    KCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(line.japanese, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
            when {
                line.english != null -> Text(line.english, style = MaterialTheme.typography.bodyMedium, color = Palette.Mute)
                translating -> Text(
                    stringResource(R.string.menu_translating),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            line.note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (line.flags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    for (flag in line.flags) {
                        Pill(stringResource(R.string.menu_flag, stringResource(flag.concern.label), flag.word), Tone.PETAL)
                    }
                }
            }
        }
    }
}
