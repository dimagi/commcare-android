package org.commcare.activities

import android.content.Intent
import android.os.Bundle
import android.widget.MediaController
import androidx.appcompat.app.AppCompatActivity
import org.commcare.dalvik.databinding.ActivityFullscreenVideoViewBinding
import org.commcare.views.media.CommCareMediaController

/**
 * Activity to view inline videos in fullscreen mode, it returns the last time position to the
 * calling activity
 *
 * @author avazirna
 */
class FullscreenVideoViewActivity: AppCompatActivity() {

    private lateinit var viewBinding: ActivityFullscreenVideoViewBinding
    private var lastPosition = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityFullscreenVideoViewBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // Get video URI from intent, finish if no URI is available
        intent.data?.let { viewBinding.fullscreenVideoView.setVideoURI(intent.data) } ?: { finish() }

        lastPosition = restoreLastPosition(savedInstanceState)

        viewBinding.fullscreenVideoView.setMediaController(MediaController(this))
        viewBinding.fullscreenVideoView.setOnPreparedListener {
            if (lastPosition != -1) {
                viewBinding.fullscreenVideoView.seekTo(lastPosition)
            }
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

    // priority is given to lastPosition saved state
    private fun restoreLastPosition(savedInstanceState: Bundle?): Int {
        val intentExtras = intent.extras
        if (savedInstanceState != null && savedInstanceState.containsKey(
                CommCareMediaController.INLINE_VIDEO_TIME_POSITION
            )) {
            return savedInstanceState.getInt(CommCareMediaController.INLINE_VIDEO_TIME_POSITION)
        } else if (intentExtras !=null && intentExtras.containsKey(
                CommCareMediaController.INLINE_VIDEO_TIME_POSITION
            )) {
            return intentExtras.getInt(CommCareMediaController.INLINE_VIDEO_TIME_POSITION)
        }
        return -1
    }
}
