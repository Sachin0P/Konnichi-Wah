package com.osakapatnam.konnichiwah.data

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// Reads Japanese text from a photo and translates it to English, entirely on
// the phone with ML Kit. Nothing is sent to our backend. Text recognition
// works offline from the start, because its model is bundled in the APK. The
// Japanese-English translation model (about 30 MB) is downloaded once, on
// the traveller's say-so, and then works offline too.
@Singleton
class MenuReader @Inject constructor() {

    // Both clients load their models once and are kept for the life of the
    // app, so later scans are quick.
    private val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())

    private val translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.JAPANESE)
            .setTargetLanguage(TranslateLanguage.ENGLISH)
            .build(),
    )

    private val translationModel = TranslateRemoteModel.Builder(TranslateLanguage.JAPANESE).build()

    // The lines of text in an upright photo, top to bottom within each block,
    // with where each one is in the photo (in its pixels). Lines with no
    // letters at all (prices like "¥800", dividers) are dropped.
    suspend fun read(photo: Bitmap): List<ReadLine> {
        val text = recognizer.process(InputImage.fromBitmap(photo, 0)).await()
        return text.textBlocks
            .flatMap { block -> block.lines.map { ReadLine(joinJapanese(it.text.trim()), it.boundingBox) } }
            .filter { line -> line.text.any { it.isLetter() } }
    }

    suspend fun canTranslate(): Boolean =
        RemoteModelManager.getInstance().isModelDownloaded(translationModel).await()

    suspend fun downloadTranslation() {
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
    }

    suspend fun translate(line: String): String = translator.translate(line).await()
}

// One line of text the camera read, and its box in the photo (null if ML Kit
// couldn't place it, which is rare).
data class ReadLine(val text: String, val box: Rect?)

// ML Kit returns Play services Tasks. This turns one into a suspend call, so
// we don't need the kotlinx-coroutines-play-services library for three calls.
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
