package org.odk.collect.android.views.media;

import android.media.MediaPlayer;
import android.util.Log;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public enum AudioControllerSingleton {
    INSTANCE;

    private static final String TAG = AudioControllerSingleton.class.getSimpleName();

    private MediaEntity currentEntity;
    private AudioButton currentButton;
    private MediaState stateBeforePause;
    private boolean inFormEntry;

    public void loadPreviousAudio(AudioController oldController) {
        MediaEntity oldEntity = oldController.getCurrMedia();
        if (oldEntity != null) {
            this.currentEntity = oldEntity;
            oldController.removeCurrentMediaEntity();
        }
    }

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

    public boolean isInFormEntry() {
        return inFormEntry;
    }

    public MediaEntity getCurrMedia() {
        return currentEntity;
    }

    public boolean isMediaLoaded() {
        return currentEntity != null;
    }

    public void refreshCurrentAudioButton(AudioButton clickedButton) {
        if (currentButton != null && currentButton != clickedButton) {
            currentButton.setStateToReady();
        }
    }

    public void setCurrent(MediaEntity e, AudioButton b) {
        refreshCurrentAudioButton(b);
        setCurrent(e);
        setCurrentAudioButton(b);
    }

    public void setCurrent(MediaEntity e) {
        releaseCurrentMediaEntity();
        currentEntity = e;
    }

    public void setCurrentAudioButton(AudioButton b) {
        currentButton = b;
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
        if (currentEntity != null && currentEntity.getState().equals(MediaState.Playing)) {
            MediaPlayer mp = currentEntity.getPlayer();
            mp.pause();
            currentEntity.setState(MediaState.Paused);
        }
    }

    public Object getMediaEntityId() {
        return currentEntity.getId();
    }

    public void attemptSetStateToPauseForRenewal() {
        if (stateBeforePause != null && stateBeforePause.equals(MediaState.Playing)) {
            currentEntity.setState(MediaState.PausedForRenewal);
        }
    }

    public void saveEntityStateAndClear() {
        stateBeforePause = currentEntity.getState();
        pauseCurrentMediaEntity();
    }

    public void setMediaEntityState(MediaState state) {
        currentEntity.setState(state);
    }

    public void removeCurrentMediaEntity() {
        currentEntity = null;
    }

    public Integer getDuration() {
        if (currentEntity != null) {
            MediaPlayer mp = currentEntity.getPlayer();
            return mp.getDuration();
        }
        return null;
    }

    public Integer getProgress() {
        if (currentEntity != null) {
            MediaPlayer mp = currentEntity.getPlayer();
            return mp.getCurrentPosition();
        }
        return null;
    }
}
