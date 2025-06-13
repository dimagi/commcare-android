package org.commcare.views.widgets;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaRecorder;
import android.os.Build;

import org.commcare.util.LogTypes;
import org.javarosa.core.services.Logger;

import java.io.IOException;

import static android.media.MediaFormat.MIMETYPE_AUDIO_AAC;

public class AudioRecordingHelper {
    private static final int HEAAC_SAMPLE_RATE = 44100;
    private static final int AMRNB_SAMPLE_RATE = 8000;

    public MediaRecorder setupRecorder(String fileName) {
        MediaRecorder recorder = new MediaRecorder();

        boolean isHeAacSupported = isHeAacEncoderSupported();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION);
        } else {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            recorder.setPrivacySensitive(true);
        }
        recorder.setAudioSamplingRate(isHeAacSupported ? HEAAC_SAMPLE_RATE : AMRNB_SAMPLE_RATE);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        if (isHeAacSupported) {
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.HE_AAC);
        } else {
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        }
        recorder.setOutputFile(fileName);

        try {
            recorder.prepare();
            Logger.log(LogTypes.TYPE_MEDIA_EVENT, "Preparing recording: " + fileName
                    + " | " + (isHeAacSupported ? HEAAC_SAMPLE_RATE : AMRNB_SAMPLE_RATE)
                    + " | " + (isHeAacSupported ? MediaRecorder.AudioEncoder.HE_AAC :
                    MediaRecorder.AudioEncoder.AMR_NB));

        } catch (IOException e) {
            e.printStackTrace();
        }
        return recorder;
    }

    // Checks whether the device supports High Efficiency AAC (HE-AAC) audio codec
    private boolean isHeAacEncoderSupported() {
        int numCodecs = MediaCodecList.getCodecCount();

        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);

            if (!codecInfo.isEncoder()) {
                continue;
            }

            for (String supportedType : codecInfo.getSupportedTypes()) {
                if (supportedType.equalsIgnoreCase(MIMETYPE_AUDIO_AAC)) {
                    MediaCodecInfo.CodecCapabilities cap = codecInfo.getCapabilitiesForType(MIMETYPE_AUDIO_AAC);
                    MediaCodecInfo.CodecProfileLevel[] profileLevels = cap.profileLevels;
                    for (MediaCodecInfo.CodecProfileLevel profileLevel : profileLevels) {
                        int profile = profileLevel.profile;
                        if (profile == MediaCodecInfo.CodecProfileLevel.AACObjectHE
                                || profile == MediaCodecInfo.CodecProfileLevel.AACObjectHE_PS) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }
}
