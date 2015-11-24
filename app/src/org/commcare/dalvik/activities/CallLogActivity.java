package org.commcare.dalvik.activities;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.provider.CallLog.Calls;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.ListFragment;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import org.commcare.android.adapters.CallRecordAdapter;
import org.commcare.android.framework.RuntimePermissionRequester;
import org.commcare.android.util.DialogCreationHelpers;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;

/**
 * @author ctsims
 */
public class CallLogActivity extends ListFragment implements RuntimePermissionRequester {
    CallRecordAdapter calls;

    private static final int PHONE_STATE_PERMISSIONS_REQUEST = 1;

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.log_list, container, false);
        View tv = v.findViewById(R.id.text);
        ((TextView)tv).setText(R.string.call_log);
        return v;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        getPermsAndLoadCallLogAdapter();
    }

    private void getPermsAndLoadCallLogAdapter() {
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.READ_CALL_LOG)) {
                AlertDialog dialog =
                        DialogCreationHelpers.buildPermissionRequestDialog(getActivity(), this,
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
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void requestNeededPermissions() {
        ActivityCompat.requestPermissions(getActivity(),
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
        if (calls == null && ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
            Cursor callCursor = null;
            try {
                callCursor = getActivity().getContentResolver().query(android.provider.CallLog.Calls.CONTENT_URI, null, null, null, Calls.DATE + " DESC");
                calls = new CallRecordAdapter(getActivity(), callCursor);
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
    public void onListItemClick(ListView listView, View view, int position, long id) {
        String number = (String)calls.getItem(position);

        Intent i = new Intent(getActivity(), CallOutActivity.class);
        i.putExtra(CallOutActivity.PHONE_NUMBER, number);
        i.putExtra(CallOutActivity.INCOMING_ACTION, Intent.ACTION_SENDTO);
        startActivity(i);
    }
}
