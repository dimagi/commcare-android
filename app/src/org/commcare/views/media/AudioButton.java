package org.commcare.views.media;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.Toast;

import org.commcare.dalvik.R;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;

import java.io.File;
import java.io.IOException;

/**
 * @author ctsims
 * @author carlhartung
 * @author amstone326
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class AudioButton extends ImageButton implements OnClickListener {
    private final static String TAG = AudioController.class.getSimpleName();

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
     * The id of the ListAdapter view that contains this button. Should be null
     * if the button resides in a form entry question.
     */
    private ViewId residingViewId;

    /**
     * Used by media inflater.
     */
    public AudioButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOnClickListener(this);
    }

    /**
     * @param URI audio to load when play button pressed
     */
    public AudioButton(Context context, final String URI, boolean visible) {
        this(context, URI, null, visible);
    }

    /**
     * @param URI     audio to load when play button pressed
     * @param viewId  Id for the ListAdapter view that contains this button
     * @param visible Should the button be visible?
     */
    public AudioButton(Context context, String URI,
                       ViewId viewId, boolean visible) {
        super(context);
        setOnClickListener(this);

        resetButton(URI, viewId, visible);
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
        setFocusable(false);
        setFocusableInTouchMode(false);

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
     * Set button appearance and playback state to 'ready'. Used when another
     * button is pressed and this one is reset.
     */
    public void setStateToReady() {
        currentState = MediaState.Ready;
        refreshAppearance();
    }

    /**
     * Change button appearance to match the playback state.
     */
    private void refreshAppearance() {
        switch (currentState) {
            case Ready:
                this.setImageResource(R.drawable.icon_audioplay_lightcool);
                break;
            case Playing:
                this.setImageResource(R.drawable.icon_audiostop_darkwarm);
                break;
            case Paused:
            case PausedForRenewal:
                this.setImageResource(R.drawable.icon_audioplay_lightcool);
        }
    }

    /**
     * Gets the audio source filename from the URI.
     *
     * @return Filepath of audio source stored in local URI. Returns an empty
     * string if no audio source is found.
     */
    private String getAudioFilename() {
        if (URI == null) {
            // No audio file specified
            Log.e(TAG, "No audio file was specified");
            Toast.makeText(getContext(),
                    getContext().getString(R.string.audio_file_error),
                    Toast.LENGTH_LONG).show();
            return "";
        }

        String audioFilename;
        try {
            audioFilename =
                    ReferenceManager._().DeriveReference(URI).getLocalURI();
        } catch (InvalidReferenceException e) {
            Log.e(TAG, "Invalid reference exception");
            e.printStackTrace();
            return "";
        }

        File audioFile = new File(audioFilename);
        if (!audioFile.exists()) {
            // We should have an audio clip, but the file doesn't exist.
            String errorMsg =
                    getContext().getString(R.string.file_missing, audioFile);
            Log.e(TAG, errorMsg);
            Toast.makeText(getContext(), errorMsg, Toast.LENGTH_LONG).show();
            return "";
        }
        return audioFilename;
    }

    @Override
    public void onClick(View v) {
        String audioFilename = getAudioFilename();
        if ("".equals(audioFilename)) {
            return;
        }

        switch (currentState) {
            case Ready:
                MediaPlayer player = new MediaPlayer();
                try {
                    player.setDataSource(audioFilename);
                    player.prepare();
                    player.setOnCompletionListener(new OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mediaPlayer) {
                            endPlaying();
                        }

                    });
                    AudioController.INSTANCE.setCurrentMediaAndButton(new MediaEntity(URI, player, residingViewId, currentState), this);
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

    private void startPlaying() {
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
}
