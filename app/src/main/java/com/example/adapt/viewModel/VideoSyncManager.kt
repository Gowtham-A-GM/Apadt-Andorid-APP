package com.example.adapt.viewModel

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.VideoView

class VideoSyncManager(private val context: Context, private val videoView: VideoView) {

    private var idleUri: Uri? = null
    private var speakUri: Uri? = null

    init {
        idleUri = Uri.parse("android.resource://${context.packageName}/raw/bg_ideal")
        speakUri = Uri.parse("android.resource://${context.packageName}/raw/bg_speaking")
    }

    fun setIdleVideo(uriString: String) {
        idleUri = Uri.parse(uriString)
    }

    fun setSpeakVideo(uriString: String) {
        speakUri = Uri.parse(uriString)
    }

    fun playIdle() {
        playVideo(idleUri, "Idle")
    }

    fun playSpeaking() {
        playVideo(speakUri, "Speaking")
    }

    private fun playVideo(uri: Uri?, tag: String) {
        uri?.let {
            videoView.setVideoURI(it)
            videoView.setOnPreparedListener { mp ->
                mp.isLooping = true
                mp.start()
            }
            videoView.setOnErrorListener { _, what, extra ->
                Log.e("VideoSyncManager", "$tag video error: what=$what extra=$extra")
                true
            }
        }
    }
}
