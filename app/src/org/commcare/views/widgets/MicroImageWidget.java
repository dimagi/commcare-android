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
import org.javarosa.core.model.data.Base64ImageData;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.form.api.FormEntryPrompt;

import java.io.ByteArrayOutputStream;
import java.io.File;

import androidx.appcompat.app.AppCompatActivity;

public class MicroImageWidget extends ImageWidget{
    private String mBinary;

    public MicroImageWidget(Context context, FormEntryPrompt prompt, PendingCalloutInterface pic) {
        super(context, prompt, pic);

        mChooseButton.setVisibility(GONE);
        if (mPrompt.getAnswerValue() instanceof Base64ImageData) {
            mBinary = ((Pair<String, String>)mPrompt.getAnswerValue().getValue()).second;
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
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        originalImage.compress(Bitmap.CompressFormat.WEBP, 100, baos);

        try {
            mBinary = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
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
}
