package org.commcare.activities.components;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v4.widget.TextViewCompat;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.commcare.activities.CommCareActivity;
import org.commcare.activities.FormEntryActivity;
import org.commcare.dalvik.R;
import org.commcare.models.ODKStorage;
import org.commcare.preferences.FormEntryPreferences;

/**
 * @author ctsims
 */
public class FormLayoutHelpers {
    public static boolean determineNumberOfValidGroupLines(FormEntryActivity activity,
                                                           Rect newRootViewDimensions,
                                                           boolean hasGroupLabel,
                                                           boolean groupForcedInvisible) {
        FrameLayout header = (FrameLayout)activity.findViewById(R.id.form_entry_header);
        TextView groupLabel = ((TextView)header.findViewById(R.id.form_entry_group_label));

        int numberOfGroupLinesAllowed =
                getNumberOfGroupLinesAllowed(groupLabel, newRootViewDimensions, activity);

        if (TextViewCompat.getMaxLines(groupLabel) == numberOfGroupLinesAllowed) {
            return groupForcedInvisible;
        }

        groupLabel.setMaxLines(numberOfGroupLinesAllowed);
        boolean result = numberOfGroupLinesAllowed == 0;
        updateGroupViewVisibility(header, groupLabel, result, hasGroupLabel);
        return result;
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
                                                 boolean groupForcedInvisible) {
        FrameLayout header = (FrameLayout)activity.findViewById(R.id.form_entry_header);
        TextView groupLabel = ((TextView)header.findViewById(R.id.form_entry_group_label));
        updateGroupViewVisibility(header, groupLabel, hasGroupLabel, groupForcedInvisible);
    }

    private static void updateGroupViewVisibility(FrameLayout header,
                                                  TextView groupLabel,
                                                  boolean hasGroupLabel,
                                                  boolean groupForcedInvisible) {
        if (hasGroupLabel && !groupForcedInvisible) {
            header.setVisibility(View.VISIBLE);
            groupLabel.setVisibility(View.VISIBLE);
        } else {
            header.setVisibility(View.GONE);
            groupLabel.setVisibility(View.GONE);
        }
    }

    private static int getFontSizeInPx(Activity activity) {
        SharedPreferences settings =
                PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext());
        String question_font =
                settings.getString(FormEntryPreferences.KEY_FONT_SIZE, ODKStorage.DEFAULT_FONTSIZE);

        int sizeInPx = Integer.valueOf(question_font);

        return (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, sizeInPx,
                activity.getResources().getDisplayMetrics());
    }

    private static int getActionBarSize(CommCareActivity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB &&
                activity.getActionBar() != null) {
            int actionBarHeight = activity.getActionBar().getHeight();

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
