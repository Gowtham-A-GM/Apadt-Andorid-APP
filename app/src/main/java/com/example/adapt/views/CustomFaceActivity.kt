package com.example.adapt.views

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.adapt.R
import com.example.adapt.databinding.ActivityCustomFaceBinding
import com.example.adapt.db.ChatDatabase
import com.example.adapt.db.RegisteredFaceModel
import com.example.adapt.viewModel.CustomFaceAdapter
import com.example.adapt.viewModel.FaceEmbedder
import com.google.gson.Gson
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

class CustomFaceActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCustomFaceBinding

    private val SELECT_IMAGE_REQUEST = 1
    private val CAMERA_REQUEST_CODE = 1001
    private var imageUri: Uri? = null
    private var croppedFaceBitmap: Bitmap? = null

    val highAccuracyOpts = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .build()

    private lateinit var faceDetector: FaceDetector
    private lateinit var adapter: CustomFaceAdapter
    private val faceList = mutableListOf<RegisteredFaceModel>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomFaceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        faceDetector = FaceDetection.getClient(highAccuracyOpts)

        binding.btnBack.setOnClickListener {
            finish()
        }

        // For Recycler view
        adapter = CustomFaceAdapter(faceList) { position ->
            CoroutineScope(Dispatchers.IO).launch {
                ChatDatabase.getDatabase(applicationContext).registeredFaceDao().deleteFace(faceList[position])
                loadRegisteredFaces()
            }
        }
        binding.recyclerFaces.layoutManager = LinearLayoutManager(this)
        binding.recyclerFaces.adapter = adapter

        loadRegisteredFaces()


        // Open gallery
        binding.btnOpenGallery.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, SELECT_IMAGE_REQUEST)
        }

        // Open camera
        binding.btnOpenCamera.setOnClickListener {
            openCamera()
        }

        // Save into ROOM Database
        binding.btnRegister.setOnClickListener {
            saveToDB()
        }
    }

    private fun openCamera() {
        val photoFile = File.createTempFile("IMG_", ".jpg", externalCacheDir)
        imageUri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            photoFile
        )

        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }

        if (cameraIntent.resolveActivity(packageManager) != null) {
            startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE)
        } else {
            Toast.makeText(this, "Camera not supported", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SELECT_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            val selectedUri = data.data
            selectedUri?.let {
                val bitmap = getBitmapFromUri(it)
                binding.ivFacePreview.setImageBitmap(bitmap)
                performFaceDetection(bitmap)
            }
        }

        if (requestCode == CAMERA_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            imageUri?.let {
                val bitmap = getBitmapFromUri(it)
                val rotatedBitmap = rotateBitmapIfRequired(it, bitmap)
                binding.ivFacePreview.setImageBitmap(rotatedBitmap)
                performFaceDetection(rotatedBitmap)
            } ?: run {
                Toast.makeText(this, "Image URI not found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getBitmapFromUri(uri: Uri): Bitmap {
        val original = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = true
            }
        } else {
            MediaStore.Images.Media.getBitmap(contentResolver, uri)
        }
        return original.copy(Bitmap.Config.ARGB_8888, true)
    }

    private fun rotateBitmapIfRequired(uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val exif = inputStream?.use { ExifInterface(it) }
            val orientation = exif?.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            ) ?: ExifInterface.ORIENTATION_NORMAL

            val rotationAngle = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }

            if (rotationAngle != 0f) {
                val matrix = Matrix().apply { postRotate(rotationAngle) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            bitmap
        }
    }

    private fun performFaceDetection(input: Bitmap){

        val mutableBitmap = input.copy(Bitmap.Config.ARGB_8888, true)
        val image = InputImage.fromBitmap(mutableBitmap, 0)

//        val canvas = Canvas(mutableBitmap)        // Uncomment this to view box around face
//        val paintFaceBox = Paint().apply {
//            color = Color.RED
//            style = Paint.Style.STROKE
//            strokeWidth = 5f
//        }

        faceDetector.process(image)
            .addOnSuccessListener { faces ->
                Log.d("CustomFaceActivity", "No. of faces detected: ${faces.size}")

                when (faces.size) {
                    0 -> {
                        binding.ivFacePreview.setImageResource(R.drawable.custom_face_img_placeholder)
                        Toast.makeText(this, "No face found in the image!", Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        val face = faces[0]
                        val bounds = face.boundingBox
//                        canvas.drawRect(bounds, paintFaceBox) // Uncomment this to view box around face
                        cropDetectedFace(bounds, input)
                    }
                    else -> {
                        binding.ivFacePreview.setImageResource(R.drawable.custom_face_img_placeholder)
                        Toast.makeText(this, "Multiple faces found. Please provide a photo with only one face.", Toast.LENGTH_SHORT).show()
                    }
                }

//                binding.ivFacePreview.setImageBitmap(mutableBitmap) // Uncomment this to view box around face
            }
            .addOnFailureListener {
                Log.e("CustomFaceActivity", "Face detection failed", it)
                Toast.makeText(this, "Face detection failed", Toast.LENGTH_SHORT).show()
            }

    }

    private fun cropDetectedFace(bound: Rect, input: Bitmap){
        if(bound.top < 0){
            bound.top = 0
        }
        if(bound.left<0) {
            bound.left = 0
        }
        if(bound.right > input.getWidth()){
            bound.right = input.getWidth()-1
        }
        if(bound.bottom > input.getHeight()){
            bound.bottom = input.getHeight()-1
        }
        val croppedFace = Bitmap.createBitmap(input, bound.left, bound.top, bound.width(), bound.height())
        binding.ivFacePreview.setImageBitmap(croppedFace)
        croppedFaceBitmap = croppedFace
    }

    private fun saveToDB() {
        val name = binding.etNameInput.text.toString().trim()
        val response = binding.etResponseInput.text.toString().trim()

        if (name.isEmpty() || response.isEmpty()) {
            Toast.makeText(this, "Please fill both the fields!", Toast.LENGTH_SHORT).show()
            return
        }

        val bitmap = croppedFaceBitmap ?: run {
            Toast.makeText(this, "Please detect a face first!", Toast.LENGTH_SHORT).show()
            return
        }

        //        Uncomment this to view all files in assets
        //        val assetManager = assets
        //        val fileList = assetManager.list("")
        //        Log.d("FaceEmbedder", "Assets available: ${fileList?.joinToString()}")

        val embedder = FaceEmbedder(this)
        val newEmbedding = embedder.getFaceEmbedding(bitmap)

        val db = ChatDatabase.getDatabase(applicationContext)
        CoroutineScope(Dispatchers.IO).launch {
            val existingFaces = db.registeredFaceDao().getAllFaces()
            val gson = Gson()

            // Check for duplicates
            val duplicate = existingFaces.any {
                val existingEmbedding = gson.fromJson(it.embedding, FloatArray::class.java)
                val distance = calculateEuclideanDistance(newEmbedding, existingEmbedding)
                distance < 1.0f // Threshold — adjust as needed
            }

            if (duplicate) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CustomFaceActivity, "This face is already registered!", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            // Save if not duplicate
            val jsonEmbedding = gson.toJson(newEmbedding)
            val byteArray = bitmapToByteArray(bitmap)
            val faceModel = RegisteredFaceModel(name = name, response = response, embedding = jsonEmbedding, image = byteArray)
            db.registeredFaceDao().insert(faceModel)

            withContext(Dispatchers.Main) {
                Toast.makeText(this@CustomFaceActivity, "Face saved to DB!", Toast.LENGTH_SHORT).show()
                clearInputs()
                loadRegisteredFaces()
            }
        }
    }

    private fun clearInputs() {
        binding.etNameInput.text.clear()
        binding.etResponseInput.text.clear()
        binding.ivFacePreview.setImageResource(R.drawable.custom_face_img_placeholder)
        croppedFaceBitmap = null
    }

    // Euclidean distance function
    private fun calculateEuclideanDistance(embed1: FloatArray, embed2: FloatArray): Float {
        var sum = 0f
        for (i in embed1.indices) {
            sum += (embed1[i] - embed2[i]) * (embed1[i] - embed2[i])
        }
        return kotlin.math.sqrt(sum)
    }

    private fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    private fun loadRegisteredFaces() {
        CoroutineScope(Dispatchers.IO).launch {
            val db = ChatDatabase.getDatabase(applicationContext)
            val list = db.registeredFaceDao().getAllFaces().sortedByDescending { it.id }
            withContext(Dispatchers.Main) {
                faceList.clear()
                faceList.addAll(list)
                adapter.notifyDataSetChanged()
            }
        }
    }



}
