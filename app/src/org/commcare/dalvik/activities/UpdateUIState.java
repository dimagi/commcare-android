package org.commcare.dalvik.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
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
class UpdateUIState {
    private static final String UPDATE_UI_STATE_KEY = "update_activity_ui_state";
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
    private final String checkFailedMessage =
            Localization.get("updates.check.failed");
    private final String errorMsg = Localization.get("updates.error");
    private final String upToDateText = Localization.get("updates.success");

    private enum UIState {
        Idle, UpToDate, FailedCheck, Downloading, UnappliedUpdateAvailable,
        Cancelling, Error, NoConnectivity, UpdateInstalled
    }

    private UIState currentUIState;

    public UpdateUIState(UpdateActivity updateActivity) {
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
        idleUiState();
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
        currentUIState = UIState.UpToDate;

        updateProgressBar(100, 100);
        progressText.setText(upToDateText);
    }

    protected void idleUiState() {
        currentUIState = UIState.Idle;
        checkUpdateButton.setEnabled(true);
        stopUpdateButton.setEnabled(false);
        installUpdateButton.setEnabled(false);

        progressBar.setEnabled(false);
        updateProgressText("");
        updateProgressBar(0, 100);
    }

    protected void checkFailedUiState() {
        idleUiState();
        currentUIState = UIState.FailedCheck;
        updateProgressText(checkFailedMessage);
    }

    protected void downloadingUiState() {
        currentUIState = UIState.Downloading;
        checkUpdateButton.setEnabled(false);
        stopUpdateButton.setEnabled(true);
        installUpdateButton.setEnabled(false);

        progressBar.setEnabled(true);
        updateProgressBar(0, 100);
        updateProgressText(beginCheckingText);
    }

    protected void unappliedUpdateAvailableUiState() {
        currentUIState = UIState.UnappliedUpdateAvailable;
        checkUpdateButton.setEnabled(true);
        stopUpdateButton.setEnabled(false);
        installUpdateButton.setEnabled(true);

        updateProgressBar(100, 100);
        progressBar.setEnabled(false);

        int version = ResourceInstallUtils.upgradeTableVersion();
        String versionMsg =
                Localization.get("updates.staged.version",
                        new String[]{Integer.toString(version)});
        installUpdateButton.setText(versionMsg);
        updateProgressText("");
    }

    protected void cancellingUiState() {
        currentUIState = UIState.Cancelling;
        checkUpdateButton.setEnabled(false);
        stopUpdateButton.setEnabled(false);
        installUpdateButton.setEnabled(false);

        progressBar.setEnabled(false);
        updateProgressText(cancellingMsg);
    }

    protected void errorUiState() {
        currentUIState = UIState.Error;
        checkUpdateButton.setEnabled(false);
        stopUpdateButton.setEnabled(false);
        installUpdateButton.setEnabled(false);

        progressBar.setEnabled(false);
        updateProgressText(errorMsg);
    }

    protected void noConnectivityUiState() {
        currentUIState = UIState.NoConnectivity;
        checkUpdateButton.setEnabled(false);
        stopUpdateButton.setEnabled(false);
        installUpdateButton.setEnabled(false);

        progressBar.setEnabled(false);
        updateProgressText(noConnectivityMsg);
    }

    protected void updateInstalledUiState() {
        currentUIState = UIState.UpdateInstalled;
        checkUpdateButton.setEnabled(true);
        stopUpdateButton.setEnabled(false);
        installUpdateButton.setEnabled(false);
        updateProgressBar(100, 100);
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

    public void saveCurrentUIState(Bundle outState) {
        outState.putSerializable(UPDATE_UI_STATE_KEY, currentUIState);
    }

    public void loadSavedUIState(Bundle savedInstanceState) {
        currentUIState = (UIState)savedInstanceState.getSerializable(UPDATE_UI_STATE_KEY);
        setUIFromState();
    }

    private void setUIFromState() {
        switch (currentUIState) {
            case Idle:
                idleUiState();
                break;
            case UpToDate:
                upToDateUiState();
                break;
            case FailedCheck:
                checkFailedUiState();
                break;
            case Downloading:
                downloadingUiState();
                break;
            case UnappliedUpdateAvailable:
                unappliedUpdateAvailableUiState();
                break;
            case Cancelling:
                cancellingUiState();
                break;
            case Error:
                errorUiState();
                break;
            case NoConnectivity:
                noConnectivityUiState();
                break;
            case UpdateInstalled:
                updateInstalledUiState();
                break;
            default:
                break;
        }
    }
}
