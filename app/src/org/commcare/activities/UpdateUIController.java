package org.commcare.activities;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.commcare.CommCareApplication;
import org.commcare.CommCareNoficationManager;
import org.commcare.dalvik.R;
import org.commcare.engine.resource.ResourceInstallUtils;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.views.RectangleButtonWithText;
import org.commcare.views.SquareButtonWithText;
import org.javarosa.core.services.locale.Localization;

/**
 * Handles upgrade activity UI.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
class UpdateUIController implements CommCareActivityUIController {
    private static final String UPDATE_UI_STATE_KEY = "update_activity_ui_state";
    private SquareButtonWithText checkUpdateButton;
    private SquareButtonWithText stopUpdateButton;
    private SquareButtonWithText installUpdateButton;
    private ProgressBar progressBar;
    private TextView currentVersionText;
    private TextView progressText;

    private View notificationsButtonContainer;
    private RectangleButtonWithText notificationsButton;

    protected final UpdateActivity activity;

    private final String applyUpdateButtonTextKey;

    private enum UIState {
        Idle, UpToDate, FailedCheck, Downloading, UnappliedUpdateAvailable,
        Cancelling, Error, NoConnectivity, ApplyingUpdate
    }

    private UIState currentUIState;

    public UpdateUIController(UpdateActivity updateActivity, boolean startedByAppManager) {
        applyUpdateButtonTextKey = startedByAppManager ? "updates.staged.version.app.manager" : "updates.staged.version";
        activity = updateActivity;
    }

    @Override
    public void setupUI() {
        activity.setContentView(R.layout.update_activity);

        progressBar = activity.findViewById(R.id.update_progress_bar);
        progressText = activity.findViewById(R.id.update_progress_text);
        currentVersionText =
                activity.findViewById(R.id.current_version_text);

        notificationsButtonContainer = activity.findViewById(R.id.btn_view_errors_container);

        notificationsButton = activity.findViewById(R.id.update_btn_view_notifications);

        notificationsButton.setText(Localization.get("error.button.text"));


        setupButtonListeners();
        idleUiState();
    }

    @Override
    public void refreshView() {
        if (currentUIState != UIState.ApplyingUpdate) {
            // don't load app info while changing said app info; that causes crashes
            refreshStatusText();
        }
        if (!CommCareApplication.notificationManager().messagesForCommCareArePending()) {
            notificationsButtonContainer.setVisibility(View.GONE);
        }
    }

    private void setupButtonListeners() {
        checkUpdateButton =
                activity.findViewById(R.id.check_for_update_button);
        checkUpdateButton.setOnClickListener(v -> activity.startUpdateCheck());
        checkUpdateButton.setText(Localization.getWithDefault("updates.check.start", ""));

        stopUpdateButton =
                activity.findViewById(R.id.stop_update_download_button);
        stopUpdateButton.setOnClickListener(v -> activity.stopUpdateCheck());
        stopUpdateButton.setText(Localization.getWithDefault("updates.check.cancel", ""));

        installUpdateButton =
                activity.findViewById(R.id.install_update_button);
        installUpdateButton.setOnClickListener(v -> activity.launchUpdateInstallTask());
        String updateVersionPlaceholderMsg =
                Localization.getWithDefault(applyUpdateButtonTextKey, new String[]{"-1"}, "");
        installUpdateButton.setText(updateVersionPlaceholderMsg);

        notificationsButton.setOnClickListener(v -> CommCareNoficationManager.performIntentCalloutToNotificationsView(activity));
    }

    protected void upToDateUiState() {
        idleUiState();
        currentUIState = UIState.UpToDate;

        updateProgressBar(100, 100);
        progressText.setText(Localization.get("updates.success"));
    }

    protected void idleUiState() {
        currentUIState = UIState.Idle;
        checkUpdateButton.setVisibility(View.VISIBLE);
        checkUpdateButton.setEnabled(true);
        stopUpdateButton.setVisibility(View.GONE);
        installUpdateButton.setVisibility(View.GONE);

        updateProgressText("");
        updateProgressBar(0, 100);
    }

    protected void checkFailedUiState() {
        idleUiState();
        currentUIState = UIState.FailedCheck;
        updateErrorText(Localization.get("updates.check.failed"));
    }

    protected void downloadingUiState() {
        currentUIState = UIState.Downloading;
        checkUpdateButton.setVisibility(View.GONE);
        stopUpdateButton.setVisibility(View.VISIBLE);
        stopUpdateButton.setEnabled(true);
        installUpdateButton.setVisibility(View.GONE);

        updateProgressBar(0, 100);
        updateProgressText(Localization.get("updates.check.begin"));
    }

    protected void unappliedUpdateAvailableUiState() {
        currentUIState = UIState.UnappliedUpdateAvailable;
        checkUpdateButton.setVisibility(View.GONE);
        stopUpdateButton.setVisibility(View.GONE);
        installUpdateButton.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.GONE);

        updateProgressBar(100, 100);

        if (UpdateActivity.isUpdateBlockedOnSync()) {
            installUpdateButton.setText(Localization.get("updates.staged.version.sync.required"));
            installUpdateButton.setImage(activity.getResources().getDrawable(R.drawable.home_sync));
        } else {
            int version = ResourceInstallUtils.upgradeTableVersion();
            String versionMsg =
                    Localization.get(applyUpdateButtonTextKey,
                            new String[]{Integer.toString(version)});
            installUpdateButton.setText(versionMsg);
        }
        updateProgressText("");
    }

    protected void cancellingUiState() {
        currentUIState = UIState.Cancelling;
        checkUpdateButton.setVisibility(View.GONE);
        stopUpdateButton.setEnabled(false);
        stopUpdateButton.setVisibility(View.VISIBLE);
        installUpdateButton.setVisibility(View.GONE);

        updateProgressText(Localization.get("updates.check.cancelling"));
    }

    protected void errorUiState() {
        currentUIState = UIState.Error;
        checkUpdateButton.setVisibility(View.VISIBLE);
        stopUpdateButton.setVisibility(View.GONE);
        installUpdateButton.setVisibility(View.GONE);

        updateErrorText(Localization.get("updates.error"));
    }

    protected void noConnectivityUiState() {
        currentUIState = UIState.NoConnectivity;
        checkUpdateButton.setVisibility(View.VISIBLE);
        stopUpdateButton.setVisibility(View.GONE);
        installUpdateButton.setVisibility(View.GONE);

        updateProgressText(Localization.get("updates.check.network_unavailable"));
    }

    protected void applyingUpdateUiState() {
        currentUIState = UIState.ApplyingUpdate;

        checkUpdateButton.setVisibility(View.GONE);
        stopUpdateButton.setVisibility(View.GONE);
        installUpdateButton.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
    }

    protected void updateProgressText(String msg) {
        progressText.setText(msg);
        progressText.setTextColor(Color.BLACK);
        if (!msg.equals("")) {
            notificationsButtonContainer.setVisibility(View.GONE);
        }
    }

    protected void updateErrorText(String msg) {
        progressText.setText(msg);
        progressText.setTextColor(Color.RED);
    }

    protected void setNotificationsVisible() {
        notificationsButtonContainer.setVisibility(View.VISIBLE);
    }

    protected void updateProgressBar(int currentProgress, int max) {
        progressBar.setMax(max);
        progressBar.setProgress(currentProgress);
    }

    private void refreshStatusText() {
        CommCareApplication commCareApplication = CommCareApplication.instance();

        int version = commCareApplication.getCurrentApp().getAppRecord().getVersionNumber();

        currentVersionText.setText(Localization.get("install.current.version",
                new String[]{Integer.toString(version)}));
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
            case ApplyingUpdate:
                applyingUpdateUiState();
                break;
            default:
                break;
        }
    }
}
