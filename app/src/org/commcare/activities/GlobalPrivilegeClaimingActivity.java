package org.commcare.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.dalvik.R;
import org.commcare.preferences.CommCarePreferences;
import org.commcare.preferences.GlobalPrivilegesManager;
import org.commcare.utils.SigningUtil;
import org.commcare.utils.StringUtils;
import org.javarosa.core.services.locale.Localization;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


/**
 * Activity that allows a user to scan a barcode from HQ to enable a global privilege, such as
 * superuser mode
 *
 * @author Aliza Stone (astone@dimagi.com), created 6/9/16.
 */
public class GlobalPrivilegeClaimingActivity extends Activity {

    private static final String TAG = GlobalPrivilegeClaimingActivity.class.getSimpleName();
    public static final String KEY_PRIVILEGE_NAME = "key-privilege-name";

    // activity request codes
    private static final int BARCODE_CAPTURE = 1;

    // menu item IDs
    private static final int DISABLE = 1;

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
        ((TextView)findViewById(R.id.instructions)).setText(GlobalPrivilegesManager.getInstructionsTextId(this.privilegeName));

        CommCarePreferences.addBackButtonToActionBar(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUI();
    }

    private void refreshUI() {
        TextView enabledTextView = (TextView)findViewById(R.id.enabled_textview);
        TextView notEnabledTextView = (TextView)findViewById(R.id.not_enabled_textview);
        Button claimButton = (Button)findViewById(R.id.claim_button);
        TextView instructions = (TextView)findViewById(R.id.instructions);

        if (GlobalPrivilegesManager.isPrivilegeEnabled(this.privilegeName)) {
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
            notEnabledTextView.setText(getNotEnabledText());
        }
        ActivityCompat.invalidateOptionsMenu(this);
    }

    private String getEnabledText() {
        return StringUtils.getStringRobust(this, R.string.privilege_enabled_text, privilegeDisplayName);
    }

    private String getNotEnabledText() {
        return StringUtils.getStringRobust(this, R.string.privilege_not_enabled_text, privilegeDisplayName);
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
                    String[] fields = processScanResult(data.getStringExtra("SCAN_RESULT"));
                    if (fields == null) {
                        privilegeClaimFailed();
                    } else {
                        String flag = fields[0];
                        String username = fields[1];
                        String signature = fields[2];
                        if (checkProperFormAndAuthenticity(flag, username, signature)) {
                            GlobalPrivilegesManager.enablePrivilege(this.privilegeName, username);
                            refreshUI();
                        } else {
                            privilegeClaimFailed();
                        }
                    }
                }
        }
    }

    private void privilegeClaimFailed() {
        Toast.makeText(this,
                StringUtils.getStringRobust(this, R.string.privilege_claim_failed),
                Toast.LENGTH_LONG)
                .show();
    }

    private String[] processScanResult(String scanResult) {
        try {
            JSONObject obj = new JSONObject(scanResult);
            String username = obj.getString("username");
            String flag = obj.getString("flag");
            String signature = obj.getString("signature");
            return new String[] {flag, username, signature};
        } catch (JSONException e) {
            return null;
        }
    }

    private boolean checkProperFormAndAuthenticity(String flag, String username, String signature) {
        if (!this.privilegeName.equals(flag)) {
            Log.d(TAG, "Privilege claim failed because the user scanned a barcode for privilege "
                    + flag + ", but this activity is intended to claim privilege " + privilegeName);
            return false;
        }
        if (!username.endsWith("@dimagi.com")) {
            Log.d(TAG, "Privilege claim failed because the encoded username was not a " +
                    "@dimagi.com email address");
            return false;
        }
        try {
            byte[] signatureBytes = SigningUtil.getBytesFromString(signature);
            String expectedUnsignedValue = getExpectedUnsignedValue(flag, username);
            return SigningUtil.verifyMessageAndBytes(expectedUnsignedValue, signatureBytes) != null;
        } catch (Exception e) {
            Log.d(TAG, "Privilege claim failed because signature verification failed");
            return false;
        }
    }

    private static String getExpectedUnsignedValue(String flag, String username) {
        try {
            JSONObject usernameObject = new JSONObject();
            usernameObject.put("username", username);
            JSONObject flagObject = new JSONObject();
            flagObject.put("flag", flag);

            JSONArray array = new JSONArray();
            array.put(usernameObject);
            array.put(flagObject);
            return array.toString();
        } catch (JSONException e) {
            return "";
        }
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
        menu.findItem(DISABLE).setVisible(GlobalPrivilegesManager.isPrivilegeEnabled(privilegeName));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        switch (item.getItemId()) {
            case DISABLE:
                GlobalPrivilegesManager.disablePrivilege(this.privilegeName);
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