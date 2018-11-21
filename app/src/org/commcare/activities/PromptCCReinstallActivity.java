package org.commcare.activities;

import android.view.View;

import org.commcare.AppUtils;
import org.commcare.recovery.measures.CommCareReinstallPrompt;
import org.javarosa.core.services.locale.Localization;

public class PromptCCReinstallActivity extends PromptActivity {

    @Override
    void refreshPromptObject() {
        if (AppUtils.notOnLatestCCVersion()) {
            toPrompt = CommCareReinstallPrompt.INSTANCE;
        } else {
            toPrompt = null;
        }
    }

    @Override
    String getHelpTextResource() {
        return "reinstall.prompt.help.text";
    }

    @Override
    void setUpTypeSpecificUIComponents() {
        promptTitle.setText(
                Localization.get("apk.reinstall.needed.title", getCurrentClientName()));

        actionButton.setText(Localization.get("apk.reinstall.action"));
        actionButton.setOnClickListener(v -> launchCurrentAppOnPlayStore());

        imageCue.setVisibility(View.GONE);
    }

    @Override
    String getInstructionsStringKey() {
        return "apk.reinstall.instructions";
    }

}
