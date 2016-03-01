package org.commcare.fragments;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.commcare.interfaces.RuntimePermissionRequester;
import org.commcare.utils.Permissions;
import org.javarosa.core.services.locale.Localization;

/**
 * Block user until they accept necessary permissions. Shows permissions
 * requirement rationale and allows user to ask for permissions again.
 *
 * NOTE: Pressing the 'Never show again' on the permission request dialog
 * results in the Android platform denying future permission requests w/o UI
 * queues, hence show permission request attempt count to user.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class InstallPermissionsFragment extends Fragment {
    private int attemptCount = 0;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.install_permission_requester, container, false);

        TextView neededPermDetails = (TextView)view.findViewById(R.id.perms_rationale_message);
        neededPermDetails.setText(Localization.get("install.perms.rationale.message"));

        Button requestPermsButton = (Button)view.findViewById(R.id.get_perms_button);
        requestPermsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                RuntimePermissionRequester permissionRequester = (RuntimePermissionRequester)getActivity();
                Permissions.acquireAllAppPermissions(getActivity(), permissionRequester,
                        Permissions.ALL_PERMISSIONS_REQUEST);
            }
        });
        requestPermsButton.setText(Localization.get("permission.acquire.required"));

        return view;
    }

    public void updateDeniedState() {
        attemptCount++;
        View currentView = getView();
        if (currentView != null) {
            TextView deniedDetails = (TextView)currentView.findViewById(R.id.needed_perms_message);
            deniedDetails.setText(Localization.get("install.perms.denied.message",
                    new String[]{attemptCount + ""}));
        }
    }
}
