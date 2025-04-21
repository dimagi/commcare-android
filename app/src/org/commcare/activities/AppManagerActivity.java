package org.commcare.activities;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;

import org.commcare.CommCareApplication;
import org.commcare.adapters.AppManagerAdapter;
import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.preferences.AppManagerDeveloperPreferences;
import org.commcare.services.CommCareSessionService;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.views.dialogs.CommCareAlertDialog;
import org.commcare.views.dialogs.DialogCreationHelpers;
import org.commcare.views.dialogs.StandardAlertDialog;
import org.javarosa.core.services.locale.Localization;

import androidx.appcompat.app.AlertDialog;

/**
 * The activity that starts up when a user launches into the app manager.
 * Displays a list of all installed apps, each of which can be clicked to launch
 * the SingleAppManagerActivity for that app. Also includes a button for
 * installing new apps.
 *
 * @author amstone326
 */

public class AppManagerActivity extends CommCareActivity implements OnItemClickListener {

    public static final String KEY_LAUNCH_FROM_MANAGER = "from_manager";

    private static final int MENU_ABOUT = Menu.FIRST;
    private static final int MENU_ADVANCED_SETTINGS = Menu.FIRST + 1;
    private static final int MENU_CONNECTION_DIAGNOSTIC = Menu.FIRST + 2;
    private int mDeveloperModeClicks = 0;

    @Override 
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.app_manager);
        ((ListView)this.findViewById(R.id.apps_list_view)).setOnItemClickListener(this);
        FirebaseAnalyticsUtil.reportAppManagerAction(AnalyticsParamValue.OPEN_APP_MANAGER);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshView();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_ABOUT, 0, Localization.get("app.manager.menu.about")).setIcon(
                android.R.drawable.ic_menu_help);
        menu.add(0, MENU_ADVANCED_SETTINGS, 1, Localization.get("app.manager.advanced.settings.option"))
                .setIcon(android.R.drawable.ic_menu_preferences);
        menu.add(0, MENU_CONNECTION_DIAGNOSTIC, 2, Localization.get("home.menu.connection.diagnostic"))
                .setIcon(android.R.drawable.ic_menu_preferences);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ABOUT:
                showAboutCommCareDialog();
                return true;
            case MENU_CONNECTION_DIAGNOSTIC:
                Intent i = new Intent(this, ConnectionDiagnosticActivity.class);
                startActivity(i);
                return true;
            case MENU_ADVANCED_SETTINGS:
                i = new Intent(this, CommCarePreferenceActivity.class);
                i.putExtra(CommCarePreferenceActivity.EXTRA_PREF_TYPE, CommCarePreferenceActivity.PREF_TYPE_APP_MANAGER_ADVANCED);
                startActivity(i);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    protected void showAboutCommCareDialog() {
        CommCareAlertDialog dialog = DialogCreationHelpers.buildAboutCommCareDialog(this, false);
        dialog.makeCancelable();
        dialog.setOnDismissListener(dialog1 -> handleDeveloperModeClicks());
        showAlertDialog(dialog);
    }

    private void handleDeveloperModeClicks() {
        mDeveloperModeClicks++;
        if (mDeveloperModeClicks == 4) {
            AppManagerDeveloperPreferences.setDeveloperPreferencesEnabled(true);
            Toast.makeText(this, Localization.get("app.manager.developer.options.enabled"),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Refresh the list of installed apps
     */
    private void refreshView() {
        ListView lv = findViewById(R.id.apps_list_view);
        lv.setAdapter(new AppManagerAdapter(this));
    }

    /**
     * onClick method for the Install An App button
     *
     * @param v unused argument necessary for the method's use as an onClick handler.
     */
    public void installAppClicked(View v) {
        try {
            CommCareSessionService s = CommCareApplication.instance().getSession();
            if (s.isActive()) {
                triggerLogoutWarning();
            } else {
                installApp();
            }
        } catch (SessionUnavailableException e) {
            installApp();
        }
    }

    /**
     * Logs the user out and takes them to the app installation activity.
     */
    private void installApp() {
        Intent i = new Intent(getApplicationContext(), CommCareSetupActivity.class);
        i.putExtra(KEY_LAUNCH_FROM_MANAGER, true);
        this.startActivityForResult(i, DispatchActivity.INIT_APP);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch (requestCode) {
            case DispatchActivity.INIT_APP:
                if (resultCode == RESULT_OK) {
                    FirebaseAnalyticsUtil.reportAppManagerAction(
                            AnalyticsParamValue.INSTALL_FROM_MANAGER);
                    // If we have just returned from installation and the currently-seated app's
                    // resources are not validated, launch the MM verification activity
                    if (!CommCareApplication.instance().getCurrentApp().areMMResourcesValidated()) {
                        Intent i = new Intent(this, CommCareVerificationActivity.class);
                        i.putExtra(KEY_LAUNCH_FROM_MANAGER, true);
                        this.startActivityForResult(i, DispatchActivity.MISSING_MEDIA_ACTIVITY);
                    }
                } else {
                    Toast.makeText(this, R.string.no_installation, Toast.LENGTH_LONG).show();
                }
                return;
            case DispatchActivity.MISSING_MEDIA_ACTIVITY:
                if (resultCode == RESULT_CANCELED) {
                    String title = getString(R.string.media_not_verified);
                    String msg = getString(R.string.skipped_verification_warning);
                    showAlertDialog(
                            StandardAlertDialog.getBasicAlertDialog(
                                    this, title, msg, (dialog, which) -> dismissAlertDialog()));
                } else if (resultCode == RESULT_OK) {
                    Toast.makeText(this, R.string.media_verified, Toast.LENGTH_LONG).show();
                }
                return;
        }
        super.onActivityResult(requestCode, resultCode, intent);
    }

    /**
     * Redirects user to SingleAppManager when they select a particular app.
     */
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position,
                            long id) {
        Intent i = new Intent(getApplicationContext(),
                SingleAppManagerActivity.class);
        // Pass to SingleAppManager the index of the app that was selected, so it knows which
        // app to display information for
        i.putExtra("position", position);
        startActivity(i);
    }

    /**
     * Warns user that the action they are trying to conduct will result in the current
     * session being logged out
     */
    private void triggerLogoutWarning() {
        String title = getString(R.string.logging_out);
        String message = getString(R.string.logout_warning);
        StandardAlertDialog d = new StandardAlertDialog(this, title, message);
        DialogInterface.OnClickListener listener = (dialog, which) -> {
            dismissAlertDialog();
            if (which == AlertDialog.BUTTON_POSITIVE) {
                CommCareApplication.instance().expireUserSession();
                installApp();
            }
        };
        d.setPositiveButton(getString(R.string.ok), listener);
        d.setNegativeButton(getString(R.string.cancel), listener);
        showAlertDialog(d);
    }

    @Override
    protected boolean shouldShowBreadcrumbBar() {
        return false;
    }
}
