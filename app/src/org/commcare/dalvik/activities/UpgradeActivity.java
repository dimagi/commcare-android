package org.commcare.dalvik.activities;

import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;

import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.framework.UiElement;
import org.commcare.android.tasks.TaskListener;
import org.commcare.android.tasks.TaskListenerException;
import org.commcare.android.tasks.UpgradeAppTask;
import org.commcare.dalvik.R;

/**
 * Allow user to manage app upgrading:
 *  - Check and downloading new latest upgrade
 *  - Stop an upgrade download
 *  - Apply a downloaded upgrade
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class UpgradeActivity extends CommCareActivity implements TaskListener<int[], Boolean> {
    private static final String TAG = UpgradeActivity.class.getSimpleName();
    private static final String UI_STATE_KEY = "ui_state";

    @UiElement(R.id.check_for_upgrade_button)
    Button checkUpgradeButton;

    @UiElement(R.id.stop_upgrade_download_button)
    Button stopUpgradeButton;

    @UiElement(R.id.install_upgrade_button)
    Button installUpgradeButton;

    private enum UpgradeUiState {
        idle,
        downloading,
        cancelling,
        unappliedInstall,
        error
    }
    private UpgradeUiState currentUiState;

    private UpgradeAppTask upgradeTask;

    private ProgressBar progressBar;
    private String incomingRef = "";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        loadSaveInstanceState(savedInstanceState);

        setContentView(R.layout.upgrade_activity);
        progressBar = (ProgressBar)findViewById(R.id.upgrade_progress_bar);
        setupButtonListeners();

        upgradeTask = UpgradeAppTask.getRunningInstance();
        try {
            if (upgradeTask != null) {
                upgradeTask.registerTaskListener(this);
                if (currentUiState != UpgradeUiState.cancelling) {
                    setUiStateFromRunningTask(upgradeTask.getStatus());
                }
                progressBar.setProgress(upgradeTask.getProgress());
            } else {
                progressBar.setProgress(0);
                currentUiState = pendingUpgradeOrIdle();
            }
        } catch (TaskListenerException e) {
            Log.e(TAG, "Attempting to register a TaskListener to an already registered task.");
            currentUiState = UpgradeUiState.error;
        }

        // update UI based on current state
        updateButtonState();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putSerializable(UI_STATE_KEY, currentUiState);
    }

    private void loadSaveInstanceState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(UI_STATE_KEY)) {
                currentUiState = (UpgradeUiState)savedInstanceState.get(UI_STATE_KEY);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        unregisterTask();
    }

    private void setupButtonListeners() {
        checkUpgradeButton = (Button)findViewById(R.id.check_for_upgrade_button);
        checkUpgradeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startUpgradeCheck();
            }
        });

        stopUpgradeButton = (Button)findViewById(R.id.stop_upgrade_download_button);
        stopUpgradeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopUpgradeCheck();
            }
        });

        installUpgradeButton = (Button)findViewById(R.id.install_upgrade_button);
        installUpgradeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            }
        });
    }

    private void updateButtonState() {
        switch (currentUiState) {
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

    private void startUpgradeCheck() {
        upgradeTask = UpgradeAppTask.getNewInstance();
        try {
            upgradeTask.registerTaskListener(this);
        } catch (TaskListenerException e) {
            Log.e(TAG, "Attempting to register a TaskListener to an already registered task.");
            currentUiState = UpgradeUiState.error;
            updateButtonState();
            return;
        }
        upgradeTask.execute(incomingRef);
        progressBar.setProgress(0);
        currentUiState = UpgradeUiState.downloading;
        updateButtonState();
    }

    private void stopUpgradeCheck() {
        if (upgradeTask != null) {
            upgradeTask.cancel(true);
        }
        currentUiState = UpgradeUiState.cancelling;
        updateButtonState();
    }

    private void setUiStateFromRunningTask(AsyncTask.Status taskStatus) {
        switch (taskStatus) {
            case RUNNING:
                currentUiState = UpgradeUiState.downloading;
                break;
            case PENDING:
                currentUiState = pendingUpgradeOrIdle();
                break;
            case FINISHED:
                currentUiState = UpgradeUiState.error;
                break;
            default:
                currentUiState = UpgradeUiState.error;
        }
    }

    private UpgradeUiState pendingUpgradeOrIdle() {
        if (downloadedUpgradePresent()) {
            return UpgradeUiState.unappliedInstall;
        } else {
            return UpgradeUiState.idle;
        }
    }

    private boolean downloadedUpgradePresent() {
        return false;
    }

    private void unregisterTask() {
        if (upgradeTask != null) {
            try {
                upgradeTask.unregisterTaskListener(this);
            } catch (TaskListenerException e) {
                Log.e(TAG, "Attempting to unregister a not previously registered TaskListener.");
            }
            upgradeTask = null;
        }
    }

    @Override
    public void processTaskUpdate(int[]... vals) {
        int progress = vals[0][0];
        progressBar.setProgress(progress);
    }

    @Override
    public void processTaskResult(Boolean result) {
        if (result) {
            currentUiState = UpgradeUiState.unappliedInstall;
        } else {
            currentUiState = UpgradeUiState.idle;
        }

        updateButtonState()
        unregisterTask();
    }

    @Override
    public void processTaskCancel(Boolean result) {
        unregisterTask();

        currentUiState = UpgradeUiState.idle;
        updateButtonState()

        progressBar.setProgress(0);
    }
}
