package org.commcare.fragments;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.util.Pair;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.android.nsd.MicroNode;
import org.commcare.android.nsd.NSDDiscoveryTools;
import org.commcare.android.nsd.NsdServiceListener;
import org.commcare.activities.CommCareSetupActivity;
import org.commcare.dalvik.R;
import org.commcare.views.SquareButtonWithText;
import org.commcare.views.dialogs.DialogChoiceItem;
import org.commcare.views.dialogs.PaneledChoiceDialog;
import org.javarosa.core.services.locale.Localization;

import java.util.ArrayList;

/**
 * Fragment for choosing app installation mode (barcode or manual install).
 *
 * @author Daniel Luna (dluna@dimagi.com)
 */
public class SelectInstallModeFragment extends Fragment implements NsdServiceListener {

    private View mFetchHubContainer;
    ArrayList<Pair<String, String>> mLocalApps = new ArrayList<>();

    @Override
    public void onResume() {
        super.onResume();

        NSDDiscoveryTools.registerForNsdServices(this.getContext(), this);
    }

    @Override
    public void onPause() {
        super.onPause();
        NSDDiscoveryTools.unregisterForNsdServices(this);
    }

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

        SquareButtonWithText installFromLocal = (SquareButtonWithText)view.findViewById(R.id.btn_fetch_hub);
        installFromLocal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Activity currentActivity = getActivity();
                if (currentActivity instanceof CommCareSetupActivity) {
                    showLocalAppDialog();
                }
            }
        });

        mFetchHubContainer = view.findViewById(R.id.btn_fetch_hub_container);

        return view;
    }

    private void showLocalAppDialog() {
        ContextThemeWrapper wrapper = new ContextThemeWrapper(getContext(), R.style.DialogBaseTheme);
        final PaneledChoiceDialog chooseApp = new PaneledChoiceDialog(wrapper,
                Localization.get("install.choose.local.app"));

        DialogChoiceItem[] items = new DialogChoiceItem[mLocalApps.size()];
        int count = 0;
        for (final Pair<String, String> app : mLocalApps) {
            DialogChoiceItem item = new DialogChoiceItem(app.first, -1, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Activity currentActivity = getActivity();
                    if (currentActivity instanceof CommCareSetupActivity) {
                        ((CommCareSetupActivity)currentActivity).onURLChosen(app.second);
                    }
                    chooseApp.dismiss();
                }
            });
            items[count] = item;
            count++;
        }
        chooseApp.setChoiceItems(items);
        chooseApp.show();
    }

    @Override
    public synchronized void onMicronodeDiscovery() {
        boolean appsAvailable = false;
        mLocalApps = new ArrayList<>();
        for (MicroNode node : NSDDiscoveryTools.getAvailableMicronodes()) {
            for (Pair<String, String> applications : node.getAvailableApplications()) {
                mLocalApps.add(applications);
                appsAvailable = true;
            }
        }
        Activity activity = getActivity();
        if (appsAvailable && activity != null) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mFetchHubContainer.setVisibility(View.VISIBLE);
                }
            });
        }
    }
}
