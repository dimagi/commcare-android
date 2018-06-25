package org.commcare.activities;

import android.view.View;

import org.commcare.recovery.measures.CommCareReinstallPrompt;
import org.javarosa.core.services.locale.Localization;

public class PromptCCReinstallActivity extends PromptActivity {

    @Override
    void refreshPromptObject() {
        toPrompt = CommCareReinstallPrompt.INSTANCE;
    }

    @Override
    String getActionString() {
        return "reinstalling";
    }

    @Override
    void setUpTypeSpecificUIComponents() {
        promptTitle.setText(
                Localization.get("apk.reinstall.needed.title", getCurrentClientName()));

        actionButton.setText(Localization.get("apk.reinstall.action"));
        actionButton.setOnClickListener(v -> launchCurrentAppOnPlayStore());

        imageCue.setVisibility(View.GONE);
    }

}
