package org.commcare.activities;

import android.view.View;

import org.commcare.AppUtils;
import org.commcare.dalvik.R;
import org.commcare.recovery.measures.CommCareReinstallPrompt;
import org.commcare.utils.StringUtils;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.util.NoLocalizedTextException;

public class PromptCCReinstallActivity extends PromptActivity {

    @Override
    void refreshPromptObject() {
        toPrompt = new CommCareReinstallPrompt();

    }

    @Override
    String getHelpTextResource() {
        return "reinstall.prompt.help.text";
    }

    @Override
    void setUpTypeSpecificUIComponents() {
        promptTitle.setText(StringUtils.getStringRobust(this, R.string.reinstall_prompt_title, getCurrentClientName()));
        actionButton.setText(Localization.get("apk.reinstall.action"));
        actionButton.setOnClickListener(v -> launchCurrentAppOnPlayStore());
        imageCue.setVisibility(View.GONE);
    }

    @Override
    String getInstructionsStringKey() {
        return "apk.reinstall.instructions";
    }

    @Override
    protected boolean isUpdateComplete() {
        return !AppUtils.notOnLatestCCVersion();
    }

}
