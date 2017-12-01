package org.commcare.activities;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.commcare.dalvik.BuildConfig;
import org.commcare.heartbeat.UpdateToPrompt;
import org.commcare.dalvik.R;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;
import org.javarosa.core.services.locale.Localization;

/**
 * Activity used to prompt users to update to a newer .apk or app version. Will be launched
 * periodically while there is a pending update to prompt.
 *
 * Created by amstone326 on 4/19/17.
 */
public abstract class PromptUpdateActivity extends SessionAwareCommCareActivity {

    protected static final int DO_AN_UPDATE = 1;

    protected UpdateToPrompt updateToPrompt;

    protected TextView updatesAvailableTitle;
    protected Button updateButton;
    private Button updateLaterButton;
    protected ImageView imageCue;
    private TextView helpText;

    @Override
    public void onCreateSessionSafe(Bundle savedInstanceState) {
        super.onCreateSessionSafe(savedInstanceState);
        refreshUpdateToPromptObject();
        setupUI();
    }

    abstract void refreshUpdateToPromptObject();

    private void setupUI() {

        setContentView(R.layout.prompt_update_view);
        updatesAvailableTitle = (TextView)findViewById(R.id.updates_available_title);
        updateButton = (Button)findViewById(R.id.update_button);
        updateLaterButton = (Button)findViewById(R.id.update_later_button);
        imageCue = (ImageView)findViewById(R.id.update_image_cue);
        helpText = (TextView)findViewById(R.id.update_help_text);

        helpText.setText(Localization.get("prompted.update.help.text"));

        updateLaterButton.setText(Localization.get("update.later.button.text"));
        updateLaterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PromptUpdateActivity.this.finish();
            }
        });

        if (inForceMode()) {
            updatesAvailableTitle.setTextColor(getResources().getColor(R.color.cc_attention_negative_color));
            updatesAvailableTitle.setTypeface(Typeface.DEFAULT_BOLD);
        }

        setUpTypeSpecificUIComponents();
        updateVisibilities();
    }

    abstract void setUpTypeSpecificUIComponents();

    @Override
    public void onActivityResultSessionSafe(int requestCode, int resultCode, Intent intent) {
        if (requestCode == DO_AN_UPDATE) {
            refreshUpdateToPromptObject();
            if (updateToPrompt == null) {
                finish();
            } else {
                updateVisibilities();
            }
        }
    }

    private void updateVisibilities() {
        if (inForceMode()) {
            updateLaterButton.setVisibility(View.GONE);
        } else {
            updateLaterButton.setVisibility(View.VISIBLE);
        }
    }

    protected String getCurrentClientName() {
        return (BuildConfig.APPLICATION_ID.equals("org.commcare.lts") ? "CommCare LTS" : "CommCare");
    }

    protected boolean inForceMode() {
        return updateToPrompt.isForced();
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
