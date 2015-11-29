package org.commcare.dalvik.activities;

import android.content.Context;
import android.content.Intent;
import android.text.Spannable;
import android.view.View;

import org.commcare.android.adapters.HomeCardDisplayData;
import org.commcare.android.adapters.SquareButtonViewHolder;
import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.utils.SyncDetailCalculations;
import org.javarosa.core.services.locale.Localization;

import java.util.Vector;

/**
 * Build objects that contain all info needed to draw home screen buttons
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class HomeButtons {
    private final static String[] buttonNames =
            new String[]{"start", "saved", "incomplete", "sync", "report", "logout"};

    public static HomeCardDisplayData[] buildButtonData(CommCareHomeActivity activity,
                                                        Vector<String> buttonsToHide,
                                                        boolean isDemoUser) {
        String syncKey, homeMessageKey, logoutMessageKey;
        if (!isDemoUser) {
            homeMessageKey = "home.start";
            syncKey = "home.sync";
            logoutMessageKey = "home.logout";
        } else {
            syncKey = "home.sync.demo";
            homeMessageKey = "home.start.demo";
            logoutMessageKey = "home.logout.demo";
        }

        HomeCardDisplayData[] allButtons = new HomeCardDisplayData[]{
                HomeCardDisplayData.homeCardDataWithStaticText(Localization.get(homeMessageKey),
                        R.color.white,
                        R.drawable.home_start,
                        R.color.cc_attention_positive_color,
                        getStartButtonListener(activity)),
                HomeCardDisplayData.homeCardDataWithStaticText(Localization.get("home.forms.saved"),
                        R.color.white,
                        R.drawable.home_saved,
                        R.color.cc_light_cool_accent_color,
                        getViewOldFormsListener(activity)),
                HomeCardDisplayData.homeCardDataWithDynamicText(Localization.get("home.forms.incomplete"), R.color.white,
                        R.drawable.home_incomplete,
                        R.color.solid_dark_orange,
                        getIncompleteButtonListener(activity),
                        getIncompleteButtonNotificationText(activity)),
                HomeCardDisplayData.homeCardDataWithNotification(Localization.get(syncKey), R.color.white,
                        R.color.white,
                        R.drawable.home_sync,
                        R.color.cc_brand_color,
                        R.color.cc_brand_text,
                        getSyncButtonListener(activity),
                        getSyncButtonNotificationText(activity)),
                HomeCardDisplayData.homeCardDataWithStaticText(Localization.get("home.report"), R.color.white,
                        R.drawable.home_report, R.color.cc_attention_negative_color,
                        getReportButtonListener(activity)),
                HomeCardDisplayData.homeCardDataWithNotification(Localization.get(logoutMessageKey), R.color.white,
                        R.color.white,
                        R.drawable.home_logout, R.color.cc_neutral_color, R.color.cc_neutral_text,
                        getLogoutButtonListener(activity),
                        getLogoutButtonNotificationText(activity)),
        };

        return getVisibleButtons(allButtons, buttonsToHide);
    }

    private static HomeCardDisplayData[] getVisibleButtons(HomeCardDisplayData[] allButtons,
                                                           Vector<String> buttonsToHide) {
        int visibleButtonCount = buttonNames.length - buttonsToHide.size();
        HomeCardDisplayData[] buttons = new HomeCardDisplayData[visibleButtonCount];
        int visibleIndex = 0;
        for (int i = 0; i < buttonNames.length; i++) {
            if (!buttonsToHide.contains(buttonNames[i])) {
                buttons[visibleIndex] = allButtons[i];
                visibleIndex++;
            }
        }
        return buttons;
    }

    private static View.OnClickListener getViewOldFormsListener(final CommCareHomeActivity activity) {
        return new View.OnClickListener() {
            public void onClick(View v) {
                activity.goToFormArchive(false);
            }
        };
    }

    private static View.OnClickListener getSyncButtonListener(final CommCareHomeActivity activity) {
        return new View.OnClickListener() {
            public void onClick(View v) {
                activity.syncButtonPressed();
            }
        };
    }

    private static TextSetter getSyncButtonNotificationText(final CommCareHomeActivity activity) {
        return new TextSetter() {
            @Override
            public void update(HomeCardDisplayData cardDisplayData,
                               SquareButtonViewHolder squareButtonViewHolder,
                               Context context,
                               String notificationText) {
                if (notificationText != null) {
                    squareButtonViewHolder.subTextView.setText(notificationText);
                    squareButtonViewHolder.subTextView.setTextColor(activity.getResources().getColor(cardDisplayData.subTextColor));
                } else {
                    SyncDetailCalculations.updateSubText(activity, squareButtonViewHolder, cardDisplayData);
                }
                squareButtonViewHolder.subTextView.setBackgroundColor(activity.getResources().getColor(cardDisplayData.subTextBgColor));
                squareButtonViewHolder.textView.setTextColor(context.getResources().getColor(cardDisplayData.textColor));
                squareButtonViewHolder.textView.setText(cardDisplayData.text);
            }
        };
    }

    private static View.OnClickListener getStartButtonListener(final CommCareHomeActivity activity) {
        return new View.OnClickListener() {
            public void onClick(View v) {
                activity.enterRootModule();
            }
        };
    }

    private static View.OnClickListener getIncompleteButtonListener(final CommCareHomeActivity activity) {
        return new View.OnClickListener() {
            public void onClick(View v) {
                activity.goToFormArchive(true);
            }
        };
    }

    private static TextSetter getIncompleteButtonNotificationText(final CommCareHomeActivity activity) {
        return new TextSetter() {
            @Override
            public void update(HomeCardDisplayData cardDisplayData,
                               SquareButtonViewHolder squareButtonViewHolder,
                               Context context,
                               String notificationText) {
                SqlStorage<FormRecord> formsStorage = CommCareApplication._().getUserStorage(FormRecord.class);
                int numIncompleteForms = formsStorage.getIDsForValue(FormRecord.META_STATUS, FormRecord.STATUS_INCOMPLETE).size();
                if (numIncompleteForms > 0) {
                    Spannable incompleteIndicator = (activity.localize("home.forms.incomplete.indicator",
                            new String[]{String.valueOf(numIncompleteForms), Localization.get("home.forms.incomplete")}));
                    squareButtonViewHolder.textView.setText(incompleteIndicator);
                } else {
                    squareButtonViewHolder.textView.setText(activity.localize("home.forms.incomplete"));
                }
                squareButtonViewHolder.textView.setTextColor(context.getResources().getColor(cardDisplayData.textColor));
                squareButtonViewHolder.subTextView.setVisibility(View.GONE);
            }
        };
    }

    private static View.OnClickListener getLogoutButtonListener(final CommCareHomeActivity activity) {
        return new View.OnClickListener() {
            public void onClick(View v) {
                CommCareApplication._().closeUserSession();
                activity.userTriggeredLogout();
            }
        };
    }

    private static TextSetter getLogoutButtonNotificationText(final CommCareHomeActivity activity) {
        return new TextSetter() {
            @Override
            public void update(HomeCardDisplayData cardDisplayData,
                               SquareButtonViewHolder squareButtonViewHolder,
                               Context context,
                               String notificationText) {
                squareButtonViewHolder.textView.setText(cardDisplayData.text);
                squareButtonViewHolder.textView.setTextColor(context.getResources().getColor(cardDisplayData.textColor));
                squareButtonViewHolder.subTextView.setText(activity.getActivityTitle());
                squareButtonViewHolder.subTextView.setTextColor(context.getResources().getColor(cardDisplayData.subTextColor));
                squareButtonViewHolder.subTextView.setBackgroundColor(activity.getResources().getColor(cardDisplayData.subTextBgColor));
            }
        };
    }

    private static View.OnClickListener getReportButtonListener(final CommCareHomeActivity activity) {
        return new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(activity, ReportProblemActivity.class);
                activity.startActivityForResult(i, CommCareHomeActivity.REPORT_PROBLEM_ACTIVITY);
            }
        };
    }

    public interface TextSetter {
        /**
         * Set view holder's text and subtext either from provided display
         * data, notification text argument, or auxiliary computations
         *
         * @param notificationText Optional text which will always be used when provided
         */
        void update(HomeCardDisplayData cardDisplayData,
                    SquareButtonViewHolder squareButtonViewHolder,
                    Context context,
                    String notificationText);
    }
}
