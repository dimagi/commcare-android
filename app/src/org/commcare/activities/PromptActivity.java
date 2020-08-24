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
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;
import org.javarosa.core.services.locale.Localization;

/**
 * Activity used to prompt users to update to a newer .apk or app version, or to reinstall CommCare.
 *
 * Created by amstone326 on 4/19/17.
 */
public abstract class PromptActivity extends CommCareActivity {

    protected static final int DO_AN_UPDATE = 1;

    public static String FROM_RECOVERY_MEASURE = "from-recovery-measure";
    public static final String REQUIRED_VERSION = "required-version";

    protected PromptItem toPrompt;

    protected TextView promptTitle;
    protected Button actionButton;
    protected Button doLaterButton;
    protected ImageView imageCue;
    protected TextView instructions;
    protected TextView helpText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.prompt_view);
        initViews();

        refreshPromptIfNull();
        if (savedInstanceState == null &&
                !getIntent().getBooleanExtra(FROM_RECOVERY_MEASURE, false) &&
                getIntent().getStringExtra(REQUIRED_VERSION) == null) {
            // on initial activity load only
            toPrompt.incrementTimesSeen();
        }
        setupUI();
    }

    private void initViews() {
        promptTitle = findViewById(R.id.prompt_title);
        actionButton = findViewById(R.id.action_button);
        doLaterButton = findViewById(R.id.do_later_button);
        imageCue = findViewById(R.id.image_cue);
        instructions = findViewById(R.id.instructions);
        helpText = findViewById(R.id.help_text);
    }

    private void refreshPromptIfNull() {
        if (toPrompt == null) {
            refreshPromptObject();
        }
    }

    private void setupUI() {

         if (getInstructionsStringKey() != null) {
            instructions.setText(Localization.get(getInstructionsStringKey()));
        }

        helpText.setText(Localization.get(getHelpTextResource()));
        doLaterButton.setOnClickListener(v -> PromptActivity.this.finish());

        if (inForceMode()) {
            promptTitle.setTextColor(getResources().getColor(R.color.cc_attention_negative_color));
            promptTitle.setTypeface(Typeface.DEFAULT_BOLD);
        }

        setUpTypeSpecificUIComponents();
        updateVisibilities();
    }

    abstract void refreshPromptObject();

    abstract String getHelpTextResource();

    abstract void setUpTypeSpecificUIComponents();

    abstract String getInstructionsStringKey();

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == DO_AN_UPDATE) {
            if (isUpdateComplete()) {
                finish();
            } else {
                refreshPromptObject();
                updateVisibilities();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    protected abstract boolean isUpdateComplete();

    protected void updateVisibilities() {
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
        String appPackageName = getPackageName();
        if (BuildConfig.DEBUG) {
            appPackageName = "org.commcare.dalvik";
        }
        try {
            startActivityForResult(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=" + appPackageName)), DO_AN_UPDATE);
        } catch (android.content.ActivityNotFoundException e) {
            startActivityForResult(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)), DO_AN_UPDATE);
        }
    }

    protected String getCurrentClientName() {
        return getString(R.string.application_name);
    }

    protected boolean inForceMode() {
        if (toPrompt != null) {
            return toPrompt.isForced();
        }
        return false;
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
