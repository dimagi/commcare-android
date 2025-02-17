package org.commcare.views.widgets;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import org.commcare.activities.components.FormEntryConstants;
import org.commcare.fragments.MicroImageActivity;
import org.commcare.logic.PendingCalloutInterface;
import org.commcare.modern.util.Pair;
import org.commcare.utils.MediaUtil;
import org.javarosa.core.model.data.Base64ImageData;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.form.api.FormEntryPrompt;

import java.io.File;

import androidx.appcompat.app.AppCompatActivity;

public class MicroImageWidget extends ImageWidget{
    private static final int IMAGE_DIMEN_SCALED_MAX_PX = 72;
    private static final int MICRO_IMAGE_MAX_SIZE_BYTES = 2 * 1024;

    private String mBinary;

    public MicroImageWidget(Context context, FormEntryPrompt prompt, PendingCalloutInterface pic) {
        super(context, prompt, pic);

        mChooseButton.setVisibility(GONE);
        if (mPrompt.getAnswerValue() instanceof Base64ImageData) {
            mBinary = ((Base64ImageData)mPrompt.getAnswerValue()).getImageData();
        }
    }

    @Override
    protected void takePicture() {
        Intent i = new Intent(getContext(), MicroImageActivity.class);
        ((AppCompatActivity)getContext()).startActivityForResult(i, FormEntryConstants.MICRO_IMAGE_CAPTURE);
        pendingCalloutInterface.setPendingCalloutFormIndex(mPrompt.getIndex());
    }

    @Override
    public void setBinaryData(Object binaryPath) {
        if (mBinaryName != null) {
            deleteMedia();
        }

        File f = new File(binaryPath.toString());
        Bitmap originalImage = BitmapFactory.decodeFile(binaryPath.toString());

        Bitmap scaledDownBitmap = scaleImage(originalImage, IMAGE_DIMEN_SCALED_MAX_PX, IMAGE_DIMEN_SCALED_MAX_PX);
        byte[] compressedBitmapByteArray = MediaUtil.compressBitmapToTargetSize(scaledDownBitmap, MICRO_IMAGE_MAX_SIZE_BYTES);

        try {
            mBinary = Base64.encodeToString(compressedBitmapByteArray, Base64.DEFAULT);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        mBinaryName = f.getName();
    }

    @Override
    public IAnswerData getAnswer() {
        if (mBinaryName != null) {
            return new Base64ImageData(new Pair<>(mBinaryName, mBinary));
        } else {
            return null;
        }
    }

    @Override
    protected void deleteMedia() {
        super.deleteMedia();
        mBinary = null;
    }

    // TODO: Refactor
    private Bitmap scaleImage(Bitmap bitmap, int maxWidth, int maxHeight){
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // scaling factors
        float widthRatio = (float) maxWidth / width;
        float heightRatio = (float) maxHeight / height;
        float scaleFactor = Math.min(widthRatio, heightRatio);

        int newWidth = Math.round(width * scaleFactor);
        int newHeight = Math.round(height * scaleFactor);

        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);

        // Check and set the same configuration if necessary
        if (bitmap.getConfig() != resizedBitmap.getConfig()) {
            resizedBitmap = resizedBitmap.copy(bitmap.getConfig(), true);
        }

        return resizedBitmap;
    }
}
