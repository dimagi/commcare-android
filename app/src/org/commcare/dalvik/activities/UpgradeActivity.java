package org.commcare.dalvik.activities;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;

import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.framework.UiElement;
import org.commcare.android.tasks.UpgradeAppTask;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.dialogs.CustomProgressDialog;
import org.javarosa.core.services.locale.Localization;

import static java.lang.Thread.sleep;

/**
 * Allow user to manage app upgrading:
 *  - Check and downloading new latest upgrade
 *  - Stop an upgrade download
 *  - Apply a downloaded upgrade
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class UpgradeActivity extends CommCareActivity {
    private static final String TAG = UpgradeActivity.class.getSimpleName();

    private ProgressBar mProgress;
    private int mProgressStatus = 0;
    private boolean areResourcesInitialized = false;
    private String incomingRef;

    private Handler mHandler = new Handler();

    private enum UpgradeUiState {
        idle,
        checking,
        downloading,
        unappliedInstall
    }
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

        setContentView(R.layout.upgrade_activity);
        mProgress = (ProgressBar)findViewById(R.id.upgrade_progress_bar);
        setupButtonListeners();

        upgradeTask = UpgradeAppTask.getSingleRunningTask();
        if (upgradeTask != null) {
            upgradeTask.connect(this);
            setUiStateFromRunningTask(upgradeTask.getUprgradeState());
        } else {
            setUiStateFromRunningTask(UpgradeAppTask.UpgradeTaskState.notRunning);
        }

        // update UI based on current state
        setupButtonState();

        // Start lengthy operation in a background thread
        new Thread(new Runnable() {
            public void run() {
                while (mProgressStatus < 100) {
                    mProgressStatus = doWork();

                    // Update the progress bar
                    mHandler.post(new Runnable() {
                        public void run() {
                            mProgress.setProgress(mProgressStatus);
                        }
                    });
                }
            }
        }).start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (upgradeTask != null) {
            upgradeTask.disconnect();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    public int doWork() {
        try {
            sleep(1000);
        } catch (Exception e) {
        }
        return mProgressStatus + 1;
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
            default:
                setIdleButtonState();
        }
    }

    private void setIdleButtonState() {
        checkUpgradeButton.setEnabled(true);
        stopUpgradeButton.setEnabled(false);
        installUpgradeButton.setEnabled(false);
    }

    private void setDownloadingButtonState() {
        checkUpgradeButton.setEnabled(false);
        stopUpgradeButton.setEnabled(true);
        installUpgradeButton.setEnabled(false);
    }

    private void setUnappliedInstallButtonState() {
        checkUpgradeButton.setEnabled(true);
        stopUpgradeButton.setEnabled(false);
        installUpgradeButton.setEnabled(true);
    }


    private void startUpgradeCheck() {
        if (currentUiState == UpgradeUiState.idle) {
            upgradeTask = new UpgradeAppTask(CommCareApplication._().getCurrentApp(), false);
            upgradeTask.connect(this);
            upgradeTask.execute(incomingRef);
        }
        currentUiState = UpgradeUiState.checking;
        setDownloadingButtonState();
    }

    private void stopUpgradeCheck() {
        if (upgradeTask != null) {
            upgradeTask.cancel(true);
        }
        currentUiState = UpgradeUiState.idle;
        setIdleButtonState();
    }


    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        String title, message;
        if (areResourcesInitialized) {
            title = Localization.get("updates.title");
            message = Localization.get("updates.checking");
        } else {
            title = Localization.get("updates.resources.initialization");
            message = Localization.get("updates.resources.profile");
        }
        CustomProgressDialog dialog = CustomProgressDialog.newInstance(title, message, taskId);
        dialog.setCancelable(false);
        String checkboxText = Localization.get("updates.keep.trying");
        CustomProgressDialog lastDialog = getCurrentDialog();
        boolean isChecked = (lastDialog != null) && lastDialog.isChecked();
        dialog.addCheckbox(checkboxText, isChecked);
        dialog.addProgressBar();
        return dialog;
    }

    private void setUiStateFromRunningTask(UpgradeAppTask.UpgradeTaskState upgradeTaskState) {
        switch (upgradeTaskState) {
            case checking:
                currentUiState = UpgradeUiState.checking;
                break;
            case downloading:
                currentUiState = UpgradeUiState.downloading;
                break;
            case notRunning:
                currentUiState = pendingUpgradeOrIdle();
                break;
            default:
                currentUiState = pendingUpgradeOrIdle();
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
}
