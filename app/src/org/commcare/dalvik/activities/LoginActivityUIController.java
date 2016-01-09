package org.commcare.dalvik.activities;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.StateSet;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.framework.CommCareActivityUIController;
import org.commcare.android.framework.ManagedUi;
import org.commcare.android.framework.ManagedUiFramework;
import org.commcare.android.framework.UiElement;
import org.commcare.android.session.DevSessionRestorer;
import org.commcare.android.ui.CustomBanner;
import org.commcare.android.util.MediaUtil;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.javarosa.core.services.locale.Localization;

import java.util.ArrayList;

/**
 * Handles login activity UI
 *
 * @author Aliza Stone (astone@dimagi.com)
 */
@ManagedUi(R.layout.screen_login)
public class LoginActivityUIController implements CommCareActivityUIController {

    @UiElement(value= R.id.screen_login_bad_password)
    private TextView errorBox;

    @UiElement(value=R.id.edit_username, locale="login.username")
    private EditText username;

    @UiElement(value=R.id.edit_password, locale="login.password")
    private EditText password;

    @UiElement(R.id.screen_login_banner_pane)
    private View banner;

    @UiElement(value=R.id.login_button, locale="login.button")
    private Button loginButton;

    @UiElement(value=R.id.restore_session_checkbox)
    private CheckBox restoreSessionCheckbox;

    @UiElement(R.id.app_selection_spinner)
    private Spinner spinner;

    @UiElement(R.id.welcome_msg)
    private TextView welcomeMessage;

    private LoginActivity activity;

    private final TextWatcher textWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
        @Override
        public void afterTextChanged(Editable s) {
            setStyleDefault();
        }
    };

    public LoginActivityUIController(LoginActivity activity) {
        this.activity = activity;
    }

    @Override
    public void setupUI() {

        username.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS |
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);

        setLoginBoxesColorNormal();

        loginButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                activity.initiateLoginAttempt(isRestoreSessionChecked());
            }
        });

        username.addTextChangedListener(textWatcher);
        password.addTextChangedListener(textWatcher);

        username.setHint(Localization.get("login.username"));
        password.setHint(Localization.get("login.password"));

        final View activityRootView = activity.findViewById(R.id.screen_login_main);
        final SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
        activityRootView.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {

                    @Override
                    public void onGlobalLayout() {
                        int hideAll = getResources().getInteger(
                                R.integer.login_screen_hide_all_cuttoff);
                        int hideBanner = getResources().getInteger(
                                R.integer.login_screen_hide_banner_cuttoff);
                        int height = activityRootView.getHeight();

                        if (height < hideAll) {
                            banner.setVisibility(View.GONE);
                        } else if (height < hideBanner) {
                            banner.setVisibility(View.GONE);
                        } else {
                            // Override default CommCare banner if requested
                            String customBannerURI = prefs.getString(
                                    CommCarePreferences.BRAND_BANNER_LOGIN, "");
                            if (!"".equals(customBannerURI)) {
                                Bitmap bitmap = MediaUtil.inflateDisplayImage(activity, customBannerURI);
                                if (bitmap != null) {
                                    ImageView bannerView =
                                            (ImageView) banner.findViewById(R.id.main_top_banner);
                                    bannerView.setImageBitmap(bitmap);
                                }
                            }
                            banner.setVisibility(View.VISIBLE);
                        }
                    }
                });

    }

    @Override
    public void refreshView() {
        // In case the seated app has changed since last time we were in LoginActivity
        refreshForNewApp();

        updateBanner();

        activity.restoreEnteredTextFromRotation();

        // Decide whether or not to show the app selection spinner based upon # of usable apps
        ArrayList<ApplicationRecord> readyApps = CommCareApplication._().getUsableAppRecords();

        if (readyApps.size() == 1) {
            // Set this app as the last selected app, for use in choosing what app to initialize
            // on first startup
            ApplicationRecord r = readyApps.get(0);
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
            prefs.edit().putString(LoginActivity.KEY_LAST_APP, r.getUniqueId()).commit();

            setSingleAppUIState();
        } else {
            activity.populateAppSpinner(readyApps);
        }
    }

    public void refreshForNewApp() {
        // Remove any error content from trying to log into a different app
        setStyleDefault();

        final SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
        String lastUser = prefs.getString(CommCarePreferences.LAST_LOGGED_IN_USER, null);
        if (lastUser != null) {
            // If there was a last user for this app, show it
            username.setText(lastUser);
            password.requestFocus();
        } else {
            // Otherwise, clear the username text so it does not show a username from a different app
            username.setText("");
            username.requestFocus();
        }

        // Clear any password text that was entered for a different app
        password.setText("");

        // Refresh the breadcrumb bar for new app name
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            activity.refreshActionBar();
        }

        // Refresh UI for potential new language
        ManagedUiFramework.loadUiElements(activity);

        // Refresh welcome msg separately bc cannot set a single locale for its UiElement
        welcomeMessage.setText(Localization.get("login.welcome.multiple"));

        // Update checkbox visibility
        if (DevSessionRestorer.savedSessionPresent()) {
            restoreSessionCheckbox.setVisibility(View.VISIBLE);
        } else {
            restoreSessionCheckbox.setVisibility(View.GONE);
        }
    }

    public void setErrorMessageUI(String message) {
        setLoginBoxesColorError();

        username.setCompoundDrawablesWithIntrinsicBounds(getResources().getDrawable(R.drawable.icon_user_attnneg), null, null, null);
        password.setCompoundDrawablesWithIntrinsicBounds(getResources().getDrawable(R.drawable.icon_lock_attnneg), null, null, null);
        loginButton.setBackgroundColor(getResources().getColor(R.color.cc_attention_negative_bg));
        loginButton.setTextColor(getResources().getColor(R.color.cc_attention_negative_text));

        errorBox.setVisibility(View.VISIBLE);
        errorBox.setText(message);
    }

    private void setLoginBoxesColorNormal() {
        int normalColor = getResources().getColor(R.color.login_edit_text_color);
        username.setTextColor(normalColor);
        password.setTextColor(normalColor);
    }

    private void setLoginBoxesColorError() {
        int errorColor = getResources().getColor(R.color.login_edit_text_color_error);
        username.setTextColor(errorColor);
        password.setTextColor(errorColor);
    }

    public void setStyleDefault() {
        setLoginBoxesColorNormal();
        username.setCompoundDrawablesWithIntrinsicBounds(getResources().getDrawable(R.drawable.icon_user_neutral50), null, null, null);
        password.setCompoundDrawablesWithIntrinsicBounds(getResources().getDrawable(R.drawable.icon_lock_neutral50), null, null, null);
        setupLoginButton();
        if (loginButton.isEnabled()) {
            // don't hide error box when showing permission error
            errorBox.setVisibility(View.GONE);
        }
    }

    public void clearErrorMessage() {
        errorBox.setVisibility(View.GONE);
    }

    public void setSingleAppUIState() {
        spinner.setVisibility(View.GONE);
        welcomeMessage.setText(Localization.get("login.welcome.single"));
    }

    public void setMultipleAppsUIState(ArrayList<String> appNames, int position) {
        welcomeMessage.setText(Localization.get("login.welcome.multiple"));

        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity,
                R.layout.spinner_text_view, appNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(activity);

        spinner.setSelection(position);
        spinner.setVisibility(View.VISIBLE);
    }

    public void setPermissionsGrantedState() {
        loginButton.setEnabled(true);
        errorBox.setVisibility(View.GONE);
        errorBox.setText("");
    }

    public void setPermissionDeniedState() {
        loginButton.setEnabled(false);
        errorBox.setVisibility(View.VISIBLE);
        errorBox.setText(Localization.get("permission.all.denial.message"));
    }

    private void setupLoginButton() {
        ColorDrawable colorDrawable = new ColorDrawable(getResources().getColor(R.color.cc_brand_color));
        ColorDrawable disabledColor = new ColorDrawable(getResources().getColor(R.color.grey));

        StateListDrawable sld = new StateListDrawable();

        sld.addState(new int[]{-android.R.attr.state_enabled}, disabledColor);
        sld.addState(StateSet.WILD_CARD, colorDrawable);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            loginButton.setBackground(sld);
        } else {
            loginButton.setBackgroundDrawable(sld);
        }

        loginButton.setTextColor(getResources().getColor(R.color.cc_neutral_bg));
    }

    public void restoreLastUser() {
        SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
        String lastUser = prefs.getString(CommCarePreferences.LAST_LOGGED_IN_USER, null);
        if (lastUser != null) {
            username.setText(lastUser);
            password.requestFocus();
        }
    }

    public boolean isRestoreSessionChecked() {
        return restoreSessionCheckbox.isChecked();
    }

    public String getEnteredUsername() {
        return username.getText().toString();
    }

    public String getEnteredPassword() {
        return password.getText().toString();
    }

    public void setUsername(String s) {
        username.setText(s);
    }

    public void setPassword(String s) {
        password.setText(s);
    }

    public void updateBanner() {
        ImageView topBannerImageView =
                (ImageView)banner.findViewById(org.commcare.dalvik.R.id.main_top_banner);
        if (!CustomBanner.useCustomBannerFitToActivity(activity, topBannerImageView)) {
            topBannerImageView.setImageResource(R.drawable.commcare_logo);
        }
    }

    private Resources getResources() {
        return activity.getResources();
    }
}
