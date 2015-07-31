package org.commcare.dalvik.activities;

import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;

import org.commcare.android.framework.UiElement;
import org.commcare.dalvik.R;

class UpgradeUiController {
    @UiElement(R.id.check_for_upgrade_button)
    private Button checkUpgradeButton;

    @UiElement(R.id.stop_upgrade_download_button)
    private Button stopUpgradeButton;

    @UiElement(R.id.install_upgrade_button)
    private Button installUpgradeButton;

    private ProgressBar progressBar;

    private final UpgradeActivity activity;

    public UpgradeUiController(UpgradeActivity upgradeActivity) {
        activity = upgradeActivity;
    }

    public void setupUi() {
        activity.setContentView(R.layout.upgrade_activity);
        progressBar = (ProgressBar)activity.findViewById(R.id.upgrade_progress_bar);
        setupButtonListeners();
    }

    private void setupButtonListeners() {
        checkUpgradeButton = (Button)activity.findViewById(R.id.check_for_upgrade_button);
        checkUpgradeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activity.startUpgradeCheck();
            }
        });

        stopUpgradeButton = (Button)activity.findViewById(R.id.stop_upgrade_download_button);
        stopUpgradeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activity.stopUpgradeCheck();
            }
        });

        installUpgradeButton = (Button)activity.findViewById(R.id.install_upgrade_button);
        installUpgradeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            }
        });
    }

    protected void updateButtonState(UpgradeActivity.UpgradeUiState uiState) {
        switch (uiState) {
            case idle:
                setIdleButtonState();
                break;
            case downloading:
                setDownloadingButtonState();
                break;
            case unappliedInstall:
                setUnappliedInstallButtonState();
                break;
            case cancelling:
                setCancellingButtonState();
                break;
            case error:
                setErrorButtonState();
                break;
            default:
                setErrorButtonState();
        }
    }

    private void setIdleButtonState() {
        checkUpgradeButton.setEnabled(true);
        stopUpgradeButton.setEnabled(false);
        stopUpgradeButton.setText("Stop upgrade");
        installUpgradeButton.setEnabled(false);
    }

    private void setDownloadingButtonState() {
        checkUpgradeButton.setEnabled(false);
        stopUpgradeButton.setEnabled(true);
        stopUpgradeButton.setText("Stop upgrade");
        installUpgradeButton.setEnabled(false);
    }

    private void setUnappliedInstallButtonState() {
        checkUpgradeButton.setEnabled(true);
        stopUpgradeButton.setEnabled(false);
        stopUpgradeButton.setText("Stop upgrade");
        installUpgradeButton.setEnabled(true);
    }

    private void setCancellingButtonState() {
        checkUpgradeButton.setEnabled(false);
        stopUpgradeButton.setEnabled(false);
        stopUpgradeButton.setText("Cancelling task");
        installUpgradeButton.setEnabled(false);
    }

    private void setErrorButtonState() {
        checkUpgradeButton.setEnabled(false);
        stopUpgradeButton.setEnabled(false);
        stopUpgradeButton.setText("Stop upgrade");
        installUpgradeButton.setEnabled(false);
    }

    protected void updateUi(int currentProgress, UpgradeActivity.UpgradeUiState uiState) {
        updateProgressBar(currentProgress);
        updateButtonState(uiState);
    }

    protected void updateProgressBar(int currentProgress) {
        progressBar.setProgress(currentProgress);
    }
}
