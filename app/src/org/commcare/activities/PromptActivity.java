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
 *
 * Created by amstone326 on 4/19/17.
 */
public abstract class PromptActivity extends CommCareActivity {

    protected static final int DO_AN_UPDATE = 1;

    public static String FROM_RECOVERY_MEASURE = "from-recovery-measure";

    protected PromptItem toPrompt;

    protected TextView promptTitle;
    protected Button actionButton;
    protected Button doLaterButton;
    protected ImageView imageCue;
    protected TextView instructions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

    private void setupUI() {
        setContentView(R.layout.prompt_view);
        promptTitle = findViewById(R.id.prompt_title);
        actionButton = findViewById(R.id.action_button);
        doLaterButton = findViewById(R.id.do_later_button);
        imageCue = findViewById(R.id.image_cue);
        instructions = findViewById(R.id.instructions);

        if (getInstructionsStringKey() != null) {
            instructions.setText(Localization.get(getInstructionsStringKey()));
        }

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

    abstract void refreshPromptObject();

    abstract String getActionString();

    abstract void setUpTypeSpecificUIComponents();

    abstract String getInstructionsStringKey();

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == DO_AN_UPDATE) {
            refreshPromptObject();
            if (toPrompt == null) {
                finish();
            } else {
                updateVisibilities();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void updateVisibilities() {
        if (inForceMode()) {
            doLaterButton.setVisibility(View.GONE);
        } else {
            doLaterButton.setVisibility(View.VISIBLE);
        }

        if (getInstructionsStringKey() == null) {
            instructions.setVisibility(View.GONE);
        } else {
            instructions.setVisibility(View.VISIBLE);
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
