package org.commcare.activities;

import static org.commcare.connect.ConnectIDManager.ConnectAppMangement.Unmanaged;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
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
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

import org.commcare.CommCareApplication;
import org.commcare.CommCareNoficationManager;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.connect.ConnectIDManager;
import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.models.database.SqlStorage;
import org.commcare.preferences.DevSessionRestorer;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.preferences.LocalePreferences;
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

    @UiElement(value = R.id.edit_username, locale = "login.username")
    private AutoCompleteTextView username;

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

    @UiElement(R.id.welcome_msg)
    private TextView welcomeMessage;

    @UiElement(value = R.id.primed_password_message, locale = "login.primed.prompt")
    private TextView loginPrimedMessage;
    @UiElement(value = R.id.connect_login_button)
    private Button connectLoginButton;
    @UiElement(value = R.id.login_or)
    private TextView orLabel;

    @UiElement(value = R.id.login_via_connect)
    private TextView loginViaConnectLabel;

    @UiElement(value = R.id.password_wrapper)
    private RelativeLayout passwordWrapper;

    protected final LoginActivity activity;

    private LoginMode loginMode;

    private boolean manuallySwitchedToPasswordMode;
    private ConnectIDManager.ConnectAppMangement connectAppState;


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
        this.connectAppState = Unmanaged;
    }

    @Override
    public void setupUI() {
        setupUsernameEntryBox();
        setLoginBoxesColorNormal();
        setTextChangeListeners();
        setBannerLayoutLogic();

        loginButton.setOnClickListener(arg0 -> activity.initiateLoginAttempt(isRestoreSessionChecked()));

        passwordOrPin.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                activity.initiateLoginAttempt(isRestoreSessionChecked());
                return true;
            }
            return false;
        });

        notificationButton.setText(Localization.get("error.button.text"));
        notificationButton.setOnClickListener(view -> CommCareNoficationManager.performIntentCalloutToNotificationsView(activity));
        setUpConnectUiListeners();
    }

    private void setUpConnectUiListeners() {
        connectLoginButton.setOnClickListener(arg0 -> activity.handleConnectButtonPress());
        passwordOrPin.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                setConnectIdLoginState(Unmanaged);
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
        username.setHint(Localization.get("login.username"));
    }

    private void setBannerLayoutLogic() {
        final View activityRootView = activity.findViewById(R.id.screen_login_main);
        final SharedPreferences prefs = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        activityRootView.getViewTreeObserver().addOnGlobalLayoutListener(
                () -> {
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
        boolean noApps = readyApps.isEmpty();
        if (readyApps.size() == 1 || presetAppRecord != null) {
            // Set this app as the last selected app, for use in choosing what app to initialize
            // on first startup
            ApplicationRecord r = presetAppRecord != null ? presetAppRecord : readyApps.get(0);
            setLoginInputsVisibility(!noApps || !(ConnectIDManager.getInstance().isLoggedInWithConnectApp(activity, r.getUniqueId())));
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
            prefs.edit().putString(LoginActivity.KEY_LAST_APP, r.getUniqueId()).apply();
            setSingleAppUIState();
            activity.seatAppIfNeeded(r.getUniqueId());
        } else {
            activity.populateAppSpinner(readyApps);
        }

        // Not using this for now, but may turn back on later
        //refreshUsernamesAdapter();

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
        if (!CommCareApplication.notificationManager().messagesForCommCareArePending()) {
            notificationButtonView.setVisibility(View.GONE);
        }
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
            passwordOrPin.requestFocus();
        } else {
            // Otherwise, clear the username text so it does not show a username from a different app
            username.setText("");
            username.requestFocus();
        }

        // Since the entered username may have changed, need to re-check if we should be in PIN mode
        checkEnteredUsernameForMatch();

        // Clear any password text that was entered for a different app
        passwordOrPin.setText("");

        // Refresh the breadcrumb bar for new app name
        activity.refreshActionBar();

        // Refresh UI for potential new language
        ManagedUiFramework.loadUiElements(activity);

        // Refresh welcome msg separately bc cannot set a single locale for its UiElement
        welcomeMessage.setText(Localization.get("login.welcome.multiple"));
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
        passwordOrPin.setVisibility(View.GONE);
        manuallySwitchedToPasswordMode = false;

        // Switch focus to a dummy (invisible) LinearLayout so that the keyboard doesn't show
        View dummyView = activity.findViewById(R.id.dummy_focusable_view);
        dummyView.requestFocus();
    }

    protected void setNormalPasswordMode() {
        loginMode = LoginMode.PASSWORD;
        loginPrimedMessage.setVisibility(View.GONE);
        passwordOrPin.setVisibility(View.VISIBLE);
        passwordOrPin.setHint(Localization.get("login.password"));
        passwordOrPin.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new PasswordShow(showPasswordButton, passwordOrPin).setupPasswordVisibility();
        manuallySwitchedToPasswordMode = false;
    }

    private void setPinPasswordMode() {
        loginMode = LoginMode.PIN;
        loginPrimedMessage.setVisibility(View.GONE);
        passwordOrPin.setVisibility(View.VISIBLE);
        passwordOrPin.setHint(Localization.get("login.pin.password"));
        passwordOrPin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        manuallySwitchedToPasswordMode = false;
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
        setLoginBoxesColorError();

        username.setCompoundDrawablesWithIntrinsicBounds(getResources().getDrawable(R.drawable.icon_user_attnneg), null, null, null);
        passwordOrPin.setCompoundDrawablesWithIntrinsicBounds(getResources().getDrawable(R.drawable.icon_lock_attnneg), null, null, null);

        errorContainer.setVisibility(View.VISIBLE);
        errorTextView.setText(message);
        notificationButtonView.setVisibility(showNotificationButton ? View.VISIBLE : View.GONE);
    }

    private void setLoginBoxesColorNormal() {
        int normalColor = getResources().getColor(R.color.login_edit_text_color);
        username.setTextColor(normalColor);
        passwordOrPin.setTextColor(normalColor);
    }

    private void setLoginBoxesColorError() {
        int errorColor = getResources().getColor(R.color.login_edit_text_color_error);
        username.setTextColor(errorColor);
        passwordOrPin.setTextColor(errorColor);
    }

    private void setStyleDefault() {
        setLoginBoxesColorNormal();
        Drawable usernameDrawable = getResources().getDrawable(R.drawable.icon_user_neutral50);
        Drawable passwordDrawable = getResources().getDrawable(R.drawable.icon_lock_neutral50);
        if (LocalePreferences.isLocaleRTL()) {
            username.setCompoundDrawablesWithIntrinsicBounds(null, null, usernameDrawable, null);
            passwordOrPin.setCompoundDrawablesWithIntrinsicBounds(null, null, passwordDrawable, null);
        } else {
            username.setCompoundDrawablesWithIntrinsicBounds(usernameDrawable, null, null, null);
            passwordOrPin.setCompoundDrawablesWithIntrinsicBounds(passwordDrawable, null, null, null);
        }
        if (loginButton.isEnabled()) {
            clearErrorMessage();
        }
    }

    protected void clearErrorMessage() {
        errorContainer.setVisibility(View.GONE);
    }

    private void setSingleAppUIState() {
        spinner.setVisibility(View.GONE);
        welcomeMessage.setText(Localization.get("login.welcome.single"));
    }

    protected void setMultipleAppsUiState(ArrayList<String> appNames, int position) {
        welcomeMessage.setText(Localization.get("login.welcome.multiple"));

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
            passwordOrPin.requestFocus();
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

    protected boolean loginManagedByConnectId() {
        return connectAppState == ConnectIDManager.ConnectAppMangement.ConnectId ||
                connectAppState == ConnectIDManager.ConnectAppMangement.Connect;
    }

    public void setConnectButtonVisible(Boolean visible) {
        connectLoginButton.setVisibility(visible ? View.VISIBLE : View.GONE);
        orLabel.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    protected boolean isAppSelectorVisible() {
        return spinner.getVisibility() == View.VISIBLE;
    }

    protected int getSelectedAppIndex() {
        return spinner.getSelectedItemPosition();
    }

    public void setLoginInputsVisibility(boolean visible) {
        username.setVisibility(visible ? View.VISIBLE : View.GONE);
        passwordWrapper.setVisibility(visible ? View.VISIBLE : View.GONE);
        loginViaConnectLabel.setVisibility(visible ? View.GONE : View.VISIBLE);
    }

    protected void setConnectIdLoginState(ConnectIDManager.ConnectAppMangement appState) {
        boolean unmanaged = appState == Unmanaged;
        if (unmanaged &&
                connectAppState != Unmanaged) {
            setPasswordOrPin("");
        }

        connectAppState = appState;

        boolean connect = connectAppState == ConnectIDManager.ConnectAppMangement.Connect;
        setLoginInputsVisibility(!connect);

        String text;
        if (unmanaged) {
            text = Localization.get("login.button");
        } else {
            text = activity.getString(R.string.login_button_connectid);
        }
        loginButton.setText(text);

        //handle language changes from system setttings
        connectLoginButton.setText(activity.getString(R.string.connect_button_logged_in));

        passwordOrPin.setBackgroundColor(getResources().getColor(unmanaged ? R.color.white : R.color.grey_light));
        if (!unmanaged) {
            passwordOrPin.setText(R.string.login_password_by_connect);
            passwordOrPin.clearFocus();
        }
        passwordOrPin.setInputType(unmanaged ?
                (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD) :
                InputType.TYPE_CLASS_TEXT);
    }
}
