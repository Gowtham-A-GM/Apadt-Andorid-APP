package com.example.adapt.viewModel

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

class FaceRecognizer(context: Context) {

    private val faceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.15f)
            .enableTracking()
            .build()

        FaceDetection.getClient(options)
    }

    fun detectFaceOnly(
        bitmap: Bitmap,
        callback: (faceDetected: Boolean) -> Unit
    ) {
        val image = InputImage.fromBitmap(bitmap, 0)

        faceDetector.process(image)
            .addOnSuccessListener { faces ->
                val detected = faces.isNotEmpty()
                Log.d("FaceRecognizer", "Face detected: $detected")
                Handler(Looper.getMainLooper()).post {
                    callback(detected)
                }
            }
            .addOnFailureListener { e ->
                Log.e("FaceRecognizer", "Face detection failed", e)
                callback(false)
            }
    }
}
