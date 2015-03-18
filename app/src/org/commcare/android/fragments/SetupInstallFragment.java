package org.commcare.android.fragments;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import org.commcare.android.framework.UiElement;
import org.commcare.android.view.SquareButtonWithText;
import org.commcare.dalvik.R;
import org.commcare.dalvik.activities.CommCareSetupActivity;

/**
 * Created by dancluna on 3/17/15.
 */
public class SetupInstallFragment extends Fragment {
    @UiElement(R.id.btn_fetch_uri)
    Button scanBarcodeButton;

    @UiElement(R.id.enter_app_location)
    Button enterURLButton;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.setup_install, container, false);
        scanBarcodeButton = (Button)view.findViewById(R.id.btn_fetch_uri);
        enterURLButton = (Button)view.findViewById(R.id.enter_app_location);
        scanBarcodeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent i = new Intent("com.google.zxing.client.android.SCAN");
                    //Barcode only
                    i.putExtra("SCAN_FORMATS","QR_CODE");
                    getActivity().startActivityForResult(i, CommCareSetupActivity.BARCODE_CAPTURE);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(getActivity(), "No barcode scanner installed on phone!", Toast.LENGTH_SHORT).show();
                    scanBarcodeButton.setVisibility(View.GONE);
                }
            }
        });
        enterURLButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SetupEnterURLFragment enterUrl = new SetupEnterURLFragment();
                // if we use getChildFragmentManager, we're going to have a crash
                FragmentManager fm = getActivity().getSupportFragmentManager();
                FragmentTransaction ft = fm.beginTransaction();
                ft.replace(SetupInstallFragment.this.getId(), enterUrl);
                ft.addToBackStack(null);
                ft.commit();
            }
        });
        return view;
    }
}