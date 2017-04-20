package org.commcare.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.UnderlineSpan;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.commcare.CommCareHeartbeatManager;
import org.commcare.UpdateToPrompt;
import org.commcare.dalvik.R;
import org.commcare.views.ManagedUi;
import org.javarosa.core.services.locale.Localization;

/**
 * Created by amstone326 on 4/19/17.
 */
public class PromptUpdateActivity extends SessionAwareCommCareActivity {

    private static final int DO_AN_UPDATE = 1;

    private UpdateToPrompt apkUpdate;
    private UpdateToPrompt cczUpdate;

    @Override
    protected void onCreateSessionSafe(Bundle savedInstanceState) {
        super.onCreateSessionSafe(savedInstanceState);
        refreshUpdateToPromptObjects();
        setupUI();
    }

    private void refreshUpdateToPromptObjects() {
        cczUpdate = CommCareHeartbeatManager.getCurrentUpdateToPrompt(false);
        apkUpdate = CommCareHeartbeatManager.getCurrentUpdateToPrompt(true);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == DO_AN_UPDATE) {
            refreshUpdateToPromptObjects();
            if (cczUpdate == null && apkUpdate == null) {
                finish();
            } else {
                updateVisibilities();
            }
        }
    }

    private void setupUI() {
        if (inForceMode()) {
            setContentView(R.layout.force_update_view);
        } else {
            setUpPromptView();
        }
        updateVisibilities();
    }

    private boolean inForceMode() {
        return (cczUpdate != null && cczUpdate.isPastForceByDate())
                || (apkUpdate != null && apkUpdate.isPastForceByDate());
    }

    private void setUpPromptView() {
        setContentView(R.layout.prompt_update_view);

        ((TextView)findViewById(R.id.updates_available_title)).setText(
                Localization.get("updates.available.title"));

        SpannableString content = new SpannableString(Localization.get("update.later.option"));
        content.setSpan(new UnderlineSpan(), 0, content.length(), 0);
        TextView updateLater = (TextView)findViewById(R.id.update_later_option);
        updateLater.setText(content);
        updateLater.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        ((TextView)findViewById(R.id.ccz_update_info_text))
                .setText(Localization.get("prompted.ccz.update.info"));
        Button updateCczButton = (Button)findViewById(R.id.ccz_update_button);
        updateCczButton.setText(Localization.get("prompted.ccz.update.action"));
        updateCczButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchUpdateActivity();
            }
        });


        ((TextView)findViewById(R.id.apk_update_info_text))
                .setText(Localization.get("prompted.apk.update.info"));
        Button updateApkButton = (Button)findViewById(R.id.apk_update_button);
        updateApkButton.setText(Localization.get("prompted.apk.update.action"));
        updateApkButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchCommCareOnPlayStore();
            }
        });
    }

    private void updateVisibilities() {
        if (inForceMode()) {

        } else {
            View cczView = findViewById(R.id.ccz_update_container);
            if (cczUpdate != null) {
                cczView.setVisibility(View.VISIBLE);
            } else {
                cczView.setVisibility(View.GONE);
            }

            View apkView = findViewById(R.id.apk_update_container);
            if (apkUpdate != null) {
                apkView.setVisibility(View.VISIBLE);
            } else {
                apkView.setVisibility(View.GONE);
            }
        }
    }

    private void launchUpdateActivity() {
        startActivityForResult(new Intent(this, UpdateActivity.class), DO_AN_UPDATE);
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
