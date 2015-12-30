package org.commcare.android.adapters;

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

import org.commcare.android.view.ViewUtil;
import org.commcare.dalvik.R;
import org.commcare.dalvik.activities.CommCareWiFiDirectActivity;

import java.util.ArrayList;

/**
 * Created by willpride on 12/29/15.
 */
public class WiFiDirectAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    CommCareWiFiDirectActivity context;

    public WiFiDirectAdapter(CommCareWiFiDirectActivity activity) {
        context = activity;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final LayoutInflater inflater = LayoutInflater.from(context);
        View layoutView = inflater.inflate(R.layout.home_card, parent, false);
        return new SquareButtonViewHolder(layoutView);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        bindCard((SquareButtonViewHolder)holder, position);
    }

    private void bindCard(SquareButtonViewHolder holder, int position) {
        HomeCardDisplayData cardDisplayData = getItem(position);
        String notificationText = null;

        cardDisplayData.textSetter.update(cardDisplayData,
                holder, context, notificationText);
        setupViewHolder(context, cardDisplayData, holder);
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

    private static View.OnClickListener getSendButtonListener(final CommCareWiFiDirectActivity activity) {
        return new View.OnClickListener() {
            public void onClick(View v) {
                activity.prepareFileTransfer();
            }
        };
    }

    private static View.OnClickListener getDiscoverButtonListener(final CommCareWiFiDirectActivity activity) {
        return new View.OnClickListener() {
            public void onClick(View v) {
                activity.discoverPeers();
            }
        };
    }

    private static View.OnClickListener getSubmitButtonListener(final CommCareWiFiDirectActivity activity) {
        return new View.OnClickListener() {
            public void onClick(View v) {
                activity.submitFiles();
            }
        };
    }

    private static View.OnClickListener getChangeModeButtonListener(final CommCareWiFiDirectActivity activity) {
        return new View.OnClickListener() {
            public void onClick(View v) {
                activity.changeState();
            }
        };
    }

    private HomeCardDisplayData getItem(int position) {
        return getButtonDisplayData().get(position);
    }

    private ArrayList<HomeCardDisplayData> getButtonDisplayData(){
        ArrayList<HomeCardDisplayData> buttonData = new ArrayList<HomeCardDisplayData>();

        HomeCardDisplayData sendButton = HomeCardDisplayData.homeCardDataWithStaticText("Send",
                R.color.white,
                R.drawable.wifi_direct_transfer,
                R.color.cc_attention_positive_color,
                getSendButtonListener(context));
        HomeCardDisplayData discoverButton = HomeCardDisplayData.homeCardDataWithStaticText("Discover",
                R.color.white,
                R.drawable.wifi_direct_discover,
                R.color.cc_light_cool_accent_color,
                getDiscoverButtonListener(context));
        HomeCardDisplayData submitButton = HomeCardDisplayData.homeCardDataWithStaticText("Submit",
                R.color.white,
                R.drawable.wifi_direct_submit,
                R.color.solid_dark_orange,
                getSubmitButtonListener(context));
        HomeCardDisplayData changeModeButton = HomeCardDisplayData.homeCardDataWithStaticText("Change Mode",
                R.color.white,
                R.drawable.wifi_direct_change_mode,
                R.color.cc_brand_text,
                getChangeModeButtonListener(context));

        buttonData.add(changeModeButton);

        switch(context.mState){
            case send:
                buttonData.add(discoverButton);
                buttonData.add(sendButton);
                break;
            case submit:
                buttonData.add(submitButton);
                break;
        }

        return buttonData;
    }

    @Override
    public int getItemCount() {
        switch(context.mState){
            case send:
                return 3;
            case submit:
                return 2;
        }
        return 1;
    }
}
