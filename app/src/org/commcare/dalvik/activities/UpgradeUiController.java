package org.commcare.dalvik.activities;

import android.os.AsyncTask;
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

        setupUi();
    }

    private void setupUi() {
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

    protected void setUiStateFromRunningTask(AsyncTask.Status taskStatus) {
        switch (taskStatus) {
            case RUNNING:
                setDownloadingButtonState();
                break;
            case PENDING:
                pendingUpgradeOrIdle();
                break;
            case FINISHED:
                setErrorButtonState();
                break;
            default:
                setErrorButtonState();
        }
    }

    protected void pendingUpgradeOrIdle() {
        if (downloadedUpgradePresent()) {
            setUnappliedInstallButtonState();
        } else {
            setIdleButtonState();
        }
    }

    private boolean downloadedUpgradePresent() {
        return false;
    }

    protected void setIdleButtonState() {
        checkUpgradeButton.setEnabled(true);
        stopUpgradeButton.setEnabled(false);
        stopUpgradeButton.setText("Stop upgrade");
        installUpgradeButton.setEnabled(false);
    }

    protected void setDownloadingButtonState() {
        checkUpgradeButton.setEnabled(false);
        stopUpgradeButton.setEnabled(true);
        stopUpgradeButton.setText("Stop upgrade");
        installUpgradeButton.setEnabled(false);
    }

    protected void setUnappliedInstallButtonState() {
        checkUpgradeButton.setEnabled(true);
        stopUpgradeButton.setEnabled(false);
        stopUpgradeButton.setText("Stop upgrade");
        installUpgradeButton.setEnabled(true);
    }

    protected void setCancellingButtonState() {
        checkUpgradeButton.setEnabled(false);
        stopUpgradeButton.setEnabled(false);
        stopUpgradeButton.setText("Cancelling task");
        installUpgradeButton.setEnabled(false);
    }

    protected void setErrorButtonState() {
        checkUpgradeButton.setEnabled(false);
        stopUpgradeButton.setEnabled(false);
        stopUpgradeButton.setText("Stop upgrade");
        installUpgradeButton.setEnabled(false);
    }

    protected void updateProgressBar(int currentProgress) {
        progressBar.setProgress(currentProgress);
    }
}
