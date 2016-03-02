package org.commcare.adapters;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.util.StateSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.commcare.dalvik.R;
import org.commcare.views.ViewUtil;

import java.util.List;


/**
 * Inflation and binding of square cards used on home screen and other places.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
abstract class SquareButtonAdapter
        extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    final Context context;

    private static final int TYPE_BUTTON = 0;

    SquareButtonAdapter(Context context) {
        this.context = context;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        if (viewType == TYPE_BUTTON) {
            View layoutView = inflater.inflate(R.layout.square_card, parent, false);
            return new SquareButtonViewHolder(layoutView);
        } else {
            throw new RuntimeException("No " + viewType + " view type exists");
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int i) {
        if (holder instanceof SquareButtonViewHolder) {
            bindCard((SquareButtonViewHolder)holder, i, null);
        } else {
            throw new RuntimeException("Unable to bind ViewHolder of type: " + holder.getClass());
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder,
                                 int i, List<Object> payload) {
        if (holder instanceof SquareButtonViewHolder) {
            bindCard((SquareButtonViewHolder)holder, i, payload);
        } else {
            throw new RuntimeException("Unable to bind ViewHolder of type: " + holder.getClass());
        }
    }

    private void bindCard(SquareButtonViewHolder squareButtonViewHolder,
                          int i, List<Object> payload) {
        HomeCardDisplayData cardDisplayData = getItem(i);
        String notificationText = null;

        if (payload != null) {
            notificationText = getFirstPayloadString(payload);
        }

        cardDisplayData.textSetter.update(cardDisplayData,
                squareButtonViewHolder, context, notificationText);
        setupViewHolder(context, cardDisplayData, squareButtonViewHolder);
    }

    /**
     * Get nth data element in adapter.
     */
    protected abstract HomeCardDisplayData getItem(int position);

    /**
     * Get 1st string in payload list, which is constructed from payloads
     * provided on calls to notify item/data set changed.
     */
    private static String getFirstPayloadString(List<Object> payloadList) {
        String lastPayloadString = null;
        for (Object entry : payloadList) {
            if (entry instanceof String) {
                lastPayloadString = (String)entry;
            }
        }
        return lastPayloadString;
    }

    private static void setupViewHolder(Context context,
                                        HomeCardDisplayData cardDisplayData,
                                        SquareButtonViewHolder squareButtonViewHolder) {
        final Drawable buttonDrawable =
                ContextCompat.getDrawable(context, cardDisplayData.imageResource);
        squareButtonViewHolder.imageView.setImageDrawable(buttonDrawable);
        squareButtonViewHolder.cardView.setOnClickListener(cardDisplayData.listener);

        StateListDrawable bgDrawable = bgDrawStates(context, cardDisplayData.bgColor);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            squareButtonViewHolder.cardView.setBackground(bgDrawable);
        } else {
            squareButtonViewHolder.cardView.setBackgroundDrawable(bgDrawable);
        }
    }

    /**
     * Build drawable with default state being the provided color resource,
     * pressed color state being that color with less saturation, and disabled
     * state being gray.
     */
    private static StateListDrawable bgDrawStates(Context context,
                                                  int bgColorResource) {
        ColorDrawable disabledColor =
                new ColorDrawable(context.getResources().getColor(R.color.grey));
        ColorDrawable colorDrawable =
                new ColorDrawable(context.getResources().getColor(bgColorResource));
        ColorDrawable pressedBackground = desaturateColor(colorDrawable);

        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{-android.R.attr.state_enabled}, disabledColor);
        sld.addState(new int[]{android.R.attr.state_pressed}, pressedBackground);
        sld.addState(StateSet.WILD_CARD, colorDrawable);
        return sld;
    }

    private static ColorDrawable desaturateColor(ColorDrawable colorDrawable) {
        float[] hsvOutput = new float[3];
        int color = ViewUtil.getColorDrawableColor(colorDrawable);
        Color.colorToHSV(color, hsvOutput);
        hsvOutput[2] = (float)(hsvOutput[2] / 1.5);
        return new ColorDrawable(Color.HSVToColor(hsvOutput));
    }

    @Override
    public int getItemViewType(int position) {
        return TYPE_BUTTON;
    }
}
