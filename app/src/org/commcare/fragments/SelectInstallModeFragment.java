package org.commcare.fragments;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;

import org.commcare.CommCareApplication;
import org.commcare.CommCareNoficationManager;
import org.commcare.activities.CommCareActivity;
import org.commcare.activities.CommCareSetupActivity;
import org.commcare.connect.ConnectManager;
import org.commcare.android.nsd.MicroNode;
import org.commcare.android.nsd.NSDDiscoveryTools;
import org.commcare.android.nsd.NsdServiceListener;
import org.commcare.dalvik.R;
import org.commcare.views.RectangleButtonWithText;
import org.commcare.views.SquareButtonWithText;
import org.commcare.views.dialogs.DialogChoiceItem;
import org.commcare.views.dialogs.PaneledChoiceDialog;
import org.commcare.views.widgets.WidgetUtils;
import org.javarosa.core.services.locale.Localization;

import java.util.ArrayList;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

/**
 * Fragment for choosing app installation mode (barcode or manual install).
 *
 * @author Daniel Luna (dluna@dimagi.com)
 */
public class SelectInstallModeFragment extends Fragment implements NsdServiceListener {

    private View mFetchHubContainer;
    private TextView mErrorMessageView;
    private RectangleButtonWithText mViewErrorButton;
    private View mViewErrorContainer;
    private Button mConnectButton;
    private TextView mOrText;
    private ArrayList<MicroNode.AppManifest> mLocalApps = new ArrayList<>();

    @Override
    public void onResume() {
        super.onResume();
        NSDDiscoveryTools.registerForNsdServices(this.getContext(), this);
        if (!CommCareApplication.notificationManager().messagesForCommCareArePending()) {
            mViewErrorContainer.setVisibility(View.GONE);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        NSDDiscoveryTools.unregisterForNsdServices(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.select_install_mode_fragment, container, false);

        TextView setupMsg = view.findViewById(R.id.str_setup_message);
        setupMsg.setText(Localization.get("install.barcode.top"));

        mConnectButton = view.findViewById(R.id.connect_login_button);
        mOrText = view.findViewById(R.id.login_or);

        TextView setupMsg2 = view.findViewById(R.id.str_setup_message_2);
        setupMsg2.setText(Localization.get("install.barcode.bottom"));

        SquareButtonWithText scanBarcodeButton = view.findViewById(R.id.btn_fetch_uri);
        final View barcodeButtonContainer = view.findViewById(R.id.btn_fetch_uri_container);

        scanBarcodeButton.setOnClickListener(v -> {
            try {
                AppCompatActivity currentActivity = (AppCompatActivity)getActivity();
                if (currentActivity instanceof CommCareSetupActivity) {
                    ((CommCareSetupActivity)currentActivity).clearErrorMessage();
                }
                Intent intent = WidgetUtils.createScanIntent(getContext(), BarcodeFormat.QR_CODE.name());
                currentActivity.startActivityForResult(intent, CommCareSetupActivity.BARCODE_CAPTURE);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(getActivity(), "No barcode scanner installed on phone!", Toast.LENGTH_SHORT).show();
                barcodeButtonContainer.setVisibility(View.GONE);
            }
        });

        SquareButtonWithText enterURLButton = view.findViewById(R.id.enter_app_location);
        enterURLButton.setOnClickListener(v -> {
            SetupEnterURLFragment enterUrl = new SetupEnterURLFragment();
            AppCompatActivity currentActivity = (AppCompatActivity)getActivity();
            if (currentActivity instanceof CommCareSetupActivity) {
                ((CommCareSetupActivity)currentActivity).setUiState(CommCareSetupActivity.UiState.IN_URL_ENTRY);
                ((CommCareSetupActivity)currentActivity).clearErrorMessage();
                ((CommCareSetupActivity)currentActivity).checkManagedConfiguration();
            }
            // if we use getChildFragmentManager, we're going to have a crash
            FragmentManager fm = getActivity().getSupportFragmentManager();
            FragmentTransaction ft = fm.beginTransaction();
            ft.remove(SelectInstallModeFragment.this);
            ft.replace(SelectInstallModeFragment.this.getId(), enterUrl);
            ft.commit();
        });

        SquareButtonWithText installFromLocal = view.findViewById(R.id.btn_fetch_hub);
        installFromLocal.setOnClickListener(v -> {
            AppCompatActivity currentActivity = (AppCompatActivity)getActivity();
            if (currentActivity instanceof CommCareSetupActivity) {
                showLocalAppDialog();
            }
        });

        mErrorMessageView = view.findViewById(R.id.install_error_text);

        mViewErrorContainer = view.findViewById(R.id.btn_view_errors_container);

        mViewErrorButton = view.findViewById(R.id.btn_view_errors);

        mViewErrorButton.setText(Localization.get("error.button.text"));

        mViewErrorButton.setOnClickListener(view1 -> CommCareNoficationManager.performIntentCalloutToNotificationsView((AppCompatActivity)getActivity()));
        showOrHideErrorMessage();

        mFetchHubContainer = view.findViewById(R.id.btn_fetch_hub_container);

        InputMethodManager inputManager = (InputMethodManager)getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow(view.getWindowToken(), 0);

        return view;
    }

    private void showLocalAppDialog() {
        ContextThemeWrapper wrapper = new ContextThemeWrapper(getContext(), R.style.DialogBaseTheme);
        final PaneledChoiceDialog chooseApp = new PaneledChoiceDialog(wrapper,
                Localization.get("install.choose.local.app"));

        DialogChoiceItem[] items = new DialogChoiceItem[mLocalApps.size()];
        int count = 0;
        for (final MicroNode.AppManifest app : mLocalApps) {
            DialogChoiceItem item = new DialogChoiceItem(app.getName(), -1, v -> {
                AppCompatActivity currentActivity = (AppCompatActivity)getActivity();
                if (currentActivity instanceof CommCareSetupActivity) {
                    ((CommCareSetupActivity)currentActivity).onURLChosen(app.getLocalUrl());
                }
                ((CommCareActivity)getActivity()).dismissAlertDialog();
            });
            items[count] = item;
            count++;
        }
        chooseApp.setChoiceItems(items);
        ((CommCareActivity)getActivity()).showAlertDialog(chooseApp);
    }

    @Override
    public synchronized void onMicronodeDiscovery() {
        boolean appsAvailable = false;
        mLocalApps = new ArrayList<>();
        for (MicroNode node : NSDDiscoveryTools.getAvailableMicronodes()) {
            for (MicroNode.AppManifest application : node.getAvailableApplications()) {
                mLocalApps.add(application);
                appsAvailable = true;
            }
        }
        AppCompatActivity activity = (AppCompatActivity)getActivity();
        if (appsAvailable && activity != null) {
            getActivity().runOnUiThread(() -> mFetchHubContainer.setVisibility(View.VISIBLE));
        }
    }

    public void showOrHideErrorMessage() {
        AppCompatActivity currentActivity = (AppCompatActivity)getActivity();
        if (currentActivity instanceof CommCareSetupActivity) {
            String msg = ((CommCareSetupActivity)currentActivity).getErrorMessageToDisplay();
            if (msg != null && !"".equals(msg)) {
                mErrorMessageView.setText(msg);
                mErrorMessageView.setVisibility(View.VISIBLE);
                if (((CommCareSetupActivity)this.getActivity()).shouldShowNotificationErrorButton()
                        && CommCareApplication.notificationManager().messagesForCommCareArePending()) {
                    mViewErrorContainer.setVisibility(View.VISIBLE);
                }
            } else {
                mErrorMessageView.setVisibility(View.GONE);
                mViewErrorContainer.setVisibility(View.GONE);
            }
        }
    }

    public void updateConnectButton(boolean connectEnabled, View.OnClickListener listener) {
        if(mConnectButton != null) {
            boolean enabled = connectEnabled && ConnectManager.shouldShowConnectButton();

            if (enabled) {
                mConnectButton.setOnClickListener(listener);
            }

            mConnectButton.setVisibility(enabled ? View.VISIBLE : View.GONE);
            mOrText.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }
    }
}
