package org.commcare.android.adapters;

import android.content.Context;
import android.view.View;

import org.commcare.dalvik.R;
import org.commcare.dalvik.activities.HomeButtons;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class HomeCardDisplayData {
    public final int bgColor;
    public final int textColor;
    public final int imageResource;
    public final String text;
    public final int subTextColor;
    public final int subTextBgColor;
    public final View.OnClickListener listener;
    public final HomeButtons.TextSetter textSetter;

    public HomeCardDisplayData(String text, int textColor,
                               int imageResource, int bgColor,
                               View.OnClickListener listener) {
        this(text, textColor, R.color.white,
                imageResource, bgColor, R.color.cc_brand_color,
                listener, new DefaultTextSetter());
    }

    public HomeCardDisplayData(String text, int textColor,
                               int imageResource, int bgColor,
                               View.OnClickListener listener,
                               HomeButtons.TextSetter textSetter) {
        this(text, textColor, R.color.white,
                imageResource, bgColor, R.color.cc_brand_color,
                listener, textSetter);
    }

    public HomeCardDisplayData(String text, int textColor,
                               int subTextColor, int imageResource,
                               int bgColor, int subTextBgColor,
                               View.OnClickListener listener,
                               HomeButtons.TextSetter textSetter) {
        this.bgColor = bgColor;
        this.textColor = textColor;
        this.imageResource = imageResource;
        this.text = text;
        this.subTextColor = subTextColor;
        this.subTextBgColor = subTextBgColor;
        this.listener = listener;
        this.textSetter = textSetter;
    }

    private static class DefaultTextSetter implements HomeButtons.TextSetter {
        @Override
        public void update(HomeCardDisplayData cardDisplayData,
                           SquareButtonViewHolder squareButtonViewHolder,
                           Context context,
                           String notificationText) {
            squareButtonViewHolder.textView.setText(cardDisplayData.text);
            squareButtonViewHolder.textView.setTextColor(context.getResources().getColor(cardDisplayData.textColor));
            squareButtonViewHolder.subTextView.setVisibility(View.GONE);
        }
    }
}

