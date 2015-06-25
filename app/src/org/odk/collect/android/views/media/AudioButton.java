package org.odk.collect.android.views.media;

import java.io.File;
import java.io.IOException;

import org.commcare.dalvik.R;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.Toast;

/**
 * @author ctsims
 * @author carlhartung
 * @author amstone326
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class AudioButton extends ImageButton implements OnClickListener {
    private final static String TAG = AudioController.class.getSimpleName();

    private String URI;
    private MediaState currentState;
    private ViewId residingViewId;

    /**
     * Used by media inflater.
     */
    public AudioButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOnClickListener(this);
    }

    public AudioButton(Context context, final String URI, boolean visible) {
        this(context, URI, null, visible);
    }

    public AudioButton(Context context, String URI, ViewId id, boolean visible) {
        super(context);
        setOnClickListener(this);

        resetButton(URI, visible);

        this.residingViewId = id;
    }

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

    void resetButton(String URI, ViewId id, boolean visible) {
        resetButton(URI, visible);
        this.residingViewId = id;
    }

    public void modifyButtonForNewView(ViewId newViewId, String audioResource,
                                       boolean visible) {
        if (AudioController.INSTANCE.isMediaLoaded() &&
                AudioController.INSTANCE.getMediaViewId().equals(newViewId)) {
            // The view id of this button and that of the audio being played by
            // the controller match. Hence, load media info from the controller
            // into this button.
            this.URI = AudioController.INSTANCE.getMediaUri();
            this.residingViewId = AudioController.INSTANCE.getMediaViewId();
            this.currentState = AudioController.INSTANCE.getMediaState();
            AudioController.INSTANCE.registerPlaybackButton(this);
            refreshAppearance();
        } else {
            resetButton(audioResource, newViewId, visible);
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
    void refreshAppearance() {
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

        switch(currentState) {
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
