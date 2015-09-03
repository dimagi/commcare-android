package org.commcare.android.adapters;

import android.annotation.SuppressLint;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.View;

/**
 * Created by jschweers on 9/2/2015.
 */
@SuppressLint("ParcelCreator")
public class ListItemViewStriper implements ListItemViewModifier, Parcelable {
    private int mOddColor;
    private int mEvenColor;

    public ListItemViewStriper(int oddColor, int evenColor) {
        super();
        mOddColor = oddColor;
        mEvenColor = evenColor;
    }

    @Override
    public void modify(View view, int position) {
        if (position % 2 == 0) {
            view.setBackgroundColor(mEvenColor);
        } else {
            view.setBackgroundColor(mOddColor);
        }
    }

    @Override
    public int describeContents() {
        return mOddColor ^ mEvenColor;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        // do nothing
    }
}
