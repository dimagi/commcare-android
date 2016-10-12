package org.commcare.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.engine.resource.ResourceInstallUtils;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.views.SquareButtonWithText;
import org.javarosa.core.services.locale.Localization;

/**
 * Handles upgrade activity UI.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
class UpdateUIController implements CommCareActivityUIController {
    private static final String UPDATE_UI_STATE_KEY = "update_activity_ui_state";
    private static final String ERROR_MESSAGE_STATE_KEY = "error_message_state";
    private SquareButtonWithText checkUpdateButton;
    private SquareButtonWithText stopUpdateButton;
    private SquareButtonWithText installUpdateButton;
    private ProgressBar progressBar;
    private TextView currentVersionText;
    private TextView progressText;

    protected final UpdateActivity activity;

    private final String applyUpdateButtonTextKey;

    private enum UIState {
        Idle, UpToDate, FailedCheck, Downloading, UnappliedUpdateAvailable,
        Cancelling, Error, NoConnectivity, ApplyingUpdate
    }

    private UIState currentUIState;
    private String errorMsg;

    public UpdateUIController(UpdateActivity updateActivity, boolean startedByAppManager) {
        if (startedByAppManager) {
            applyUpdateButtonTextKey = "updates.staged.version.app.manager";
        } else {
            applyUpdateButtonTextKey = "updates.staged.version";
        }
        activity = updateActivity;
    }

    @Override
    public void setupUI() {
        activity.setContentView(R.layout.update_activity);

        progressBar = (ProgressBar)activity.findViewById(R.id.update_progress_bar);
        progressText = (TextView)activity.findViewById(R.id.update_progress_text);
        currentVersionText =
                (TextView)activity.findViewById(R.id.current_version_text);

        setupButtonListeners();
        idleUiState();
    }

    @Override
    public void refreshView() {
        if (currentUIState != UIState.ApplyingUpdate) {
            // don't load app info while changing said app info; that causes crashes
            refreshStatusText();
        }
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
        checkUpdateButton.setText(Localization.getWithDefault("updates.check.start", ""));

        stopUpdateButton =
                (SquareButtonWithText)activity.findViewById(R.id.stop_update_download_button);
        stopUpdateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activity.stopUpdateCheck();
            }
        });
        stopUpdateButton.setText(Localization.getWithDefault("updates.check.cancel", ""));

        installUpdateButton =
                (SquareButtonWithText)activity.findViewById(R.id.install_update_button);
        installUpdateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activity.launchUpdateInstallTask();
            }
        });
        String updateVersionPlaceholderMsg =
                Localization.getWithDefault(applyUpdateButtonTextKey, new String[]{"-1"}, "");
        installUpdateButton.setText(updateVersionPlaceholderMsg);
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

    protected void checkFailedUiState(String errorMsg) {
        idleUiState();
        this.errorMsg = errorMsg;
        currentUIState = UIState.FailedCheck;
        String failureMessage = Localization.get("updates.check.failed");
        String errorMessage = Localization.get("updates.check.failed.detail", new String[]{errorMsg});
        updateProgressText(failureMessage + "\n" + errorMessage);
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

        int version = ResourceInstallUtils.upgradeTableVersion();
        String versionMsg =
                Localization.get(applyUpdateButtonTextKey,
                        new String[]{Integer.toString(version)});
        installUpdateButton.setText(versionMsg);
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
        checkUpdateButton.setEnabled(false);
        stopUpdateButton.setVisibility(View.GONE);
        installUpdateButton.setVisibility(View.GONE);

        updateProgressText(Localization.get("updates.error"));
    }

    protected void noConnectivityUiState() {
        currentUIState = UIState.NoConnectivity;
        checkUpdateButton.setVisibility(View.VISIBLE);
        checkUpdateButton.setEnabled(false);
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
    }

    protected void updateProgressBar(int currentProgress, int max) {
        progressBar.setMax(max);
        progressBar.setProgress(currentProgress);
    }

    private void refreshStatusText() {
        CommCareApplication app = CommCareApplication._();

        int version = app.getCommCarePlatform().getCurrentProfile().getVersion();

        currentVersionText.setText(Localization.get("install.current.version",
                new String[]{Integer.toString(version)}));
    }

    public void saveCurrentUIState(Bundle outState) {
        outState.putSerializable(UPDATE_UI_STATE_KEY, currentUIState);
        outState.putString(ERROR_MESSAGE_STATE_KEY, errorMsg);
    }

    public void loadSavedUIState(Bundle savedInstanceState) {
        currentUIState = (UIState)savedInstanceState.getSerializable(UPDATE_UI_STATE_KEY);
        errorMsg = (String)savedInstanceState.getSerializable(ERROR_MESSAGE_STATE_KEY);
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
                checkFailedUiState(errorMsg);
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
