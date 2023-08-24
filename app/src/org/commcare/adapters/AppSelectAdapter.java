package org.commcare.adapters;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.dalvik.R;
import org.commcare.utils.MultipleAppsUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Adapter for retrieving tiles representing installed apps for the user to select
 *
 * @author dviggiano
 */
public class AppSelectAdapter extends SquareButtonAdapter {

    private final HomeCardDisplayData[] buttonData;

    private final HashMap<Integer, String> messagePayload = new HashMap<>();

    public AppSelectAdapter(AppCompatActivity activity) {
        super(activity);

        List<HomeCardDisplayData> cards = new ArrayList<>();
        for (ApplicationRecord record : MultipleAppsUtil.getUsableAppRecords()) {
            HomeCardDisplayData card = HomeCardDisplayData.homeCardDataWithStaticText(record.getDisplayName(),
                    R.color.white,
                    R.drawable.commcare_logo,
                    R.color.cc_dark_cool_accent_color,
                    v -> {
                        //TODO: Handle button press
                    });

            //TODO: Store app image in card and use it
            //ImageView topBannerImageView = card.findViewById(R.id.main_top_banner);
            //if (!CustomBanner.useCustomBannerFitToActivity(activity, topBannerImageView,
            //        CustomBanner.Banner.LOGIN)) {
            //    topBannerImageView.setImageResource(R.drawable.commcare_logo);
            //}

            cards.add(card);
        }

        buttonData = new HomeCardDisplayData[cards.size()];
        cards.toArray(buttonData);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int i, List<Object> payload) {
        if (payload == null || payload.isEmpty()) {
            payload = new ArrayList<>();
            payload.add(messagePayload.remove(i));
        }

        super.onBindViewHolder(holder, i, payload);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int i) {
        ArrayList<Object> payload = new ArrayList<>();
        payload.add(messagePayload.remove(i));

        super.onBindViewHolder(holder, i, payload);
    }

    @Override
    protected HomeCardDisplayData getItem(int position) {
        return buttonData[position];
    }

    @Override
    public int getItemCount() {
        return buttonData.length;
    }

    @Override
    public int getItemViewType(int position) {
        return super.getItemViewType(position);
    }
}
