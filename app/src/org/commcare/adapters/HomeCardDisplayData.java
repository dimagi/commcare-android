package org.commcare.adapters;

import android.content.Context;
import android.view.View;

import org.commcare.activities.HomeButtons;
import org.commcare.dalvik.R;

/**
 * Holds data for displaying home screen buttons
 *
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

    public static HomeCardDisplayData homeCardDataWithStaticText(String text,
                                                                 int textColor,
                                                                 int imageResource,
                                                                 int bgColor,
                                                                 View.OnClickListener listener) {
        return new HomeCardDisplayData(text, textColor, R.color.white,
                imageResource, bgColor, R.color.cc_brand_color,
                listener, new DefaultTextSetter());
    }

    /**
     * @param textSetter logic for setting button text and subtext
     */
    public static HomeCardDisplayData homeCardDataWithDynamicText(String text,
                                                                  int textColor,
                                                                  int imageResource,
                                                                  int bgColor,
                                                                  View.OnClickListener listener,
                                                                  HomeButtons.TextSetter textSetter) {
        return new HomeCardDisplayData(text, textColor, R.color.white,
                imageResource, bgColor, R.color.cc_brand_color,
                listener, textSetter);
    }

    /**
     * @param textSetter logic for setting button text and subtext
     */
    public static HomeCardDisplayData homeCardDataWithNotification(String text,
                                                                   int textColor,
                                                                   int subTextColor,
                                                                   int imageResource,
                                                                   int bgColor,
                                                                   int subTextBgColor,
                                                                   View.OnClickListener listener,
                                                                   HomeButtons.TextSetter textSetter) {
        return new HomeCardDisplayData(text, textColor, subTextColor,
                imageResource, bgColor, subTextBgColor, listener, textSetter);
    }

    private HomeCardDisplayData(String text, int textColor,
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

    /**
     * Default text setter implementation that shows button text and hides button subtext
     */
    private static class DefaultTextSetter implements HomeButtons.TextSetter {
        @Override
        public void update(HomeCardDisplayData cardDisplayData,
                           SquareButtonViewHolder squareButtonViewHolder,
                           Context context,
                           String notificationText) {
            squareButtonViewHolder.textView.setText(cardDisplayData.text);

            int textColor = context.getResources().getColor(cardDisplayData.textColor);
            squareButtonViewHolder.textView.setTextColor(textColor);

            squareButtonViewHolder.subTextView.setVisibility(View.GONE);
        }
    }
}

