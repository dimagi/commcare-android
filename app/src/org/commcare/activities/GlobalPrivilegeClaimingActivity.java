package org.commcare.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.preferences.GlobalPrivilegesManager;
import org.commcare.utils.StringUtils;
import org.javarosa.core.services.locale.Localization;
import org.json.JSONException;
import org.json.JSONObject;


/**
 * Created by amstone326 on 4/7/16.
 */
public class GlobalPrivilegeClaimingActivity extends Activity {

    public static final String KEY_PRIVILEGE_NAME = "key-privilege-name";

    // activity request codes
    public static final int BARCODE_CAPTURE = 1;

    // menu item IDs
    private static final int REVOKE_AUTH = 1;

    // the name of the privilege that this activity is set up to claim/revoke
    private String privilegeName;

    private String privilegeDisplayName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        privilegeName = getIntent().getStringExtra(KEY_PRIVILEGE_NAME);
        if (!GlobalPrivilegesManager.allGlobalPrivilegesList.contains(privilegeName)) {
            setResult(RESULT_CANCELED);
            finish();
        }
        privilegeDisplayName = GlobalPrivilegesManager.getPrivilegeDisplayName(privilegeName);

        setContentView(R.layout.privilege_claiming_view);
        findViewById(R.id.claim_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                callOutToBarcodeScanner();
            }
        });
        ((TextView)findViewById(R.id.instructions)).setText(getInstructionsText());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUI();
    }

    private void refreshUI() {
        TextView enabledTextView = (TextView)this.findViewById(R.id.enabled_textview);
        TextView notEnabledTextView = (TextView)this.findViewById(R.id.not_enabled_textview);

        boolean privilegeIsEnabled = GlobalPrivilegesManager.isPrivilegeEnabled(this.privilegeName);
        if (privilegeIsEnabled) {
            notEnabledTextView.setVisibility(View.GONE);
            enabledTextView.setVisibility(View.VISIBLE);
            enabledTextView.setText(getEnabledText());
        } else {
            enabledTextView.setVisibility(View.GONE);
            notEnabledTextView.setVisibility(View.VISIBLE);
            notEnabledTextView.setText(getNotEnabledText());
        }
    }

    private String getEnabledText() {
        return StringUtils.getStringRobust(this, R.string.privilege_enabled_text, privilegeDisplayName);
    }

    private String getNotEnabledText() {
        return StringUtils.getStringRobust(this, R.string.privilege_not_enabled_text, privilegeDisplayName);
    }

    private String getInstructionsText() {
        return StringUtils.getStringRobust(this, R.string.claim_privilege_instructions,
                new String[] {privilegeDisplayName, GlobalPrivilegesManager.getInstructionsText(this.privilegeName)});
    }

    private void callOutToBarcodeScanner() {
        Intent i = new Intent("com.google.zxing.client.android.SCAN");
        i.putExtra("SCAN_FORMATS", "QR_CODE");
        startActivityForResult(i, BARCODE_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch(requestCode) {
            case BARCODE_CAPTURE:
                if (resultCode == RESULT_OK) {
                    processJsonScanResult(data.getStringExtra("SCAN_RESULT"));
                    /*String usernameAuthenticatedWith = processScanResult(data.getStringExtra("SCAN_RESULT"));
                    if (usernameAuthenticatedWith != null) {
                        CommCareApplication._().enableSuperUserMode(usernameAuthenticatedWith);
                        setResult(RESULT_OK);
                        finish();
                    } else {
                        Toast.makeText(this, "Authentication Failed", Toast.LENGTH_LONG).show();
                    }*/
                }
                else {
                    Toast.makeText(this, "Authentication Failed", Toast.LENGTH_LONG).show();
                }
                break;
        }
    }

    private static void processJsonScanResult(String scanResult) {
        try {
            JSONObject obj = new JSONObject(scanResult);
            String URL = obj.getString("URL");
            String username = obj.getString("username");
            String signature = obj.getString("signature");
            System.out.println(URL);
            System.out.println(username);
            System.out.println(signature);
        } catch (JSONException e) {

        }
    }

    /**
     *
     * @return the username authenticated with if superuser auth was successful, or null if it was
     * not successful
     */
    /*private static String processScanResult(String scanResult) {
        String[] valueAndSignature = scanResult.split(" ");
        if (valueAndSignature.length != 2) {
            return null;
        }
        SignedPermission superuserPermission = new SignedPermission(
                SignedPermission.KEY_SUPERUSER_AUTHENTICATION,
                valueAndSignature[0],
                valueAndSignature[1]);
        if (superuserPermission.verifyValue(new AndroidSignedPermissionVerifier())) {
            return valueAndSignature[0];
        }
        return null;
    }*/

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, REVOKE_AUTH, 0, Localization.get("superuser.auth.menu.revoke"));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        switch (item.getItemId()) {
            case REVOKE_AUTH:
                GlobalPrivilegesManager.disablePrivilege(this.privilegeName);
                refreshUI();
        }
        return true;
    }

}