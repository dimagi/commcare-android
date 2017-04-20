package org.commcare.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.commcare.CommCareHeartbeatManager;
import org.commcare.UpdateToPrompt;
import org.commcare.dalvik.R;
import org.javarosa.core.services.locale.Localization;

/**
 * Created by amstone326 on 4/19/17.
 */

public class PromptUpdateActivity extends SessionAwareCommCareActivity {

    private UpdateToPrompt apkUpdate;
    private UpdateToPrompt cczUpdate;

    @Override
    protected void onCreateSessionSafe(Bundle savedInstanceState) {
        cczUpdate = CommCareHeartbeatManager.getCurrentUpdateToPrompt(false);
        apkUpdate = CommCareHeartbeatManager.getCurrentUpdateToPrompt(true);
        setupUI();
    }

    private void setupUI() {
        if (inForceMode()) {
            setContentView(R.layout.force_update_view);
        } else {
            setUpPromptView();
        }
    }

    private boolean inForceMode() {
        return (cczUpdate != null && cczUpdate.isPastForceByDate())
                || (apkUpdate != null && apkUpdate.isPastForceByDate());
    }

    private void setUpPromptView() {
        setContentView(R.layout.prompt_update_view);

        View cczView = findViewById(R.id.ccz_update_container);
        if (cczUpdate != null) {
            cczView.setVisibility(View.VISIBLE);
            TextView infoText = (TextView)findViewById(R.id.ccz_update_info_text);
            infoText.setText(Localization.get("prompted.ccz.update.info"));
            Button updateButton = (Button)findViewById(R.id.ccz_update_button);
            updateButton.setText(Localization.get("prompted.ccz.update.action"));
        } else {
            cczView.setVisibility(View.GONE);
        }

        View apkView = findViewById(R.id.apk_update_container);
        if (apkUpdate != null) {
            apkView.setVisibility(View.VISIBLE);
            TextView infoText = (TextView)findViewById(R.id.apk_update_info_text);
            infoText.setText(Localization.get("prompted.apk.update.info"));
            Button updateButton = (Button)findViewById(R.id.apk_update_button);
            updateButton.setText(Localization.get("prompted.apk.update.action"));
        } else {
            apkView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onBackPressed() {
        // Prevent navigating away from this activity if we're in force mode
        if (!inForceMode()) {
            super.onBackPressed();
        }
    }

    @Override
    public boolean isBackEnabled() {
        return !inForceMode();
    }

}
