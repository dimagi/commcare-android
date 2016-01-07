package org.commcare.dalvik.activities;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Pair;
import android.util.StateSet;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.database.user.DemoUserBuilder;
import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.framework.ManagedUi;
import org.commcare.android.framework.ManagedUiFramework;
import org.commcare.android.framework.Permissions;
import org.commcare.android.framework.RuntimePermissionRequester;
import org.commcare.android.framework.UiElement;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.models.notifications.MessageTag;
import org.commcare.android.models.notifications.NotificationMessage;
import org.commcare.android.models.notifications.NotificationMessageFactory;
import org.commcare.android.models.notifications.NotificationMessageFactory.StockMessages;
import org.commcare.android.resource.AppInstallStatus;
import org.commcare.android.resource.ResourceInstallUtils;
import org.commcare.android.session.DevSessionRestorer;
import org.commcare.android.tasks.DataPullTask;
import org.commcare.android.tasks.InstallStagedUpdateTask;
import org.commcare.android.tasks.ManageKeyRecordListener;
import org.commcare.android.tasks.ManageKeyRecordTask;
import org.commcare.android.tasks.templates.HttpCalloutTask.HttpCalloutOutcomes;
import org.commcare.android.ui.CustomBanner;
import org.commcare.android.util.ACRAUtil;
import org.commcare.android.util.MediaUtil;
import org.commcare.android.view.ViewUtil;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.dialogs.CustomProgressDialog;
import org.commcare.dalvik.dialogs.DialogCreationHelpers;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.ArrayList;

/**
 * @author ctsims
 */
@ManagedUi(R.layout.screen_login)
public class LoginActivity extends CommCareActivity<LoginActivity>
        implements OnItemSelectedListener, RuntimePermissionRequester {

    private static final String TAG = LoginActivity.class.getSimpleName();

    private static final int MENU_DEMO = Menu.FIRST;
    private static final int MENU_ABOUT = Menu.FIRST + 1;
    private static final int MENU_PERMISSIONS = Menu.FIRST + 2;
    public static final String NOTIFICATION_MESSAGE_LOGIN = "login_message";
    public final static String KEY_LAST_APP = "id_of_last_selected";
    public final static String KEY_ENTERED_USER = "entered-username";
    public final static String KEY_ENTERED_PW = "entered-password";

    private static final int SEAT_APP_ACTIVITY = 0;
    public final static String KEY_APP_TO_SEAT = "app_to_seat";
    public final static String USER_TRIGGERED_LOGOUT = "user-triggered-logout";

    @UiElement(value=R.id.screen_login_bad_password)
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
    
    private static final int TASK_KEY_EXCHANGE = 1;
    private static final int TASK_UPGRADE_INSTALL = 2;

    private SqlStorage<UserKeyRecord> storage;
    private final ArrayList<String> appIdDropdownList = new ArrayList<>();

    private String usernameBeforeRotation;
    private String passwordBeforeRotation;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        username.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        setLoginBoxesColorNormal();

        if (savedInstanceState == null) {
            // Only restore last user on the initial creation
            SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
            String lastUser = prefs.getString(CommCarePreferences.LAST_LOGGED_IN_USER, null);
            if (lastUser != null) {
                username.setText(lastUser);
                password.requestFocus();
            }
        } else {
            // If the screen was rotated with entered text present, we will want to restore it
            // in onResume (can't do it here b/c will get overriden by logic in refreshForNewApp())
            usernameBeforeRotation = savedInstanceState.getString(KEY_ENTERED_USER);
            passwordBeforeRotation = savedInstanceState.getString(KEY_ENTERED_PW);
        }

        setupUIElements();
    }

    private void setupUIElements() {
        username.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS |
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);

        setLoginBoxesColorNormal();

        loginButton.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                loginButtonPressed(isRestoreSessionChecked());
            }
        });

        username.addTextChangedListener(textWatcher);
        password.addTextChangedListener(textWatcher);

        username.setHint(Localization.get("login.username"));
        password.setHint(Localization.get("login.password"));

        final View activityRootView = findViewById(R.id.screen_login_main);
        final SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
        activityRootView.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                int hideAll = LoginActivity.this.getResources().getInteger(R.integer.login_screen_hide_all_cuttoff);
                int hideBanner = LoginActivity.this.getResources().getInteger(R.integer.login_screen_hide_banner_cuttoff);
                int height = activityRootView.getHeight();

                if (height < hideAll) {
                    banner.setVisibility(View.GONE);
                } else if (height < hideBanner) {
                    banner.setVisibility(View.GONE);
                } else {
                    // Override default CommCare banner if requested
                    String customBannerURI = prefs.getString(CommCarePreferences.BRAND_BANNER_LOGIN, "");
                    if (!"".equals(customBannerURI)) {
                        Bitmap bitmap = MediaUtil.inflateDisplayImage(LoginActivity.this, customBannerURI);
                        if (bitmap != null) {
                            ImageView bannerView = (ImageView) banner.findViewById(R.id.main_top_banner);
                            bannerView.setImageBitmap(bitmap);
                        }
                    }
                    banner.setVisibility(View.VISIBLE);
                }
            }
        });

        Permissions.acquireAllAppPermissions(this, this, Permissions.ALL_PERMISSIONS_REQUEST);
    }

    @Override
    @TargetApi(Build.VERSION_CODES.M)
    public void requestNeededPermissions(int requestCode) {
        ActivityCompat.requestPermissions(this, Permissions.getAppPermissions(),
                requestCode);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        String[] requiredPerms = Permissions.getRequiredPerms();

        if (requestCode == Permissions.ALL_PERMISSIONS_REQUEST) {
            for (int i = 0; i < permissions.length; i++) {
                for (String requiredPerm : requiredPerms) {
                    if (requiredPerm.equals(permissions[i]) &&
                            grantResults[i] == PackageManager.PERMISSION_DENIED) {
                        loginButton.setEnabled(false);
                        errorBox.setVisibility(View.VISIBLE);
                        errorBox.setText(Localization.get("permission.all.denial.message"));
                        return;
                    }
                }
            }
        }
        loginButton.setEnabled(true);
        errorBox.setVisibility(View.GONE);
        errorBox.setText("");
    }

    private boolean isRestoreSessionChecked() {
        return restoreSessionCheckbox.isChecked();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        String enteredUsername = username.getText().toString();
        if (!"".equals(enteredUsername) && enteredUsername != null) {
            savedInstanceState.putString(KEY_ENTERED_USER, enteredUsername);
        }
        String enteredPassword = password.getText().toString();
        if (!"".equals(enteredPassword) && enteredPassword != null) {
            savedInstanceState.putString(KEY_ENTERED_PW, enteredPassword);
        }
    }

    private void loginButtonPressed(boolean restoreSession) {
        errorBox.setVisibility(View.GONE);
        ViewUtil.hideVirtualKeyboard(LoginActivity.this);

        DevSessionRestorer.tryAutoLoginPasswordSave(password.getText().toString());

        if (ResourceInstallUtils.isUpdateReadyToInstall()) {
            // install update, which triggers login upon completion
            installPendingUpdate();
        } else {
            localLoginOrPullAndLogin(restoreSession);
        }
    }

    public String getActivityTitle() {
        //TODO: "Login"?
        return null;
    }

    private void startOta() {
        // We should go digest auth this user on the server and see whether to
        // pull them down.
        SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();

        // TODO: we don't actually always want to do this. We need to have an
        // alternate route where we log in locally and sync (with unsent form
        // submissions) more centrally.

        DataPullTask<LoginActivity> dataPuller = 
            new DataPullTask<LoginActivity>(getUsername(), password.getText().toString(),
                 prefs.getString("ota-restore-url", LoginActivity.this.getString(R.string.ota_restore_url)),
                 LoginActivity.this) {
                    @Override
                    protected void deliverResult( LoginActivity receiver, Integer result) {
                        if (result == null) {
                            // The task crashed unexpectedly
                            receiver.raiseLoginMessage(StockMessages.Restore_Unknown, true);
                            return;
                        }

                        switch(result) {
                        case DataPullTask.AUTH_FAILED:
                            receiver.raiseLoginMessage(StockMessages.Auth_BadCredentials, false);
                            break;
                        case DataPullTask.BAD_DATA:
                            receiver.raiseLoginMessage(StockMessages.Remote_BadRestore, true);
                            break;
                        case DataPullTask.STORAGE_FULL:
                            receiver.raiseLoginMessage(StockMessages.Storage_Full, true);
                            break;
                        case DataPullTask.DOWNLOAD_SUCCESS:
                            if(!tryLocalLogin(true, isRestoreSessionChecked())) {
                                receiver.raiseLoginMessage(StockMessages.Auth_CredentialMismatch, true);
                            }
                            break;
                        case DataPullTask.UNREACHABLE_HOST:
                            receiver.raiseLoginMessage(StockMessages.Remote_NoNetwork, true);
                            break;
                        case DataPullTask.CONNECTION_TIMEOUT:
                            receiver.raiseLoginMessage(StockMessages.Remote_Timeout, true);
                            break;
                        case DataPullTask.SERVER_ERROR:
                            receiver.raiseLoginMessage(StockMessages.Remote_ServerError, true);
                            break;
                        case DataPullTask.UNKNOWN_FAILURE:
                            receiver.raiseLoginMessage(StockMessages.Restore_Unknown, true);
                            break;
                        }
                    }

                    @Override
                    protected void deliverUpdate(LoginActivity receiver, Integer... update) {
                        if(update[0] == DataPullTask.PROGRESS_STARTED) {
                            receiver.updateProgress(Localization.get("sync.progress.purge"), DataPullTask.DATA_PULL_TASK_ID);
                        } else if(update[0] == DataPullTask.PROGRESS_CLEANED) {
                            receiver.updateProgress(Localization.get("sync.progress.authing"), DataPullTask.DATA_PULL_TASK_ID);
                        } else if(update[0] == DataPullTask.PROGRESS_AUTHED) {
                            receiver.updateProgress(Localization.get("sync.progress.downloading"), DataPullTask.DATA_PULL_TASK_ID);
                        } else if(update[0] == DataPullTask.PROGRESS_DOWNLOADING) {
                            receiver.updateProgress(Localization.get("sync.process.downloading.progress", new String[] {String.valueOf(update[1])}), DataPullTask.DATA_PULL_TASK_ID);
                        } else if(update[0] == DataPullTask.PROGRESS_PROCESSING) {
                            receiver.updateProgress(Localization.get("sync.process.processing", new String[] {String.valueOf(update[1]), String.valueOf(update[2])}), DataPullTask.DATA_PULL_TASK_ID);
                            receiver.updateProgressBar(update[1], update[2], DataPullTask.DATA_PULL_TASK_ID);
                        } else if(update[0] == DataPullTask.PROGRESS_RECOVERY_NEEDED) {
                            receiver.updateProgress(Localization.get("sync.recover.needed"), DataPullTask.DATA_PULL_TASK_ID);
                        } else if(update[0] == DataPullTask.PROGRESS_RECOVERY_STARTED) {
                            receiver.updateProgress(Localization.get("sync.recover.started"), DataPullTask.DATA_PULL_TASK_ID);
                        }
                    }

                    @Override
                    protected void deliverError( LoginActivity receiver, Exception e) {
                        receiver.raiseLoginMessage(StockMessages.Restore_Unknown, true);
                    }
        };

        dataPuller.connect(this);
        dataPuller.execute();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // It is possible that we left off at the LoginActivity last time we were on the main CC
        // screen, but have since done something in the app manager to either leave no seated app
        // at all, or to render the seated app unusable. Redirect to CCHomeActivity if we encounter
        // either case
        CommCareApp currentApp = CommCareApplication._().getCurrentApp();
        if (currentApp == null || !currentApp.getAppRecord().isUsable()) {
            // send back to dispatch activity
            setResult(RESULT_OK);
            this.finish();
            return;
        }

        // Otherwise, refresh the login screen for current conditions
        refreshView();
    }

    @Override
    public void onResumeFragments() {
        super.onResumeFragments();

        tryAutoLogin();
    }

    private void tryAutoLogin() {
        Pair<String, String> userAndPass =
                DevSessionRestorer.getAutoLoginCreds();
        if (userAndPass != null) {
            username.setText(userAndPass.first);
            password.setText(userAndPass.second);

            if (!getIntent().getBooleanExtra(USER_TRIGGERED_LOGOUT, false)) {
                // If we are attempting auto-login, assume that we want to restore a saved session
                loginButtonPressed(true);
            }
        }
    }

    private String getUsername() {
        return username.getText().toString().toLowerCase().trim();
    }
    
    private boolean tryLocalLogin(final boolean warnMultipleAccounts, boolean restoreSession) {
        //TODO: check username/password for emptiness
        return tryLocalLogin(getUsername(), password.getText().toString(), warnMultipleAccounts,
                restoreSession);
    }
        
    private boolean tryLocalLogin(final String username, String password,
                                  final boolean warnMultipleAccounts, final boolean restoreSession) {
        try{
            // TODO: We don't actually even use this anymore other than for hte
            // local login count, which seems super silly.
            UserKeyRecord matchingRecord = null;
            int count = 0;
            for(UserKeyRecord record : storage()) {
                if(!record.getUsername().equals(username)) {
                    continue;
                }
                count++;
                String hash = record.getPasswordHash();
                if(hash.contains("$")) {
                    String alg = "sha1";
                    String salt = hash.split("\\$")[1];
                    String check = hash.split("\\$")[2];
                    MessageDigest md = MessageDigest.getInstance("SHA-1");
                    BigInteger number = new BigInteger(1, md.digest((salt+password).getBytes()));
                    String hashed = number.toString(16);

                    while (hashed.length() < check.length()) {
                        hashed = "0" + hashed;
                    }
                    if (hash.equals(alg + "$" + salt + "$" + hashed)) {
                        matchingRecord = record;
                    }
                }
            }

            final boolean triggerTooManyUsers = count > 1 && warnMultipleAccounts;

            ManageKeyRecordTask<LoginActivity> task =
                new ManageKeyRecordTask<LoginActivity>(this, TASK_KEY_EXCHANGE,
                        username, password,
                        CommCareApplication._().getCurrentApp(), restoreSession,
                        new ManageKeyRecordListener<LoginActivity>() {

                @Override
                public void keysLoginComplete(LoginActivity r) {
                    if(triggerTooManyUsers) {
                        // We've successfully pulled down new user data.
                        // Should see if the user already has a sandbox and let
                        // them know that their old data doesn't transition
                        r.raiseMessage(NotificationMessageFactory.message(StockMessages.Auth_RemoteCredentialsChanged), true);
                        Logger.log(AndroidLogger.TYPE_USER, "User " + username + " has logged in for the first time with a new password. They may have unsent data in their other sandbox");
                    }
                    r.done();
                }

                @Override
                public void keysReadyForSync(LoginActivity r) {
                    // TODO: we only wanna do this on the _first_ try. Not
                    // subsequent ones (IE: On return from startOta)
                    r.startOta();
                }

                @Override
                public void keysDoneOther(LoginActivity r, HttpCalloutOutcomes outcome) {
                    switch(outcome) {
                    case AuthFailed:
                        Logger.log(AndroidLogger.TYPE_USER, "auth failed");
                        r.raiseLoginMessage(StockMessages.Auth_BadCredentials, false);
                        break;
                    case BadResponse:
                        Logger.log(AndroidLogger.TYPE_USER, "bad response");
                        r.raiseLoginMessage(StockMessages.Remote_BadRestore, true);
                        break;
                    case NetworkFailure:
                        Logger.log(AndroidLogger.TYPE_USER, "bad network");
                        r.raiseLoginMessage(StockMessages.Remote_NoNetwork, false);
                        break;
                    case NetworkFailureBadPassword:
                        Logger.log(AndroidLogger.TYPE_USER, "bad network");
                        r.raiseLoginMessage(StockMessages.Remote_NoNetwork_BadPass, true);
                        break;
                    case BadCertificate:
                        Logger.log(AndroidLogger.TYPE_USER, "bad certificate");
                        r.raiseLoginMessage(StockMessages.BadSSLCertificate, false);
                        break;
                    case UnknownError:
                        Logger.log(AndroidLogger.TYPE_USER, "unknown");
                        r.raiseLoginMessage(StockMessages.Restore_Unknown, true);
                        break;
                    default:
                        break;
                    }
                }
            }) {
                @Override
                protected void deliverUpdate(LoginActivity receiver, String... update) {
                    receiver.updateProgress(update[0], TASK_KEY_EXCHANGE);
                }
            };

            task.connect(this);
            task.execute();

            return true;
        }catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    private void done() {
        ACRAUtil.registerUserData();

        CommCareApplication._().clearNotifications(NOTIFICATION_MESSAGE_LOGIN);

        Intent i = new Intent();
        setResult(RESULT_OK, i);

        finish();
    }
    
    private SqlStorage<UserKeyRecord> storage() {
        if(storage == null) {
            storage = CommCareApplication._().getAppStorage(UserKeyRecord.class);
        }
        return storage;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_DEMO, 0, Localization.get("login.menu.demo")).setIcon(android.R.drawable.ic_menu_preferences);
        menu.add(0, MENU_ABOUT, 1, Localization.get("home.menu.about")).setIcon(android.R.drawable.ic_menu_help);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            menu.add(0, MENU_PERMISSIONS, 1, Localization.get("login.menu.permission")).setIcon(android.R.drawable.ic_menu_manage);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean otherResult = super.onOptionsItemSelected(item);
        switch(item.getItemId()) {
        case MENU_DEMO:
            DemoUserBuilder.build(this, CommCareApplication._().getCurrentApp());
            tryLocalLogin(DemoUserBuilder.DEMO_USERNAME, DemoUserBuilder.DEMO_PASSWORD, false ,false);
            return true;
        case MENU_ABOUT:
            DialogCreationHelpers.buildAboutCommCareDialog(this).show();
            return true;
        case MENU_PERMISSIONS:
            Permissions.acquireAllAppPermissions(this, this, Permissions.ALL_PERMISSIONS_REQUEST);
            return true;
        default:
            return otherResult;
        }
    }

    private void raiseLoginMessage(MessageTag messageTag, boolean showTop) {
        NotificationMessage message = NotificationMessageFactory.message(messageTag,
                NOTIFICATION_MESSAGE_LOGIN);
        raiseMessage(message, showTop);
    }

    private void raiseMessage(NotificationMessage message, boolean showTop) {
        String toastText = message.getTitle();

        if (showTop) {
            CommCareApplication._().reportNotificationMessage(message);
            toastText = Localization.get("notification.for.details.wrapper",
                    new String[] {toastText});
        }
        
        setLoginBoxesColorError();
        username.setCompoundDrawablesWithIntrinsicBounds(getResources().getDrawable(R.drawable.icon_user_attnneg),  null, null, null);
        password.setCompoundDrawablesWithIntrinsicBounds(getResources().getDrawable(R.drawable.icon_lock_attnneg), null, null, null);
        loginButton.setBackgroundColor(getResources().getColor(R.color.cc_attention_negative_bg));
        loginButton.setTextColor(getResources().getColor(R.color.cc_attention_negative_text));

        errorBox.setVisibility(View.VISIBLE);
        errorBox.setText(toastText);

        Toast.makeText(this, toastText, Toast.LENGTH_LONG).show();
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

    /**
     * Implementation of generateProgressDialog() for DialogController -- other methods
     * handled entirely in CommCareActivity
     */
    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        CustomProgressDialog dialog;
        switch (taskId) {
        case TASK_KEY_EXCHANGE:
            dialog = CustomProgressDialog.newInstance(Localization.get("key.manage.title"), 
                    Localization.get("key.manage.start"), taskId);
            break;
        case DataPullTask.DATA_PULL_TASK_ID:
            dialog = CustomProgressDialog.newInstance(Localization.get("sync.progress.title"),
                    Localization.get("sync.progress.starting"), taskId);
            dialog.addCancelButton();
            dialog.addProgressBar();
            break;
        case TASK_UPGRADE_INSTALL:
            dialog = CustomProgressDialog.newInstance(Localization.get("updates.installing.title"), 
                    Localization.get("updates.installing.message"), taskId);
            break;
        default:
            Log.w(TAG, "taskId passed to generateProgressDialog does not match "
                    + "any valid possibilities in LoginActivity");
            return null;
        }
        return dialog;
    }

    private void refreshView() {
        // In case the seated app has changed since last time we were in LoginActivity
        refreshForNewApp();

        restoreEnteredTextFromRotation();

        updateBanner();

        // Decide whether or not to show the app selection spinner based upon # of usable apps
        ArrayList<ApplicationRecord> readyApps = CommCareApplication._().getUsableAppRecords();
        if (readyApps.size() == 1) {
            spinner.setVisibility(View.GONE);
            welcomeMessage.setText(Localization.get("login.welcome.single"));
            // Set this app as the last selected app, for use in choosing what app to initialize
            // on first startup
            ApplicationRecord r = readyApps.get(0);
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            prefs.edit().putString(KEY_LAST_APP, r.getUniqueId()).commit();
        }
        else {
            welcomeMessage.setText(Localization.get("login.welcome.multiple"));
            ArrayList<String> appNames = new ArrayList<>();
            appIdDropdownList.clear();
            for (ApplicationRecord r : readyApps) {
                appNames.add(r.getDisplayName());
                appIdDropdownList.add(r.getUniqueId());
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_text_view, appNames);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
            spinner.setOnItemSelectedListener(this);

            // Set the spinner's selection to match whatever the currently seated app is
            String currAppId = CommCareApplication._().getCurrentApp().getUniqueId();
            int position = appIdDropdownList.indexOf(currAppId);
            spinner.setSelection(position);
            spinner.setVisibility(View.VISIBLE);
        }
    }

    private void updateBanner() {
        ImageView topBannerImageView =
                (ImageView)banner.findViewById(org.commcare.dalvik.R.id.main_top_banner);
        if (!CustomBanner.useCustomBannerFitToActivity(this, topBannerImageView)) {
            topBannerImageView.setImageResource(R.drawable.commcare_logo);
        }
    }

    private void restoreEnteredTextFromRotation() {
        if (usernameBeforeRotation != null) {
            username.setText(usernameBeforeRotation);
            usernameBeforeRotation = null;
        }
        if (passwordBeforeRotation != null) {
            password.setText(passwordBeforeRotation);
            passwordBeforeRotation = null;
        }
    }

    private void refreshForNewApp() {
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
            refreshActionBar();
        }

        // Refresh UI for potential new language
        ManagedUiFramework.loadUiElements(this);

        // Refresh welcome msg separately bc cannot set a single locale for its UiElement
        welcomeMessage.setText(Localization.get("login.welcome.multiple"));

        // Update checkbox visibility 
        if (DevSessionRestorer.savedSessionPresent()) {
            restoreSessionCheckbox.setVisibility(View.VISIBLE);
        } else {
            restoreSessionCheckbox.setVisibility(View.GONE);
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        // Retrieve the app record corresponding to the app selected
        String appId = appIdDropdownList.get(position);

        boolean appChanged = !appId.equals(CommCareApplication._().getCurrentApp().getUniqueId());
        if (appChanged) {
            // Set the id of the last selected app
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            prefs.edit().putString(KEY_LAST_APP, appId).commit();

            // Launch the activity to seat the new app
            Intent i = new Intent(this, SeatAppActivity.class);
            i.putExtra(KEY_APP_TO_SEAT, appId);
            this.startActivityForResult(i, SEAT_APP_ACTIVITY);
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    /**
     * Block the user with a dialog while downloaded update is installed.
     */
    private void installPendingUpdate() {
        InstallStagedUpdateTask<LoginActivity> task =
                new InstallStagedUpdateTask<LoginActivity>(TASK_UPGRADE_INSTALL) {
                    @Override
                    protected void deliverResult(LoginActivity receiver,
                                                 AppInstallStatus result) {
                        if (result == AppInstallStatus.Installed) {
                            Toast.makeText(receiver,
                                    Localization.get("login.update.install.success"),
                                    Toast.LENGTH_LONG).show();
                        } else {
                            CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(result));
                        }

                        localLoginOrPullAndLogin(isRestoreSessionChecked());
                    }

                    @Override
                    protected void deliverUpdate(LoginActivity receiver,
                                                 int[]... update) {
                    }

                    @Override
                    protected void deliverError(LoginActivity receiver,
                                                Exception e) {
                        e.printStackTrace();
                        Log.e(TAG, "update installation on login failed: " + e.getMessage());
                        Toast.makeText(receiver,
                                Localization.get("login.update.install.failure"),
                                Toast.LENGTH_LONG).show();

                        localLoginOrPullAndLogin(isRestoreSessionChecked());
                    }
                };
        task.connect(this);
        task.execute();
    }

    private void localLoginOrPullAndLogin(boolean restoreSession) {
        if (tryLocalLogin(false, restoreSession)) {
            return;
        }

        startOta();
    }
}
