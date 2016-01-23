package org.commcare.android.adapters;

import android.os.Parcel;
import android.os.Parcelable;
import android.view.View;

/**
 * Created by jschweers on 9/2/2015.
 */
public class ListItemViewStriper implements ListItemViewModifier, Parcelable {
    private final int mOddColor;
    private final int mEvenColor;

    public ListItemViewStriper(int oddColor, int evenColor) {
        mOddColor = oddColor;
        mEvenColor = evenColor;
    }

    private ListItemViewStriper(Parcel in) {
        mOddColor = in.readInt();
        mEvenColor = in.readInt();
    }

    public static final Creator<ListItemViewStriper> CREATOR = new Creator<ListItemViewStriper>() {
        @Override
        public ListItemViewStriper createFromParcel(Parcel in) {
            return new ListItemViewStriper(in);
        }

        @Override
        public ListItemViewStriper[] newArray(int size) {
            return new ListItemViewStriper[size];
        }
    };

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
        dest.writeInt(mOddColor);
        dest.writeInt(mEvenColor);
    }
}
