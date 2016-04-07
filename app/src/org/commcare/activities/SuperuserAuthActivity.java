package org.commcare.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.utils.StringUtils;


/**
 * Created by amstone326 on 4/7/16.
 */
public class SuperuserAuthActivity extends Activity {

    public static final int BARCODE_CAPTURE = 1;


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
                    // TODO: I think we decided it makes sense to not worry about doing any sort
                    // of encryption of this on the first go-around, since it's even more unlikely
                    // that anyone would try to reverse engineer this, but will double-check
                    String usernameAuthenticatedWith = data.getStringExtra("SCAN_RESULT");
                    CommCareApplication._().enableSuperUserMode(usernameAuthenticatedWith);
                    setResult(RESULT_OK);
                    finish();
                }
                else {
                    Toast.makeText(this, "Authentication Failed", Toast.LENGTH_LONG).show();
                }
                break;
        }
    }

    private void callOutToBarcodeScanner() {
        Intent i = new Intent("com.google.zxing.client.android.SCAN");
        i.putExtra("SCAN_FORMATS", "QR_CODE");
        startActivityForResult(i, BARCODE_CAPTURE);
    }

}
