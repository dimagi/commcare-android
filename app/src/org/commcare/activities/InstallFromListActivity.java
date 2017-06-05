package org.commcare.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
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
import org.commcare.core.interfaces.HttpResponseProcessor;
import org.commcare.dalvik.R;
import org.commcare.logging.AndroidLogger;
import org.commcare.models.database.SqlStorage;
import org.commcare.android.database.global.models.AppAvailableToInstall;
import org.commcare.tasks.SimpleGetTask;
import org.commcare.tasks.templates.CommCareTaskConnector;
import org.commcare.utils.ConnectivityStatus;
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
import java.util.List;

/**
 * Created by amstone326 on 2/3/17.
 */
public class InstallFromListActivity<T> extends CommCareActivity<T> implements HttpResponseProcessor {

    private static final String TAG = InstallFromListActivity.class.getSimpleName();
    public static final String PROFILE_REF = "profile-ref-selected";

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
        errorMessageBox = (TextView)findViewById(R.id.error_message);
        authenticateView = findViewById(R.id.authenticate_view);
        setUpGetAppsButton();
        setUpAppsList();
        setUpToggle();
        setProperAuthView();
    }

    private void setUpGetAppsButton() {
        Button getAppsButton = (Button)findViewById(R.id.get_apps_button);
        getAppsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                errorMessageBox.setVisibility(View.INVISIBLE);
                if (inputIsValid()) {
                    if (ConnectivityStatus.isNetworkAvailable(InstallFromListActivity.this)) {
                        authenticateView.setVisibility(View.GONE);
                        requestedFromIndia = false;
                        requestedFromProd = false;
                        requestAppList();
                    } else {
                        enterErrorState(Localization.get("updates.check.network_unavailable"));
                    }
                }
            }
        });
    }

    private void setUpAppsList() {
        appsListContainer = findViewById(R.id.apps_list_container);
        appsListView = (ListView)findViewById(R.id.apps_list_view);
        appsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < availableApps.size()) {
                    AppAvailableToInstall app = availableApps.get(position);
                    Intent i = new Intent(getIntent());
                    i.putExtra(PROFILE_REF, app.getMediaProfileRef());
                    setResult(RESULT_OK, i);
                    finish();
                }
            }
        });
    }

    private void setUpToggle() {
        FrameLayout toggleContainer = (FrameLayout)findViewById(R.id.toggle_button_container);
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
        userTypeToggler.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                inMobileUserAuthMode = isChecked;
                setProperAuthView();
                errorMessage = null;
                errorMessageBox.setVisibility(View.INVISIBLE);
                ((EditText)findViewById(R.id.edit_password)).setText("");
            }
        });

        toggleContainer.addView(userTypeToggler);
    }

    private void setProperAuthView() {
        final View mobileUserView = findViewById(R.id.mobile_user_view);
        final View webUserView = findViewById(R.id.web_user_view);
        if (inMobileUserAuthMode) {
            mobileUserView.setVisibility(View.VISIBLE);
            webUserView.setVisibility(View.GONE);
        } else {
            mobileUserView.setVisibility(View.GONE);
            webUserView.setVisibility(View.VISIBLE);
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
     *
     * @return whether a request was initiated
     */
    private boolean requestAppList() {
        String urlToTry = getURLToTry();
        if (urlToTry != null) {
            final View processingRequestView = findViewById(R.id.processing_request_view);
            String username = getUsernameForAuth();
            String password = ((EditText)findViewById(R.id.edit_password)).getText().toString();
            SimpleGetTask task = new SimpleGetTask(username, password, this) {

                @Override
                protected void onPreExecute() {
                    super.onPreExecute();
                    processingRequestView.setVisibility(View.VISIBLE);
                }

                @Override
                protected void onPostExecute(Void result) {
                    super.onPostExecute(result);
                    processingRequestView.setVisibility(View.GONE);
                }

            };

            task.connect((CommCareTaskConnector)this);
            setAttemptedRequestFlag();
            task.executeParallel(urlToTry);
            return true;
        }
        return false;
    }

    private String getUsernameForAuth() {
        if (inMobileUserAuthMode) {
            String username =  ((EditText)findViewById(R.id.edit_username)).getText().toString();
            String domain =  ((EditText)findViewById(R.id.edit_domain)).getText().toString();
            return username + "@" + domain + ".commcarehq.org";
        } else {
            return ((EditText)findViewById(R.id.edit_email)).getText().toString();
        }
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
            return null;
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
        repeatRequestOrShowResults(false);
    }

    private void processResponseIntoAppsList(InputStream responseData) {
        try {
            KXmlParser baseParser = ElementParser.instantiateParser(responseData);
            List<AppAvailableToInstall> apps = (new AvailableAppsParser(baseParser)).parse();
            availableApps.addAll(apps);
        } catch (IOException | InvalidStructureException | XmlPullParserException | UnfullfilledRequirementsException e) {
            Logger.log(AndroidLogger.TYPE_RESOURCES, "Error encountered while parsing apps available for install");
        }
    }

    @Override
    public void processRedirection(int responseCode) {
        handleRequestError(responseCode);
    }

    @Override
    public void processClientError(int responseCode) {
        handleRequestError(responseCode);
    }

    @Override
    public void processServerError(int responseCode) {
        handleRequestError(responseCode);
    }

    @Override
    public void processOther(int responseCode) {
        handleRequestError(responseCode);
    }

    @Override
    public void handleIOException(IOException exception) {
        repeatRequestOrShowResults(true);
    }

    private void handleRequestError(int responseCode) {
        Log.e(TAG,
                "Request to " + urlCurrentlyRequestingFrom + " in get available apps request " +
                        "had error code response: " + responseCode);
        repeatRequestOrShowResults(true);
    }

    private void repeatRequestOrShowResults(final boolean responseWasError) {
        if (!requestAppList()) {
            // Means we've tried requesting to both endpoints

            this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (availableApps.size() == 0) {
                        if (responseWasError) {
                            enterErrorState(Localization.get("invalid.fields.entered." +
                                    (inMobileUserAuthMode ? "mobile" : "web")));
                        } else {
                            enterErrorState(Localization.get("no.apps.available"));
                        }
                    } else {
                        showResults();
                    }
                }
            });
        }
    }

    private void showResults() {
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
                TextView appName = (TextView)v.findViewById(R.id.app_name);
                appName.setText(app.getAppName());
                TextView domain = (TextView)v.findViewById(R.id.domain);
                domain.setText(app.getDomainName());

                return v;
            }
        });
        rebuildOptionsMenu();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, RETRIEVE_APPS_FOR_DIFF_USER, 0,
                Localization.get("menu.app.list.install.other.user"));
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(RETRIEVE_APPS_FOR_DIFF_USER)
                .setVisible(appsListContainer.getVisibility() != View.GONE);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == RETRIEVE_APPS_FOR_DIFF_USER) {
            clearPreviouslyRetrievedApps();
            availableApps.clear();
            appsListContainer.setVisibility(View.GONE);
            authenticateView.setVisibility(View.VISIBLE);
            clearAllFields();
            rebuildOptionsMenu();
            return true;
        }
        return super.onOptionsItemSelected(item);
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
