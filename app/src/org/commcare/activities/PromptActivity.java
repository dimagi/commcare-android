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
@ManagedUi(R.layout.prompt_view)
public abstract class PromptActivity extends CommCareActivity {

    protected static final int DO_AN_UPDATE = 1;

    public static String FROM_RECOVERY_MEASURE = "from-recovery-measure";

    protected PromptItem toPrompt;

    @UiElement(value = R.id.prompt_title)
    protected TextView promptTitle;
    @UiElement(value = R.id.action_button)
    protected Button actionButton;
    @UiElement(value = R.id.do_later_button)
    protected Button doLaterButton;
    @UiElement(value = R.id.image_cue)
    protected ImageView imageCue;
    @UiElement(value = R.id.instructions)
    protected TextView instructions;
    @UiElement(value = R.id.help_text)
    protected TextView helpText;

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
        String appPackageName = getPackageName();
        if (BuildConfig.DEBUG) {
            appPackageName = appPackageName.replace(".debug", "");
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
        return getString(R.string.app_name);
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
        if (!inForceMode()) {
            super.onBackPressed();
        }
    }

    @Override
    public boolean isBackEnabled() {
        return !inForceMode();
    }

}
