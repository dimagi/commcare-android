/**
 * 
 */

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
    private String shortURI;
    private MediaState currentState;
    private AudioController controller;
    private Object residingViewId;

    /*
     * Constructor for if not explicitly using an AudioController
     */
    public AudioButton(Context context, final String URI, boolean visible) {
        this(context, null, URI, visible);
    }

    public AudioButton(Context context, AttributeSet attrs) {
        this(context, attrs, "", true);
    }

    public AudioButton(Context context, AttributeSet attributeSet, final String URI, boolean visible) {
        super(context, attributeSet);
        resetButton(URI, visible);
        shortURI = URI;
        shortURI = shortURI.replaceAll("^.*\\/", "");

        // default implementation of controller if none is passed in
        this.controller = new AudioController() {
            private MediaPlayer mp;
            boolean alive = false;
            
            @Override
            public MediaEntity getCurrMedia() {
                return null;
            }

            @Override
            public void setCurrent(MediaEntity newEntity) {
                this.mp = newEntity.getPlayer();
            }

            @Override
            public void setCurrent(MediaEntity newEntity, AudioButton newButton) {
                setCurrent(newEntity);
            }

            @Override
            public void releaseCurrentMediaEntity() {
                if (mp != null) {
                    mp.reset();
                    mp.release();
                    alive = false;
                }
            }

            @Override
            public Object getMediaEntityId() {
                return residingViewId;
            }

            @Override
            public void refreshCurrentAudioButton(AudioButton clicked) {
                return;
            }


            @Override
            public void saveEntityStateAndClear() {
                return;
            }

            @Override
            public void setMediaEntityState(MediaState state) {
                return;
            }

            @Override
            public void playCurrentMediaEntity() {
                alive = true;
                mp.start();
            }

            @Override
            public void pauseCurrentMediaEntity() {
                mp.pause();
            }

            @Override
            public void setCurrentAudioButton(AudioButton b) {
                return;
            }

            @Override
            public void removeCurrentMediaEntity() {
                return;
            }
            
            @Override
            public void attemptSetStateToPauseForRenewal() {
                return;
            }

            @Override
            public Integer getDuration() {
                if (!alive) {
                    return null;
                }
                return mp.getDuration();
            }
            
            @Override
            public Integer getProgress() {
                if (!alive) {
                    return null;
                }
                return mp.getCurrentPosition();
            }

        };
    }
    
    /*
     * Constructor for if an AudioController is being used
     */
    public AudioButton(Context context, String URI, Object id, AudioController controller,
            boolean visible) {
        this(context, URI, visible);
        if(controller != null) {
            this.controller = controller;
        }
        this.residingViewId = id;
    }
    
    public void resetButton(String URI, boolean visible) {
        this.URI = URI;
        this.currentState = MediaState.Ready;
        // sets the correct icon for this MediaState
        refreshAppearance();
        setFocusable(false);
        setFocusableInTouchMode(false);
        this.setOnClickListener(this);
        if (visible) {
            this.setVisibility(View.VISIBLE);
        }
        else {
            this.setVisibility(View.INVISIBLE);
        }
    }
    
    public void resetButton(String URI, Object id, boolean visible) {
        resetButton(URI, visible);
        this.residingViewId = id;
    }
    
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        /*As soon as this button is attached to the Window we want it to "grab" the handle
        to the currently playing media. This will have the side effect of dropping the handle
        from anything else that was currently holding it. Only one View at a time should
        be in control of the media handle.*/
        attachToMedia();
    }
    
    private void attachToMedia() {
        /*
         * Check if the button in this view had media assigned to 
         * it in a previously-existing app (before rotation, etc.)
         */
        MediaEntity currEntity = controller.getCurrMedia();
        if (currEntity != null) {
            Object oldId = currEntity.getId();
            if (oldId.equals(residingViewId)) {
                controller.setCurrentAudioButton(this);
                restoreButtonFromEntity(currEntity);
            }
        }
    }
    
    public void restoreButtonFromEntity(MediaEntity currentEntity) {
        this.URI = currentEntity.getSource();
        this.residingViewId = currentEntity.getId();
        this.currentState = currentEntity.getState();
        refreshAppearance();
    }
    
    public Object getViewId() {
        return residingViewId;
    }
        
    public String getSource() {
        return URI;
    }
    
    public void modifyButtonForNewView(Object newViewId, String audioResource, boolean visible) {
        MediaEntity currentEntity = controller.getCurrMedia();
        if (currentEntity == null) {
            resetButton(audioResource, newViewId, visible);
            return;
        }
        Object activeId = currentEntity.getId();
        if (activeId.equals(newViewId)) {
            restoreButtonFromEntity(currentEntity);
        }
        else {
            resetButton(audioResource, newViewId, visible);
        }
    }
    
    public void setStateToReady() {
        currentState = MediaState.Ready;
        refreshAppearance();
    }
    
    public void setStateToPlaying() {
        currentState = MediaState.Playing;
        refreshAppearance();
    }
    
    public void setStateToPaused() {
        currentState = MediaState.Paused;
        refreshAppearance();
    }
    
    public void refreshAppearance() {
        switch(currentState) {
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
    
    /*
     * Derives the audio source filename from the URI. Returns the 
     * filename if successful, and an empty string otherwise
     */
    private String preprocessClick() {
        if (URI == null) {
            // No audio file specified
            Log.e(t, "No audio file was specified");
            Toast.makeText(getContext(), getContext().getString(R.string.audio_file_error),
                       Toast.LENGTH_LONG).show();
            return "";
        }

        String audioFilename = "";
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
        String audioFilename = preprocessClick();
        if ("".equals(audioFilename)) return;
        
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
                controller.setCurrent(new MediaEntity(URI, player, residingViewId, 
                            currentState), this);
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
    
    public MediaState getMediaState() {
        return currentState;
    }
    
    private void logAction(String action) {
        String message = action + " " + shortURI;
        Integer progress = controller.getProgress();
        Integer duration = controller.getDuration();
        if (progress != null && duration != null) {
            message += " " + formatTime(progress) + "/" + formatTime(duration);
        }
        Logger.log("media", message);
    }
    
    private String formatTime(Integer milliseconds) {
        if (milliseconds == null) {
            return "";
        }
        int numSeconds = Math.round(milliseconds);
        int hours = (int) (numSeconds / 3600);
        int minutes = (int) (numSeconds / 60);
        int seconds = numSeconds % 60;
        String returnValue = "";
        returnValue += seconds;
        if (seconds < 10) {
            returnValue = "0" + returnValue;
        }
        returnValue = minutes + ":" + returnValue;
        if (hours > 0) {
            if (minutes < 10) {
                returnValue += "0" + returnValue;
            }
            returnValue += hours + ":" + returnValue;
        }
        return returnValue;
    }

    public void startPlaying() {
        logAction("start");
        controller.playCurrentMediaEntity();
        setStateToPlaying();
    }

    public void endPlaying() {
        logAction("stop");
        if (!currentState.equals(MediaState.Playing)) {
            controller.releaseCurrentMediaEntity();
            setStateToReady();
        }
    }

    public void pausePlaying() {
        logAction("pause");
        controller.pauseCurrentMediaEntity();
        setStateToPaused();
    }
}
