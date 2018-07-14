package org.commcare.activities;

import android.os.Bundle;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.heartbeat.UpdatePromptHelper;
import org.commcare.heartbeat.UpdateToPrompt;
import org.commcare.utils.SessionUnavailableException;
import org.javarosa.core.services.locale.Localization;

/**
 * Created by amstone326 on 7/11/17.
 */

public class PromptApkUpdateActivity extends PromptActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Mark that we have shown the prompt for this user login
        try {
            CommCareApplication.instance().getSession().setApkUpdatePromptWasShown();
        } catch (SessionUnavailableException e) {
            // we are showing the prompt before user login, so nothing to mark
        }
    }

    @Override
    void refreshPromptObject() {
        if (getIntent().getBooleanExtra(FROM_RECOVERY_MEASURE, false)) {
            toPrompt = UpdateToPrompt.DUMMY_APK_PROMPT_FOR_RECOVERY_MEASURE;
        } else {
            toPrompt = UpdatePromptHelper.getCurrentUpdateToPrompt(UpdateToPrompt.Type.APK_UPDATE);
        }
    }

    @Override
    String getActionString() {
        return "updating";
    }

    @Override
    protected void setUpTypeSpecificUIComponents() {
        promptTitle.setText(
                Localization.get(inForceMode() ? "apk.update.required.title" : "apk.update.available.title",
                        getCurrentClientName()));
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
}
