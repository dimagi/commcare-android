package org.commcare.activities;

import static org.commcare.activities.LoginActivity.EXTRA_FORCE_SINGLE_APP_MODE;
import static org.commcare.connect.PersonalIdManager.ConnectAppMangement.Connect;
import static org.commcare.connect.PersonalIdManager.ConnectAppMangement.Unmanaged;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.preference.PreferenceManager;

import com.google.android.material.textfield.TextInputLayout;

import org.commcare.CommCareApplication;
import org.commcare.CommCareNoficationManager;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.connect.PersonalIdManager;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.models.database.SqlStorage;
import org.commcare.preferences.DevSessionRestorer;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.utils.MultipleAppsUtil;
import org.commcare.views.CustomBanner;
import org.commcare.views.ManagedUi;
import org.commcare.views.ManagedUiFramework;
import org.commcare.views.PasswordShow;
import org.commcare.views.RectangleButtonWithText;
import org.commcare.views.UiElement;
import org.javarosa.core.services.locale.Localization;

import java.util.ArrayList;
import java.util.Vector;

import javax.annotation.Nullable;

/**
 * Handles login activity UI
 *
 * @author Aliza Stone (astone@dimagi.com)
 */
@ManagedUi(R.layout.screen_login)
public class LoginActivityUIController implements CommCareActivityUIController {

    @UiElement(value = R.id.screen_login_error_view)
    private View errorContainer;

    @UiElement(value = R.id.btn_view_errors_container)
    private View notificationButtonView;

    @UiElement(value = R.id.screen_login_bad_password)
    private TextView errorTextView;

    @UiElement(value = R.id.btn_view_notifications)
    private RectangleButtonWithText notificationButton;

    @UiElement(value = R.id.connect_login_button)
    private Button connectLoginButton;

    @UiElement(value = R.id.username_wrapper)
    private View usernameWrapper;

    @UiElement(value = R.id.username_input_layout)
    private TextInputLayout usernameInputLayout;

    @UiElement(value = R.id.username_icon)
    private ImageView usernameIcon;

    @UiElement(value = R.id.edit_username)
    private AutoCompleteTextView username;

    @UiElement(value = R.id.password_input_layout)
    private TextInputLayout passwordInputLayout;

    @UiElement(value = R.id.password_icon)
    private ImageView passwordIcon;

    @UiElement(value = R.id.password_input_field)
    private View passwordInputField;

    @UiElement(value = R.id.edit_password)
    private EditText passwordOrPin;

    @UiElement(value = R.id.show_password)
    private Button showPasswordButton;

    @UiElement(R.id.screen_login_banner_pane)
    private View banner;

    @UiElement(value = R.id.login_button, locale = "login.button")
    private Button loginButton;

    @UiElement(value = R.id.restore_session_checkbox)
    private CheckBox restoreSessionCheckbox;

    @UiElement(R.id.app_selection_spinner)
    private Spinner spinner;

    @UiElement(value = R.id.primed_password_message, locale = "login.primed.prompt")
    private TextView loginPrimedMessage;

    @UiElement(value = R.id.login_or)
    private View orDivider;

    @UiElement(value = R.id.login_via_connect)
    private TextView loginViaConnectLabel;

    @UiElement(value = R.id.password_wrapper)
    private View passwordWrapper;

    protected final LoginActivity activity;

    private LoginMode loginMode;

    private boolean manuallySwitchedToPasswordMode;


    private final TextWatcher usernameTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            setStyleDefault();
            checkEnteredUsernameForMatch();
        }
    };

    private final TextWatcher passwordTextWatcher = new TextWatcher() {
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
        this.loginMode = LoginMode.PASSWORD;
    }

    @Override
    public void setupUI() {
        setupUsernameEntryBox();
        setLoginInputFieldsError(false);
        setTextChangeListeners();
        setBannerLayoutLogic();

        loginButton.setOnClickListener(arg0 -> {
            FirebaseAnalyticsUtil.reportLoginClicks();
            activity.initiateLoginAttempt(isRestoreSessionChecked());
        });

        passwordOrPin.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                activity.initiateLoginAttempt(isRestoreSessionChecked());
                return true;
            }
            return false;
        });

        notificationButton.setText(Localization.get("error.button.text"));
        notificationButton.setOnClickListener(
                view -> CommCareNoficationManager.performIntentCalloutToNotificationsView(activity));
        setUpConnectUiListeners();
    }

    private void setUpConnectUiListeners() {
        connectLoginButton.setOnClickListener(arg0 -> activity.handleConnectButtonPress());
        passwordOrPin.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                if (activity.getConnectAppState() != Unmanaged) {
                    setPasswordOrPin("");
                }
                activity.setConnectAppState(Unmanaged);
                refreshConnectView();
            }
        });
    }

    private void setTextChangeListeners() {
        username.addTextChangedListener(usernameTextWatcher);
        passwordOrPin.addTextChangedListener(passwordTextWatcher);
    }

    private void setupUsernameEntryBox() {
        username.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS |
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        usernameInputLayout.setHint(Localization.get("login.username"));
    }

    private void setBannerLayoutLogic() {
        final View activityRootView = activity.findViewById(R.id.screen_login_main);
        activityRootView.getViewTreeObserver().addOnGlobalLayoutListener(
                () -> {
                    int hideAll = getResources().getInteger(
                            R.integer.login_screen_hide_all_cutoff);
                    int hideBanner = getResources().getInteger(
                            R.integer.login_screen_hide_banner_cutoff);
                    int height = activityRootView.getHeight();

                    if (height < hideAll) {
                        banner.setVisibility(View.GONE);
                    } else if (height < hideBanner) {
                        banner.setVisibility(View.GONE);
                    } else {
                        banner.setVisibility(View.VISIBLE);
                        updateBanner();
                    }
                });
    }

    @Override
    public void refreshView() {
        updateBanner();
        activity.restoreEnteredTextFromRotation();

        // Decide whether or not to show the app selection spinner based upon # of usable apps
        ArrayList<ApplicationRecord> readyApps = MultipleAppsUtil.getUsableAppRecords();

        ApplicationRecord presetAppRecord = getPresetAppRecord(readyApps);
        if (readyApps.size() == 1 || presetAppRecord != null) {
            // Set this app as the last selected app, for use in choosing what app to initialize on first startup
            ApplicationRecord r = presetAppRecord != null ? presetAppRecord : readyApps.get(0);
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
            prefs.edit().putString(LoginActivity.KEY_LAST_APP, r.getUniqueId()).apply();
            if (shouldForceSingleAppMode()) {
                setSingleAppUIState();
            } else {
                activity.populateAppSpinner(readyApps);
            }
            activity.seatAppIfNeeded(r.getUniqueId());
        } else {
            activity.populateAppSpinner(readyApps);
        }

        // Update checkbox visibility
        if (DevSessionRestorer.savedSessionPresent()) {
            restoreSessionCheckbox.setVisibility(View.VISIBLE);
        } else {
            restoreSessionCheckbox.setVisibility(View.GONE);
        }

        if (activity.checkForSeatedAppChange()) {
            refreshForNewApp();
        } else {
            checkEnteredUsernameForMatch();
        }
        activity.evaluateConnectAppState();
        refreshConnectView();
        if (!CommCareApplication.notificationManager().messagesForCommCareArePending()) {
            notificationButtonView.setVisibility(View.GONE);
        }
    }

    private boolean shouldForceSingleAppMode() {
        return activity.getIntent().getBooleanExtra(EXTRA_FORCE_SINGLE_APP_MODE, true);
    }

    @Nullable
    private ApplicationRecord getPresetAppRecord(ArrayList<ApplicationRecord> readyApps) {
        String presetAppId = activity.getPresetAppID();
        if (presetAppId != null) {
            for (ApplicationRecord readyApp : readyApps) {
                if (readyApp.getUniqueId().equals(presetAppId)) {
                    return readyApp;
                }
            }

            // if preset App id is supplied but not found show an error
            String appNotFoundError = activity.getString(R.string.app_with_id_not_found);
            setErrorMessageUI(appNotFoundError, false);
        }
        return null;
    }

    protected void refreshForNewApp() {
        // Remove any error content from trying to log into a different app
        setStyleDefault();

        final SharedPreferences prefs = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        String lastUser = prefs.getString(HiddenPreferences.LAST_LOGGED_IN_USER, null);
        if (lastUser != null) {
            // If there was a last user for this app, show it
            username.setText(lastUser);
            requestFocusIfNoError(passwordOrPin);
        } else {
            // Otherwise, clear the username text so it does not show a username from a different app
            username.setText("");
            requestFocusIfNoError(username);
        }

        // Since the entered username may have changed, need to re-check if we should be in PIN mode
        checkEnteredUsernameForMatch();

        // Clear any password text that was entered for a different app
        passwordOrPin.setText("");

        // Refresh the breadcrumb bar for new app name
        activity.refreshActionBar();

        // Refresh UI for potential new language
        ManagedUiFramework.loadUiElements(activity);
        usernameInputLayout.setHint(Localization.get("login.username"));
    }

    private void requestFocusIfNoError(EditText view) {
        if (!activity.isShowingGlobalError()) {
            view.requestFocus();
        }
    }

    private void checkEnteredUsernameForMatch() {
        UserKeyRecord matchingRecord = getActiveRecordForUsername(getEnteredUsername());
        if (matchingRecord != null) {
            setExistingUserMode(matchingRecord);
        } else {
            setNewUserMode();
        }
    }

    /**
     * @return the active UKR for the given username, or null if none exists
     */
    private static UserKeyRecord getActiveRecordForUsername(String username) {
        SqlStorage<UserKeyRecord> existingUsers =
                CommCareApplication.instance().getCurrentApp().getStorage(UserKeyRecord.class);

        // Even though we don't allow multiple users with same username in a domain, there can be
        // multiple UKRs for 1 user (for ex if password changes)
        Vector<UserKeyRecord> matchingRecords = existingUsers.
                getRecordsForValue(UserKeyRecord.META_USERNAME, username);

        // However, we guarantee that there will be at most 1 record marked ACTIVE per username
        for (UserKeyRecord record : matchingRecords) {
            if (record.isActive()) {
                return record;
            }
        }
        return null;
    }

    private void setExistingUserMode(UserKeyRecord existingRecord) {
        if (existingRecord.isPrimedForNextLogin()) {
            // Primed login takes precedence (meaning if a record has a PIN set AND is primed for
            // next login, we show primed mode rather than PIN mode)
            setPrimedLoginMode();
        } else if (existingRecord.hasPinSet()) {
            setPinPasswordMode();
        } else {
            setNormalPasswordMode();
        }
    }

    private void setNewUserMode() {
        setNormalPasswordMode();
    }

    private void setPrimedLoginMode() {
        loginMode = LoginMode.PRIMED;
        loginPrimedMessage.setVisibility(View.VISIBLE);
        setPasswordInputFieldVisible(false);
        manuallySwitchedToPasswordMode = false;

        // Switch focus to a dummy (invisible) LinearLayout so that the keyboard doesn't show
        View dummyView = activity.findViewById(R.id.dummy_focusable_view);
        dummyView.requestFocus();
    }

    protected void setNormalPasswordMode() {
        loginMode = LoginMode.PASSWORD;
        loginPrimedMessage.setVisibility(View.GONE);
        setPasswordInputFieldVisible(true);
        passwordInputLayout.setHint(Localization.get("login.password"));
        passwordOrPin.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new PasswordShow(showPasswordButton, passwordOrPin).setupPasswordVisibility();
        manuallySwitchedToPasswordMode = false;
    }

    private void setPinPasswordMode() {
        loginMode = LoginMode.PIN;
        loginPrimedMessage.setVisibility(View.GONE);
        setPasswordInputFieldVisible(true);
        passwordInputLayout.setHint(Localization.get("login.pin.password"));
        passwordOrPin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        manuallySwitchedToPasswordMode = false;
    }

    private void setPasswordInputFieldVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        passwordIcon.setVisibility(visibility);
        passwordInputField.setVisibility(visibility);
    }

    protected void manualSwitchToPasswordMode() {
        setNormalPasswordMode();
        setStyleDefault();
        setPasswordOrPin("");
        manuallySwitchedToPasswordMode = true;
    }

    protected boolean userManuallySwitchedToPasswordMode() {
        return manuallySwitchedToPasswordMode;
    }

    protected LoginMode getLoginMode() {
        return loginMode;
    }

    protected void setErrorMessageUI(String message, boolean showNotificationButton) {
        setLoginInputFieldsError(true);

        errorContainer.setVisibility(View.VISIBLE);
        errorTextView.setText(message);
        notificationButtonView.setVisibility(showNotificationButton ? View.VISIBLE : View.GONE);
    }

    private void setLoginInputFieldsError(boolean hasError) {
        int strokeColor;
        int labelColor;
        int iconColor;

        if (hasError) {
            strokeColor = ContextCompat.getColor(activity, R.color.red_600);
            labelColor = strokeColor;
            iconColor = strokeColor;
        } else {
            strokeColor = ContextCompat.getColor(activity, R.color.cornflower_blue);
            labelColor = ContextCompat.getColor(activity, R.color.slate_gray);
            iconColor = ContextCompat.getColor(activity, R.color.neon_blue);
        }

        final ColorStateList iconColors = ColorStateList.valueOf(iconColor);
        final ColorStateList iconTileColors = ColorStateList.valueOf(ColorUtils.setAlphaComponent(iconColor, 51));
        final ColorStateList strokeColors = new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_focused}, new int[]{}},
                new int[]{strokeColor, strokeColor}
        );
        final ColorStateList labelColors = ColorStateList.valueOf(labelColor);

        usernameIcon.setImageTintList(iconColors);
        usernameIcon.setBackgroundTintList(iconTileColors);
        usernameInputLayout.setBoxStrokeColorStateList(strokeColors);
        usernameInputLayout.setDefaultHintTextColor(labelColors);
        usernameInputLayout.setHintTextColor(labelColors);

        passwordIcon.setImageTintList(iconColors);
        passwordIcon.setBackgroundTintList(iconTileColors);
        passwordInputLayout.setBoxStrokeColorStateList(strokeColors);
        passwordInputLayout.setDefaultHintTextColor(labelColors);
        passwordInputLayout.setHintTextColor(labelColors);
    }

    private void setStyleDefault() {
        setLoginInputFieldsError(false);
        if (loginButton.isEnabled()) {
            clearErrorMessage();
        }
    }

    protected void clearErrorMessage() {
        errorContainer.setVisibility(View.GONE);
    }

    private void setSingleAppUIState() {
        spinner.setVisibility(View.GONE);
    }

    protected void setMultipleAppsUiState(ArrayList<String> appNames, int position) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity,
                R.layout.spinner_text_view, appNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(activity);

        spinner.setSelection(position);
        spinner.setVisibility(View.VISIBLE);
    }


    protected void setPermissionsGrantedState() {
        loginButton.setEnabled(true);
        errorContainer.setVisibility(View.GONE);
        errorTextView.setText("");
    }

    protected void setPermissionDeniedState() {
        loginButton.setEnabled(false);
        errorContainer.setVisibility(View.VISIBLE);
        errorTextView.setText(Localization.get("permission.all.denial.message"));
    }

    protected void restoreLastUser() {
        SharedPreferences prefs = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        String lastUser = prefs.getString(HiddenPreferences.LAST_LOGGED_IN_USER, null);
        if (lastUser != null) {
            username.setText(lastUser);
            requestFocusIfNoError(passwordOrPin);
        }
    }

    protected boolean isRestoreSessionChecked() {
        return restoreSessionCheckbox.isChecked();
    }

    protected String getEnteredUsername() {
        return username.getText().toString();
    }

    protected String getEnteredPasswordOrPin() {
        return passwordOrPin.getText().toString();
    }

    protected void setUsername(String s) {
        username.setText(s);
    }

    protected void setPasswordOrPin(String s) {
        passwordOrPin.setText(s);
    }

    private void updateBanner() {
        ImageView topBannerImageView =
                banner.findViewById(R.id.main_top_banner);
        if (!CustomBanner.useCustomBannerFitToActivity(activity, topBannerImageView, CustomBanner.Banner.LOGIN)) {
            topBannerImageView.setImageResource(R.drawable.commcare_by_dimagi);
        }
    }

    private Resources getResources() {
        return activity.getResources();
    }

    private void setConnectButtonVisible(Boolean visible) {
        connectLoginButton.setVisibility(visible ? View.VISIBLE : View.GONE);
        orDivider.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    protected boolean isAppSelectorVisible() {
        return spinner.getVisibility() == View.VISIBLE;
    }

    protected int getSelectedAppIndex() {
        return spinner.getSelectedItemPosition();
    }

    public void setLoginInputsVisibility(boolean visible) {
        usernameWrapper.setVisibility(visible ? View.VISIBLE : View.GONE);
        passwordWrapper.setVisibility(visible ? View.VISIBLE : View.GONE);
        loginViaConnectLabel.setVisibility(visible ? View.GONE : View.VISIBLE);
    }

    protected void refreshConnectView() {
        PersonalIdManager.ConnectAppMangement appState = activity.getConnectAppState();
        if (appState == Unmanaged) {
            loginButton.setText(Localization.get("login.button"));
            passwordInputLayout.setBoxBackgroundColor(ContextCompat.getColor(activity, R.color.white));
            passwordOrPin.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        } else {
            loginButton.setText(activity.getString(R.string.personalid_login_with));
            passwordInputLayout.setBoxBackgroundColor(ContextCompat.getColor(activity, R.color.grey_light));
            passwordOrPin.setText(R.string.personalid_login_via);
            passwordOrPin.clearFocus();
            passwordOrPin.setInputType(InputType.TYPE_CLASS_TEXT);
        }
        setLoginInputsVisibility(appState != Connect);
        setConnectButtonVisible(
                PersonalIdManager.getInstance().isloggedIn() && ConnectUserDatabaseUtil.hasConnectAccess()
        );
    }
}
