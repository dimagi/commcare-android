package org.odk.collect.android.views.media;

import java.io.File;
import java.io.IOException;

import org.commcare.dalvik.R;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.Logger;

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
 */
public class AudioButton extends ImageButton implements OnClickListener {
    private final static String t = "AudioButton";
    private String URI;
    private MediaState currentState;
    private Object residingViewId;

    /**
     * Used by inflater.
     */
    public AudioButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOnClickListener(this);
    }

    public AudioButton(Context context, final String URI, boolean visible) {
        this(context, URI, null, visible);
    }

    public AudioButton(Context context, String URI, Object id, boolean visible) {
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

    void resetButton(String URI, Object id, boolean visible) {
        resetButton(URI, visible);
        this.residingViewId = id;
    }

    void restoreButtonFromEntity(MediaEntity currentEntity) {
        this.URI = currentEntity.getSource();
        this.residingViewId = currentEntity.getId();
        this.currentState = currentEntity.getState();
        AudioControllerSingleton.INSTANCE.setButton(this);
        refreshAppearance();
    }

    public String getSource() {
        return URI;
    }

    public void modifyButtonForNewView(Object newViewId, String audioResource,
                                       boolean visible) {
        if (residingViewId == null) {
            throw new RuntimeException("shouldn't happen");
        }

        MediaEntity currentEntity = AudioControllerSingleton.INSTANCE.getCurrMedia();
        if (currentEntity != null && currentEntity.getId().equals(newViewId)) {
            restoreButtonFromEntity(currentEntity);
        } else {
            resetButton(audioResource, newViewId, visible);
        }
    }

    public void setStateToReady() {
        currentState = MediaState.Ready;
        refreshAppearance();
    }

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
            Log.e(t, "No audio file was specified");
            Toast.makeText(getContext(), getContext().getString(R.string.audio_file_error),
                       Toast.LENGTH_LONG).show();
            return "";
        }

        String audioFilename;
        try {
            audioFilename = ReferenceManager._().DeriveReference(URI).getLocalURI();
        } catch (InvalidReferenceException e) {
            Log.e(t, "Invalid reference exception");
            e.printStackTrace();
            return "";
        }

        File audioFile = new File(audioFilename);
        if (!audioFile.exists()) {
            // We should have an audio clip, but the file doesn't exist.
            String errorMsg = getContext().getString(R.string.file_missing, audioFile);
            Log.e(t, errorMsg);
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
                    AudioControllerSingleton.INSTANCE.setCurrentMediaAndButton(new MediaEntity(URI, player, residingViewId, currentState), this);
                    startPlaying();
                } catch (IOException e) {
                    String errorMsg = getContext().getString(R.string.audio_file_invalid);
                    Log.e(t, errorMsg);
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
        }
    }

    void startPlaying() {
        AudioControllerSingleton.INSTANCE.playCurrentMediaEntity();

        currentState = MediaState.Playing;
        refreshAppearance();
    }

    public void endPlaying() {
        AudioControllerSingleton.INSTANCE.releaseCurrentMediaEntity();

        currentState = MediaState.Ready;
        refreshAppearance();
    }

    void pausePlaying() {
        AudioControllerSingleton.INSTANCE.pauseCurrentMediaEntity();

        currentState = MediaState.Paused;
        refreshAppearance();
    }
}
