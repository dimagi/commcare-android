package org.commcare.views.widgets;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore.Audio;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import org.commcare.CommCareApplication;
import org.commcare.activities.components.FormEntryConstants;
import org.commcare.dalvik.R;
import org.commcare.logic.PendingCalloutInterface;
import org.commcare.utils.FileUtil;
import org.commcare.utils.StringUtils;
import org.commcare.utils.UriToFilePath;
import org.javarosa.form.api.FormEntryPrompt;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Widget that allows user to take pictures, sounds or video and add them to
 * the form.
 *
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public class AudioWidget extends MediaWidget {
    private static final String TAG = AudioWidget.class.getSimpleName();
    protected static final String CUSTOM_TAG = "custom";

    protected String recordedFileName;
    private String customFileTag;
    private String destAudioPath;

    public AudioWidget(Context context, final FormEntryPrompt prompt, PendingCalloutInterface pic) {
        super(context, prompt, pic);
    }

    @Override
    protected void initializeButtons() {
        // setup capture button
        mCaptureButton = new Button(getContext());
        WidgetUtils.setupButton(mCaptureButton,
                StringUtils.getStringSpannableRobust(getContext(), R.string.capture_audio),
                mAnswerFontSize,
                !mPrompt.isReadOnly());

        // setup audio filechooser button
        mChooseButton = new Button(getContext());
        WidgetUtils.setupButton(mChooseButton,
                StringUtils.getStringSpannableRobust(getContext(), R.string.choose_sound),
                mAnswerFontSize,
                !mPrompt.isReadOnly());

        // setup play button
        mPlayButton = new Button(getContext());
        WidgetUtils.setupButton(mPlayButton,
                StringUtils.getStringSpannableRobust(getContext(), R.string.play_audio),
                mAnswerFontSize,
                !mPrompt.isReadOnly());

        // launch capture intent on click
        mCaptureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                captureAudio(mPrompt);
            }
        });

        // launch audio filechooser intent on click
        mChooseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("audio/*");
                try {
                    ((Activity)getContext()).startActivityForResult(i, FormEntryConstants.AUDIO_VIDEO_FETCH);
                    pendingCalloutInterface.setPendingCalloutFormIndex(mPrompt.getIndex());
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(getContext(),
                            StringUtils.getStringSpannableRobust(getContext(),
                                    R.string.activity_not_found,
                                    "choose audio"),
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        if (QuestionWidget.ACQUIREFIELD.equalsIgnoreCase(mPrompt.getAppearanceHint())) {
            mChooseButton.setVisibility(View.GONE);
        }

        // on play, launch the appropriate viewer
        mPlayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playAudio();
            }
        });
    }

    protected void playAudio() {
        Intent i = new Intent("android.intent.action.VIEW");
        File audioFile = new File(mInstanceFolder + mBinaryName);
        Uri audioUri = FileUtil.getUriForExternalFile(getContext(), audioFile);
        i.setDataAndType(audioUri, "audio/*");

        UriToFilePath.grantPermissionForUri(getContext(), i, audioUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            getContext().startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(getContext(),
                    StringUtils.getStringSpannableRobust(getContext(),
                            R.string.activity_not_found,
                            "play audio"),
                    Toast.LENGTH_SHORT).show();
        }
    }

    protected void captureAudio(FormEntryPrompt prompt) {
        Intent i = new Intent(Audio.Media.RECORD_SOUND_ACTION);
        i.putExtra(android.provider.MediaStore.EXTRA_OUTPUT,
                Audio.Media.EXTERNAL_CONTENT_URI.toString());
        try {
            ((Activity)getContext()).startActivityForResult(i, FormEntryConstants.AUDIO_VIDEO_FETCH);
            pendingCalloutInterface.setPendingCalloutFormIndex(prompt.getIndex());
        } catch (ActivityNotFoundException e) {
            Toast.makeText(getContext(),
                    StringUtils.getStringSpannableRobust(getContext(),
                            R.string.activity_not_found,
                            "audio capture"),
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void setBinaryData(Object binaryuri) {
        String binaryPath = getBinaryPathWithSizeCheck(binaryuri);
        if (binaryPath == null) {
            return;
        }

        File newAudio;

        if (!destAudioPath.isEmpty()) {
            // we have already copied the file at newPath during createFilePath
            newAudio = new File(destAudioPath);
        } else {
            recordedFileName = FileUtil.getFileName(binaryPath);
            destAudioPath = mInstanceFolder + System.currentTimeMillis() + customFileTag + FileUtil.getExtension(binaryPath);

            // Copy to destAudioPath
            File source = new File(binaryPath);
            newAudio = new File(destAudioPath);
            try {
                FileUtil.copyFile(source, newAudio);
            } catch (IOException e) {
                showToast("form.attachment.copy.fail");
                Log.e(TAG, "IOExeception while copying audio");
                e.printStackTrace();
            }
        }

        if (newAudio.exists()) {
            // Add the copy to the content provider
            ContentValues values = new ContentValues(6);
            values.put(Audio.Media.TITLE, newAudio.getName());
            values.put(Audio.Media.DISPLAY_NAME, newAudio.getName());
            values.put(Audio.Media.DATE_ADDED, System.currentTimeMillis());
            values.put(Audio.Media.DATA, newAudio.getAbsolutePath());

            Uri audioUri =
                    getContext().getContentResolver().insert(Audio.Media.EXTERNAL_CONTENT_URI, values);
            String audioUriString = audioUri == null ? "null" : audioUri.toString();
            Log.i(TAG, "Inserting AUDIO returned uri = " + audioUriString);
            showToast("form.attachment.success");
        } else {
            Log.e(TAG, "Inserting Audio file FAILED");
        }

        mBinaryName = newAudio.getName();
    }

    /**
     * If file is chosen by user, the file selection intent will return an URI
     * If file is auto-selected after recording_fragment, then the recordingfragment will provide a string file path
     * Set value of customFileTag if the file is a recent recording from the RecordingFragment
     */
    @Override
    protected String createFilePath(Object binaryuri) {
        String path = "";
        destAudioPath = "";
        if (binaryuri instanceof Uri) {
            try {
                path = UriToFilePath.getPathFromUri(CommCareApplication.instance(),
                        (Uri)binaryuri);
            } catch (UriToFilePath.NoDataColumnForUriException e) {
                // Need to make a copy of file using uri, so might as well copy to final destination path directly
                InputStream inputStream;
                try {
                    inputStream = getContext().getContentResolver().openInputStream((Uri)binaryuri);
                } catch (FileNotFoundException e1) {
                    showToast("form.attachment.notfound");
                    e1.printStackTrace();
                    return "";
                }

                recordedFileName = FileUtil.getFileName(((Uri)binaryuri).getPath());
                destAudioPath = mInstanceFolder + System.currentTimeMillis() + FileUtil.getExtension(((Uri)binaryuri).getPath());

                try {
                    FileUtil.copyFile(inputStream, new File(destAudioPath));
                } catch (IOException e1) {
                    e1.printStackTrace();
                    showToast("form.attachment.copy.fail");
                    return "";
                }
                path = destAudioPath;
            }
            customFileTag = "";
        } else {
            path = (String)binaryuri;
            customFileTag = CUSTOM_TAG;
        }
        return path;
    }
}
