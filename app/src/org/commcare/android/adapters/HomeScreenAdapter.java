package org.commcare.android.adapters;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.StateSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.commcare.android.view.SquareButtonWithNotification;
import org.commcare.android.view.SquareImageView;
import org.commcare.android.view.ViewUtil;
import org.commcare.dalvik.R;
import org.commcare.dalvik.activities.CommCareHomeActivity;
import org.commcare.dalvik.activities.HomeButtons;

import java.util.List;
import java.util.Vector;

/**
 * Sets up home screen buttons and gives accessors for setting their visibility and listeners
 * Created by dancluna on 3/19/15.
 */
public class HomeScreenAdapter
        extends RecyclerView.Adapter<HomeScreenAdapter.SquareButtonViewHolder> {

    private final Context context;
    private final HomeButtons.HomeCardDisplayData[] buttonData;

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
        HomeButtons.HomeCardDisplayData cardDisplayData = buttonData[i];

        cardDisplayData.textSetter.update(cardDisplayData, squareButtonViewHolder, context);

        squareButtonViewHolder.imageView.setImageDrawable(ContextCompat.getDrawable(context, cardDisplayData.imageResource));
        squareButtonViewHolder.imageView.setOnClickListener(cardDisplayData.listener);

        StateListDrawable bgDrawable = getSLD(context, cardDisplayData.bgColor);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            squareButtonViewHolder.imageView.setBackground(bgDrawable);
        } else {
            squareButtonViewHolder.imageView.setBackgroundDrawable(bgDrawable);
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

    public static class SquareButtonViewHolder extends RecyclerView.ViewHolder {
        public final SquareImageView imageView;
        public final TextView textView;
        public final TextView subTextView;

        public SquareButtonViewHolder(View view) {
            super(view);

            imageView = (SquareImageView)view.findViewById(R.id.card_image);
            textView = (TextView)view.findViewById(R.id.card_text);
            subTextView = (TextView)view.findViewById(R.id.card_subtext);
        }
    }
}
