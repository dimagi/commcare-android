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
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.commcare.CommCareApplication;
import org.commcare.activities.FormEntryActivity;
import org.commcare.dalvik.R;
import org.commcare.logic.PendingCalloutInterface;
import org.commcare.utils.FileUtil;
import org.commcare.utils.StringUtils;
import org.commcare.utils.UriToFilePath;
import org.javarosa.form.api.FormEntryPrompt;

import java.io.File;
import java.io.IOException;

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

    private Button mCaptureButton;
    private Button mPlayButton;
    private Button mChooseButton;
    protected final PendingCalloutInterface pendingCalloutInterface;

    protected String recordedFileName;
    private String customFileTag;

    public AudioWidget(Context context, final FormEntryPrompt prompt, PendingCalloutInterface pic) {
        super(context, prompt);

        initializeButtons(prompt);
        setupLayout();

        this.pendingCalloutInterface = pic;

        setOrientation(LinearLayout.VERTICAL);

        // retrieve answer from data model and update ui
        mBinaryName = prompt.getAnswerText();
        if (mBinaryName != null) {
            reloadFile();
        } else {
            togglePlayButton(false);
        }
    }

    protected void reloadFile() {
        togglePlayButton(true);
        File f = new File(mInstanceFolder + mBinaryName);
        checkFileSize(f);
    }

    protected void togglePlayButton(boolean enabled) {
        mPlayButton.setEnabled(enabled);
    }

    protected void initializeButtons(final FormEntryPrompt prompt){
        // setup capture button
        mCaptureButton = new Button(getContext());
        WidgetUtils.setupButton(mCaptureButton,
                StringUtils.getStringSpannableRobust(getContext(), R.string.capture_audio),
                mAnswerFontsize,
                !prompt.isReadOnly());

        // setup audio filechooser button
        mChooseButton = new Button(getContext());
        WidgetUtils.setupButton(mChooseButton,
                StringUtils.getStringSpannableRobust(getContext(), R.string.choose_sound),
                mAnswerFontsize,
                !prompt.isReadOnly());

        // setup play button
        mPlayButton = new Button(getContext());
        WidgetUtils.setupButton(mPlayButton,
                StringUtils.getStringSpannableRobust(getContext(), R.string.play_audio),
                mAnswerFontsize,
                !prompt.isReadOnly());

        // launch capture intent on click
        mCaptureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                captureAudio(prompt);
            }
        });

        // launch audio filechooser intent on click
        mChooseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("audio/*");
                try {
                    ((Activity)getContext()).startActivityForResult(i, FormEntryActivity.AUDIO_VIDEO_FETCH);
                    pendingCalloutInterface.setPendingCalloutFormIndex(prompt.getIndex());
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(getContext(),
                            StringUtils.getStringSpannableRobust(getContext(),
                                    R.string.activity_not_found,
                                    "choose audio"),
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        if (QuestionWidget.ACQUIREFIELD.equalsIgnoreCase(prompt.getAppearanceHint())) {
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
        File f = new File(mInstanceFolder + mBinaryName);
        i.setDataAndType(Uri.fromFile(f), "audio/*");
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

    protected void setupLayout() {
        // finish complex layout
        addView(mCaptureButton);
        addView(mChooseButton);
        addView(mPlayButton);
    }

    protected void captureAudio(FormEntryPrompt prompt) {
        Intent i = new Intent(Audio.Media.RECORD_SOUND_ACTION);
        i.putExtra(android.provider.MediaStore.EXTRA_OUTPUT,
                Audio.Media.EXTERNAL_CONTENT_URI.toString());
        try {
            ((Activity)getContext()).startActivityForResult(i, FormEntryActivity.AUDIO_VIDEO_FETCH);
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
        String binaryPath = checkBinarySize(binaryuri);
        if (binaryPath == null) {
            return;
        }
        File source = new File(binaryPath);

        // get the file path and create a copy in the instance folder
        String[] filenameSegments = binaryPath.split("\\.");
        String extension = "";
        if (filenameSegments.length > 1) {
            extension = "." + filenameSegments[filenameSegments.length - 1];
        }

        String[] filePathSegments = binaryPath.split("/");
        if(filePathSegments.length >1){
            recordedFileName = filePathSegments[filePathSegments.length-1];
        }

        String destAudioPath = mInstanceFolder + System.currentTimeMillis() + customFileTag + extension;

        File newAudio = new File(destAudioPath);
        try {
            FileUtil.copyFile(source, newAudio);
        }catch (IOException e) {
            Log.e(TAG, "IOExeception while copying audio");
            e.printStackTrace();
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
        } else {
            Log.e(TAG, "Inserting Audio file FAILED");
        }

        mBinaryName = newAudio.getName();
    }

    //If file is chosen by user, the file selection intent will return an URI
    //If file is auto-selected after recording_fragment, then the recordingfragment will provide a string file path
    //Set value of customFileTag if the file is a recent recording from the RecordingFragment
    @Override
    protected String createFilePath(Object binaryuri){
        String path;

        if(binaryuri instanceof Uri){
            path = UriToFilePath.getPathFromUri(CommCareApplication._(),
                    (Uri)binaryuri);
            customFileTag = "";
        }else{
            path = (String) binaryuri;
            customFileTag = CUSTOM_TAG;
        }

        return path;
    }

    @Override
    public void setFocus(Context context) {
        // Hide the soft keyboard if it's showing.
        InputMethodManager inputManager =
                (InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow(this.getWindowToken(), 0);
    }

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        mCaptureButton.setOnLongClickListener(l);
        mChooseButton.setOnLongClickListener(l);
        mPlayButton.setOnLongClickListener(l);
    }

    @Override
    public void unsetListeners() {
        super.unsetListeners();

        mCaptureButton.setOnLongClickListener(null);
        mChooseButton.setOnLongClickListener(null);
        mPlayButton.setOnLongClickListener(null);
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        mCaptureButton.cancelLongPress();
        mChooseButton.cancelLongPress();
        mPlayButton.cancelLongPress();
    }
}
