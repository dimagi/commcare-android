package org.commcare.dalvik.activities;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.ListView;

import org.commcare.android.adapters.MessageRecordAdapter;
import org.commcare.android.framework.RuntimePermissionRequester;
import org.commcare.android.util.DialogCreationHelpers;
import org.commcare.dalvik.R;

/**
 * @author ctsims
 */
public class MessageLogActivity extends ListActivity
        implements RuntimePermissionRequester {
    MessageRecordAdapter messages;
    private final static int SMS_PERMISSION_REQUEST = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setTitle(getString(R.string.application_name) + " > " + R.string.message_log);
        
        getPermsAndLoadMessageAdapter();
    }

    private void getPermsAndLoadMessageAdapter() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_SMS)) {
                AlertDialog dialog =
                        DialogCreationHelpers.buildPermissionRequestDialog(this, this,
                                "Permissions to view sms messages",
                                "To use CommCare's message log functionality please grant it permission to view message history.");
                dialog.show();
            } else {
                requestNeededPermissions();
            }
        } else {
            loadMessageAdapter();
        }
    }

    @Override
    public void requestNeededPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.READ_SMS},
                SMS_PERMISSION_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == SMS_PERMISSION_REQUEST) {
            for (int i = 0; i < permissions.length; i++) {
                if (Manifest.permission.READ_SMS.equals(permissions[i]) &&
                        grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    loadMessageAdapter();
                }
            }
        }
    }

    /**
     * Get form list from database and insert into view.
     */
    private void loadMessageAdapter() {
        messages = new MessageRecordAdapter(this, this.getContentResolver().query(Uri.parse("content://sms"), new String[]{"_id", "address", "date", "type", "read", "thread_id"}, "type=?", new String[]{"1"}, "date" + " DESC"));
        this.setListAdapter(messages);
    }

    /**
     * Stores the path of selected form and finishes.
     */
    @Override
    protected void onListItemClick(ListView listView, View view, int position, long id) {
        String number = (String)messages.getItem(position);
        Intent i = new Intent(this, CallOutActivity.class);
        i.putExtra(CallOutActivity.PHONE_NUMBER, number);
        i.putExtra(CallOutActivity.INCOMING_ACTION, Intent.ACTION_SENDTO);
        startActivity(i);
    }
}
