package org.commcare.dalvik.activities;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.CallLog.Calls;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.ListView;

import org.commcare.android.adapters.CallRecordAdapter;
import org.commcare.android.framework.RuntimePermissionRequester;
import org.commcare.android.util.DialogCreationHelpers;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;

/**
 * @author ctsims
 */
public class CallLogActivity extends ListActivity implements RuntimePermissionRequester {
    CallRecordAdapter calls;

    private static final int PHONE_STATE_PERMISSIONS_REQUEST = 1;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setTitle(getString(R.string.application_name) + " > " + R.string.call_log);
        
        getPermsAndLoadCallLogAdapter();
    }

    private void getPermsAndLoadCallLogAdapter() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_CALL_LOG)) {
                AlertDialog dialog =
                        DialogCreationHelpers.buildPermissionRequestDialog(this, this,
                                "Permissions to view call logs",
                                "To use CommCare's call log functionality please grant it permission to view call history.");
                dialog.show();
            } else {
                requestNeededPermissions();
            }
        } else {
            loadCallLogAdapter();
        }
    }

    @Override
    public void requestNeededPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.READ_CALL_LOG},
                PHONE_STATE_PERMISSIONS_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == PHONE_STATE_PERMISSIONS_REQUEST) {
            for (int i = 0; i < permissions.length; i++) {
                if (Manifest.permission.READ_CALL_LOG.equals(permissions[i]) &&
                        grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    loadCallLogAdapter();
                }
            }
        }
    }

    /**
     * Get form list from database and insert into view.
     */
    private void loadCallLogAdapter() {
        if (calls == null && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
            Cursor callCursor = null;
            try {
                callCursor = getContentResolver().query(android.provider.CallLog.Calls.CONTENT_URI, null, null, null, Calls.DATE + " DESC");
                calls = new CallRecordAdapter(this, callCursor);
            } finally {
                if (callCursor != null && !callCursor.isClosed()) {
                    callCursor.close();
                }
            }
        }
        this.setListAdapter(calls);
    }

    /**
     * Stores the path of selected form and finishes.
     */
    @Override
    protected void onListItemClick(ListView listView, View view, int position, long id) {
        String number = (String)calls.getItem(position);
        Intent detail = CommCareApplication._().getCallListener().getDetailIntent(this, number);
        if (detail == null) {
            //Start normal callout activity
            Intent i = new Intent(this, CallOutActivity.class);
            i.putExtra(CallOutActivity.PHONE_NUMBER, number);
            i.putExtra(CallOutActivity.INCOMING_ACTION, Intent.ACTION_SENDTO);
            startActivity(i);
        } else {
            startActivity(detail);
        }
    }
}
