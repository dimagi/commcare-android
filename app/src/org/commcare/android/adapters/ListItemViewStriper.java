package org.commcare.android.adapters;

import android.view.View;

/**
 * Created by jschweers on 9/2/2015.
 */
public class ListItemViewStriper implements ListItemViewModifier {
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
}
