package org.odk.collect.android.adapters;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.commcare.android.util.MediaUtil;
import org.commcare.android.util.StringUtils;
import org.commcare.dalvik.R;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;

import java.io.File;

public class ImageAdapter extends BaseAdapter {
    private final String[] choices;
    private final ImageView[] imageViews;
    private final Context context;

    public ImageAdapter(Context context, String[] choices, ImageView[] imageViews) {
        this.choices = choices;
        this.context = context;
        this.imageViews = imageViews;
    }

    @Override
    public int getCount() {
        return choices.length;
    }

    @Override
    public Object getItem(int position) {
        return null;
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    // create a new ImageView for each item referenced by the Adapter
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        String imageURI = choices[position];

        // It is possible that an imageview already exists and has been updated
        // by updateViewAfterAnswer
        ImageView mImageView = null;
        if (imageViews[position] != null) {
            mImageView = imageViews[position];
        }
        TextView mMissingImage = null;

        String errorMsg = null;
        if (imageURI != null) {
            try {
                String imageFilename =
                        ReferenceManager._().DeriveReference(imageURI).getLocalURI();
                final File imageFile = new File(imageFilename);
                if (imageFile.exists()) {
                    Display display =
                            ((WindowManager)context.getSystemService(Context.WINDOW_SERVICE))
                                    .getDefaultDisplay();
                    int screenWidth = display.getWidth();
                    int screenHeight = display.getHeight();
                    Bitmap b = MediaUtil.getBitmapScaledToContainer(imageFile, screenHeight, screenWidth);
                    if (b != null) {

                        if (mImageView == null) {
                            mImageView = new ImageView(context);
                            mImageView.setBackgroundColor(Color.WHITE);
                        }

                        mImageView.setPadding(3, 3, 3, 3);
                        mImageView.setImageBitmap(b);

                        imageViews[position] = mImageView;
                    } else {
                        // Loading the image failed, so it's likely a bad file.
                        errorMsg = StringUtils.getStringRobust(context, R.string.file_invalid, imageFile.toString());
                    }
                } else {
                    // We should have an image, but the file doesn't exist.
                    errorMsg = StringUtils.getStringRobust(context, R.string.file_missing, imageFile.toString());
                }

                if (errorMsg != null) {
                    // errorMsg is only set when an error has occurred
                    Log.e("GridWidget", errorMsg);
                    mMissingImage = new TextView(context);
                    mMissingImage.setText(errorMsg);
                    mMissingImage.setPadding(10, 10, 10, 10);
                }
            } catch (InvalidReferenceException e) {
                Log.e("GridWidget", "image invalid reference exception");
                e.printStackTrace();
            }
        }

        if (mImageView != null) {
            mImageView.setScaleType(ImageView.ScaleType.FIT_XY);
            return mImageView;
        } else {
            return mMissingImage;
        }
    }
}
