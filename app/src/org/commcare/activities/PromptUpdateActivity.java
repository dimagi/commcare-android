package org.commcare.activities;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.UnderlineSpan;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.commcare.heartbeat.UpdatePromptFieldTesting;
import org.commcare.heartbeat.UpdateToPrompt;
import org.commcare.dalvik.R;
import org.commcare.utils.ConnectivityStatus;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;
import org.javarosa.core.services.locale.Localization;

/**
 * Activity used to prompt users to update to a newer .apk or app version. Will be launched
 * periodically while there is a pending update to prompt.
 *
 * Created by amstone326 on 4/19/17.
 */
@ManagedUi(R.layout.prompt_update_view)
public class PromptUpdateActivity extends SessionAwareCommCareActivity {

    private static final int DO_AN_UPDATE = 1;

    private UpdateToPrompt apkUpdate;
    private UpdateToPrompt cczUpdate;

    @UiElement(value = R.id.updates_available_title)
    private TextView updatesAvailableTitle;

    @UiElement(value = R.id.update_later_option)
    private TextView updateLaterText;

    @UiElement(value = R.id.connection_needed_message, locale = "connectivity.needed.for.updates")
    private TextView connectivityNeededMsg;

    @UiElement(value = R.id.ccz_update_info_text)
    private TextView cczUpdateInfoText;

    @UiElement(value = R.id.ccz_update_button)
    private Button updateCczButton;

    @UiElement(value = R.id.apk_update_info_text)
    private TextView apkUpdateInfoText;

    @UiElement(value = R.id.apk_update_button)
    private Button updateApkButton;

    @Override
    protected void onCreateSessionSafe(Bundle savedInstanceState) {
        super.onCreateSessionSafe(savedInstanceState);
        if (!UpdatePromptFieldTesting.ALREADY_PERFORMED_CCZ_UPDATE) {
            cczUpdate = UpdatePromptFieldTesting.generateRandomUpdateToPrompt(false, false);
        }
        apkUpdate = UpdatePromptFieldTesting.generateRandomUpdateToPrompt(true,
                !UpdatePromptFieldTesting.ALREADY_PERFORMED_CCZ_UPDATE);
        setupUI();
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

    private void refreshUpdateToPromptObjects() {
        if (cczUpdate != null && !cczUpdate.isNewerThanCurrentVersion()) {
            cczUpdate = null;
        }
        if (apkUpdate != null && !apkUpdate.isNewerThanCurrentVersion()) {
            apkUpdate = null;
        }
    }

    private void setupUI() {
        setUpComponents();
        updateVisibilities();
    }

    private void setUpComponents() {
        updatesAvailableTitle.setText(
                Localization.get(inForceMode() ? "update.required.title" : "updates.available.title"));

        setUpUpdateLaterMessage();

        cczUpdateInfoText.setText(Localization.get(
                cczUpdateIsForced() ? "forced.ccz.update.info" : "prompted.ccz.update.info"));
        updateCczButton.setText(Localization.get("ccz.update.action"));
        updateCczButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchUpdateActivity();
            }
        });
        if (cczUpdateIsForced()) {
            cczUpdateInfoText.setTextColor(getResources().getColor(R.color.cc_attention_negative_color));
            cczUpdateInfoText.setTypeface(Typeface.DEFAULT_BOLD);
        }

        apkUpdateInfoText.setText(Localization.get(
                apkUpdateIsForced() ? "forced.apk.update.info" : "prompted.apk.update.info"));
        updateApkButton.setText(Localization.get("apk.update.action"));
        updateApkButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchCommCareOnPlayStore();
            }
        });
        if (apkUpdateIsForced()) {
            apkUpdateInfoText.setTextColor(getResources().getColor(R.color.cc_attention_negative_color));
            apkUpdateInfoText.setTypeface(Typeface.DEFAULT_BOLD);
        }
    }

    private void setUpUpdateLaterMessage() {
        String updateLaterTextKey;
        if (ConnectivityStatus.isNetworkAvailable(this)) {
            updateLaterTextKey = "update.later.option";
        } else {
            updateLaterTextKey = "back.for.now";
        }
        SpannableString content = new SpannableString(Localization.get(updateLaterTextKey));
        content.setSpan(new UnderlineSpan(), 0, content.length(), 0);

        if (!ConnectivityStatus.isNetworkAvailable(this)) {
            updateLaterText.setTextColor(getResources().getColor(R.color.cc_attention_negative_text));
        }

        updateLaterText.setText(content);
        updateLaterText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    private void updateVisibilities() {
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

        if (!inForceMode() || !ConnectivityStatus.isNetworkAvailable(this)) {
            updateLaterText.setVisibility(View.VISIBLE);
        } else {
            updateLaterText.setVisibility(View.GONE);
        }

        if (!ConnectivityStatus.isNetworkAvailable(this)) {
            connectivityNeededMsg.setVisibility(View.VISIBLE);
        } else {
            connectivityNeededMsg.setVisibility(View.GONE);
        }
    }

    private boolean inForceMode() {
        return cczUpdateIsForced() || apkUpdateIsForced();
    }

    private boolean cczUpdateIsForced() {
        return cczUpdate != null && cczUpdate.isPastForceByDate();
    }

    private boolean apkUpdateIsForced() {
        return apkUpdate != null && apkUpdate.isPastForceByDate();
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
