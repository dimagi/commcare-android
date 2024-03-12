package org.commcare.activities

import android.os.Bundle
import android.widget.MediaController
import androidx.appcompat.app.AppCompatActivity
import org.commcare.dalvik.databinding.ActivityFullscreenVideoViewBinding

/**
 * Activity to view inline videos in fullscreen mode, it returns the last time position to the
 * calling activity
 *
 * @author avazirna
 */
class FullscreenVideoViewActivity: AppCompatActivity() {

    private lateinit var viewBinding: ActivityFullscreenVideoViewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityFullscreenVideoViewBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // Get video URI from intent, finish if no URI is available
        intent.data?.let { viewBinding.fullscreenVideoView.setVideoURI(intent.data) } ?: { finish() }

        viewBinding.fullscreenVideoView.setMediaController(MediaController(this))
        viewBinding.fullscreenVideoView.setOnPreparedListener {
            viewBinding.fullscreenVideoView.start()
        }
    }

    override fun onPause() {
        super.onPause()
        if (viewBinding.fullscreenVideoView != null) {
            viewBinding.fullscreenVideoView.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewBinding.fullscreenVideoView.stopPlayback()
    }
}
