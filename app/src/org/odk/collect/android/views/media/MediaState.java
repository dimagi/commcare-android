package org.odk.collect.android.views.media;

/**
 * Representation of the state of an AudioButton OR MediaEntity. For an
 * AudioButton, refers to the state of the media player that the button
 * controls. For a MediaEntity, refers to the state of that entity's media
 * player.
 *
 * @author amstone326
 */

public enum MediaState {

    /**
     * The MediaPlayer is currently playing music
     */
    Playing,

    /**
     * MediaPlayer methods setDataSource() and prepare() have been called,
     * but music is not currently playing
     */
    Paused,

    /**
     * The MediaPlayer has not had any data source initialized yet
     */
    Ready,

    /**
     * Represents the same MediaPlayer state as paused,
     * but used for activity life cycle purposes in handling rotation.
     * This state can be used to indicate to the onCreate method that
     * music was playing in the previous version of the app, was paused
     * onDestroy, and should be renewed to the Playing state upon resuming
     */
    PausedForRenewal
}
