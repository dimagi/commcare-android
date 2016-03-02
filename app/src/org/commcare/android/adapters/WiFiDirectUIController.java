package org.commcare.android.adapters;

import android.view.View;

import org.commcare.activities.CommCareWiFiDirectActivity;
import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.views.SquareButtonWithText;
import org.javarosa.core.services.locale.Localization;

public class WiFiDirectUIController implements CommCareActivityUIController {
    private SquareButtonWithText sendButton;
    private SquareButtonWithText submitButton;
    private SquareButtonWithText discoverButton;
    private SquareButtonWithText modeButton;

    private final CommCareWiFiDirectActivity activity;

    public WiFiDirectUIController(CommCareWiFiDirectActivity activity) {
        this.activity = activity;
    }

    @Override
    public void setupUI() {
        setupButtonListeners();
    }

    private void setupButtonListeners() {
        modeButton = (SquareButtonWithText)activity.findViewById(R.id.mode);
        modeButton.setText(Localization.get("wifi.direct.change.mode.button"));
        modeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activity.showChangeStateDialog();
            }
        });

        submitButton = (SquareButtonWithText)activity.findViewById(R.id.submit);
        submitButton.setText(Localization.get("wifi.direct.submit.button"));
        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activity.submitFiles();
            }
        });

        discoverButton = (SquareButtonWithText)activity.findViewById(R.id.discover);
        discoverButton.setText(Localization.get("wifi.direct.discover.button"));
        discoverButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activity.discoverPeers();
            }
        });

        sendButton = (SquareButtonWithText)activity.findViewById(R.id.send);
        sendButton.setText(Localization.get("wifi.direct.send.button"));
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activity.prepareFileTransfer();
            }
        });
    }

    @Override
    public void refreshView() {
        switch (activity.getWifiDirectState()) {
            case send:
                sendUIState();
                break;
            case submit:
                submitUIState();
                break;
            case receive:
                receiveUIState();
                break;
            default:
                errorUIState();
                break;
        }
    }

    private void sendUIState() {
        modeButton.setVisibility(View.VISIBLE);
        discoverButton.setVisibility(View.VISIBLE);
        sendButton.setVisibility(View.VISIBLE);

        submitButton.setVisibility(View.GONE);
    }

    private void submitUIState() {
        modeButton.setVisibility(View.VISIBLE);
        submitButton.setVisibility(View.VISIBLE);

        sendButton.setVisibility(View.GONE);
        discoverButton.setVisibility(View.GONE);
    }

    private void receiveUIState() {
        modeButton.setVisibility(View.VISIBLE);

        sendButton.setVisibility(View.GONE);
        discoverButton.setVisibility(View.GONE);
        submitButton.setVisibility(View.GONE);
    }

    private void errorUIState() {
        modeButton.setVisibility(View.GONE);
        sendButton.setVisibility(View.GONE);
        discoverButton.setVisibility(View.GONE);
        submitButton.setVisibility(View.GONE);
    }
}
