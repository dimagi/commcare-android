package org.commcare.views.media;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;

import org.commcare.dalvik.R;
import org.commcare.mediadownload.MissingMediaDownloadHelper;
import org.commcare.mediadownload.MissingMediaDownloadResult;
import org.commcare.utils.AndroidUtil;
import org.commcare.utils.FileUtil;

import java.io.IOException;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public abstract class AudioPlaybackButtonBase extends FrameLayout {

    private final static String TAG = AudioPlaybackButtonBase.class.getSimpleName();
    /**
     * Audio to load when play button pressed.
     */
    private String URI;

    /**
     * Audio playback state, used for correctly displaying the button and
     * dispatching playback logic on button presses.
     */
    private MediaState currentState;

    /**
     * Unique ID used to re-attach button to currently playing media
     */
    private ViewId residingViewId;

    private ImageButton playButton;

    public AudioPlaybackButtonBase(Context context) {
        super(context);
        setupView(context);
    }

    /**
     * Used by media inflater.
     */
    public AudioPlaybackButtonBase(Context context, AttributeSet attrs) {
        super(context, attrs);
        setupView(context);
    }

    public AudioPlaybackButtonBase(Context context, final String URI,
                                   ViewId viewId, boolean visible) {
        super(context);
        setupView(context);

        modifyButtonForNewView(viewId, URI, visible);
    }

    protected void setupView(Context context) {
        LayoutInflater vi = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = vi.inflate(getLayout(), null);

        addView(view);
        setupButton();
    }

    private void setupButton() {
        playButton = findViewById(R.id.play_button);
        playButton.setOnClickListener(buildOnClickListener());
    }

    /**
     * Set button appearance and playback state to 'ready'. Used when another
     * button is pressed and this one is reset.
     */
    public void resetPlaybackState() {
        currentState = MediaState.Ready;
        refreshAppearance();
    }

    /**
     * Change button appearance to match the playback state.
     */
    private void refreshAppearance() {
        switch (currentState) {
            case Ready:
                resetProgressBar();
                playButton.setSelected(false);
                break;
            case Playing:
                startProgressBar(
                        AudioController.INSTANCE.getCurrentPosition(),
                        AudioController.INSTANCE.getDuration());
                playButton.setSelected(true);
                break;
            case Paused:
            case PausedForRenewal:
                pauseProgressBar();
                playButton.setSelected(false);
                break;
            default:
                break;
        }

        if (currentState == MediaState.Missing) {
            playButton.setImageResource(R.drawable.update_download_icon);
        } else {
            playButton.setImageResource(R.drawable.audio_playback_selector);
        }
    }

    public void startPlaying() {
        AudioController.INSTANCE.playCurrentMediaEntity();

        currentState = MediaState.Playing;
        refreshAppearance();
    }

    public void endPlaying() {
        currentState = MediaState.Ready;
        refreshAppearance();
    }

    private void pausePlaying() {
        AudioController.INSTANCE.pauseCurrentMediaEntity();
        currentState = MediaState.Paused;
        refreshAppearance();
    }

    private OnClickListener buildOnClickListener() {
        return view -> {
            switch (currentState) {
                case Missing:
                    downloadMissingAudioResource();
                    break;
                case Ready:
                    try {
                        MediaEntity mediaEntity = new MediaEntity(URI, residingViewId, currentState);
                        AudioController.INSTANCE.setCurrentMediaAndButton(
                                mediaEntity,
                                AudioPlaybackButtonBase.this);
                        startPlaying();
                    } catch (IOException e) {
                        String errorMsg =
                                getContext().getString(R.string.audio_file_invalid);
                        Log.e(TAG, errorMsg);
                        Toast.makeText(getContext(), errorMsg, Toast.LENGTH_LONG).show();
                        e.printStackTrace();
                    }
                    break;
                case PausedForRenewal:
                case Paused:
                    startPlaying();
                    break;
                case Playing:
                    pausePlaying();
                    break;
                default:
                    Log.w(TAG, "Current playback state set to unexpected value");
            }
        };
    }

    private void downloadMissingAudioResource() {
        AndroidUtil.showToast(getContext(), R.string.media_download_started);
        MissingMediaDownloadHelper.requestMediaDownload(URI, result -> {
            if (result instanceof MissingMediaDownloadResult.Success) {
                boolean mediaPresent = FileUtil.referenceFileExists(URI);
                if (mediaPresent) {
                    currentState = MediaState.Ready;
                    AndroidUtil.showToast(getContext(), R.string.media_download_completed);
                } else {
                    currentState = MediaState.Missing;
                    AndroidUtil.showToast(getContext(), R.string.media_download_failed);
                }
                refreshAppearance();
            } else if (result instanceof MissingMediaDownloadResult.InProgress) {
                AndroidUtil.showToast(getContext(), R.string.media_download_in_progress);
            } else {
                Toast.makeText(getContext(), result.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Setup button using the AudioController's state if containing view ids
     * match-up between the button and the controller. Otherwise, setup the
     * button using the provided arguments.
     *
     * @param viewId  Set button's residing view id to this ListAdapter view id.
     * @param URI     audio to load when play button pressed
     * @param visible Should the button be visible?
     */
    public void modifyButtonForNewView(ViewId viewId, String URI,
                                       boolean visible) {
        if (AudioController.INSTANCE.isMediaLoaded() &&
                AudioController.INSTANCE.getMediaViewId().equals(viewId)) {
            // The containing view's id of this button and that of the audio
            // being played by the controller match. Hence, load media info
            // from the controller into this button.
            this.URI = AudioController.INSTANCE.getMediaUri();
            this.residingViewId = AudioController.INSTANCE.getMediaViewId();
            this.currentState = AudioController.INSTANCE.getMediaState();
            AudioController.INSTANCE.registerPlaybackButton(this);
            refreshAppearance();
            toggleVisibility(visible);
        } else {
            // the containing view's id of the button doesn't match the audio
            // controller, so just setup the button normally using the provided
            // arguments
            resetButton(URI, viewId, visible);
        }
        validateUri(visible);
    }

    private void validateUri(boolean visible) {
        if(!TextUtils.isEmpty(URI)) {
            boolean exists = FileUtil.referenceFileExists(URI);
            if (!exists) {
                currentState = MediaState.Missing;
                refreshAppearance();
                if(visible) {
                    AndroidUtil.showToast(getContext(), R.string.audio_download_prompt);
                }
            }
        }
    }

    /**
     * Set playback and display state to ready and update media URI.
     *
     * @param URI     audio to load when play button pressed
     * @param visible Should the button be visible?
     */
    public void resetButton(String URI, boolean visible) {
        this.URI = URI;
        this.currentState = MediaState.Ready;
        // sets the correct icon for this MediaState
        refreshAppearance();

        // Set not focusable so that list onclick will work
        playButton.setFocusable(false);
        playButton.setFocusableInTouchMode(false);
        toggleVisibility(visible);
    }

    private void toggleVisibility(boolean visible) {
        if (visible) {
            setVisibility(View.VISIBLE);
        } else {
            setVisibility(View.GONE);
        }
    }

    /**
     * Set playback and display state to ready and update media URI and the id
     * of the button's containing view.
     *
     * @param URI     audio to load when play button pressed
     * @param viewId  Set button's residing view id to this ListAdapter view id.
     * @param visible Should the button be visible?
     */
    private void resetButton(String URI, ViewId viewId, boolean visible) {
        resetButton(URI, visible);
        this.residingViewId = viewId;
    }

    protected abstract void startProgressBar(int milliPosition, int milliDuration);

    protected abstract void resetProgressBar();

    protected abstract void pauseProgressBar();

    protected abstract int getLayout();
}
