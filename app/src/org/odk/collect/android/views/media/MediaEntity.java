package org.odk.collect.android.views.media;

import android.media.MediaPlayer;

/**
 * This class can be used to represent any single entity of audio or video media,
 * encompassing its source, the current state (playing, paused, etc.), the MediaPlayer
 * object used to play it, etc.
 *
 * @author amstone326
 */

class MediaEntity {

    private final String source;
    private final ViewId idOfOriginView;
    private final MediaPlayer player;
    private MediaState MediaState;

    public MediaEntity(String source, MediaPlayer player, ViewId id, MediaState state) {
        this.player = player;
        this.source = source;
        this.idOfOriginView = id;
        this.MediaState = state;
    }

    public ViewId getId() {
        return idOfOriginView;
    }

    public MediaState getState() {
        return MediaState;
    }

    public void setState(MediaState state) {
        this.MediaState = state;
    }

    MediaPlayer getPlayer() {
        return player;
    }

    public String getSource() {
        return source;
    }
}
