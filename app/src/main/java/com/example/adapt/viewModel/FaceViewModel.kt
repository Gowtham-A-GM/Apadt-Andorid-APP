package com.example.adapt.viewModel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.adapt.db.ChatDatabase
import com.example.adapt.db.RegisteredFaceModel
import com.google.gson.Gson
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import java.util.concurrent.ExecutorService

data class FaceMatch(val name: String, val response: String)

class FaceViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application
    private val faceEmbedder = FaceEmbedder(context)
    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
    )

    private val _matchedFace = MutableSharedFlow<FaceMatch>()
    val matchedFace: SharedFlow<FaceMatch> = _matchedFace

    private var faceList = listOf<RegisteredFaceModel>()
    private var lastRecognizedName: String? = null


    private val lastDetectedMap = mutableMapOf<String, Long>()
    private var lastDetectedName: String? = null
    private val cooldownMillis = 5 * 60 * 1000L // 5 minutes

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    fun startFaceRecognition(
        cameraProvider: ProcessCameraProvider,
        executor: ExecutorService,
        lifecycleOwner: androidx.lifecycle.LifecycleOwner
    ): ImageAnalysis {
        val preview = Preview.Builder().build()
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(executor, ImageAnalysis.Analyzer { imageProxy ->
            val mediaImage = imageProxy.image ?: run {
                imageProxy.close()
                return@Analyzer
            }

            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

            faceDetector.process(inputImage)
                .addOnSuccessListener { faces ->
                    if (faces.isNotEmpty()) {
                        val face = faces.first()
                        val boundingBox = face.boundingBox
                        val bitmap = imageProxy.toBitmap() ?: return@addOnSuccessListener
                        val cropped = Bitmap.createBitmap(
                            bitmap,
                            boundingBox.left.coerceAtLeast(0),
                            boundingBox.top.coerceAtLeast(0),
                            boundingBox.width().coerceAtMost(bitmap.width - boundingBox.left),
                            boundingBox.height().coerceAtMost(bitmap.height - boundingBox.top)
                        )
                        val embedding = faceEmbedder.getFaceEmbedding(cropped)
                        checkAndEmit(embedding)
                    }
                }
                .addOnFailureListener {
                    Log.e("FaceViewModel", "Face detection failed", it)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        })

        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageAnalysis
        )
        return imageAnalysis
    }

    private fun checkAndEmit(liveEmbedding: FloatArray) {
        if (faceList.isEmpty()) return  // No faces to compare

        viewModelScope.launch(Dispatchers.IO) {
            for (face in faceList) {
                val storedEmbedding = Gson().fromJson(face.embedding, FloatArray::class.java)
                val distance = calculateEuclideanDistance(liveEmbedding, storedEmbedding)
                if (distance < 1.0f) {
                    val currentTime = System.currentTimeMillis()
                    val lastTime = lastDetectedMap[face.name] ?: 0L

                    val isDifferentFace = face.name != lastDetectedName
                    val cooldownPassed = currentTime - lastTime > cooldownMillis

                    if (isDifferentFace || cooldownPassed) {
                        lastDetectedMap[face.name] = currentTime
                        lastDetectedName = face.name
                        _matchedFace.emit(FaceMatch(face.name, face.response))
                    }
                    break
                }
            }
        }
    }

    private fun calculateEuclideanDistance(a: FloatArray, b: FloatArray): Float {
        return sqrt(a.zip(b) { x, y -> (x - y) * (x - y) }.sum())
    }

    private fun ImageProxy.toBitmap(): Bitmap? {
        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    fun reloadRegisteredFaces() {
        viewModelScope.launch(Dispatchers.IO) {
            val db = ChatDatabase.getDatabase(context)
            faceList = db.registeredFaceDao().getAllFaces()
            lastDetectedMap.clear()  // Clear previous detection timestamps to avoid stale cooldowns
            lastDetectedName = null  // Reset last recognized name
            Log.d("FaceViewModel", "Reloaded ${faceList.size} faces from DB")
        }
    }
}
