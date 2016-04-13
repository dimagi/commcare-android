package org.commcare.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.models.encryption.AndroidSignedPermissionVerifier;
import org.commcare.suite.model.SignedPermission;
import org.commcare.utils.StringUtils;
import org.javarosa.core.services.locale.Localization;


/**
 * Created by amstone326 on 4/7/16.
 */
public class SuperuserAuthActivity extends Activity {

    public static final int BARCODE_CAPTURE = 1;

    private static final int REVOKE_AUTH = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.superuser_auth_view);
        this.findViewById(R.id.authenticate_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                callOutToBarcodeScanner();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUI();
    }

    private void refreshUI() {
        TextView authenticatedTextView = (TextView)this.findViewById(R.id.authenticated_text);
        TextView notAuthenticatedTextView = (TextView)this.findViewById(R.id.not_authenticated_text);
        if (CommCareApplication._().isSuperUserEnabled()) {
            authenticatedTextView.setVisibility(View.VISIBLE);
            notAuthenticatedTextView.setVisibility(View.GONE);
            authenticatedTextView.setText(StringUtils.getStringRobust(
                    this, R.string.authenticated_text,
                    CommCareApplication._().getAuthenticatedSuperuserUsername()));
        } else {
            notAuthenticatedTextView.setVisibility(View.VISIBLE);
            authenticatedTextView.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch(requestCode) {
            case BARCODE_CAPTURE:
                if (resultCode == RESULT_OK) {
                    String usernameAuthenticatedWith = processScanResult(data.getStringExtra("SCAN_RESULT"));
                    if (usernameAuthenticatedWith != null) {
                        CommCareApplication._().enableSuperUserMode(usernameAuthenticatedWith);
                        setResult(RESULT_OK);
                        finish();
                    } else {
                        Toast.makeText(this, "Authentication Failed", Toast.LENGTH_LONG).show();
                    }
                }
                else {
                    Toast.makeText(this, "Authentication Failed", Toast.LENGTH_LONG).show();
                }
                break;
        }
    }

    /**
     *
     * @param scanResult the string returned by the barcode scan callout
     * @return the username authenticated with if superuser auth was successful, or null if it was
     * not successful
     */
    private static String processScanResult(String scanResult) {
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
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, REVOKE_AUTH, 0, Localization.get("superuser.auth.menu.revoke"));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case REVOKE_AUTH:
                CommCareApplication._().disableSuperUserMode();
                refreshUI();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void callOutToBarcodeScanner() {
        Intent i = new Intent("com.google.zxing.client.android.SCAN");
        i.putExtra("SCAN_FORMATS", "QR_CODE");
        startActivityForResult(i, BARCODE_CAPTURE);
    }

}
