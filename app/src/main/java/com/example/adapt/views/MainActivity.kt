package com.example.adapt.views

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.adapt.databinding.ActivityMainBinding
import com.example.adapt.viewModel.GeminiHandler
import com.example.adapt.viewModel.SimpleSpeechListener
import com.example.adapt.viewModel.TTSManager
import com.example.adapt.viewModel.VideoSyncManager
import kotlinx.coroutines.*
import android.widget.AdapterView
import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var responseTextView: TextView
    private lateinit var startListeningButton: Button
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var textToSpeech: TextToSpeech

    private lateinit var geminiHandler: GeminiHandler
    private lateinit var ttsManager: TTSManager
    private lateinit var videoSyncManager: VideoSyncManager

    private var isSpeaking = false
    private var isButtonTriggeredListening = false
    private var currentLanguage = "en-US"

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_REQUEST_CODE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        responseTextView = binding.responseTextView
        startListeningButton = binding.startListeningButton

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        textToSpeech = TextToSpeech(this, this)

        geminiHandler = GeminiHandler()
        ttsManager = TTSManager(this, textToSpeech, ::onTTSStart, ::onTTSEnd)
        videoSyncManager = VideoSyncManager(this, binding.backgroundVideo)

        setupPermissions()
        setupListeners()
        setupLanguageSelector()
        setupInitialVideo()
    }

    private fun setupListeners() {
        startListeningButton.setOnClickListener {
            if (!isButtonTriggeredListening) {
                startVoiceInput()
            }
        }
    }

    private fun setupPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_REQUEST_CODE)
        }
    }

    private fun setupLanguageSelector() {
        binding.languageSpinner.setSelection(0)

        binding.languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                currentLanguage = if (position == 0) "en-US" else "ta-IN"
                ttsManager.updateLanguage(currentLanguage)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Optional: handle case if nothing selected
            }
        }
    }

    private fun setupInitialVideo() {
        videoSyncManager.setIdleVideo("android.resource://$packageName/raw/bg_ideal")
        videoSyncManager.setSpeakVideo("android.resource://$packageName/raw/bg_speaking")
        videoSyncManager.playIdle()
    }

    private fun startVoiceInput() {
        if (isSpeaking) textToSpeech.stop()
        isButtonTriggeredListening = true
        startListeningButton.text = "Listening..."
        responseTextView.text = ""

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguage)
        }

        speechRecognizer.setRecognitionListener(object : SimpleSpeechListener() {
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                text?.let { processInput(it) }
                resetListeningButton()
            }
            override fun onError(error: Int) {
                Toast.makeText(this@MainActivity, "Please speak clearly.", Toast.LENGTH_SHORT).show()
                resetListeningButton()
            }
        })

        speechRecognizer.startListening(intent)
    }

    private fun resetListeningButton() {
        isButtonTriggeredListening = false
        startListeningButton.text = "Start Speaking"
    }

    private fun processInput(input: String) {
        scope.launch {
            responseTextView.text = if (currentLanguage == "ta-IN") "பதில் தயாராகிறது..." else "Processing..."
            val reply = geminiHandler.getResponse(input, currentLanguage)
            responseTextView.text = reply
            ttsManager.speak(reply, currentLanguage)
        }
    }

    private fun onTTSStart() {
        isSpeaking = true
        videoSyncManager.playSpeaking()
    }

    private fun onTTSEnd() {
        isSpeaking = false
        videoSyncManager.playIdle()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) ttsManager.updateLanguage(currentLanguage)
    }

    override fun onDestroy() {
        speechRecognizer.destroy()
        textToSpeech.shutdown()
        super.onDestroy()
    }
}