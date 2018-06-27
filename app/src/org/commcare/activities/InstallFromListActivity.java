package org.commcare.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ToggleButton;

import org.commcare.CommCareApplication;
import org.commcare.android.database.global.models.AppAvailableToInstall;
import org.commcare.core.interfaces.HttpResponseProcessor;
import org.commcare.core.network.AuthenticationInterceptor;
import org.commcare.dalvik.R;
import org.commcare.models.database.SqlStorage;
import org.commcare.modern.util.Pair;
import org.commcare.network.CommcareRequestGenerator;
import org.commcare.preferences.GlobalPrivilegesManager;
import org.commcare.tasks.ModernHttpTask;
import org.commcare.tasks.templates.CommCareTaskConnector;
import org.commcare.util.LogTypes;
import org.commcare.utils.ConnectivityStatus;
import org.commcare.views.UserfacingErrorHandling;
import org.commcare.xml.AvailableAppsParser;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.xml.ElementParser;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * Created by amstone326 on 2/3/17.
 */
public class InstallFromListActivity<T> extends CommCareActivity<T> implements HttpResponseProcessor {

    public static final String PROFILE_REF = "profile-ref-selected";
    private static final String KEY_LAST_SUCCESSFUL_USERNAME = "last-successful-username";
    private static final String KEY_LAST_SUCCESSFUL_PW = "last-successful-password";

    private static final String REQUESTED_FROM_PROD_KEY = "have-requested-from-prod";
    private static final String REQUESTED_FROM_INDIA_KEY = "have-requested-from-india";
    private static final String ERROR_MESSAGE_KEY = "error-message-key";
    private static final String AUTH_MODE_KEY = "auth-mode-key";

    private static final String PROD_URL = "https://www.commcarehq.org/phone/list_apps";
    private static final String INDIA_URL = "https://india.commcarehq.org/phone/list_apps";

    private static final int RETRIEVE_APPS_FOR_DIFF_USER = Menu.FIRST;

    private boolean inMobileUserAuthMode;

    private boolean requestedFromProd;
    private boolean requestedFromIndia;
    private String urlCurrentlyRequestingFrom;

    private String errorMessage;
    private View authenticateView;
    private TextView errorMessageBox;
    private View appsListContainer;
    private ListView appsListView;

    private String lastUsernameUsed;
    private String lastPasswordUsed;

    private List<AppAvailableToInstall> availableApps = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setInitialValues(savedInstanceState);
        setupUI();
        if (errorMessage != null) {
            enterErrorState(errorMessage);
        }
        loadPreviouslyRetrievedAvailableApps();
    }

    private void setupUI() {
        setContentView(R.layout.user_get_available_apps);
        errorMessageBox = findViewById(R.id.error_message);
        authenticateView = findViewById(R.id.authenticate_view);
        setUpGetAppsButton();
        setUpAppsList();
        setUpToggle();
        setProperAuthView();
    }

    private void setUpGetAppsButton() {
        Button getAppsButton = findViewById(R.id.get_apps_button);
        getAppsButton.setOnClickListener(v -> {
            errorMessageBox.setVisibility(View.INVISIBLE);
            if (inputIsValid()) {
                if (ConnectivityStatus.isNetworkAvailable(InstallFromListActivity.this)) {
                    startRequests(getUsernameForAuth(), getPassword());
                } else {
                    enterErrorState(Localization.get("updates.check.network_unavailable"));
                }
            }
        });
    }

    private void startRequests(String username, String password) {
        authenticateView.setVisibility(View.GONE);
        appsListContainer.setVisibility(View.GONE);
        requestedFromIndia = false;
        requestedFromProd = false;
        requestAppList(username, password);
    }

    private void setUpAppsList() {
        appsListContainer = findViewById(R.id.apps_list_container);
        appsListView = findViewById(R.id.apps_list_view);
        appsListView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < availableApps.size()) {
                AppAvailableToInstall app = availableApps.get(position);
                Intent i = new Intent(getIntent());
                i.putExtra(PROFILE_REF, app.getMediaProfileRef());
                setResult(RESULT_OK, i);
                finish();
            }
        });
    }

    private void setUpToggle() {
        FrameLayout toggleContainer = findViewById(R.id.toggle_button_container);
        CompoundButton userTypeToggler;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            Switch switchButton = new Switch(this);
            switchButton.setTextOff(Localization.get("toggle.web.user.mode"));
            switchButton.setTextOn(Localization.get("toggle.mobile.user.mode"));
            userTypeToggler = switchButton;
        } else {
            ToggleButton toggleButton = new ToggleButton(this);
            toggleButton.setTextOff(Localization.get("toggle.web.user.mode"));
            toggleButton.setTextOn(Localization.get("toggle.mobile.user.mode"));
            userTypeToggler = toggleButton;
        }

        // Important for this call to come first; we don't want the listener to be invoked on the
        // first auto-setting, just on user-triggered ones
        userTypeToggler.setChecked(inMobileUserAuthMode);
        userTypeToggler.setOnCheckedChangeListener((buttonView, isChecked) -> {
            inMobileUserAuthMode = isChecked;
            errorMessage = null;
            errorMessageBox.setVisibility(View.INVISIBLE);
            ((EditText)findViewById(R.id.edit_password)).setText("");
            setProperAuthView();
        });

        toggleContainer.addView(userTypeToggler);
    }

    private void setProperAuthView() {
        final View mobileUserView = findViewById(R.id.mobile_user_view);
        final View webUserView = findViewById(R.id.web_user_view);
        if (inMobileUserAuthMode) {
            mobileUserView.setVisibility(View.VISIBLE);
            webUserView.setVisibility(View.GONE);
            findViewById(R.id.edit_username).requestFocus();
        } else {
            mobileUserView.setVisibility(View.GONE);
            webUserView.setVisibility(View.VISIBLE);
            findViewById(R.id.edit_email).requestFocus();
        }
    }

    private boolean inputIsValid() {
        String enteredPassword = ((EditText)findViewById(R.id.edit_password)).getText().toString();
        if ("".equals(enteredPassword)) {
            enterErrorState(Localization.get("missing.fields"));
            return false;
        }

        if (inMobileUserAuthMode) {
            String enteredMobileUser = ((EditText)findViewById(R.id.edit_username)).getText().toString();
            String enteredDomain = ((EditText)findViewById(R.id.edit_domain)).getText().toString();
            if ("".equals(enteredMobileUser) || "".equals(enteredDomain)) {
                enterErrorState(Localization.get("missing.fields"));
                return false;
            }
        } else {
            String enteredEmail = ((EditText)findViewById(R.id.edit_email)).getText().toString();
            if ("".equals(enteredEmail)) {
                enterErrorState(Localization.get("missing.fields"));
                return false;
            }
            if (!enteredEmail.contains("@")) {
                enterErrorState(Localization.get("email.address.invalid"));
                return false;
            }
        }

        return true;
    }

    private void setInitialValues(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            requestedFromIndia = savedInstanceState.getBoolean(REQUESTED_FROM_INDIA_KEY);
            requestedFromProd = savedInstanceState.getBoolean(REQUESTED_FROM_PROD_KEY);
            errorMessage = savedInstanceState.getString(ERROR_MESSAGE_KEY);
            inMobileUserAuthMode = savedInstanceState.getBoolean(AUTH_MODE_KEY);
        } else {
            inMobileUserAuthMode = true;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putBoolean(REQUESTED_FROM_INDIA_KEY, requestedFromIndia);
        savedInstanceState.putBoolean(REQUESTED_FROM_PROD_KEY, requestedFromProd);
        savedInstanceState.putString(ERROR_MESSAGE_KEY, errorMessage);
        savedInstanceState.putBoolean(AUTH_MODE_KEY, inMobileUserAuthMode);
    }

    /**
     * @return whether a request was initiated
     */
    private boolean requestAppList(String username, String password) {
        String urlToTry = getURLToTry();
        if (urlToTry != null) {
            this.lastUsernameUsed = username;
            this.lastPasswordUsed = password;
            final View processingRequestView = findViewById(R.id.processing_request_view);
            ModernHttpTask task = new ModernHttpTask(this, urlToTry, new HashMap(),
                    CommcareRequestGenerator.getHeaders(""), new Pair(username, password)) {

                @Override
                protected void onPreExecute() {
                    super.onPreExecute();
                    processingRequestView.setVisibility(View.VISIBLE);
                }

                @Override
                protected void onPostExecute(Void result) {
                    super.onPostExecute(result);
                    if (urlCurrentlyRequestingFrom == null) {
                        // Only hide the spinner if we didn't start another request
                        processingRequestView.setVisibility(View.GONE);
                    }
                }
            };

            task.connect((CommCareTaskConnector)this);
            setAttemptedRequestFlag();
            task.executeParallel();
            return true;
        }
        return false;
    }

    private String getUsernameForAuth() {
        if (inMobileUserAuthMode) {
            String username = ((EditText)findViewById(R.id.edit_username)).getText().toString();
            String domain = ((EditText)findViewById(R.id.edit_domain)).getText().toString();
            return username + "@" + domain + ".commcarehq.org";
        } else {
            return ((EditText)findViewById(R.id.edit_email)).getText().toString();
        }
    }

    private String getPassword() {
        return ((EditText)findViewById(R.id.edit_password)).getText().toString();
    }

    private void setAttemptedRequestFlag() {
        if (PROD_URL.equals(urlCurrentlyRequestingFrom)) {
            requestedFromProd = true;
        } else {
            requestedFromIndia = true;
        }
    }

    private String getURLToTry() {
        if (!requestedFromProd) {
            urlCurrentlyRequestingFrom = PROD_URL;
        } else if (!requestedFromIndia) {
            urlCurrentlyRequestingFrom = INDIA_URL;
        } else {
            urlCurrentlyRequestingFrom = null;
        }
        return urlCurrentlyRequestingFrom;
    }

    private void enterErrorState(String message) {
        errorMessage = message;
        authenticateView.setVisibility(View.VISIBLE);
        errorMessageBox.setVisibility(View.VISIBLE);
        errorMessageBox.setText(errorMessage);
        findViewById(R.id.auth_scroll_view).scrollTo(0, errorMessageBox.getBottom());
    }

    @Override
    public void processSuccess(int responseCode, InputStream responseData) {
        processResponseIntoAppsList(responseData);
        saveLastSuccessfulCredentials();
        repeatRequestOrShowResultsAfterSuccess();
    }

    private void processResponseIntoAppsList(InputStream responseData) {
        try {
            KXmlParser baseParser = ElementParser.instantiateParser(responseData);
            List<AppAvailableToInstall> apps = (new AvailableAppsParser(baseParser)).parse();
            availableApps.addAll(apps);
        } catch (IOException | InvalidStructureException | XmlPullParserException | UnfullfilledRequirementsException e) {
            Logger.log(LogTypes.TYPE_RESOURCES, "Error encountered while parsing apps available for install");
        }
    }

    private void saveLastSuccessfulCredentials() {
        SharedPreferences globalPrefsObject = GlobalPrivilegesManager.getGlobalPrefsRecord();
        globalPrefsObject.edit().putString(KEY_LAST_SUCCESSFUL_USERNAME, lastUsernameUsed).apply();
        globalPrefsObject.edit().putString(KEY_LAST_SUCCESSFUL_PW, lastPasswordUsed).apply();
    }

    @Override
    public void processClientError(int responseCode) {
        handleRequestError(responseCode, true);
    }

    @Override
    public void processServerError(int responseCode) {
        handleRequestError(responseCode, false);
    }

    @Override
    public void processOther(int responseCode) {
        handleRequestError(responseCode, true);
    }

    @Override
    public void handleIOException(IOException exception) {
        if (exception instanceof AuthenticationInterceptor.PlainTextPasswordException) {
            Logger.log(LogTypes.TYPE_ERROR_CONFIG_STRUCTURE, "Encountered PlainTextPasswordException while sending get available apps request: Sending password over HTTP");
            UserfacingErrorHandling.createErrorDialog(this, Localization.get("auth.over.http"), true);
        } else if (exception instanceof IOException) {
            Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS,
                    "An IOException was encountered during get available apps request: " + exception.getMessage());
        }
        repeatRequestOrShowResults(true, false);
    }

    private void handleRequestError(int responseCode, boolean couldBeUserError) {
        Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS,
                "Request to " + urlCurrentlyRequestingFrom + " in get available apps request " +
                        "had error code response: " + responseCode);
        repeatRequestOrShowResults(true, couldBeUserError);
    }

    private void repeatRequestOrShowResultsAfterSuccess() {
        repeatRequestOrShowResults(false, false);
    }

    private void repeatRequestOrShowResults(final boolean responseWasError,
                                            final boolean couldBeUserError) {
        if (!requestAppList(getUsernameForAuth(), getPassword())) {
            // Means we've tried requesting to both endpoints

            this.runOnUiThread(() -> {
                if (availableApps.size() == 0) {
                    if (responseWasError) {
                        if (couldBeUserError) {
                            enterErrorState(Localization.get("get.app.list.user.error." +
                                    (inMobileUserAuthMode ? "mobile" : "web")));
                        } else {
                            enterErrorState(Localization.get("get.app.list.unknown.error"));
                        }
                    } else {
                        enterErrorState(Localization.get("no.apps.available"));
                    }
                } else {
                    showResults();
                }
            });
        }
    }

    private void showResults() {
        sortAppList();
        appsListContainer.setVisibility(View.VISIBLE);
        authenticateView.setVisibility(View.GONE);
        appsListView.setAdapter(new ArrayAdapter<AppAvailableToInstall>(this,
                android.R.layout.simple_list_item_1, availableApps) {

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = convertView;
                Context context = InstallFromListActivity.this;
                if (v == null) {
                    v = View.inflate(context, R.layout.single_available_app_view, null);
                }

                AppAvailableToInstall app = this.getItem(position);
                TextView appName = v.findViewById(R.id.app_name);
                appName.setText(app.getAppName());
                TextView domain = v.findViewById(R.id.domain);
                domain.setText(app.getDomainName());

                return v;
            }
        });
        rebuildOptionsMenu();
    }

    private void sortAppList() {
        Collections.sort(this.availableApps, (o1, o2) -> o1.getAppName().toLowerCase().compareTo(o2.getAppName().toLowerCase()));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, RETRIEVE_APPS_FOR_DIFF_USER, 0,
                Localization.get("menu.app.list.install.other.user"));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.install_from_list_menu, menu);
        }

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        boolean appListIsShowing = appsListContainer.getVisibility() == View.VISIBLE;
        menu.findItem(RETRIEVE_APPS_FOR_DIFF_USER).setVisible(appListIsShowing);
        menu.findItem(R.id.refresh_app_list_item).setVisible(appListIsShowing);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == RETRIEVE_APPS_FOR_DIFF_USER) {
            retrieveAppsForDiffUser();
            return true;
        } else if (item.getItemId() == R.id.refresh_app_list_item) {
            attemptRefresh();
        }
        return super.onOptionsItemSelected(item);
    }

    private void attemptRefresh() {
        SharedPreferences globalPrefsObject = GlobalPrivilegesManager.getGlobalPrefsRecord();
        String username = globalPrefsObject.getString(KEY_LAST_SUCCESSFUL_USERNAME, "");
        String password = globalPrefsObject.getString(KEY_LAST_SUCCESSFUL_PW, "");
        if (!"".equals(username) && !"".equals(password)) {
            this.availableApps.clear();
            clearPreviouslyRetrievedApps();
            startRequests(username, password);
        } else {
            retrieveAppsForDiffUser();
            enterErrorState(Localization.get("could.not.refresh.apps"));
        }
    }

    private void retrieveAppsForDiffUser() {
        clearPreviouslyRetrievedApps();
        availableApps.clear();
        appsListContainer.setVisibility(View.GONE);
        authenticateView.setVisibility(View.VISIBLE);
        clearAllFields();
        rebuildOptionsMenu();
    }

    private void clearAllFields() {
        ((TextView)findViewById(R.id.edit_username)).setText("");
        ((TextView)findViewById(R.id.edit_password)).setText("");
        ((TextView)findViewById(R.id.edit_domain)).setText("");
        ((TextView)findViewById(R.id.edit_email)).setText("");
    }

    private void loadPreviouslyRetrievedAvailableApps() {
        for (AppAvailableToInstall availableApp : storage()) {
            this.availableApps.add(availableApp);
        }
        if (this.availableApps.size() > 0) {
            showResults();
        }
    }

    private void clearPreviouslyRetrievedApps() {
        storage().removeAll();
    }

    private SqlStorage<AppAvailableToInstall> storage() {
        return CommCareApplication.instance()
                .getGlobalStorage(AppAvailableToInstall.STORAGE_KEY, AppAvailableToInstall.class);
    }

}
