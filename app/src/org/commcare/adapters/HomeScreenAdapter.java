package org.commcare.adapters;

import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import org.commcare.activities.CommCareHomeActivity;
import org.commcare.activities.HomeButtons;
import org.commcare.android.ui.CustomBanner;
import org.commcare.dalvik.R;

import java.util.List;
import java.util.Vector;

/**
 * Shows home screen buttons and header banner
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class HomeScreenAdapter
        extends SquareButtonAdapter {

    private final HomeCardDisplayData[] buttonData;

    private static final int TYPE_HEADER = 1;
    private final int screenHeight, screenWidth;
    private final int syncButtonPosition;

    public HomeScreenAdapter(CommCareHomeActivity activity,
                             Vector<String> buttonsToHide,
                             boolean isDemoUser) {
        super(activity);

        buttonData = HomeButtons.buildButtonData(activity, buttonsToHide, isDemoUser);
        syncButtonPosition = calcSyncButtonPos();

        // get screen dimensions for drawing custom header image
        DisplayMetrics displaymetrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        screenHeight = displaymetrics.heightPixels;
        screenWidth = displaymetrics.widthPixels;
    }

    private int calcSyncButtonPos() {
        for (int i = 0; i < buttonData.length; i++) {
            if (buttonData[i].imageResource == R.drawable.home_sync) {
                // pos in button array plus initial custom header
                return i + 1;
            }
        }
        return -1;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            View header = inflater.inflate(R.layout.grid_header_top_banner, parent, false);
            return new HeaderViewHolder(header);
        } else {
            return super.onCreateViewHolder(parent, viewType);
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int i) {
        if (holder instanceof HeaderViewHolder) {
            bindHeader((HeaderViewHolder)holder);
        } else {
            super.onBindViewHolder(holder, i);
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder,
                                 int i, List<Object> payload) {
        if (holder instanceof HeaderViewHolder) {
            bindHeader((HeaderViewHolder)holder);
        } else {
            super.onBindViewHolder(holder, i, payload);
        }
    }

    @Override
    protected HomeCardDisplayData getItem(int position) {
        return buttonData[position - 1];
    }

    private void bindHeader(HeaderViewHolder headerHolder) {
        StaggeredGridLayoutManager.LayoutParams layoutParams =
                (StaggeredGridLayoutManager.LayoutParams)headerHolder.itemView.getLayoutParams();
        layoutParams.setFullSpan(true);

        boolean noCustomBanner =
                !CustomBanner.useCustomBanner(context, screenHeight,
                        screenWidth, headerHolder.headerImage);
        if (noCustomBanner) {
            headerHolder.headerImage.setImageResource(R.drawable.commcare_logo);
        }
    }

    @Override
    public int getItemCount() {
        // buttons and header
        return buttonData.length + 1;
    }

    @Override
    public int getItemViewType(int position) {
        if (isPositionHeader(position)) {
            return TYPE_HEADER;
        } else {
            return super.getItemViewType(position);
        }
    }

    private boolean isPositionHeader(int position) {
        return position == 0;
    }

    public int getSyncButtonPosition() {
        return syncButtonPosition;
    }

    private static class HeaderViewHolder extends RecyclerView.ViewHolder {
        public final ImageView headerImage;

        public HeaderViewHolder(View itemView) {
            super(itemView);

            headerImage = (ImageView)itemView.findViewById(R.id.main_top_banner);
        }
    }
}
