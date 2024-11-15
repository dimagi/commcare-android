package org.commcare.activities.connect;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.commcare.activities.DispatchActivity;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ActivityHquserInviteBinding;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

public class HQUserInviteActivity extends AppCompatActivity {

    private ActivityHquserInviteBinding binding;
    String domain;
    String inviteCode;
    String username;
    String callBackURL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHquserInviteBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Intent intent = getIntent();
        Uri data = intent.getData();
        if (data != null) {
            List<String> pathSegments = data.getPathSegments();
            if (pathSegments.size() >= 3) {
                callBackURL = pathSegments.get(1);
                username = pathSegments.get(2);
                inviteCode = pathSegments.get(3);
                domain = pathSegments.get(4);
            }
        }
        binding.btnAcceptInvitation.setOnClickListener(view -> handleInvitation(callBackURL, inviteCode, true));

        binding.btnDeniedInvitation.setOnClickListener(view -> finish());

        binding.tvHqInvitationHeaderTitle.setText(getString(R.string.connect_hq_invitation_heading,username));
    }

    private void handleInvitation(String callBackUrl, String inviteCode, boolean acceptStatus) {
        IApiCallback callback = new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                try {
                    String responseAsString = new String(StreamsUtil.inputStreamToByteArray(responseData));
                    if (responseAsString.length() > 0) {
                        startActivity(new Intent(HQUserInviteActivity.this, DispatchActivity.class));
                        finish();
                    }
                } catch (IOException e) {
                    Logger.exception("Parsing return from OTP request", e);
                }
            }

            @Override
            public void processFailure(int responseCode, IOException e) {
                String message = "";
                if (responseCode > 0) {
                    message = String.format(Locale.getDefault(), "(%d)", responseCode);
                } else if (e != null) {
                    message = e.toString();
                }
                setErrorMessage("Error requesting SMS code" + message);
            }

            @Override
            public void processNetworkFailure() {
                setErrorMessage(getString(R.string.recovery_network_unavailable));
            }

            @Override
            public void processOldApiError() {
                setErrorMessage(getString(R.string.recovery_network_outdated));
            }
        };

        boolean isBusy = !ApiConnectId.hqUserInvitation(HQUserInviteActivity.this, callBackUrl, inviteCode, callback);
        if (isBusy) {
            Toast.makeText(HQUserInviteActivity.this, R.string.busy_message, Toast.LENGTH_SHORT).show();
        }
    }

    public void setErrorMessage(String message) {
        if (message == null) {
            binding.connectPhoneVerifyError.setVisibility(View.GONE);
        } else {
            binding.connectPhoneVerifyError.setVisibility(View.VISIBLE);
            binding.connectPhoneVerifyError.setText(message);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
