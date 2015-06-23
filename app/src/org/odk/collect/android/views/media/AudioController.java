package org.odk.collect.android.views.media;

/**
 * This interface is currently used for purposes of controlling audio buttons
 * that appear in a list adapter, for managing the re-attachment of a currently
 * active MediaEntity to its button of origin.
 *
 * Can be used for any need to control multiple audio buttons residing within the
 * same view
 *
 * @author amstone326
 */

public interface AudioController {

    /*
     * Returns the current MediaEntity, or null if none is set
     */
    public MediaEntity getCurrMedia();

    /*
     * Removes the current MediaEntity if there is one,
     * and sets the current MediaEntity to newEntity
     */
    public void setCurrent(MediaEntity newEntity);

    /*
     * Replaces the current MediaEntity with newEntity
     * and the current AudioButton with newButton
     */
    public void setCurrent(MediaEntity newEntity, AudioButton newButton);

    /*
     * Sets/replaces the current button
     */
    public void setCurrentAudioButton(AudioButton b);

    /*
     * Releases the current MediaEntity's associated MediaPlayer
     * and sets the current MediaEntity to null
     */
    public void releaseCurrentMediaEntity();

    /*
     * Sets the current MediaEntity to null
     */
    public void removeCurrentMediaEntity();

    /*
     * Starts playing the current MediaPlayer, assuming
     * setDataSource() and prepare() were already called successfully
     */
    public void playCurrentMediaEntity();

    /*
     * Pauses the current MediaPlayer
     */
    public void pauseCurrentMediaEntity();

    /*
     * Gets the associated viewId of the current MediaEntity
     */
    public Object getMediaEntityId();

    /*
     * Sets the state of the current MediaEntity
     */
    public void setMediaEntityState(MediaState state);

    /*
     * If the current button and the button passed in are
     * not the same button, resets the current button to
     * the ready state
     */
    public void refreshCurrentAudioButton(AudioButton clicked);


    /*
     * -Sets the current media entity's state to PausedForRenewal
     *  IF the state before it was paused was MediaState.Playing
     * -Should only be called after saveStateAndClear
     * -If implementing class is an activity, should be called in
     *  implementing class's onDestroy method
     */
    public void attemptSetStateToPauseForRenewal();

    /*
     * -Saves the current state and then pauses the current
     * media and clears the current button
     * -If implementing class is an activity, should be called in
     * implementing class's onPause method
     */
    public void saveEntityStateAndClear();

    /*
     * Return the length of the media, in milliseconds. May return
     * null if the media isn't in a state where duration is relevant.
     */
    public Integer getDuration();

    /*
     * Return the number of milliseconds the media has been playing.
     * May return null if the media is in a state where progress
     * isn't relevant.
     */
    public Integer getProgress();
}
