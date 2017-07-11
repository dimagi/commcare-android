package org.commcare.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import org.commcare.CommCareApplication;
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

    }

    private void launchCommCareOnPlayStore() {
        final String appPackageName = getPackageName(); // getPackageName() from Context or Activity object
        Intent intent;
        try {
            intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=" + appPackageName));
        } catch (android.content.ActivityNotFoundException e) {
            intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName));
        }
        startActivityForResult(intent, DO_AN_UPDATE);
    }
}
