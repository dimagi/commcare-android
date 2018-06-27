package org.commcare.activities;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.interfaces.PromptItem;
import org.javarosa.core.services.locale.Localization;

/**
 * Activity used to prompt users to update to a newer .apk or app version, or to reinstall CommCare.
 * Will be launched periodically while there is a pending update to prompt.
 *
 * Created by amstone326 on 4/19/17.
 */
public abstract class PromptActivity extends SessionAwareCommCareActivity {

    protected static final int DO_AN_UPDATE = 1;

    public static String FROM_RECOVERY_MEASURE = "from-recovery-measure";

    protected PromptItem toPrompt;

    protected TextView promptTitle;
    protected Button actionButton;
    protected Button doLaterButton;
    protected ImageView imageCue;

    @Override
    public void onCreateSessionSafe(Bundle savedInstanceState) {
        super.onCreateSessionSafe(savedInstanceState);
        if (toPrompt == null) {
            refreshPromptObject();
        }
        if (savedInstanceState == null &&
                !getIntent().getBooleanExtra(FROM_RECOVERY_MEASURE, false)) {
            // on initial activity load only
            toPrompt.incrementTimesSeen();
        }
        setupUI();
    }

    abstract void refreshPromptObject();
    abstract String getActionString();

    private void setupUI() {
        setContentView(R.layout.prompt_view);
        promptTitle = findViewById(R.id.prompt_title);
        actionButton = findViewById(R.id.action_button);
        doLaterButton = findViewById(R.id.do_later_button);
        imageCue = findViewById(R.id.image_cue);

        TextView helpText = findViewById(R.id.help_text);
        helpText.setText(Localization.get("prompt.help.text", getActionString()));

        doLaterButton.setOnClickListener(v -> PromptActivity.this.finish());

        if (inForceMode()) {
            promptTitle.setTextColor(getResources().getColor(R.color.cc_attention_negative_color));
            promptTitle.setTypeface(Typeface.DEFAULT_BOLD);
        }

        setUpTypeSpecificUIComponents();
        updateVisibilities();
    }

    abstract void setUpTypeSpecificUIComponents();

    @Override
    public void onActivityResultSessionSafe(int requestCode, int resultCode, Intent intent) {
        if (requestCode == DO_AN_UPDATE) {
            refreshPromptObject();
            if (toPrompt == null) {
                finish();
            } else {
                updateVisibilities();
            }
        }
    }

    private void updateVisibilities() {
        if (inForceMode()) {
            doLaterButton.setVisibility(View.GONE);
        } else {
            doLaterButton.setVisibility(View.VISIBLE);
        }
    }

    protected void launchCurrentAppOnPlayStore() {
        final String appPackageName = getPackageName();
        try {
            startActivityForResult(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=" + appPackageName)), DO_AN_UPDATE);
        } catch (android.content.ActivityNotFoundException e) {
            startActivityForResult(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)), DO_AN_UPDATE);
        }
    }

    protected String getCurrentClientName() {
        return (BuildConfig.APPLICATION_ID.equals("org.commcare.lts") ? "CommCare LTS" : "CommCare");
    }

    protected boolean inForceMode() {
        if (toPrompt == null) {
            refreshPromptObject();
        }
        return toPrompt.isForced();
    }

    @Override
    public void onBackPressed() {
        // Prevent navigating away from this activity if we're in force mode
        if (!inForceMode() || getIntent().getBooleanExtra(FROM_RECOVERY_MEASURE, false)) {
            super.onBackPressed();
        }
    }

    @Override
    public boolean isBackEnabled() {
        return !inForceMode();
    }

}
