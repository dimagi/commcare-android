package org.commcare.views.media;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.commcare.dalvik.R;

import java.io.IOException;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public abstract class AudioPlaybackButtonBase extends LinearLayout {

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
        playButton = (ImageButton)findViewById(R.id.play_button);
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
        }
    }

    public void startPlaying() {
        AudioController.INSTANCE.playCurrentMediaEntity();

        currentState = MediaState.Playing;
        refreshAppearance();
    }

    public void endPlaying() {
        AudioController.INSTANCE.releaseCurrentMediaEntity();

        currentState = MediaState.Ready;
        refreshAppearance();
    }

    private void pausePlaying() {
        AudioController.INSTANCE.pauseCurrentMediaEntity();

        currentState = MediaState.Paused;
        refreshAppearance();
    }

    private OnClickListener buildOnClickListener() {
        return new OnClickListener() {
            @Override
            public void onClick(View view) {
                switch (currentState) {
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
            }
        };
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
        } else {
            // the containing view's id of the button doesn't match the audio
            // controller, so just setup the button normally using the provided
            // arguments
            resetButton(URI, viewId, visible);
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

        if (visible) {
            this.setVisibility(View.VISIBLE);
        } else {
            this.setVisibility(View.INVISIBLE);
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
