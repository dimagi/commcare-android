package org.commcare.views.widgets;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Build;

import org.commcare.preferences.DeveloperPreferences;
import org.commcare.preferences.PrefValues;
import org.commcare.util.LogTypes;
import org.javarosa.core.services.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static android.media.MediaFormat.MIMETYPE_AUDIO_AAC;

public class AudioRecordingHelper {
    private static final int HEAAC_SAMPLE_RATE = 44100;
    private static final int AMRNB_SAMPLE_RATE = 8000;
    private static final int OPUS_SAMPLE_RATE = 48000;
    private static final int BALANCED_BIT_RATE = 24000;

    static final class EncoderProfile {
        final int encoder, samplerate, bitrate, container;
        public EncoderProfile(int encoder, int samplerate) {
            this(encoder, samplerate, -1);
        }
        public EncoderProfile(int encoder, int samplerate, int bitrate) {
            this(encoder, samplerate, bitrate, MediaRecorder.OutputFormat.MPEG_4);
        }
        public EncoderProfile(int encoder, int samplerate, int bitrate, int container) {
            this.encoder = encoder;
            this.samplerate = samplerate;
            this.bitrate = bitrate;
            this.container = container;
        }
    }


    public MediaRecorder setupRecorder(String fileName, String profile) {
        MediaRecorder recorder = new MediaRecorder();

        EncoderProfile encoder = filterBestSupportedEncoder(profile);

        if (PrefValues.AUDIO_QUALITY_SMALLEST.equals(profile) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION);
        } else if (PrefValues.AUDIO_QUALITY_BALANCED.equals(profile)) {
            recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION);
        } else {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            recorder.setPrivacySensitive(true);
        }

        recorder.setOutputFormat(encoder.container);
        recorder.setAudioEncoder(encoder.encoder);
        recorder.setAudioSamplingRate(encoder.samplerate);

        if (encoder.bitrate != -1) {
            recorder.setAudioEncodingBitRate(encoder.bitrate);
        }

        recorder.setOutputFile(fileName);

        try {
            recorder.prepare();
            Logger.log(LogTypes.TYPE_MEDIA_EVENT, "Preparing recording: " + fileName
                    + " | " + encoder.samplerate
                    + " | " + encoder.encoder
                    + " | " + encoder.bitrate);

        } catch (IOException e) {
            e.printStackTrace();
        }
        return recorder;
    }

    // Checks whether the device supports High Efficiency AAC (HE-AAC) audio codec
    private EncoderProfile filterBestSupportedEncoder(String profileKey) {

        List<EncoderProfile> encoderPreference = new ArrayList<>();

        if (PrefValues.AUDIO_QUALITY_BALANCED.equals(profileKey)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                encoderPreference.add(new EncoderProfile(MediaRecorder.AudioEncoder.OPUS,
                        OPUS_SAMPLE_RATE, BALANCED_BIT_RATE, MediaRecorder.OutputFormat.OGG));
            }
            encoderPreference.add(new EncoderProfile(MediaRecorder.AudioEncoder.HE_AAC, HEAAC_SAMPLE_RATE, BALANCED_BIT_RATE));
            encoderPreference.add(new EncoderProfile(MediaRecorder.AudioEncoder.AAC, HEAAC_SAMPLE_RATE, BALANCED_BIT_RATE));
        } else {
            encoderPreference.add(new EncoderProfile(MediaRecorder.AudioEncoder.HE_AAC, HEAAC_SAMPLE_RATE));
            encoderPreference.add(new EncoderProfile(MediaRecorder.AudioEncoder.AMR_NB, AMRNB_SAMPLE_RATE));
        }

        HashSet<Integer> encoders = new HashSet<>();

        //Always available
        encoders.add(MediaRecorder.AudioEncoder.AAC);
        encoders.add(MediaRecorder.AudioEncoder.AMR_NB);

        int numCodecs = MediaCodecList.getCodecCount();

        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);

            if (!codecInfo.isEncoder()) {
                continue;
            }

            for (String supportedType : codecInfo.getSupportedTypes()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        supportedType.equalsIgnoreCase(MediaFormat.MIMETYPE_AUDIO_OPUS)) {
                    encoders.add(MediaRecorder.AudioEncoder.OPUS);
                }
                if (supportedType.equalsIgnoreCase(MIMETYPE_AUDIO_AAC)) {
                    MediaCodecInfo.CodecCapabilities cap = codecInfo.getCapabilitiesForType(MIMETYPE_AUDIO_AAC);
                    MediaCodecInfo.CodecProfileLevel[] profileLevels = cap.profileLevels;
                    for (MediaCodecInfo.CodecProfileLevel profileLevel : profileLevels) {
                        int profile = profileLevel.profile;
                        if (profile == MediaCodecInfo.CodecProfileLevel.AACObjectHE
                                || profile == MediaCodecInfo.CodecProfileLevel.AACObjectHE_PS) {
                            encoders.add(MediaRecorder.AudioEncoder.HE_AAC);
                        }
                    }
                }
            }
        }

        //preferences list is in order, pick the first supported one
        for (EncoderProfile profile : encoderPreference) {
            if (encoders.contains(profile.encoder)) {
                return profile;
            }
        }

        throw new RuntimeException("No supported audio codecs for recording");
    }
}
