package org.commcare.views.media;

import android.media.MediaPlayer;

import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;

import java.io.File;
import java.io.IOException;

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

    protected MediaEntity(String source, ViewId id, MediaState state) throws IOException {
        this.source = source;
        this.idOfOriginView = id;
        this.MediaState = state;
        player = new MediaPlayer();
        player.setDataSource(getAudioFilename(source));
        player.prepare();
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

    /**
     * Gets the audio source filename from the URI.
     *
     * @return Filepath of audio source stored in local URI. Returns an empty
     * string if no audio source is found.
     */
    private String getAudioFilename(String URI) throws IOException {
        if (URI == null) {
            throw new IOException("No audio file was specified");
        }

        String audioFilename;
        try {
            audioFilename =
                    ReferenceManager.instance().DeriveReference(URI).getLocalURI();
        } catch (InvalidReferenceException e) {
            throw new IOException(e);
        }

        File audioFile = new File(audioFilename);
        if (!audioFile.exists()) {
            throw new IOException("File missing: " + audioFilename);
        }
        return audioFilename;
    }
}
