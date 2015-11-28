package org.commcare.android.adapters;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.util.StateSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.commcare.android.view.ViewUtil;
import org.commcare.dalvik.R;
import org.commcare.dalvik.activities.CommCareHomeActivity;
import org.commcare.dalvik.activities.HomeButtons;

import java.util.List;
import java.util.Vector;

/**
 * Shows home screen buttons
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class HomeScreenAdapter
        extends RecyclerView.Adapter<SquareButtonViewHolder> {

    private final Context context;
    private final HomeCardDisplayData[] buttonData;

    public HomeScreenAdapter(CommCareHomeActivity activity,
                             Vector<String> buttonsToHide,
                             boolean isDemoUser) {
        this.context = activity;
        buttonData = HomeButtons.buildButtonData(activity, buttonsToHide, isDemoUser);
    }

    @Override
    public SquareButtonViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View layoutView = LayoutInflater.from(parent.getContext()).inflate(R.layout.home_card, parent, false);

        return new SquareButtonViewHolder(layoutView);
    }

    @Override
    public void onBindViewHolder(SquareButtonViewHolder squareButtonViewHolder, int i) {
        HomeCardDisplayData cardDisplayData = buttonData[i];

        cardDisplayData.textSetter.update(cardDisplayData, squareButtonViewHolder, context, null);

        setupViewHolder(context, cardDisplayData, squareButtonViewHolder);
    }

    @Override
    public void onBindViewHolder(SquareButtonViewHolder squareButtonViewHolder, int i, List<Object> payload) {
        HomeCardDisplayData cardDisplayData = buttonData[i];

        String notificationText = getLastPayloadString(payload);
        cardDisplayData.textSetter.update(cardDisplayData, squareButtonViewHolder, context, notificationText);

        setupViewHolder(context, cardDisplayData, squareButtonViewHolder);
    }

    private static String getLastPayloadString(List<Object> payload) {
        String lastPayloadString = null;
        for (Object entry : payload) {
            if (entry instanceof String) {
                lastPayloadString = (String)entry;
            }
        }
        return lastPayloadString;
    }

    private static void setupViewHolder(Context context,
                                        HomeCardDisplayData cardDisplayData,
                                        SquareButtonViewHolder squareButtonViewHolder) {
        squareButtonViewHolder.imageView.setImageDrawable(ContextCompat.getDrawable(context, cardDisplayData.imageResource));
        squareButtonViewHolder.imageView.setOnClickListener(cardDisplayData.listener);

        StateListDrawable bgDrawable = getSLD(context, cardDisplayData.bgColor);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            squareButtonViewHolder.imageView.setBackground(bgDrawable);
            squareButtonViewHolder.textView.setBackground(bgDrawable);
        } else {
            squareButtonViewHolder.imageView.setBackgroundDrawable(bgDrawable);
            squareButtonViewHolder.textView.setBackgroundDrawable(bgDrawable);
        }
    }

    private static StateListDrawable getSLD(Context context, int backgroundColorRes) {
        final int backgroundColor = context.getResources().getColor(backgroundColorRes);
        ColorDrawable colorDrawable = new ColorDrawable(backgroundColor);
        ColorDrawable disabledColor = new ColorDrawable(context.getResources().getColor(R.color.grey));

        int color = ViewUtil.getColorDrawableColor(colorDrawable);

        float[] hsvOutput = new float[3];
        Color.colorToHSV(color, hsvOutput);

        hsvOutput[2] = (float)(hsvOutput[2] / 1.5);

        int selectedColor = Color.HSVToColor(hsvOutput);

        ColorDrawable pressedBackground = new ColorDrawable(selectedColor);

        StateListDrawable sld = new StateListDrawable();

        sld.addState(new int[]{-android.R.attr.state_enabled}, disabledColor);
        sld.addState(new int[]{android.R.attr.state_pressed}, pressedBackground);
        sld.addState(StateSet.WILD_CARD, colorDrawable);
        return sld;
    }

    @Override
    public int getItemCount() {
        return buttonData.length;
    }

    public int getSyncButtonPosition() {
        // NOTE PLM: assumes sync button is always the second to last button.
        return getItemCount() - 2;
    }
}
