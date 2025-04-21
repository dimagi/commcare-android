package org.commcare.views.widgets;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.commcare.CommCareApplication;
import org.commcare.activities.FormEntryActivity;
import org.commcare.activities.components.FormEntryInstanceState;
import org.commcare.dalvik.R;
import org.commcare.logic.PendingCalloutInterface;
import org.commcare.models.encryption.EncryptionIO;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.util.LogTypes;
import org.commcare.utils.FileExtensionNotFoundException;
import org.commcare.utils.FileUtil;
import org.commcare.utils.FormUploadUtil;
import org.commcare.utils.StringUtils;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.IntegerData;
import org.javarosa.core.model.data.InvalidData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.form.api.FormEntryPrompt;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.crypto.spec.SecretKeySpec;

import androidx.annotation.NonNull;

/**
 * Generic logic for capturing or choosing audio/video/image media
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public abstract class MediaWidget extends QuestionWidget {
    private static final String TAG = MediaWidget.class.getSimpleName();

    protected static final String CUSTOM_TAG = "custom";
    public static final String AES_EXTENSION = ".aes";

    protected Button mCaptureButton;
    protected Button mPlayButton;
    protected Button mChooseButton;
    protected String mBinaryName;
    private String mTempBinaryPath;


    protected final PendingCalloutInterface pendingCalloutInterface;
    protected final String mInstanceFolder;

    private int oversizedMediaSize;

    protected String customFileTag;
    private String destMediaPath;

    public MediaWidget(Context context, FormEntryPrompt prompt,
                       PendingCalloutInterface pendingCalloutInterface) {
        super(context, prompt);

        this.pendingCalloutInterface = pendingCalloutInterface;

        mInstanceFolder = FormEntryInstanceState.getInstanceFolder();

        setOrientation(LinearLayout.VERTICAL);
        initializeButtons();
        setupLayout();
    }


    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == View.VISIBLE) {
            loadAnswerFromDataModel();
        }
    }

    private void loadAnswerFromDataModel() {
        mBinaryName = mPrompt.getAnswerText();
        if (mBinaryName != null) {
            reloadFile();
        } else {
            checkForOversizedMedia(mPrompt.getAnswerValue());
            togglePlayButton(false);
        }
    }

    private void reloadFile() {
        File f = new File(mInstanceFolder + mBinaryName);
        if (f.exists()) {
            checkFileSize(f);
        } else if (mTempBinaryPath == null) {
            File encryptedFile = new File(mInstanceFolder + mBinaryName + AES_EXTENSION);
            checkFileSize(encryptedFile);
            mTempBinaryPath = decryptMedia(encryptedFile, getSecretKey());
        } else {
            checkFileSize(new File(mTempBinaryPath));
        }

        togglePlayButton(true);
    }

    protected String getSourceFilePathToDisplay() {
        File f = new File(mInstanceFolder + mBinaryName);
        if (f.exists()) {
            return f.getAbsolutePath();
        } else {
            // file should have been decrypted at the temp path
            return mTempBinaryPath;
        }
    }

    // decrypt the given file to a temp path
    public static String decryptMedia(File f, SecretKeySpec secretKey) {
        if (!f.getName().endsWith(AES_EXTENSION)) {
            return null;
        }

        String tempMediaPath = createTempMediaPath(FileUtil.getExtension(removeAESExtension(f.getName())));
        try {
            FileOutputStream fos = new FileOutputStream(tempMediaPath);
            InputStream is = EncryptionIO.getFileInputStream(f.getPath(), secretKey);
            StreamsUtil.writeFromInputToOutputNew(is, fos);
        } catch (IOException e) {
            throw new RuntimeException("Failed to decrypt media at path " + f.getAbsolutePath()
                    + " due to " + e.getMessage(), e);
        }
        return tempMediaPath;
    }

    private SecretKeySpec getSecretKey() {
        return ((FormEntryActivity)getContext()).getSymetricKey();
    }

    protected void togglePlayButton(boolean enabled) {
        mPlayButton.setEnabled(enabled);
    }

    protected abstract void initializeButtons();

    protected void setupLayout() {
        addView(mCaptureButton);
        addView(mChooseButton);
        addView(mPlayButton);
    }

    @Override
    public IAnswerData getAnswer() {
        if (oversizedMediaSize > 0) {
            // media was too big to upload, set answer as invalid data to
            // allow showing the user a proper warning message.
            return new InvalidData("", new IntegerData(oversizedMediaSize));
        } else if (mBinaryName != null) {
            return new StringData(removeAESExtension(mBinaryName));
        }
        return null;
    }

    /**
     * @return whether the media file passes the size check
     */
    private boolean ifMediaSizeChecks(String binaryPath) {
        File source = new File(binaryPath);
        boolean isTooLargeToUpload = checkFileSize(source);
        if (isTooLargeToUpload) {
            oversizedMediaSize = (int)source.length() / (1024 * 1024);
            return false;
        } else {
            oversizedMediaSize = -1;
            return true;
        }
    }

    /**
     * @return whether the media file has a valid extension
     */
    private boolean ifMediaExtensionChecks(String binaryPath) {
        String extension = FileUtil.getExtension(binaryPath);
        if (!FormUploadUtil.isSupportedMultimediaFile(binaryPath)) {
            Toast.makeText(getContext(),
                    Localization.get("form.attachment.invalid"),
                    Toast.LENGTH_LONG).show();
            Log.e(TAG, String.format(
                    "Could not save file with URI %s because of bad extension %s.",
                    binaryPath,
                    extension
            ));
            return false;
        }
        return true;
    }

    @Override
    public void clearAnswer() {
        deleteMedia();
        togglePlayButton(false);
    }

    private void deleteMedia() {
        deleteMediaFiles(mInstanceFolder, mBinaryName);
        mBinaryName = null;
        mTempBinaryPath = null;
    }

    // get the file path and delete the file along with the corresponding encrypted file
    public static void deleteMediaFiles(String instanceFolder, String binaryName) {
        String filePath = instanceFolder + "/" + binaryName;
        if (!FileUtil.deleteFileOrDir(filePath)) {
            Logger.log(LogTypes.TYPE_FORM_ENTRY, "Failed to delete media at path " + filePath);
        }
        String encryptedFilePath = filePath + MediaWidget.AES_EXTENSION;
        if (!FileUtil.deleteFileOrDir(encryptedFilePath)) {
            Logger.log(LogTypes.TYPE_FORM_ENTRY, "Failed to delete media at path " + encryptedFilePath);
        }
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

    @Override
    public void setFocus(Context context) {
        // Hide the soft keyboard if it's showing.
        InputMethodManager inputManager =
                (InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow(this.getWindowToken(), 0);
    }

    @Override
    public void setBinaryData(Object binaryURI) {
        // delete any existing media
        if (mBinaryName != null) {
            deleteMedia();
        }

        String binaryPath;
        try {
            binaryPath = createFilePath(binaryURI);
        } catch (FileExtensionNotFoundException e) {
            showToast("form.attachment.invalid.extension");
            Logger.exception("Error while saving media ", e);
            return;
        } catch (IOException e) {
            e.printStackTrace();
            showToast("form.attachment.copy.fail");
            Logger.exception("Error while saving media ", e);
            return;
        }

        if (!ifMediaSizeChecks(binaryPath) && !ifMediaExtensionChecks(binaryPath)) {
            return;
        }

        encryptRecordedFileToDestination(binaryPath);
        File newMedia = new File(destMediaPath);

        if (newMedia.exists()) {
            showToast("form.attachment.success");
        }

        mTempBinaryPath = binaryPath;
        mBinaryName = removeAESExtension(newMedia.getName());
    }

    // removes ".aes" from file name if exists
    public static String removeAESExtension(String fileName) {
        if (fileName.endsWith(AES_EXTENSION)) {
            return fileName.replace(AES_EXTENSION, "");
        }
        return fileName;
    }

    private void encryptRecordedFileToDestination(String binaryPath) {
        String extension = FileUtil.getExtension(binaryPath);
        destMediaPath = mInstanceFolder + System.currentTimeMillis() +
                customFileTag + "." + extension;
        Logger.log(LogTypes.TYPE_MEDIA_EVENT, "Setting recording destination folder: " + destMediaPath
                + "|" + HiddenPreferences.isMediaCaptureEncryptionEnabled());
        try {
            if (HiddenPreferences.isMediaCaptureEncryptionEnabled()) {
                destMediaPath = destMediaPath + AES_EXTENSION;
                EncryptionIO.encryptFile(binaryPath, destMediaPath, getSecretKey());
                Logger.log(LogTypes.TYPE_MEDIA_EVENT, "Media successfully encrypted and saved: " + destMediaPath);
            } else {
                FileUtil.copyFile(binaryPath, destMediaPath);
                Logger.log(LogTypes.TYPE_MEDIA_EVENT, "Media successfully saved: " + destMediaPath);
            }
        } catch (IOException e) {
            showToast("form.attachment.copy.fail");
            Logger.exception(LogTypes.TYPE_MAINTENANCE, e);
        }
    }

    /**
     * If file is chosen by user, the file selection intent will return an URI
     * If file is auto-selected after recording_fragment, then the recordingfragment will provide a string file path
     * Set value of customFileTag if the file is a recent recording from the RecordingFragment
     */
    private String createFilePath(Object binaryuri) throws IOException {
        String path;
        if (binaryuri instanceof Uri) {
            // Make a copy to a temporary location
            InputStream inputStream = getContext().getContentResolver().openInputStream((Uri)binaryuri);
            String fileName = FileUtil.getFileName(getContext(), (Uri)binaryuri);
            path = createTempMediaPath(FileUtil.getExtension(fileName));
            FileUtil.copyFile(inputStream, new File(path));
            customFileTag = "";
        } else {
            path = (String)binaryuri;
            customFileTag = CUSTOM_TAG;
        }
        return path;
    }

    private static String createTempMediaPath(String fileExtension) {
        return CommCareApplication.instance().getAndroidFsTemp() +
                System.currentTimeMillis() + "." + fileExtension;
    }

    // launches an ACTION_VIEW Intent for the given file
    public static void playMedia(Context context, String mediaType, String filePath) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        File mediaFile = new File(filePath);
        Uri mediaUri = FileUtil.getUriForExternalFile(context, mediaFile);
        i.setDataAndType(mediaUri, mediaType);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            context.startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context,
                    StringUtils.getStringSpannableRobust(context,
                            R.string.activity_not_found,
                            "play " + mediaType.substring(0, mediaType.indexOf("/"))),
                    Toast.LENGTH_SHORT).show();
        }
    }
}
