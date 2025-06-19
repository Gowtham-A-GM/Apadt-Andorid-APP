package com.example.adapt.viewModel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.media.Image
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.util.Size

class BackgroundFaceService : LifecycleService() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var faceRecognizer: FaceRecognizer
    private var imageAnalysis: ImageAnalysis? = null
    private var isProcessing = false
    private var isFirstFaceDetected = false

    private val binder = LocalBinder()
    private var camera: Camera? = null

    interface FaceRecognitionCallback {
        fun onFirstFaceDetected()
    }

    private var callback: FaceRecognitionCallback? = null

    inner class LocalBinder : Binder() {
        fun getService(): BackgroundFaceService = this@BackgroundFaceService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("BackgroundFaceService", "Service created")

        cameraExecutor = Executors.newSingleThreadExecutor()
        faceRecognizer = FaceRecognizer(this)

        startCamera()
    }

    override fun onBind(intent: android.content.Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    fun setCallback(callback: FaceRecognitionCallback) {
        this.callback = callback
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(cameraProvider)
            } catch (exc: Exception) {
                Log.e("BackgroundFaceService", "Camera initialization failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases(cameraProvider: ProcessCameraProvider) {
        imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis?.setAnalyzer(cameraExecutor) { imageProxy ->
            if (!isProcessing && !isFirstFaceDetected) {
                processImage(imageProxy)
            } else {
                imageProxy.close()
            }
        }

        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
        cameraProvider.unbindAll()
        camera = cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis)
    }

    private fun processImage(imageProxy: ImageProxy) {
        isProcessing = true
        try {
            val bitmap = imageProxyToBitmap(imageProxy)
            if (bitmap != null) {
                faceRecognizer.detectFaceOnly(bitmap) { faceDetected ->
                    if (faceDetected && !isFirstFaceDetected) {
                        isFirstFaceDetected = true
                        callback?.onFirstFaceDetected()
                    }
                    isProcessing = false
                }
            } else {
                isProcessing = false
            }
        } catch (e: Exception) {
            Log.e("BackgroundFaceService", "Error processing image", e)
            isProcessing = false
        } finally {
            imageProxy.close()
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val yBuffer = imageProxy.planes[0].buffer
            val uBuffer = imageProxy.planes[1].buffer
            val vBuffer = imageProxy.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = android.graphics.YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 85, out)
            BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
        } catch (e: Exception) {
            Log.e("BackgroundFaceService", "Bitmap conversion failed", e)
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ProcessCameraProvider.getInstance(this).get().unbindAll()
        cameraExecutor.shutdown()
    }
}
