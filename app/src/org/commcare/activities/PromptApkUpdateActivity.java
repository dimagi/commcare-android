package org.commcare.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.google.android.play.core.install.model.InstallErrorCode;

import org.commcare.AppUtils;
import org.commcare.CommCareApplication;
import org.commcare.appupdate.AppUpdateController;
import org.commcare.appupdate.AppUpdateControllerFactory;
import org.commcare.appupdate.AppUpdateState;
import org.commcare.appupdate.FlexibleAppUpdateController;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.heartbeat.UpdatePromptHelper;
import org.commcare.heartbeat.UpdateToPrompt;
import org.commcare.util.LogTypes;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.views.dialogs.StandardAlertDialog;
import org.commcare.views.notifications.NotificationMessage;
import org.commcare.views.notifications.NotificationMessageFactory;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

/**
 * Created by amstone326 on 7/11/17.
 */

public class PromptApkUpdateActivity extends PromptActivity {

    private FlexibleAppUpdateController flexibleAppUpdateController;
    private AppUpdateController immediateAppUpdateController;
    private static final String APP_UPDATE_NOTIFICATION = "APP_UPDATE_NOTIFICATION";

    public static final String CUSTOM_PROMPT_TITLE = "custom-prompt-title";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Mark that we have shown the prompt for this user login
        try {
            CommCareApplication.instance().getSession().setApkUpdatePromptWasShown();
        } catch (SessionUnavailableException e) {
            // we are showing the prompt before user login, so nothing to mark
        }
        if (toPrompt.isForced()) {
            immediateAppUpdateController = AppUpdateControllerFactory.createImmediateController(this);
            immediateAppUpdateController.startUpdate(this);
        } else {
            flexibleAppUpdateController = AppUpdateControllerFactory.create(this::handleAppUpdate, getApplicationContext());
            flexibleAppUpdateController.register();
        }
    }

    @Override
    protected void onDestroy() {
        if (flexibleAppUpdateController != null) {
            flexibleAppUpdateController.unregister();
        }
        super.onDestroy();
    }

    private void handleAppUpdate() {
        AppUpdateState state = flexibleAppUpdateController.getStatus();
        switch (state) {
            case UNAVAILABLE:
                break;
            case AVAILABLE:
                flexibleAppUpdateController.startUpdate(this);
                break;
            case DOWNLOADING:
                // Native downloads app gives a notification regarding the current download in progress.
                NotificationMessage message = NotificationMessageFactory.message(
                        NotificationMessageFactory.StockMessages.InApp_Update, APP_UPDATE_NOTIFICATION);
                CommCareApplication.notificationManager().reportNotificationMessage(message);
                break;
            case DOWNLOADED:
                CommCareApplication.notificationManager().clearNotifications(APP_UPDATE_NOTIFICATION);
                StandardAlertDialog dialog = StandardAlertDialog.getBasicAlertDialog(this,
                        Localization.get("in.app.update.installed.title"),
                        Localization.get("in.app.update.installed.detail"),
                        null);
                dialog.setPositiveButton(Localization.get("in.app.update.dialog.restart"), (dialog1, which) -> {
                    flexibleAppUpdateController.completeUpdate();
                    dismissAlertDialog();
                });
                dialog.setNegativeButton(Localization.get("in.app.update.dialog.cancel"), (dialog1, which) -> {
                    dismissAlertDialog();
                });
                showAlertDialog(dialog);
                break;
            case FAILED:
                String errorReason = "in.app.update.error.unknown";
                switch (flexibleAppUpdateController.getErrorCode()) {
                    case InstallErrorCode.ERROR_INSTALL_NOT_ALLOWED:
                        errorReason = "in.app.update.error.not.allowed";
                        break;
                    case InstallErrorCode.NO_ERROR_PARTIALLY_ALLOWED:
                        errorReason = "in.app.update.error.partially.allowed";
                        break;
                    case InstallErrorCode.ERROR_UNKNOWN:
                        errorReason = "in.app.update.error.unknown";
                        break;
                    case InstallErrorCode.ERROR_PLAY_STORE_NOT_FOUND:
                        errorReason = "in.app.update.error.playstore";
                        break;
                    case InstallErrorCode.ERROR_INVALID_REQUEST:
                        errorReason = "in.app.update.error.invalid.request";
                        break;
                    case InstallErrorCode.ERROR_INTERNAL_ERROR:
                        errorReason = "in.app.update.error.internal.error";
                        break;
                }
                Logger.log(LogTypes.TYPE_CC_UPDATE, "CommCare In App Update failed because : " + errorReason);
                CommCareApplication.notificationManager().clearNotifications(APP_UPDATE_NOTIFICATION);
                Toast.makeText(this, Localization.get(errorReason), Toast.LENGTH_LONG).show();
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == AppUpdateController.IN_APP_UPDATE_REQUEST_CODE) {
            if (isUpdateComplete()) {
                finish();
            } else {
                refreshPromptObject();
                updateVisibilities();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    void refreshPromptObject() {
        if (getIntent().getStringExtra(REQUIRED_VERSION) != null) {
            String requiredVersion = getIntent().getStringExtra(REQUIRED_VERSION);
            toPrompt = new UpdateToPrompt(requiredVersion, "false", UpdateToPrompt.Type.APK_UPDATE);
        } else if (getIntent().getBooleanExtra(FROM_RECOVERY_MEASURE, false)) {
            toPrompt = UpdateToPrompt.DUMMY_APK_PROMPT_FOR_RECOVERY_MEASURE;
        } else {
            toPrompt = UpdatePromptHelper.getCurrentUpdateToPrompt(UpdateToPrompt.Type.APK_UPDATE);
        }
    }

    @Override
    String getHelpTextResource() {
        return "update.prompt.help.text";
    }

    @Override
    protected void setUpTypeSpecificUIComponents() {
        String customText = getIntent().getStringExtra(CUSTOM_PROMPT_TITLE);
        if (customText != null) {
            promptTitle.setText(customText);
        } else {
            promptTitle.setText(
                    Localization.get(inForceMode() ? "apk.update.required.title" : "apk.update.available.title",
                            getCurrentClientName()));
        }
        doLaterButton.setText(Localization.get("update.later.button.text"));

        actionButton.setText(Localization.get("apk.update.action", getCurrentClientName()));
        actionButton.setOnClickListener(v -> launchCurrentAppOnPlayStore());

        if (BuildConfig.APPLICATION_ID.equals("org.commcare.lts")) {
            imageCue.setImageResource(R.drawable.apk_update_cue_lts);
        } else {
            imageCue.setImageResource(R.drawable.apk_update_cue_commcare);
        }
    }

    @Override
    String getInstructionsStringKey() {
        return null;
    }

    @Override
    protected boolean isUpdateComplete() {
        return !AppUtils.notOnLatestCCVersion();
    }
}
