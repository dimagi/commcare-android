package org.commcare.adapters;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.commcare.utils.MediaUtil;
import org.commcare.utils.StringUtils;

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

        if (imageURI != null) {
            Bitmap b = MediaUtil.inflateDisplayImage(context, imageURI);
            if (b != null) {
                if (mImageView == null) {
                    mImageView = new ImageView(context);
                    mImageView.setBackgroundColor(Color.WHITE);
                }
                mImageView.setPadding(3, 3, 3, 3);
                mImageView.setImageBitmap(b);
                imageViews[position] = mImageView;
            } else {
                String errorMsg = StringUtils.getStringRobust(context, R.string.file_invalid, imageURI);
                Log.e("GridWidget", errorMsg);
                mMissingImage = new TextView(context);
                mMissingImage.setText(errorMsg);
                mMissingImage.setPadding(10, 10, 10, 10);
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
