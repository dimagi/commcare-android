package org.commcare.dalvik.activities;

import android.content.SharedPreferences;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.commcare.android.resource.ResourceInstallUtils;
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
class UpdateUiController {
    private Button checkUpdateButton;
    private Button stopUpdateButton;
    private Button installUpdateButton;
    private ProgressBar progressBar;
    private TextView pendingUpdateStatus;
    private TextView currentVersionText;
    private TextView progressText;

    private final UpdateActivity activity;

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


    public UpdateUiController(UpdateActivity updateActivity) {
        activity = updateActivity;

        setupUi();
    }

    private void setupUi() {
        activity.setContentView(R.layout.update_activity);

        progressBar = (ProgressBar)activity.findViewById(R.id.update_progress_bar);
        progressText = (TextView)activity.findViewById(R.id.update_progress_text);
        pendingUpdateStatus =
                (TextView)activity.findViewById(R.id.pending_update_status_text);
        currentVersionText =
                (TextView)activity.findViewById(R.id.current_version_text);

        setupButtonListeners();
    }

    private void setupButtonListeners() {
        checkUpdateButton =
                (Button)activity.findViewById(R.id.check_for_update_button);
        checkUpdateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activity.startUpdateCheck();
            }
        });

        stopUpdateButton =
                (Button)activity.findViewById(R.id.stop_update_download_button);
        stopUpdateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activity.stopUpdateCheck();
            }
        });
        stopUpdateButton.setText(stopCheckingText);

        installUpdateButton =
                (Button)activity.findViewById(R.id.install_update_button);
        installUpdateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activity.lauchUpdateInstallTask();
            }
        });
    }

    protected void upToDate() {
        idle();

        progressText.setText(upToDateText);
    }

    protected void idle() {
        checkUpdateButton.setEnabled(true);
        stopUpdateButton.setEnabled(false);
        installUpdateButton.setEnabled(false);

        progressBar.setEnabled(false);
        updateProgressText("");
        updateProgressBar(0, 100);
        pendingUpdateStatus.setText("");
    }

    protected void downloading() {
        checkUpdateButton.setEnabled(false);
        stopUpdateButton.setEnabled(true);
        installUpdateButton.setEnabled(false);

        progressBar.setEnabled(true);
        updateProgressBar(0, 100);
        updateProgressText(beginCheckingText);
        pendingUpdateStatus.setText("");
    }

    protected void unappliedUpdateAvailable() {
        checkUpdateButton.setEnabled(true);
        stopUpdateButton.setEnabled(false);
        installUpdateButton.setEnabled(true);

        updateProgressBar(0, 100);
        progressBar.setEnabled(false);

        int version = ResourceInstallUtils.upgradeTableVersion();
        String versionMsg =
                Localization.get("update.staged.version",
                        new String[]{Integer.toString(version)});
        pendingUpdateStatus.setText(versionMsg);
        updateProgressText("");
    }

    protected void cancelling() {
        checkUpdateButton.setEnabled(false);
        stopUpdateButton.setEnabled(false);
        installUpdateButton.setEnabled(false);

        progressBar.setEnabled(false);
        updateProgressText(cancellingMsg);
    }

    protected void error() {
        checkUpdateButton.setEnabled(false);
        stopUpdateButton.setEnabled(false);
        installUpdateButton.setEnabled(false);

        progressBar.setEnabled(false);
        updateProgressText("Error!");
    }

    protected void updateInstalled() {
        checkUpdateButton.setEnabled(true);
        stopUpdateButton.setEnabled(false);
        installUpdateButton.setEnabled(false);

        pendingUpdateStatus.setText(upgradeFinishedText);
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
