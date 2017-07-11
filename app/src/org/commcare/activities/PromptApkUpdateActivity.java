package org.commcare.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.heartbeat.UpdatePromptHelper;
import org.javarosa.core.services.locale.Localization;

/**
 * Created by amstone326 on 7/11/17.
 */

public class PromptApkUpdateActivity extends PromptUpdateActivity {

    @Override
    protected void onCreateSessionSafe(Bundle savedInstanceState) {
        super.onCreateSessionSafe(savedInstanceState);
        CommCareApplication.instance().getSession().setApkUpdatePromptWasShown();
    }

    @Override
    void refreshUpdateToPromptObject() {
        updateToPrompt = UpdatePromptHelper.getCurrentUpdateToPrompt(true);
    }

    @Override
    protected void setUpTypeSpecificUIComponents() {
        updatesAvailableTitle.setText(
                Localization.get(inForceMode() ? "apk.update.required.title" : "apk.update.available.title"));

        updateButton.setText(Localization.get("apk.update.action"));
        updateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchCommCareOnPlayStore();
            }
        });

        imageCue.setImageResource(R.drawable.apk_update_cue_lts);
    }

    private void launchCommCareOnPlayStore() {
        final String appPackageName = getPackageName();
        Intent intent;
        try {
            startActivityForResult(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=" + appPackageName)), DO_AN_UPDATE);
        } catch (android.content.ActivityNotFoundException e) {
            startActivityForResult(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)), DO_AN_UPDATE);
        }
    }
}
