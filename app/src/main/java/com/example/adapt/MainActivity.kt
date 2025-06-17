package com.example.adapt

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.adapt.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        playBGVideo()

        val languages = listOf("English", "தமிழ்")
        val adapter = ArrayAdapter(this, R.layout.spinner_item, languages)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        binding.languageSpinner.adapter = adapter

        binding.languageSpinner.setSelection(0) // Set default to English

        binding.languageSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val selectedLanguage = parent.getItemAtPosition(position).toString()
                    when (selectedLanguage) {
                        "தமிழ்" -> {

                            Toast.makeText(this@MainActivity, "Language: Tamil", Toast.LENGTH_SHORT)
                                .show()
                        }

                        "English" -> {
                            // Default case
                            Toast.makeText(this@MainActivity, "Language: English", Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            }


    }

    private fun playBGVideo() {
        val videoUri = Uri.parse("android.resource://${packageName}/${R.raw.bg_ideal_video}")
        binding.backgroundVideo.setVideoURI(videoUri)

        // Loop the video
        binding.backgroundVideo.setOnPreparedListener { mediaPlayer ->
            mediaPlayer.isLooping = true
            mediaPlayer.setVolume(0f, 0f) // mute video
            binding.backgroundVideo.start()
        }

        // Optional: If something interrupts, restart
        binding.backgroundVideo.setOnCompletionListener {
            binding.backgroundVideo.start()
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
}
