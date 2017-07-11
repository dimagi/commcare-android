package org.commcare.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import org.commcare.CommCareApplication;
import org.commcare.heartbeat.UpdatePromptHelper;
import org.commcare.heartbeat.UpdateToPrompt;
import org.javarosa.core.services.locale.Localization;

/**
 * Created by amstone326 on 7/11/17.
 */

public class PromptCczUpdateActivity extends PromptUpdateActivity {

    @Override
    protected void onCreateSessionSafe(Bundle savedInstanceState) {
        super.onCreateSessionSafe(savedInstanceState);
        CommCareApplication.instance().getSession().setCczUpdatePromptWasShown();
    }

    @Override
    void refreshUpdateToPromptObject() {
        updateToPrompt = UpdatePromptHelper.getCurrentUpdateToPrompt(UpdateToPrompt.Type.CCZ_UPDATE);
    }

    @Override
    protected void setUpTypeSpecificUIComponents() {
        updatesAvailableTitle.setText(
                Localization.get(inForceMode() ? "ccz.update.required.title" : "ccz.update.available.title"));

        updateButton.setText(Localization.get("ccz.update.action"));
        updateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchUpdateActivity();
            }
        });

        imageCue.setVisibility(View.GONE);
    }

    private void launchUpdateActivity() {
        startActivityForResult(new Intent(this, UpdateActivity.class), DO_AN_UPDATE);
    }
}
