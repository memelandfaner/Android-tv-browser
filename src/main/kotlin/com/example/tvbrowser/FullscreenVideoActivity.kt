package com.example.tvbrowser

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView

class FullscreenVideoActivity : Activity() {

    private lateinit var videoView: VideoView
    private lateinit var bufferingSpinner: ProgressBar
    private lateinit var topHud: View
    private lateinit var bottomHud: View
    private lateinit var txtTitle: TextView
    private lateinit var txtCurrentTime: TextView
    private lateinit var txtTotalDuration: TextView
    private lateinit var videoSeekBar: SeekBar
    private lateinit var feedbackToast: TextView

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isHudVisible = true
    private var videoDurationMs = 0

    private val hideHudRunnable = Runnable {
        hideHud()
    }

    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            if (videoView.isPlaying) {
                val cur = videoView.currentPosition
                txtCurrentTime.text = formatDuration(cur)
                if (videoDurationMs > 0) {
                    val progress = ((cur.toDouble() / videoDurationMs.toDouble()) * 1000).toInt()
                    videoSeekBar.progress = progress
                }
            }
            mainHandler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemUI()
        setContentView(R.layout.activity_fullscreen_video)

        videoView = findViewById(R.id.nativeVideoView)
        bufferingSpinner = findViewById(R.id.videoBufferingSpinner)
        topHud = findViewById(R.id.topHudContainer)
        bottomHud = findViewById(R.id.bottomHudContainer)
        txtTitle = findViewById(R.id.txtVideoTitle)
        txtCurrentTime = findViewById(R.id.txtCurrentTime)
        txtTotalDuration = findViewById(R.id.txtTotalDuration)
        videoSeekBar = findViewById(R.id.videoSeekBar)
        feedbackToast = findViewById(R.id.centerFeedbackToast)

        val videoUrl = intent.getStringExtra("VIDEO_URL") ?: ""
        val videoTitle = intent.getStringExtra("VIDEO_TITLE") ?: "Predvajalnik Videa"
        txtTitle.text = videoTitle

        if (videoUrl.isEmpty()) {
            Toast.makeText(this, "⚠️ Ni veljavnega video naslova", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 🔊 Hardware Sound Unmute (JBL 300 / Philips TV)
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        am?.let {
            it.mode = AudioManager.MODE_NORMAL
            @Suppress("DEPRECATION")
            it.setStreamMute(AudioManager.STREAM_MUSIC, false)
        }

        bufferingSpinner.visibility = View.VISIBLE

        videoView.setOnPreparedListener { mp ->
            bufferingSpinner.visibility = View.GONE
            videoDurationMs = mp.duration
            txtTotalDuration.text = formatDuration(videoDurationMs)
            mp.start()
            showHud()
            scheduleHideHud(4000)
            mainHandler.post(updateProgressRunnable)
        }

        videoView.setOnCompletionListener {
            Toast.makeText(this, "Predvajanje zaključeno", Toast.LENGTH_SHORT).show()
            finish()
        }

        videoView.setOnErrorListener { _, what, extra ->
            bufferingSpinner.visibility = View.GONE
            Toast.makeText(this, "⚠️ Napaka pri predvajanju videa ($what, $extra)", Toast.LENGTH_LONG).show()
            finish()
            true
        }

        videoView.setOnInfoListener { _, what, _ ->
            if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                bufferingSpinner.visibility = View.VISIBLE
            } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
                bufferingSpinner.visibility = View.GONE
            }
            false
        }

        videoView.setVideoURI(Uri.parse(videoUrl))
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        videoView.stopPlayback()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        showHud()
        scheduleHideHud(3500)

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                togglePlayPause()
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD -> {
                seekRelative(-10000)
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_STEP_FORWARD -> {
                seekRelative(10000)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                if (!videoView.isPlaying) videoView.start()
                showFeedback("▶ Predvajaj")
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (videoView.isPlaying) videoView.pause()
                showFeedback("⏸ Pavza")
                return true
            }
            KeyEvent.KEYCODE_MEDIA_STOP -> {
                finish()
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                finish()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun togglePlayPause() {
        if (videoView.isPlaying) {
            videoView.pause()
            showFeedback("⏸ Pavza")
        } else {
            videoView.start()
            showFeedback("▶ Predvajaj")
        }
    }

    private fun seekRelative(offsetMs: Int) {
        val cur = videoView.currentPosition
        val target = (cur + offsetMs).coerceIn(0, videoDurationMs.coerceAtLeast(0))
        videoView.seekTo(target)
        val sign = if (offsetMs > 0) "+10s" else "-10s"
        showFeedback("$sign (${formatDuration(target)})")
    }

    private fun showFeedback(text: String) {
        feedbackToast.text = text
        feedbackToast.visibility = View.VISIBLE
        mainHandler.removeCallbacks(hideFeedbackRunnable)
        mainHandler.postDelayed(hideFeedbackRunnable, 1500)
    }

    private val hideFeedbackRunnable = Runnable {
        feedbackToast.visibility = View.GONE
    }

    private fun showHud() {
        isHudVisible = true
        topHud.visibility = View.VISIBLE
        bottomHud.visibility = View.VISIBLE
    }

    private fun hideHud() {
        isHudVisible = false
        topHud.visibility = View.GONE
        bottomHud.visibility = View.GONE
    }

    private fun scheduleHideHud(delayMs: Long) {
        mainHandler.removeCallbacks(hideHudRunnable)
        if (videoView.isPlaying) {
            mainHandler.postDelayed(hideHudRunnable, delayMs)
        }
    }

    private fun formatDuration(millis: Int): String {
        val totalSec = (millis / 1000).coerceAtLeast(0)
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format("%02d:%02d", m, s)
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
    }
}
