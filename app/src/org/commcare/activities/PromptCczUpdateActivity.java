package org.commcare.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import org.commcare.CommCareApplication;
import org.commcare.heartbeat.UpdatePromptHelper;
import org.commcare.heartbeat.UpdateToPrompt;
import org.commcare.utils.SessionUnavailableException;
import org.javarosa.core.services.locale.Localization;

/**
 * Created by amstone326 on 7/11/17.
 */

public class PromptCczUpdateActivity extends PromptActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Mark that we have shown the prompt for this user login
        try {
            CommCareApplication.instance().getSession().setCczUpdatePromptWasShown();
        } catch (SessionUnavailableException e) {
            // we are showing the prompt before user login, so nothing to mark
        }
    }

    @Override
    void refreshPromptObject() {
        toPrompt = UpdatePromptHelper.getCurrentUpdateToPrompt(UpdateToPrompt.Type.CCZ_UPDATE);
    }

    @Override
    String getHelpTextResource() {
        return "ccz.update.prompt.help.text";
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

    @Override
    String getInstructionsStringKey() {
        return null;
    }

    private void launchUpdateActivity() {
        startActivityForResult(new Intent(this, UpdateActivity.class), DO_AN_UPDATE);
    }
}
