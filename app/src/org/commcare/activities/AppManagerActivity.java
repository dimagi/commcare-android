package org.commcare.activities;

import android.app.AlertDialog;
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
import org.commcare.services.CommCareSessionService;
import org.commcare.utils.MultipleAppsUtil;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.views.dialogs.AlertDialogFactory;
import org.javarosa.core.services.locale.Localization;

/**
 * The activity that starts up when a user launches into the app manager.
 * Displays a list of all installed apps, each of which can be clicked to launch
 * the SingleAppManagerActivity for that app. Also includes a button for
 * installing new apps.
 *
 * @author amstone326
 */

public class AppManagerActivity extends CommCareActivity implements OnItemClickListener {

    private static final String TAG = AppManagerActivity.class.getSimpleName();

    public static final String KEY_LAUNCH_FROM_MANAGER = "from_manager";

    private static final int MULTIPLE_APPS_AUTH = 1;

    private static final int MENU_CONNECTION_DIAGNOSTIC = 0;
    private static final int MENU_ENABLE_MULTIPLE_APPS_SEAT = 1;
    private static final int MENU_REFRESH_PROPERTIES = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.app_manager);
        ((ListView)this.findViewById(R.id.apps_list_view)).setOnItemClickListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshView();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_CONNECTION_DIAGNOSTIC, 0, Localization.get("home.menu.connection.diagnostic"));
        menu.add(0, MENU_ENABLE_MULTIPLE_APPS_SEAT, 1, Localization.get("app.manager.menu.claim.seat"));
        menu.add(0, MENU_REFRESH_PROPERTIES, 2, Localization.get("app.manager.menu.refresh.properties"));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_CONNECTION_DIAGNOSTIC:
                Intent i = new Intent(this, ConnectionDiagnosticActivity.class);
                startActivity(i);
                return true;
            case MENU_ENABLE_MULTIPLE_APPS_SEAT:
                i = new Intent(this, AppManagerSeatClaimingActivity.class);
                startActivityForResult(i, MULTIPLE_APPS_AUTH);
                return true;
            case MENU_REFRESH_PROPERTIES:
                FormAndDataSyncer.refreshPropertiesForAllInstalledApps(this);
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Refresh the list of installed apps
     */
    private void refreshView() {
        ListView lv = (ListView)findViewById(R.id.apps_list_view);
        lv.setAdapter(new AppManagerAdapter(this, android.R.layout.simple_list_item_1,
                MultipleAppsUtil.appRecordArray()));
    }

    /**
     * onClick method for the Install An App button
     *
     * @param v unused argument necessary for the method's use as an onClick handler.
     */
    public void installAppClicked(View v) {
        try {
            CommCareSessionService s = CommCareApplication._().getSession();
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
                boolean installFailed = intent != null && intent.getBooleanExtra(
                        CommCareSetupActivity.KEY_INSTALL_FAILED, false);
                if (resultCode == RESULT_OK && !installFailed) {
                    // If we have just returned from installation and the currently-seated app's
                    // resources are not validated, launch the MM verification activity
                    if (!CommCareApplication._().getCurrentApp().areMMResourcesValidated()) {
                        Intent i = new Intent(this, CommCareVerificationActivity.class);
                        i.putExtra(KEY_LAUNCH_FROM_MANAGER, true);
                        this.startActivityForResult(i, DispatchActivity.MISSING_MEDIA_ACTIVITY);
                    }
                } else {
                    Toast.makeText(this, R.string.no_installation,
                            Toast.LENGTH_LONG).show();
                }
                return;
            case DispatchActivity.MISSING_MEDIA_ACTIVITY:
                if (resultCode == RESULT_CANCELED) {
                    String title = getString(R.string.media_not_verified);
                    String msg = getString(R.string.skipped_verification_warning);
                    AlertDialogFactory.getBasicAlertFactory(this, title, msg, new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }

                    }).showDialog();
                } else if (resultCode == RESULT_OK) {
                    Toast.makeText(this, R.string.media_verified, Toast.LENGTH_LONG).show();
                }
                return;
            case MULTIPLE_APPS_AUTH:
                if (resultCode == RESULT_OK) {
                    Toast.makeText(this, "Authentication Succeeded!", Toast.LENGTH_LONG).show();
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
        AlertDialogFactory factory = new AlertDialogFactory(this, title, message);
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                if (which == AlertDialog.BUTTON_POSITIVE) {
                    CommCareApplication._().expireUserSession();
                    installApp();
                }
            }

        };
        factory.setPositiveButton(getString(R.string.ok), listener);
        factory.setNegativeButton(getString(R.string.cancel), listener);
        factory.showDialog();
    }

}
