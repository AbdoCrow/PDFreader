package com.example.camscan.core.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

object OcrManager {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognizeText(context: Context, imagePath: String): Text? = suspendCancellableCoroutine { continuation ->
        try {
            val file = File(imagePath)
            if (!file.exists()) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            val image = InputImage.fromFilePath(context, Uri.fromFile(file))
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    if (continuation.isActive) {
                        continuation.resume(visionText)
                    }
                }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
            if (continuation.isActive) {
                continuation.resume(null)
            }
        }
    }
}
