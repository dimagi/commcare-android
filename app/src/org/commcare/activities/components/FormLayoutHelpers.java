package org.commcare.activities.components;

import android.graphics.Rect;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.commcare.activities.CommCareActivity;
import org.commcare.activities.FormEntryActivity;
import org.commcare.dalvik.R;
import org.commcare.preferences.FormEntryPreferences;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.TextViewCompat;

/**
 * @author ctsims
 */
public class FormLayoutHelpers {
    public static boolean determineNumberOfValidGroupLines(FormEntryActivity activity,
                                                           Rect newRootViewDimensions,
                                                           boolean hasGroupLabel,
                                                           boolean shouldHideGroupLabel) {
        FrameLayout header = activity.findViewById(R.id.form_entry_header);
        TextView groupLabel = header.findViewById(R.id.form_entry_group_label);

        int numberOfGroupLinesAllowed =
                getNumberOfGroupLinesAllowed(groupLabel, newRootViewDimensions, activity);

        if (TextViewCompat.getMaxLines(groupLabel) != numberOfGroupLinesAllowed) {
            shouldHideGroupLabel = numberOfGroupLinesAllowed == 0;
            groupLabel.setMaxLines(numberOfGroupLinesAllowed);
            updateGroupViewVisibility(header, groupLabel, hasGroupLabel, shouldHideGroupLabel);
        }
        return shouldHideGroupLabel;
    }

    private static int getNumberOfGroupLinesAllowed(TextView groupLabel,
                                                    Rect newRootViewDimensions,
                                                    FormEntryActivity activity) {
        int contentSize = newRootViewDimensions.height();
        View navBar = activity.findViewById(R.id.nav_pane);
        int headerSize = navBar.getHeight();
        if (headerSize == 0) {
            headerSize = activity.getResources().getDimensionPixelSize(R.dimen.new_progressbar_minheight);
        }

        int availableWindow = contentSize - headerSize - getActionBarSize(activity);

        // Request a consistent amount of the screen before groups can cut down
        int spaceRequested = getFontSizeInPx(activity) * 6;
        int spaceAvailable = availableWindow - spaceRequested;

        int defaultHeaderSpace =
                activity.getResources().getDimensionPixelSize(R.dimen.content_min_margin) * 2;

        float textSize = groupLabel.getTextSize();
        return Math.max(0, (int)((spaceAvailable - defaultHeaderSpace) / textSize));
    }

    public static void updateGroupViewVisibility(FormEntryActivity activity,
                                                 boolean hasGroupLabel,
                                                 boolean shouldHideGroupLabel) {
        FrameLayout header = activity.findViewById(R.id.form_entry_header);
        TextView groupLabel = header.findViewById(R.id.form_entry_group_label);
        updateGroupViewVisibility(header, groupLabel, hasGroupLabel, shouldHideGroupLabel);
    }

    private static void updateGroupViewVisibility(FrameLayout header,
                                                  TextView groupLabel,
                                                  boolean hasGroupLabel,
                                                  boolean shouldHideGroupLabel) {
        if (hasGroupLabel && !shouldHideGroupLabel) {
            header.setVisibility(View.VISIBLE);
            groupLabel.setVisibility(View.VISIBLE);
        } else {
            header.setVisibility(View.GONE);
            groupLabel.setVisibility(View.GONE);
        }
    }

    private static int getFontSizeInPx(AppCompatActivity activity) {
        return (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, FormEntryPreferences.getQuestionFontSize(),
                activity.getResources().getDisplayMetrics());
    }

    private static int getActionBarSize(CommCareActivity activity) {
        if (activity.getSupportActionBar() != null) {
            int actionBarHeight = activity.getSupportActionBar().getHeight();

            if (actionBarHeight != 0) {
                return actionBarHeight;
            }
            final TypedValue tv = new TypedValue();
            if (activity.getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
                actionBarHeight = TypedValue.complexToDimensionPixelSize(tv.data, activity.getResources().getDisplayMetrics());
            }
            return actionBarHeight;
        }
        return 0;
    }
}
