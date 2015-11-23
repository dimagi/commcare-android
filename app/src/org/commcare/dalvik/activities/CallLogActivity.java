package org.commcare.dalvik.activities;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CallLog.Calls;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;

import org.commcare.android.adapters.CallRecordAdapter;
import org.commcare.android.adapters.MessageRecordAdapter;
import org.commcare.android.framework.RuntimePermissionRequester;
import org.commcare.android.util.DialogCreationHelpers;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.services.storage.Persistable;

/**
 * @author ctsims
 */
public class CallLogActivity<T extends Persistable>
        extends ListActivity implements RuntimePermissionRequester {
    CallRecordAdapter calls;
    MessageRecordAdapter messages;
    
    private static final String EXTRA_MESSAGES = "cla_messages";
    private static final int PHONE_STATE_PERMISSIONS_REQUEST = 1;
    
    boolean isMessages = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setTitle(getString(R.string.application_name) + " > " + R.string.call_log);
        
        isMessages = getIntent().getBooleanExtra(EXTRA_MESSAGES, false);

        if (acquirePhoneStatePermissions()) {
            refreshView();
        }
    }

    private boolean acquirePhoneStatePermissions() {
        if (missingPhonePerms()) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_SMS) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_PHONE_STATE) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_CALL_LOG)) {
                AlertDialog dialog =
                        DialogCreationHelpers.buildPermissionRequestDialog(this, this,
                                "Permissions for call & message logging",
                                "To use CommCare's call & message logging functionality please allow enable phone call and sms permissions.");
                dialog.show();
            } else {
                requestNeededPermissions();
            }
            return false;
        }
        return true;
    }

    private boolean missingPhonePerms() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void requestNeededPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.READ_SMS, Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG},
                PHONE_STATE_PERMISSIONS_REQUEST);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(EXTRA_MESSAGES, isMessages);
    }
    
    @Override
    protected void onRestoreInstanceState(Bundle inState) {
        super.onRestoreInstanceState(inState);
        inState.getBoolean(EXTRA_MESSAGES, false);
    }

    /**
     * Get form list from database and insert into view.
     */
    private void refreshView() {
        ListAdapter adapter;
        if (isMessages) {
            if (messages == null && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
                messages = new MessageRecordAdapter(this, this.getContentResolver().query(Uri.parse("content://sms"),new String[] {"_id","address","date","type","read","thread_id"}, "type=?", new String[] {"1"}, "date" + " DESC"));
            }
            adapter = messages;
        } else {
            if (calls == null && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
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
            adapter = calls;
        }

        this.setListAdapter(adapter);
    }

    /**
     * Stores the path of selected form and finishes.
     */
    @Override
    protected void onListItemClick(ListView listView, View view, int position, long id) {
        if (isMessages) {
            String number = (String)messages.getItem(position);
            Intent i = new Intent(this, CallOutActivity.class);
            i.putExtra(CallOutActivity.PHONE_NUMBER, number);
            i.putExtra(CallOutActivity.INCOMING_ACTION, Intent.ACTION_SENDTO);
            startActivity(i);
        } else {
            String number = (String)calls.getItem(position);
            Intent detail = CommCareApplication._().getCallListener().getDetailIntent(this,number);
            if(detail == null) {
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

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        int permsGranted = 0;
        if (requestCode == PHONE_STATE_PERMISSIONS_REQUEST) {
            for (int i = 0; i < permissions.length; i++) {
                if ((Manifest.permission.READ_SMS.equals(permissions[i]) ||
                        Manifest.permission.READ_PHONE_STATE.equals(permissions[i])) &&
                        grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    permsGranted++;
                }
            }

            if (permsGranted == 2) {
                refreshView();
            }
        }
    }
}
