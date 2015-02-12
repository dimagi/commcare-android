package org.commcare.dalvik.activities;

import org.commcare.android.adapters.AppManagerAdapter;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.services.CommCareSessionService;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;

/**
 * 
 * The activity that starts up when a user launches into the app manager.
 * Displays a list of all installed apps, each of which can be clicked to launch
 * the SingleAppManagerAcitivity for that app. Also includes a button for
 * installing new apps.
 * 
 * @author amstone326
 * 
 */

public class AppManagerActivity extends Activity implements OnItemClickListener {

    public static final String KEY_LAUNCH_FROM_MANAGER = "from_manager";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.app_manager);
        ((ListView) this.findViewById(R.id.apps_list_view)).setOnItemClickListener(this);
    }

    private void refreshView() {
        ListView lv = (ListView) findViewById(R.id.apps_list_view);
        lv.setAdapter(new AppManagerAdapter(this, android.R.layout.simple_list_item_1, 
                CommCareApplication._().appRecordArray()));
    }

    public void onResume() {
        super.onResume();
        refreshView();
    }

    public void installAppClicked(View v) {
        try {
            CommCareSessionService s = CommCareApplication._().getSession();
            if (s.isLoggedIn()) {
                triggerLogoutWarning();
            } else {
                installApp();
            }
        } catch (SessionUnavailableException e) {
            installApp();
        }
    }

    public void installApp() {
        CommCareApplication._().logout();
        Intent i = new Intent(getApplicationContext(), CommCareSetupActivity.class);
        i.putExtra(KEY_LAUNCH_FROM_MANAGER, true);
        this.startActivityForResult(i, CommCareHomeActivity.INIT_APP);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch (requestCode) {
        case CommCareHomeActivity.INIT_APP:
            if (resultCode == RESULT_OK) {
                if (!CommCareApplication._().getCurrentApp().areResourcesValidated()) {
                    Intent i = new Intent(this, CommCareVerificationActivity.class);
                    i.putExtra(KEY_LAUNCH_FROM_MANAGER, true);
                    this.startActivityForResult(i, CommCareHomeActivity.MISSING_MEDIA_ACTIVITY);
                } else {
                    Toast.makeText(this, "New app installed successfully", Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, "No app was installed!", Toast.LENGTH_LONG).show();
            }
            break;
        case CommCareHomeActivity.MISSING_MEDIA_ACTIVITY:
            if (resultCode == RESULT_CANCELED) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Media Not Verified");
                builder.setMessage(R.string.skipped_verification_warning)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }

                    });
                AlertDialog dialog = builder.create();
                dialog.show();
            } else if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Media Validated!", Toast.LENGTH_LONG).show();
            }
            break;
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position,
            long id) {
        Intent i = new Intent(getApplicationContext(),
                SingleAppManagerActivity.class);
        i.putExtra("position", position);
        startActivity(i);
    }

    public void triggerLogoutWarning() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Logging out your app");
        builder.setMessage(R.string.logout_warning)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        installApp();
                    }

                })
                .setNegativeButton("CANCEL", new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }

                });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

}