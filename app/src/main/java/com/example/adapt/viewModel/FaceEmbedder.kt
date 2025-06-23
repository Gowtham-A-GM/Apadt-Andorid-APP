package com.example.adapt.viewModel

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class FaceEmbedder(context: Context) {

    private val inputImageSize = 112
    private val modelPath = "mobile_face_net.tflite" // Put this file in assets/

    private val interpreter: Interpreter

    init {
        val assetFileDescriptor = context.assets.openFd(modelPath)
        val fileInputStream = assetFileDescriptor.createInputStream()
        val fileChannel = fileInputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength

        val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        interpreter = Interpreter(modelBuffer)
    }


    fun getFaceEmbedding(bitmap: Bitmap): FloatArray {
        val resized = Bitmap.createScaledBitmap(bitmap, inputImageSize, inputImageSize, true)
        val byteBuffer = convertBitmapToByteBuffer(resized)

        val embedding = Array(1) { FloatArray(192) } // or 128, depending on model
        interpreter.run(byteBuffer, embedding)

        return embedding[0]
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(1 * inputImageSize * inputImageSize * 3 * 4)
        byteBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(inputImageSize * inputImageSize)
        bitmap.getPixels(intValues, 0, inputImageSize, 0, 0, inputImageSize, inputImageSize)

        for (pixel in intValues) {
            val r = (pixel shr 16 and 0xFF) / 255.0f
            val g = (pixel shr 8 and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f
            byteBuffer.putFloat(r)
            byteBuffer.putFloat(g)
            byteBuffer.putFloat(b)
        }
        return byteBuffer
    }
}
