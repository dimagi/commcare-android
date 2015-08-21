package org.commcare.dalvik.activities;

import android.content.SharedPreferences;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.commcare.android.util.InstallAndUpdateUtils;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.javarosa.core.services.locale.Localization;

import java.util.Date;

/**
 * Controls the UI for the upgrade activity.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
class UpgradeUiController {
    private Button checkUpgradeButton;
    private Button stopUpgradeButton;
    private Button installUpgradeButton;
    private ProgressBar progressBar;
    private TextView pendingUpgradeStatus;
    private TextView currentVersionText;
    private TextView progressText;

    private final UpgradeActivity activity;

    private final String upToDateText =
            Localization.get("updates.success");
    private final String stopCheckingText =
            Localization.get("updates.check.cancel");
    private final String upgradeFinishedText =
            Localization.get("updates.install.finished");
    private final String cancellingMsg =
            Localization.get("updates.check.cancelling");
    private final String beginCheckingText =
            Localization.get("updates.check.begin");


    public UpgradeUiController(UpgradeActivity upgradeActivity) {
        activity = upgradeActivity;

        setupUi();
    }

    private void setupUi() {
        activity.setContentView(R.layout.upgrade_activity);

        progressBar = (ProgressBar)activity.findViewById(R.id.upgrade_progress_bar);
        progressText = (TextView)activity.findViewById(R.id.upgrade_progress_text);
        pendingUpgradeStatus =
                (TextView)activity.findViewById(R.id.pending_upgrade_status_text);
        currentVersionText =
                (TextView)activity.findViewById(R.id.current_version_text);

        setupButtonListeners();
    }

    private void setupButtonListeners() {
        checkUpgradeButton =
                (Button)activity.findViewById(R.id.check_for_upgrade_button);
        checkUpgradeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activity.startUpgradeCheck();
            }
        });

        stopUpgradeButton =
                (Button)activity.findViewById(R.id.stop_upgrade_download_button);
        stopUpgradeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activity.stopUpgradeCheck();
            }
        });
        stopUpgradeButton.setText(stopCheckingText);

        installUpgradeButton =
                (Button)activity.findViewById(R.id.install_upgrade_button);
        installUpgradeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activity.launchUpgradeInstallTask();
            }
        });
    }

    protected void upToDate() {
        idle();

        progressText.setText(upToDateText);
    }

    protected void idle() {
        checkUpgradeButton.setEnabled(true);
        stopUpgradeButton.setEnabled(false);
        installUpgradeButton.setEnabled(false);

        progressBar.setEnabled(false);
        updateProgressText("");
        updateProgressBar(0, 100);
        pendingUpgradeStatus.setText("");
    }

    protected void downloading() {
        checkUpgradeButton.setEnabled(false);
        stopUpgradeButton.setEnabled(true);
        installUpgradeButton.setEnabled(false);

        progressBar.setEnabled(true);
        updateProgressBar(0, 100);
        updateProgressText(beginCheckingText);
        pendingUpgradeStatus.setText("");
    }

    protected void unappliedUpdateAvailable() {
        checkUpgradeButton.setEnabled(true);
        stopUpgradeButton.setEnabled(false);
        installUpgradeButton.setEnabled(true);

        updateProgressBar(0, 100);
        progressBar.setEnabled(false);

        int version = InstallAndUpdateUtils.upgradeTableVersion();
        String versionMsg =
                Localization.get("update.staged.version",
                        new String[]{Integer.toString(version)});
        pendingUpgradeStatus.setText(versionMsg);
        updateProgressText("");
    }

    protected void cancelling() {
        checkUpgradeButton.setEnabled(false);
        stopUpgradeButton.setEnabled(false);
        installUpgradeButton.setEnabled(false);

        progressBar.setEnabled(false);
        updateProgressText(cancellingMsg);
    }

    protected void error() {
        checkUpgradeButton.setEnabled(false);
        stopUpgradeButton.setEnabled(false);
        installUpgradeButton.setEnabled(false);

        progressBar.setEnabled(false);
        updateProgressText("Error!");
    }

    protected void upgradeInstalled() {
        checkUpgradeButton.setEnabled(true);
        stopUpgradeButton.setEnabled(false);
        installUpgradeButton.setEnabled(false);

        pendingUpgradeStatus.setText(upgradeFinishedText);
        progressBar.setEnabled(false);
        updateProgressText("");

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
