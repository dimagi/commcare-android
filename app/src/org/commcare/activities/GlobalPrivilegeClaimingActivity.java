package org.commcare.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import androidx.core.app.ActivityCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;

import org.commcare.dalvik.R;
import org.commcare.modern.util.Pair;
import org.commcare.preferences.GlobalPrivilegesManager;
import org.commcare.utils.GlobalConstants;
import org.commcare.utils.PrivilegesUtility;
import org.commcare.utils.StringUtils;
import org.javarosa.core.services.locale.Localization;


/**
 * Activity that allows a user to scan a barcode from HQ to enable a global privilege, such as
 * superuser mode
 *
 * @author Aliza Stone (astone@dimagi.com), created 6/9/16.
 */
public class GlobalPrivilegeClaimingActivity extends Activity {

    private static final String TAG = GlobalPrivilegeClaimingActivity.class.getSimpleName();

    // activity request codes
    private static final int BARCODE_CAPTURE = 1;

    // menu item IDs
    private static final int DISABLE = 1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.privilege_claiming_view);

        findViewById(R.id.claim_button).setOnClickListener(v -> callOutToBarcodeScanner());

        CommCarePreferenceActivity.addBackButtonToActionBar(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUI();
    }

    private void refreshUI() {
        TextView enabledTextView = findViewById(R.id.enabled_textview);
        TextView notEnabledTextView = findViewById(R.id.not_enabled_textview);
        Button claimButton = findViewById(R.id.claim_button);
        TextView instructions = findViewById(R.id.instructions);

        if (GlobalPrivilegesManager.getEnabledPrivileges().size() > 0) {
            notEnabledTextView.setVisibility(View.GONE);
            claimButton.setVisibility(View.GONE);
            instructions.setVisibility(View.GONE);
            enabledTextView.setVisibility(View.VISIBLE);
            enabledTextView.setText(getEnabledText());
        } else {
            enabledTextView.setVisibility(View.GONE);
            claimButton.setVisibility(View.VISIBLE);
            instructions.setVisibility(View.VISIBLE);
            notEnabledTextView.setVisibility(View.VISIBLE);
        }
        ActivityCompat.invalidateOptionsMenu(this);
    }

    private String getEnabledText() {
        return StringUtils.getStringRobust(this, R.string.privilege_enabled_text,
                GlobalPrivilegesManager.getEnabledPrivilegesString());
    }

    private void callOutToBarcodeScanner() {
        Intent intent = new IntentIntegrator(this)
                .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                .createScanIntent();
        startActivityForResult(intent, BARCODE_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case BARCODE_CAPTURE:
                if (resultCode == RESULT_OK) {
                    String scanResult = data.getStringExtra("SCAN_RESULT");
                    Log.d("SHIVAMMMM", "scan result : " + scanResult);
                    try {
                        Pair<String, String[]> activatedPrivileges =
                                new PrivilegesUtility(GlobalConstants.TRUSTED_SOURCE_PUBLIC_KEY).
                                        processPrivilegePayloadForActivatedPrivileges(scanResult);

                        for (String p : activatedPrivileges.second) {
                            if (!GlobalPrivilegesManager.allGlobalPrivilegesList.contains(p)) {
                                Log.d(TAG, "Request to activate unknown privilege: " + p);
                            } else {
                                GlobalPrivilegesManager.enablePrivilege(p, activatedPrivileges.first);
                            }
                        }
                        refreshUI();

                    } catch (PrivilegesUtility.UnrecognizedPayloadVersionException e) {
                        e.printStackTrace();
                        privilegePayloadVersionTooNew();
                    } catch (PrivilegesUtility.PrivilagePayloadException e) {
                        Log.d("SHIVAMMMM", "scan result : " + e.getLocalizedMessage());
                        e.printStackTrace();
                        privilegeClaimFailed();
                    }
                }
        }
    }

    private void privilegePayloadVersionTooNew() {
        Toast.makeText(this,
                StringUtils.getStringRobust(this, R.string.privilege_claim_bad_version),
                Toast.LENGTH_LONG)
                .show();
    }


    private void privilegeClaimFailed() {
        Toast.makeText(this,
                StringUtils.getStringRobust(this, R.string.privilege_claim_failed),
                Toast.LENGTH_LONG)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, DISABLE, 0, Localization.get("menu.privilege.claim.disable"));
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(DISABLE).setVisible(GlobalPrivilegesManager.getEnabledPrivileges().size() > 0);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        switch (item.getItemId()) {
            case DISABLE:
                for (String privilege : GlobalPrivilegesManager.getEnabledPrivileges()) {
                    GlobalPrivilegesManager.disablePrivilege(privilege);
                }
                refreshUI();
                return true;
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                finish();
                return true;
        }
        return false;
    }

}