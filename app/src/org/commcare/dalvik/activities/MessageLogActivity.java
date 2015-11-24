package org.commcare.dalvik.activities;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ListFragment;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import org.commcare.android.adapters.MessageRecordAdapter;
import org.commcare.android.framework.RuntimePermissionRequester;
import org.commcare.android.util.DialogCreationHelpers;
import org.commcare.dalvik.R;

/**
 * @author ctsims
 */
public class MessageLogActivity extends ListFragment
        implements RuntimePermissionRequester {
    MessageRecordAdapter messages;
    private final static int SMS_PERMISSION_REQUEST = 1;

    @Override
    public View onCreateView(LayoutInflater inflater,
            ViewGroup container, Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.log_list, container, false);
        View tv = v.findViewById(R.id.text);
        ((TextView)tv).setText(R.string.message_log);
        return v;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        getPermsAndLoadMessageAdapter();
    }

    private void getPermsAndLoadMessageAdapter() {
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.READ_SMS)) {
                AlertDialog dialog =
                        DialogCreationHelpers.buildPermissionRequestDialog(getActivity(), this,
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
        ActivityCompat.requestPermissions(getActivity(),
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
        messages = new MessageRecordAdapter(getContext(), getActivity().getContentResolver().query(Uri.parse("content://sms"), new String[]{"_id", "address", "date", "type", "read", "thread_id"}, "type=?", new String[]{"1"}, "date" + " DESC"));
        this.setListAdapter(messages);
    }

    /**
     * Stores the path of selected form and finishes.
     */
    @Override
    public void onListItemClick(ListView listView, View view, int position, long id) {
        String number = (String)messages.getItem(position);
        Intent i = new Intent(getActivity(), CallOutActivity.class);
        i.putExtra(CallOutActivity.PHONE_NUMBER, number);
        i.putExtra(CallOutActivity.INCOMING_ACTION, Intent.ACTION_SENDTO);
        startActivity(i);
    }
}
