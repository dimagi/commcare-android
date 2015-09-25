package org.commcare.dalvik.activities;

import android.content.SharedPreferences;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.commcare.android.resource.ResourceInstallUtils;
import org.commcare.android.view.CustomButtonWithText;
import org.commcare.android.view.SquareButtonWithText;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.javarosa.core.services.locale.Localization;

import java.util.Date;

/**
 * Handles upgrade activity UI.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
class UpdateUiController {
    private SquareButtonWithText checkUpdateButton;
    private SquareButtonWithText stopUpdateButton;
    private CustomButtonWithText installUpdateButton;
    private ProgressBar progressBar;
    private TextView currentVersionText;
    private TextView progressText;

    private final UpdateActivity activity;

    private final String stopCheckingText =
            Localization.get("updates.check.cancel");
    private final String upgradeFinishedText =
            Localization.get("updates.install.finished");
    private final String cancellingMsg =
            Localization.get("updates.check.cancelling");
    private final String beginCheckingText =
            Localization.get("updates.check.begin");
    private final String noConnectivityMsg =
            Localization.get("updates.check.network_unavailable");
    private final String errorMsg = Localization.get("updates.error");
    private final String upToDateText = Localization.get("updates.success");

    public UpdateUiController(UpdateActivity updateActivity) {
        activity = updateActivity;

        setupUi();
    }

    private void setupUi() {
        activity.setContentView(R.layout.update_activity);

        progressBar = (ProgressBar)activity.findViewById(R.id.update_progress_bar);
        progressText = (TextView)activity.findViewById(R.id.update_progress_text);
        currentVersionText =
                (TextView)activity.findViewById(R.id.current_version_text);

        setupButtonListeners();
    }

    private void setupButtonListeners() {
        checkUpdateButton =
                (SquareButtonWithText)activity.findViewById(R.id.check_for_update_button);
        checkUpdateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activity.startUpdateCheck();
            }
        });

        stopUpdateButton =
                (SquareButtonWithText)activity.findViewById(R.id.stop_update_download_button);
        stopUpdateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activity.stopUpdateCheck();
            }
        });
        stopUpdateButton.setText(stopCheckingText);

        installUpdateButton =
                (CustomButtonWithText)activity.findViewById(R.id.install_update_button);
        installUpdateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activity.lauchUpdateInstallTask();
            }
        });
    }

    protected void upToDateUiState() {
        idleUiState();

        progressText.setText(upToDateText);
    }

    protected void idleUiState() {
        checkUpdateButton.setEnabled(true);
        stopUpdateButton.setEnabled(false);
        installUpdateButton.setEnabled(false);

        progressBar.setEnabled(false);
        updateProgressText("");
        updateProgressBar(0, 100);
    }

    protected void downloadingUiState() {
        checkUpdateButton.setEnabled(false);
        stopUpdateButton.setEnabled(true);
        installUpdateButton.setEnabled(false);

        progressBar.setEnabled(true);
        updateProgressBar(0, 100);
        updateProgressText(beginCheckingText);
    }

    protected void unappliedUpdateAvailableUiState() {
        checkUpdateButton.setEnabled(true);
        stopUpdateButton.setEnabled(false);
        installUpdateButton.setEnabled(true);

        updateProgressBar(0, 100);
        progressBar.setEnabled(false);

        int version = ResourceInstallUtils.upgradeTableVersion();
        String versionMsg =
                Localization.get("updates.staged.version",
                        new String[]{Integer.toString(version)});
        installUpdateButton.setText(versionMsg);
        updateProgressText("");
    }

    protected void cancellingUiState() {
        checkUpdateButton.setEnabled(false);
        stopUpdateButton.setEnabled(false);
        installUpdateButton.setEnabled(false);

        progressBar.setEnabled(false);
        updateProgressText(cancellingMsg);
    }

    protected void errorUiState() {
        checkUpdateButton.setEnabled(false);
        stopUpdateButton.setEnabled(false);
        installUpdateButton.setEnabled(false);

        progressBar.setEnabled(false);
        updateProgressText(errorMsg);
    }

    protected void noConnectivityUiState() {
        checkUpdateButton.setEnabled(false);
        stopUpdateButton.setEnabled(false);
        installUpdateButton.setEnabled(false);

        progressBar.setEnabled(false);
        updateProgressText(noConnectivityMsg);
    }

    protected void updateInstalledUiState() {
        checkUpdateButton.setEnabled(true);
        stopUpdateButton.setEnabled(false);
        installUpdateButton.setEnabled(false);

        progressBar.setEnabled(false);
        updateProgressText(upgradeFinishedText);

        refreshStatusText();
    }

    protected void updateProgressText(String msg) {
        progressText.setText(msg);
    }

    protected void updateProgressBar(int currentProgress, int max) {
        progressBar.setProgress(currentProgress);
        progressBar.setMax(max);
    }

    public void refreshStatusText() {
        CommCareApplication app = CommCareApplication._();

        SharedPreferences preferences =
                app.getCurrentApp().getAppPreferences();

        long lastUpdateCheck =
                preferences.getLong(CommCarePreferences.LAST_UPDATE_ATTEMPT, 0);

        int version = app.getCommCarePlatform().getCurrentProfile().getVersion();
        Date lastChecked = new Date(lastUpdateCheck);

        String checkedMsg =
                Localization.get("updates.check.last",
                        new String[]{lastChecked.toString()});

        String versionMsg =
                Localization.get("install.current.version",
                        new String[]{Integer.toString(version)});
        currentVersionText.setText(versionMsg + "\n" + checkedMsg);
    }
}
