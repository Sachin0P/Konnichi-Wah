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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.common.InputImage
import com.osakapatnam.konnichiwah.R
import com.osakapatnam.konnichiwah.data.MenuConcern
import kotlinx.coroutines.awaitCancellation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(
    onBack: () -> Unit,
    onDietCards: () -> Unit,
    viewModel: MenuViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TranslationBanner(state.model, onDownload = viewModel::downloadTranslation)

            val lines = state.lines
            when {
                state.reading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            when (model) {
                TranslationModel.DOWNLOADING -> CircularProgressIndicator(modifier = Modifier.padding(start = 12.dp))
                else -> TextButton(onClick = onDownload) { Text(stringResource(if (model == TranslationModel.FAILED) R.string.menu_retry else R.string.menu_download)) }
            }
        }
    }
}

// The camera, plus a way to pick a photo instead (a menu photographed
// earlier, or when there's no camera permission).
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.menu_instructions),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            if (hasCamera) {
                CameraPreview(imageCapture, Modifier.fillMaxSize())
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.menu_camera_off))
                    TextButton(onClick = { askCamera.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.menu_allow_camera)) }
                }
            }
        }

        if (error != null) {
            Text(stringResource(error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { capture(context, imageCapture, onImage, onError) },
                enabled = hasCamera,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.menu_read)) }
            OutlinedButton(
                onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.menu_choose_photo)) }
        }
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
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Summary(lines.isEmpty(), found) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onScanAgain, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.menu_scan_again)) }
                Button(onClick = onDietCards, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.menu_my_cards)) }
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

@Composable
private fun Summary(empty: Boolean, found: List<MenuConcern>) {
    val (title, detail) = when {
        empty -> stringResource(R.string.menu_no_text) to stringResource(R.string.menu_no_text_hint)
        found.isNotEmpty() -> stringResource(R.string.menu_may_contain, found.map { stringResource(it.label) }.joinToString()) to
            stringResource(R.string.menu_marked)
        else -> stringResource(R.string.menu_none_found) to stringResource(R.string.menu_none_found_hint)
    }

    val colors = if (found.isNotEmpty()) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    } else {
        CardDefaults.cardColors()
    }

    Card(modifier = Modifier.fillMaxWidth(), colors = colors) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun LineCard(line: MenuLine, translating: Boolean) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(line.japanese, style = MaterialTheme.typography.titleMedium)
            when {
                line.english != null -> Text(line.english, style = MaterialTheme.typography.bodyMedium)
                translating -> Text(
                    stringResource(R.string.menu_translating),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            for (flag in line.flags) {
                Text(
                    stringResource(R.string.menu_flag, stringResource(flag.concern.label), flag.word),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
