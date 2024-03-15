package org.commcare.activities

import android.content.Intent
import android.os.Bundle
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

        viewBinding.fullscreenVideoView.setMediaController(CommCareMediaController(this, true))
        viewBinding.fullscreenVideoView.setOnPreparedListener {
            if (lastPosition != -1) {
                viewBinding.fullscreenVideoView.seekTo(lastPosition)
            }
            viewBinding.fullscreenVideoView.start()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (lastPosition != -1) {
            outState.putInt(CommCareMediaController.INLINE_VIDEO_TIME_POSITION, lastPosition)
        }
    }

    override fun onPause() {
        super.onPause()
        if (viewBinding.fullscreenVideoView != null) {
            viewBinding.fullscreenVideoView.pause()
            lastPosition = viewBinding.fullscreenVideoView.currentPosition
        }
    }

    override fun onBackPressed() {
        setResultIntent()
        super.onBackPressed()
    }

    private fun setResultIntent(){
        val i = Intent()
        i.putExtra(CommCareMediaController.INLINE_VIDEO_TIME_POSITION, viewBinding.fullscreenVideoView.currentPosition)
        this.setResult(RESULT_OK, i)
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
