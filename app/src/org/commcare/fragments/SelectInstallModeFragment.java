package org.commcare.fragments;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.activities.CommCareSetupActivity;
import org.commcare.dalvik.R;
import org.commcare.views.SquareButtonWithText;
import org.javarosa.core.services.locale.Localization;

/**
 * Fragment for choosing app installation mode (barcode or manual install).
 *
 * @author Daniel Luna (dluna@dimagi.com)
 */
public class SelectInstallModeFragment extends Fragment {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.select_install_mode_fragment, container, false);

        TextView setupMsg = (TextView)view.findViewById(R.id.str_setup_message);
        setupMsg.setText(Localization.get("install.barcode.top"));

        TextView setupMsg2 = (TextView)view.findViewById(R.id.str_setup_message_2);
        setupMsg2.setText(Localization.get("install.barcode.bottom"));

        SquareButtonWithText scanBarcodeButton = (SquareButtonWithText)view.findViewById(R.id.btn_fetch_uri);
        final View barcodeButtonContainer = view.findViewById(R.id.btn_fetch_uri_container);
        scanBarcodeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent i = new Intent("com.google.zxing.client.android.SCAN");
                    //Barcode only
                    i.putExtra("SCAN_FORMATS", "QR_CODE");
                    getActivity().startActivityForResult(i, CommCareSetupActivity.BARCODE_CAPTURE);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(getActivity(), "No barcode scanner installed on phone!", Toast.LENGTH_SHORT).show();
                    barcodeButtonContainer.setVisibility(View.GONE);
                }
            }
        });

        SquareButtonWithText enterURLButton = (SquareButtonWithText)view.findViewById(R.id.enter_app_location);
        enterURLButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SetupEnterURLFragment enterUrl = new SetupEnterURLFragment();
                Activity currentActivity = getActivity();
                if (currentActivity instanceof CommCareSetupActivity) {
                    ((CommCareSetupActivity)currentActivity).setUiState(CommCareSetupActivity.UiState.IN_URL_ENTRY);
                }
                // if we use getChildFragmentManager, we're going to have a crash
                FragmentManager fm = getActivity().getSupportFragmentManager();
                FragmentTransaction ft = fm.beginTransaction();
                ft.replace(SelectInstallModeFragment.this.getId(), enterUrl);
                ft.addToBackStack(null);
                ft.commit();
            }
        });

        return view;
    }
}