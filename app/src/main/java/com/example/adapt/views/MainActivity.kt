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
import com.example.adapt.db.ChatModel
import com.example.adapt.viewModel.BackgroundFaceService
import com.example.adapt.viewModel.ChatViewModel
import kotlinx.coroutines.launch
//import transliterateToTamil
import android.content.Context

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val chatViewModel: ChatViewModel by viewModels()

    private val permissionsArray = arrayOf(
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.CAMERA
    )
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechRecognizerIntent: Intent
    private var selectedLanguageCode = "en-US"
    private var isListening = false
    private var isSpeaking = false

    private var faceServiceBound = false
    private var backgroundFaceService: BackgroundFaceService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSaveChat.setOnClickListener {
            val intent = Intent(this, CustomQueryActivity::class.java)
            startActivity(intent)
        }

        initGemini()
        playBGVideo(R.raw.bg_ideal)
        setUpSpinner()
        initTTS()
        setupSpeechToText()
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
                override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
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
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
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
                Toast.makeText(this@MainActivity, "Error recognizing speech: $errorMsg", Toast.LENGTH_SHORT).show()
            }

            override fun onResults(results: Bundle?) {
                val speechToTextResult = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!speechToTextResult.isNullOrEmpty()) {
                    val phoneticInput = speechToTextResult[0].lowercase().trim()
                    Log.d("MainActivity", "STT Raw: $phoneticInput")

                    binding.responseTextView.text = if (selectedLanguageCode == "ta-IN") "பதில் தயாராகிறது..." else "Processing..."

                    lifecycleScope.launch {
                        // Transliterate Tamil if detected or selected
//                        val tamilKeyword = transliterateToTamil(phoneticInput)
                        val tamilKeyword = phoneticInput
                        val englishKeyword = phoneticInput

                        // Try both Tamil and English keywords
                        val savedResponse = chatViewModel.getResponseForKeyword(tamilKeyword ?: englishKeyword)
                            ?: chatViewModel.getResponseForKeyword(englishKeyword)

                        Log.d("MainActivity", "tamilKeyword: $tamilKeyword, englishKeyword: $englishKeyword, savedResponse: $savedResponse")

                        if (savedResponse != null) {
                            Log.d("MainActivity", "Found Saved Response")
                            binding.responseTextView.text = savedResponse
                            chatViewModel.speakOut(savedResponse, selectedLanguageCode)
                        } else {
                            Log.d("MainActivity", "No Saved Response. Using Gemini.")
                            val reply = chatViewModel.sendMessageToGeminiAI(englishKeyword, selectedLanguageCode)
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 200 && grantResults.isNotEmpty()) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if (allGranted) {
                Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show()
                setupSpeechToText()
            } else {
                Toast.makeText(this, "Microphone and Camera permissions are required", Toast.LENGTH_LONG).show()
            }
        }
    }

    private val faceServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BackgroundFaceService.LocalBinder
            backgroundFaceService = binder.getService()
            faceServiceBound = true

            backgroundFaceService?.setCallback(object : BackgroundFaceService.FaceRecognitionCallback {
                override fun onFirstFaceDetected() {
                    runOnUiThread {
                        val greeting = if (selectedLanguageCode == "ta-IN") {
                            "வணக்கம்! அடாப்ட் ரோபாட்டிக்ஸ் சார்பாக எங்கள் T-Bot உங்களை அன்புடன் வரவேற்கிறது."
                        } else {
                            "Welcome to Adapt Robotics! I'm T-Bot, it's a pleasure to meet you and assist you today."
                        }
                        binding.responseTextView.text = greeting

//                        Toast.makeText(this@MainActivity, greeting, Toast.LENGTH_LONG).show()
                        chatViewModel.speakOut(greeting, selectedLanguageCode)
                    }
                }

            })
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            faceServiceBound = false
            backgroundFaceService = null
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, BackgroundFaceService::class.java).also { intent ->
            bindService(intent, faceServiceConnection, Context.BIND_AUTO_CREATE)
            startService(intent)
        }
    }

    override fun onStop() {
        super.onStop()
        if (faceServiceBound) {
            unbindService(faceServiceConnection)
            faceServiceBound = false
        }
    }

    override fun onResume() {
        super.onResume()
        binding.backgroundVideo.start()
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
