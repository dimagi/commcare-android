package org.odk.collect.android.views.media;

import android.media.MediaPlayer;
import android.util.Log;

/**
 * Audio playback is delegated through this singleton class since only one
 * track should play at a time.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public enum AudioControllerSingleton {
    INSTANCE;

    private static final String TAG = AudioControllerSingleton.class.getSimpleName();

    private MediaEntity currentEntity;
    private AudioButton currentButton;
    private MediaState stateBeforePause;

    public void playPreviousAudio() {
        if (currentEntity != null) {
            switch (currentEntity.getState()) {
                case PausedForRenewal:
                    playCurrentMediaEntity();
                    break;
                case Paused:
                    break;
                case Playing:
                case Ready:
                    Log.w(TAG, "State in loadPreviousAudio is invalid");
            }
        }
    }

    public MediaEntity getCurrMedia() {
        return currentEntity;
    }

    public boolean isMediaLoaded() {
        return currentEntity != null;
    }

    public void setCurrent(MediaEntity e) {
        if (e == currentEntity) {
            return;
        }
        releaseCurrentMediaEntity();
        currentEntity = e;
    }

    public void setCurrentMediaAndButton(MediaEntity media,
                                         AudioButton clickedButton) {
        if (currentButton != null && currentButton != clickedButton) {
            // reset the old button to not be playing
            currentButton.setStateToReady();
        }
        currentButton = clickedButton;
        setCurrent(media);
    }

    public void releaseCurrentMediaEntity() {
        if (currentEntity != null) {
            MediaPlayer mp = currentEntity.getPlayer();
            mp.reset();
            mp.release();
        }
        currentEntity = null;
    }

    public void playCurrentMediaEntity() {
        if (currentEntity != null) {
            MediaPlayer mp = currentEntity.getPlayer();
            mp.start();
            currentEntity.setState(MediaState.Playing);
        }
    }

    public void pauseCurrentMediaEntity() {
        if (currentEntity != null) {
            stateBeforePause = currentEntity.getState();
            if (currentEntity.getState().equals(MediaState.Playing)) {
                MediaPlayer mp = currentEntity.getPlayer();
                mp.pause();
                currentEntity.setState(MediaState.Paused);
            }
        }
    }

    public void setPauseForRenewal() {
        if (stateBeforePause != null && stateBeforePause.equals(MediaState.Playing)) {
            currentEntity.setState(MediaState.PausedForRenewal);
        }
    }
}
