package com.example.adapt.views

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.example.adapt.R
import com.example.adapt.databinding.ActivityMainBinding
import com.example.adapt.viewModel.ChatViewModel
import kotlinx.coroutines.launch
//import transliterateToTamil
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build

import kotlin.math.sqrt
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.adapt.db.ChatDatabase
import com.example.adapt.viewModel.FaceEmbedder
import com.example.adapt.viewModel.FaceViewModel
import com.google.gson.Gson
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val chatViewModel: ChatViewModel by viewModels()
    private val faceViewModel: FaceViewModel by viewModels()

    private var tapCount = 0
    private var lastTapTime = 0L

    // PermissionsArray: READ_EXTERNAL_STORAGE is deprecated from Android 13 (API 33), Instead, use READ_MEDIA_IMAGES.
    private val permissionsArray = arrayOf(
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.CAMERA,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            android.Manifest.permission.READ_MEDIA_IMAGES
        else
            android.Manifest.permission.READ_EXTERNAL_STORAGE
    )

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechRecognizerIntent: Intent
    private var selectedLanguageCode = "en-US"
    private var isListening = false
    private var isSpeaking = false

    private val faceExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSaveChat.setOnClickListener {
            val intent = Intent(this, CustomQueryActivity::class.java)
            startActivity(intent)
        }

//        hideSystemUI()
        lockSystemUI()
        setupSecretExitTap()
        initGemini()
        playBGVideo(R.raw.bg_ideal)
        setUpSpinner()
        initTTS()
        setupSpeechToText()
        startFaceRecognition()
    }

//    Uncomment this and comment lockSystemUI to hide the System top UI (by default),but on swipping above it will show
//    private fun hideSystemUI() {
//        window.decorView.systemUiVisibility = (
//                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
//                        or View.SYSTEM_UI_FLAG_FULLSCREEN
//                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
//                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
//                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
//                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
//                )
//    }

    private fun lockSystemUI(){
        val devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val componentName = ComponentName(this, MyDeviceAdminReceiver::class.java)

        // If already a device owner or admin:
        if (devicePolicyManager.isDeviceOwnerApp(packageName)) {
            devicePolicyManager.setLockTaskPackages(componentName, arrayOf(packageName))
            startLockTask()
        } else {
            // Fallback: allow lock task anyway (if app is manually whitelisted)
            startLockTask()
        }
    }

    private fun setupSecretExitTap() {
        val tapZone = findViewById<View>(R.id.exitTapZone)
        tapZone.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastTapTime < 800) {
                tapCount++
            } else {
                tapCount = 1
            }
            lastTapTime = currentTime

            if (tapCount >= 5) {
                tapCount = 0
                stopLockTask()
                finish() // Optional: or navigate to another screen
                Toast.makeText(this, "Exiting lock mode", Toast.LENGTH_SHORT).show()
            }
        }
    }



    private fun playBGVideo(resourceId: Int) {
        val videoUri = Uri.parse("android.resource://$packageName/$resourceId")
        binding.backgroundVideo.setVideoURI(videoUri)
        binding.backgroundVideo.setOnPreparedListener {
            it.isLooping = true
            it.setVolume(0f, 0f)
            binding.backgroundVideo.start()
        }
    }

    private fun setUpSpinner() {
        val languages = listOf("English", "தமிழ்")
        val adapter = ArrayAdapter(this, R.layout.spinner_item, languages)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        binding.languageSpinner.adapter = adapter
        binding.languageSpinner.setSelection(0)

        binding.languageSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    selectedLanguageCode = if (position == 0) "en-US" else "ta-IN"
                    chatViewModel.updateLanguage(selectedLanguageCode)

                    if (!ChatViewModel.hasIntroInitialized) {
                        initGemini()
                    }

                    Toast.makeText(
                        this@MainActivity,
                        "Language: ${parent.getItemAtPosition(position)}",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            }
    }

    private fun initGemini() {
        lifecycleScope.launch {
            chatViewModel.initGeminiAI(selectedLanguageCode)
            ChatViewModel.hasIntroInitialized = true
        }
    }

    private fun initTTS() {
        chatViewModel.initTTS(
            context = this,
            onStart = {
                isSpeaking = true
                runOnUiThread {
                    playBGVideo(R.raw.bg_speaking)
                }
            },
            onDone = {
                isSpeaking = false
                runOnUiThread {
                    playBGVideo(R.raw.bg_ideal)
                    binding.responseTextView.text = ""
                }
            }
        )
    }

    private fun setupSpeechToText() {
        if (permissionsArray.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(this, permissionsArray, 200)
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLanguageCode)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak something...")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
        }

        binding.btnStartListening.setOnClickListener {
            if (!isListening) {
                binding.btnStartListening.text = "Listening..."
                speechRecognizer.startListening(speechRecognizerIntent)
                isListening = true
            } else {
                binding.btnStartListening.text = "Touch me to speak"
                playBGVideo(R.raw.bg_ideal)
                speechRecognizer.stopListening()
                isListening = false
            }
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
                binding.btnStartListening.text = "Touch me to speak"
                playBGVideo(R.raw.bg_ideal)
            }

            override fun onError(error: Int) {
                isListening = false
                binding.btnStartListening.text = "Touch me to speak"
                playBGVideo(R.raw.bg_ideal)
                val errorMsg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic permission denied"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "Didn't understand, try again"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy, try again"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                    else -> "Unknown error"
                }

                Log.e("STT", "Error: $errorMsg")
                Toast.makeText(
                    this@MainActivity,
                    "Error recognizing speech: $errorMsg",
                    Toast.LENGTH_SHORT
                ).show()
            }

            override fun onResults(results: Bundle?) {
                val speechToTextResult =
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!speechToTextResult.isNullOrEmpty()) {
                    val phoneticInput = speechToTextResult[0].lowercase().trim()
                    Log.d("MainActivity", "STT Raw: $phoneticInput")

                    binding.responseTextView.text =
                        if (selectedLanguageCode == "ta-IN") "பதில் தயாராகிறது..." else "Processing..."

                    lifecycleScope.launch {
                        // Transliterate Tamil if detected or selected
//                        val tamilKeyword = transliterateToTamil(phoneticInput)
                        val tamilKeyword = phoneticInput
                        val englishKeyword = phoneticInput

                        // Try both Tamil and English keywords
                        val savedResponse =
                            chatViewModel.getResponseForKeyword(tamilKeyword ?: englishKeyword)
                                ?: chatViewModel.getResponseForKeyword(englishKeyword)

                        Log.d(
                            "MainActivity",
                            "tamilKeyword: $tamilKeyword, englishKeyword: $englishKeyword, savedResponse: $savedResponse"
                        )

                        if (savedResponse != null) {
                            Log.d("MainActivity", "Found Saved Response")
                            binding.responseTextView.text = savedResponse
                            chatViewModel.speakOut(savedResponse, selectedLanguageCode)
                        } else {
                            Log.d("MainActivity", "No Saved Response. Using Gemini.")
                            val reply = chatViewModel.sendMessageToGeminiAI(
                                englishKeyword,
                                selectedLanguageCode
                            )
                            binding.responseTextView.text = reply
                            chatViewModel.speakOut(reply, selectedLanguageCode)
                        }
                    }
                }
            }


            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 200 && grantResults.isNotEmpty()) {
            val deniedPermissions = permissions.zip(grantResults.toTypedArray())
                .filter { it.second != PackageManager.PERMISSION_GRANTED }
                .map { it.first }

            if (deniedPermissions.isEmpty()) {
                Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show()
                setupSpeechToText()
            } else {
                val deniedMsg = when {
                    deniedPermissions.contains(android.Manifest.permission.RECORD_AUDIO) ||
                            deniedPermissions.contains(android.Manifest.permission.CAMERA) ->
                        "Microphone and Camera permissions are required."

                    deniedPermissions.contains(android.Manifest.permission.READ_EXTERNAL_STORAGE) ||
                            deniedPermissions.contains(android.Manifest.permission.READ_MEDIA_IMAGES) ->
                        "Gallery permission is required to pick an image."

                    else -> "Some permissions were denied."
                }

                Toast.makeText(this, deniedMsg, Toast.LENGTH_LONG).show()
            }
        }
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun startFaceRecognition() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            faceViewModel.reloadRegisteredFaces()
            faceViewModel.startFaceRecognition(
                cameraProvider = cameraProvider,
                executor = faceExecutor,
                lifecycleOwner = this
            )
        }, ContextCompat.getMainExecutor(this))

        // Observe matched face
        lifecycleScope.launchWhenStarted {
            faceViewModel.matchedFace.collect { faceMatch ->
                Toast.makeText(this@MainActivity, "Matched: ${faceMatch.name}", Toast.LENGTH_SHORT).show()
                binding.responseTextView.text = faceMatch.response
                chatViewModel.speakOut(faceMatch.response, selectedLanguageCode)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.backgroundVideo.start()
        faceViewModel.reloadRegisteredFaces()
    }

    override fun onPause() {
        super.onPause()
        binding.backgroundVideo.pause()
    }

    override fun onDestroy() {
        speechRecognizer.destroy()
        chatViewModel.shutdownTTS()
        super.onDestroy()
    }
}
