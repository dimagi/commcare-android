package org.commcare.views.media;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Build;
import android.support.v4.util.Pair;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.commcare.dalvik.R;
import org.commcare.interfaces.AudioPlaybackReset;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;

import java.io.File;
import java.io.IOException;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class AudioPlaybackButton extends LinearLayout implements AudioPlaybackReset {
    private final static String TAG = AudioPlaybackButton.class.getSimpleName();

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

    private ImageButton playButton;
    private ObjectAnimator animation;

    /**
     * Used by media inflater.
     */
    public AudioPlaybackButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        setupView(context);
    }

    /**
     * @param URI audio to load when play button pressed
     */
    public AudioPlaybackButton(Context context, final String URI, boolean visible) {
        this(context, URI, null, visible);
    }

    /**
     * @param URI     audio to load when play button pressed
     * @param viewId  Id for the ListAdapter view that contains this button
     * @param visible Should the button be visible?
     */
    public AudioPlaybackButton(Context context, String URI,
                               ViewId viewId, boolean visible) {
        super(context);
        setupView(context);

        resetButton(URI, viewId, visible);
    }

    private void setupView(Context context) {
        LayoutInflater vi = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = vi.inflate(R.layout.small_audio_playback, null);
        addView(view);
        setupButton();
    }

    private void setupButton() {
        playButton = (ImageButton)findViewById(R.id.play_button);

        // Set not focusable so that list onclick will work
        playButton.setFocusable(false);
        playButton.setFocusableInTouchMode(false);

        playButton.setOnClickListener(buildOnClickListener());
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
    @Override
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
                clearProgressBar();
                playButton.setImageResource(R.drawable.play_question_audio);
                break;
            case Playing:
                playButton.setImageResource(R.drawable.pause_question_audio);
                break;
            case Paused:
            case PausedForRenewal:
                pauseProgressBar();
                playButton.setImageResource(R.drawable.play_question_audio);
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

    private OnClickListener buildOnClickListener() {
        return new OnClickListener() {
            @Override
            public void onClick(View view) {
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
                            AudioController.INSTANCE.setCurrentMediaAndButton(new MediaEntity(URI, player, residingViewId, currentState), AudioPlaybackButton.this);
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

    private void startPlaying() {
        Pair<Integer, Integer> posAndduration = AudioController.INSTANCE.playCurrentMediaEntity();

        currentState = MediaState.Playing;
        refreshAppearance();

        if (posAndduration != null) {
            animateProgress(posAndduration.first, posAndduration.second);
        }
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

    private void animateProgress(int milliPosition, int milliDuration) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            ProgressBar progressBar = (ProgressBar)findViewById(R.id.circular_progress_bar);
            animation = ObjectAnimator.ofInt(progressBar, "progress", 0, 500);
            animation.setDuration(milliDuration);
            animation.setCurrentPlayTime(milliPosition);
            animation.setInterpolator(new DecelerateInterpolator());
            animation.start();
        }
    }

    private void clearProgressBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            ProgressBar progressBar = (ProgressBar)findViewById(R.id.circular_progress_bar);
            if (animation != null) {
                animation.removeAllListeners();
                animation.end();
                animation.cancel();
            }
            progressBar.clearAnimation();
            progressBar.setProgress(0);
        }
    }

    private void pauseProgressBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            animation.pause();
        }
    }
}
