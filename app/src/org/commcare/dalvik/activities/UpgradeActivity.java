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

    private ProgressBar progressBar;
    private String incomingRef = "";

    private enum UpgradeUiState {
        idle,
        checking,
        downloading,
        cancelling,
        unappliedInstall,
        error
    }
    private static final String UI_STATE_KEY = "ui_state";
    private UpgradeUiState currentUiState;

    private UpgradeAppTask upgradeTask;

    @UiElement(R.id.check_for_upgrade_button)
    Button checkUpgradeButton;

    @UiElement(R.id.stop_upgrade_download_button)
    Button stopUpgradeButton;

    @UiElement(R.id.install_upgrade_button)
    Button installUpgradeButton;

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
        setupButtonState();
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

    private void setupButtonState() {
        switch (currentUiState) {
            case idle:
                setIdleButtonState();
                break;
            case checking:
                setDownloadingButtonState();
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
        if (currentUiState == UpgradeUiState.idle) {
            upgradeTask = UpgradeAppTask.getInstance();
            try {
                upgradeTask.registerTaskListener(this);
            } catch (TaskListenerException e) {
                Log.e(TAG, "Attempting to register a TaskListener to an already registered task.");
                currentUiState = UpgradeUiState.error;
                setupButtonState();
                return;
            }
            upgradeTask.execute(incomingRef);
        }
        progressBar.setProgress(0);
        currentUiState = UpgradeUiState.checking;
        setDownloadingButtonState();
    }

    private void stopUpgradeCheck() {
        if (upgradeTask != null) {
            upgradeTask.cancel(true);
        }
        currentUiState = UpgradeUiState.cancelling;
        setCancellingButtonState();
    }

    private void setUiStateFromRunningTask(AsyncTask.Status taskStatus) {
        switch (taskStatus) {
            case RUNNING:
                currentUiState = UpgradeUiState.checking;
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
        unregisterTask();
    }

    @Override
    public void processTaskCancel(Boolean result) {
        unregisterTask();

        currentUiState = UpgradeUiState.idle;
        progressBar.setProgress(0);
        setIdleButtonState();
    }
}
