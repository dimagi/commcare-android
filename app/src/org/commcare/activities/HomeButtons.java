package org.commcare.activities;

import android.content.Context;
import android.content.Intent;
import android.text.Spannable;
import android.view.View;
import android.widget.Toast;

import org.commcare.adapters.HomeCardDisplayData;
import org.commcare.adapters.SquareButtonViewHolder;
import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.services.CommCareSessionService;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.utils.StorageUtils;
import org.commcare.utils.SyncDetailCalculations;
import org.javarosa.core.services.locale.Localization;

import java.util.Vector;


/**
 * Build objects that contain all info needed to draw home screen buttons
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class HomeButtons {

    private final static String[] buttonNames =
            new String[]{"start", "training", "saved", "incomplete","connect", "sync", "report", "logout"};

    /**
     * Note: The order in which home cards are returned by this method should be consistent with
     * the buttonNames array above
     */
    public static HomeCardDisplayData[] buildButtonData(StandardHomeActivity activity,
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
                        R.color.start_home_button,
                        getStartButtonListener(activity)),
                HomeCardDisplayData.homeCardDataWithStaticText(Localization.get("training.root.title"), R.color.white,
                        R.drawable.home_training, R.color.cc_dark_cool_accent_color,
                        getTrainingButtonListener(activity)),
                HomeCardDisplayData.homeCardDataWithStaticText(Localization.get("home.forms.saved"),
                        R.color.white,
                        R.drawable.home_saved,
                        R.color.start_save_button,
                        getViewOldFormsListener(activity)),
                HomeCardDisplayData.homeCardDataWithDynamicText(Localization.get("home.forms.incomplete"), R.color.white,
                        R.drawable.home_incomplete,
                        R.color.red_incomplete,
                        getIncompleteButtonListener(activity),
                        null,
                        getIncompleteButtonTextSetter(activity)),
                HomeCardDisplayData.homeCardDataWithStaticText(Localization.get("home.connect"), R.color.white,
                        R.drawable.quick_reference, R.color.orange_500,
                        getConnectButtonListener(activity)),
                HomeCardDisplayData.homeCardDataWithNotification(Localization.get(syncKey), R.color.white,
                        R.color.white,
                        R.drawable.home_sync,
                        R.color.start_sync_button,
                        R.color.start_sync_dark_button,
                        getSyncButtonListener(activity),
                        getSyncButtonSubTextListener(activity),
                        getSyncButtonTextSetter(activity)),
                HomeCardDisplayData.homeCardDataWithStaticText(Localization.get("home.report"), R.color.white,
                        R.drawable.home_report, R.color.cc_attention_negative_color,
                        getReportButtonListener(activity)),
                HomeCardDisplayData.homeCardDataWithNotification(Localization.get(logoutMessageKey), R.color.white,
                        R.color.white,
                        R.drawable.home_logout, R.color.start_logout_button, R.color.cc_core_text,
                        getLogoutButtonListener(activity),
                        null,
                        getLogoutButtonTextSetter(activity)),
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

    private static View.OnClickListener getViewOldFormsListener(final StandardHomeActivity activity) {
        return v -> {
            reportButtonClick(AnalyticsParamValue.SAVED_FORMS_BUTTON);
            activity.goToFormArchive(false);
        };
    }

    private static View.OnClickListener getSyncButtonListener(final StandardHomeActivity activity) {
        return v -> {
            if (CommCareSessionService.sessionAliveLock.isLocked()) {
                Toast.makeText(activity, Localization.get("background.sync.user.sync.attempt.during.sync"), Toast.LENGTH_LONG).show();
                return;
            }
            reportButtonClick(AnalyticsParamValue.SYNC_BUTTON);
            activity.syncButtonPressed();
        };
    }

    private static View.OnClickListener getSyncButtonSubTextListener(final StandardHomeActivity activity) {
        return v -> {
            reportButtonClick(AnalyticsParamValue.SYNC_SUBTEXT);
            activity.syncSubTextPressed();
        };
    }

    private static View.OnClickListener getConnectButtonListener(final StandardHomeActivity activity) {
        return v -> {
            reportButtonClick(AnalyticsParamValue.CONNECT_BUTTON);
            activity.userPressedOpportunityStatus();
        };
    }

    private static TextSetter getSyncButtonTextSetter(final StandardHomeActivity activity) {
        return (cardDisplayData, squareButtonViewHolder, context, notificationText) -> {
            try {
                SyncDetailCalculations.updateSubText(activity, squareButtonViewHolder, cardDisplayData,
                        notificationText);
            } catch (SessionUnavailableException e) {
                // stop button setup, since redirection to login is imminent
                return;
            }

            squareButtonViewHolder.subTextView.setBackgroundColor(activity.getResources().getColor(cardDisplayData.subTextBgColor));
            squareButtonViewHolder.textView.setTextColor(context.getResources().getColor(cardDisplayData.textColor));
            squareButtonViewHolder.textView.setText(cardDisplayData.text);
        };
    }

    private static View.OnClickListener getStartButtonListener(final StandardHomeActivity activity) {
        return v ->  {
            reportButtonClick(AnalyticsParamValue.START_BUTTON);
            activity.enterRootModule();
        };
    }

    private static View.OnClickListener getTrainingButtonListener(final StandardHomeActivity activity) {
        return view -> activity.enterTrainingModule();
    }

    private static View.OnClickListener getIncompleteButtonListener(final StandardHomeActivity activity) {
        return v -> {
            reportButtonClick(AnalyticsParamValue.INCOMPLETE_FORMS_BUTTON);
            activity.goToFormArchive(true);
        };
    }

    private static TextSetter getIncompleteButtonTextSetter(final StandardHomeActivity activity) {
        return (cardDisplayData, squareButtonViewHolder, context, notificationText) -> {
            int numIncompleteForms;
            try {
                numIncompleteForms = StorageUtils.getNumIncompleteForms();
            } catch (SessionUnavailableException e) {
                // stop button setup, since redirection to login is imminent
                return;
            }

            if (numIncompleteForms > 0) {
                Spannable incompleteIndicator =
                        (activity.localize("home.forms.incomplete.indicator",
                                new String[]{String.valueOf(numIncompleteForms),
                                        Localization.get("home.forms.incomplete")}));
                squareButtonViewHolder.textView.setText(incompleteIndicator);
            } else {
                squareButtonViewHolder.textView.setText(activity.localize("home.forms.incomplete"));
            }
            squareButtonViewHolder.textView.setTextColor(context.getResources()
                    .getColor(cardDisplayData.textColor));
            squareButtonViewHolder.subTextView.setVisibility(View.GONE);
        };
    }

    private static View.OnClickListener getLogoutButtonListener(final StandardHomeActivity activity) {
        return v -> {
            reportButtonClick(AnalyticsParamValue.LOGOUT_BUTTON);
            activity.userTriggeredLogout();
        };
    }

    private static TextSetter getLogoutButtonTextSetter(final StandardHomeActivity activity) {
        return (cardDisplayData, squareButtonViewHolder, context, notificationText) -> {
            squareButtonViewHolder.textView.setText(cardDisplayData.text);
            squareButtonViewHolder.textView.setTextColor(context.getResources().getColor(cardDisplayData.textColor));
            squareButtonViewHolder.subTextView.setText(activity.getActivityTitle());
            squareButtonViewHolder.subTextView.setTextColor(context.getResources().getColor(cardDisplayData.subTextColor));
            squareButtonViewHolder.subTextView.setBackgroundColor(activity.getResources().getColor(cardDisplayData.subTextBgColor));
        };
    }

    private static View.OnClickListener getReportButtonListener(final StandardHomeActivity activity) {
        return v -> {
            reportButtonClick(AnalyticsParamValue.REPORT_BUTTON);
            Intent i = new Intent(activity, ReportProblemActivity.class);
            activity.startActivity(i);
        };
    }

    private static void reportButtonClick(String buttonLabel) {
        FirebaseAnalyticsUtil.reportHomeButtonClick(buttonLabel);
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
