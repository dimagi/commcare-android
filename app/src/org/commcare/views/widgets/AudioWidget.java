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
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.StringData;
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

public class AudioWidget extends QuestionWidget {
    private static final String TAG = AudioWidget.class.getSimpleName();

    private Button mCaptureButton;
    private Button mPlayButton;
    private Button mChooseButton;
    protected final PendingCalloutInterface pendingCalloutInterface;

    protected String prevFileName;
    protected String mBinaryName;
    protected final String mInstanceFolder;

    public AudioWidget(Context context, final FormEntryPrompt prompt, PendingCalloutInterface pic) {
        super(context, prompt);

        initializeButtons(prompt);
        setupLayout();

        this.pendingCalloutInterface = pic;

        mInstanceFolder =
                FormEntryActivity.mInstancePath.substring(0,
                        FormEntryActivity.mInstancePath.lastIndexOf("/") + 1);

        setOrientation(LinearLayout.VERTICAL);

        // retrieve answer from data model and update ui
        mBinaryName = prompt.getAnswerText();
        if (mBinaryName != null) {
            togglePlayButton(true);
            File f = new File(mInstanceFolder + mBinaryName);

            checkFileSize(f);
        } else {
            togglePlayButton(false);
        }

        String acq = prompt.getAppearanceHint();
        if ((QuestionWidget.ACQUIREFIELD.equalsIgnoreCase(acq))) {
            mChooseButton.setVisibility(View.GONE);
        }
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

    private void deleteMedia() {
        // get the file path and delete the file
        File f = new File(mInstanceFolder + mBinaryName);
        if (!f.delete()) {
            Log.i(TAG, "Failed to delete " + f);
        }

        // clean up variables
        mBinaryName = null;
    }

    @Override
    public void clearAnswer() {
        // remove the file
        deleteMedia();

        // reset buttons
        togglePlayButton(false);
    }

    @Override
    public IAnswerData getAnswer() {
        if (mBinaryName != null) {
            return new StringData(mBinaryName);
        } else {
            return null;
        }
    }

    @Override
    public void setBinaryData(Object binaryuri) {
        // when replacing an answer. remove the current media.
        if (mBinaryName != null) {
            deleteMedia();
        }

        String binaryPath = createFilePath(binaryuri);

        // get the file path and create a copy in the instance folder

        String[] filenameSegments = binaryPath.split("\\.");
        String extension = "";
        if (filenameSegments.length > 1) {
            extension = "." + filenameSegments[filenameSegments.length - 1];
        }

        String[] filePathSegments = binaryPath.split("/");
        if(filePathSegments.length >1){
            prevFileName = filePathSegments[filePathSegments.length-1];
        }

        String destAudioPath = mInstanceFolder + System.currentTimeMillis() + extension;

        File source = new File(binaryPath);
        File newAudio = new File(destAudioPath);
        try {
            FileUtil.copyFile(source, newAudio);
        }catch (IOException e) {
            Log.e(TAG, "IOExeception while copying audio");
            e.printStackTrace();
        }

        checkFileSize(newAudio);

        if (newAudio.exists()) {
            // Add the copy to the content provider
            ContentValues values = new ContentValues(6);
            values.put(Audio.Media.TITLE, newAudio.getName());
            values.put(Audio.Media.DISPLAY_NAME, newAudio.getName());
            values.put(Audio.Media.DATE_ADDED, System.currentTimeMillis());
            values.put(Audio.Media.DATA, newAudio.getAbsolutePath());

            Uri AudioURI =
                    getContext().getContentResolver().insert(Audio.Media.EXTERNAL_CONTENT_URI, values);
            Log.i(TAG, "Inserting AUDIO returned uri = " + AudioURI.toString());
        } else {
            Log.e(TAG, "Inserting Audio file FAILED");
        }

        mBinaryName = newAudio.getName();
    }

    //If file is chosen by user, the file selection intent will return an URI
    //If file is auto-selected after recording_fragment, then the recordingfragment will provide a string file path
    private String createFilePath(Object binaryuri){
        String path;

        if(binaryuri instanceof Uri){
            path = UriToFilePath.getPathFromUri(CommCareApplication._(),
                    (Uri)binaryuri);
        }else{
            path = (String) binaryuri;
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
