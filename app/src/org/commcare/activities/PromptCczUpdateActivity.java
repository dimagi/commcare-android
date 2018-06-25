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

public class PromptCczUpdateActivity extends PromptActivity {

    @Override
    public void onCreateSessionSafe(Bundle savedInstanceState) {
        super.onCreateSessionSafe(savedInstanceState);
        CommCareApplication.instance().getSession().setCczUpdatePromptWasShown();
    }

    @Override
    void refreshPromptObject() {
        toPrompt = UpdatePromptHelper.getCurrentUpdateToPrompt(UpdateToPrompt.Type.CCZ_UPDATE);
    }

    @Override
    String getActionString() {
        return "updating";
    }

    @Override
    protected void setUpTypeSpecificUIComponents() {
        promptTitle.setText(
                Localization.get(inForceMode() ? "ccz.update.required.title" : "ccz.update.available.title"));
        doLaterButton.setText(Localization.get("update.later.button.text"));

        actionButton.setText(Localization.get("ccz.update.action"));
        actionButton.setOnClickListener(v -> launchUpdateActivity());

        imageCue.setVisibility(View.GONE);
    }

    private void launchUpdateActivity() {
        startActivityForResult(new Intent(this, UpdateActivity.class), DO_AN_UPDATE);
    }
}
