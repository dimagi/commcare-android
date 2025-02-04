package org.commcare.activities.connect;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import org.commcare.activities.CommCareActivity;
import org.commcare.activities.DispatchActivity;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectManager;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ActivityHquserInviteBinding;
import org.commcare.utils.CrashUtil;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;

import java.io.IOException;
import java.io.InputStream;

public class HQUserInviteActivity extends CommCareActivity<HQUserInviteActivity> {

    private ActivityHquserInviteBinding binding;
    String domain;
    String inviteCode;
    String username;
    String callBackURL;
    String connectUserName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Logger.log("HQInvite", "Entering HQ user invite activity");

        binding = ActivityHquserInviteBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Intent intent = getIntent();
        Uri data = intent.getData();
        if (data != null) {
            callBackURL = data.getQueryParameter("callback_url");
            username = data.getQueryParameter("hq_username");
            inviteCode = data.getQueryParameter("invite_code");
            domain = data.getQueryParameter("hq_domain");
            connectUserName = data.getQueryParameter("connect_username");
        }
        handleButtons();
    }

    private void handleButtons() {
        ConnectManager.init(this);

        ConnectUserRecord user = ConnectManager.getUser(this);
        boolean isTokenPresent = ConnectManager.isConnectIdConfigured();
        boolean isCorrectUser = user == null || user.getUserId().equals(connectUserName);

        if (isCorrectUser) {
            binding.tvHqInvitationHeaderTitle.setText(isTokenPresent
                    ? getString(R.string.connect_hq_invitation_heading, username)
                    : getString(R.string.connect_hq_invitation_connectId_not_configure));
            setButtonVisibility(isTokenPresent);
            setButtonListeners(isTokenPresent);
        } else {
            binding.tvHqInvitationHeaderTitle.setText(getString(R.string.connect_hq_invitation_wrong_user));
            setButtonVisibility(false);
        }
    }

    private void setButtonVisibility(boolean visible) {
        binding.btnAcceptInvitation.setVisibility(visible ? View.VISIBLE : View.GONE);
        binding.btnDeniedInvitation.setVisibility(visible ? View.VISIBLE : View.GONE);
        binding.btnGoToRecovery.setVisibility(visible ? View.GONE : View.VISIBLE);
    }

    private void setButtonListeners(boolean isTokenPresent) {
        if (isTokenPresent) {
            binding.btnAcceptInvitation.setOnClickListener(view -> handleInvitation(callBackURL, inviteCode));
            binding.btnDeniedInvitation.setOnClickListener(view -> finish());
        } else {
            binding.btnGoToRecovery.setOnClickListener(view -> ConnectManager.registerUser(this, success -> {
                        if (success) {
                            ConnectManager.goToConnectJobsList(this);
                        }
                    })
            );
        }
    }

    private void handleInvitation(String callBackUrl, String inviteCode) {
        Logger.log("HQInvite", "User accepted invitation");

        IApiCallback callback = new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                Logger.log("HQInvite", "Acceptance succeeded");
                binding.progressBar.setVisibility(View.GONE);
                try {
                    String responseAsString = new String(StreamsUtil.inputStreamToByteArray(responseData));
                    if (responseAsString.length() > 0) {
                        startActivity(new Intent(HQUserInviteActivity.this, DispatchActivity.class));
                        finish();
                    }
                } catch (IOException e) {
                    CrashUtil.reportException(e);
                    setErrorMessage(getString(R.string.connect_hq_invitation_accept_error));
                }
            }

            @Override
            public void processFailure(int responseCode, IOException e) {
                Logger.log("HQInvite", "Acceptance failed");
                binding.progressBar.setVisibility(View.GONE);
                setErrorMessage(getString(R.string.connect_hq_invitation_accept_error));
            }

            @Override
            public void processNetworkFailure() {
                binding.progressBar.setVisibility(View.GONE);
                setErrorMessage(getString(R.string.recovery_network_unavailable));
            }

            @Override
            public void processOldApiError() {
                binding.progressBar.setVisibility(View.GONE);
                setErrorMessage(getString(R.string.recovery_network_outdated));
            }
        };
        ConnectUserRecord user = ConnectManager.getUser(this);
        binding.progressBar.setVisibility(View.VISIBLE);
        boolean isBusy = !ApiConnectId.hqUserInvitation(HQUserInviteActivity.this,user.getUserId(),user.getPassword(), callBackUrl, inviteCode, callback);
        if (isBusy) {
            binding.progressBar.setVisibility(View.GONE);
            Toast.makeText(HQUserInviteActivity.this, R.string.busy_message, Toast.LENGTH_SHORT).show();
        }
    }

    public void setErrorMessage(String message) {
        boolean show = message != null;

        binding.connectPhoneVerifyError.setVisibility(show ? View.VISIBLE : View.GONE);

        if (show) {
            binding.connectPhoneVerifyError.setText(message);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        finish();
        ConnectManager.handleFinishedActivity(this, requestCode, resultCode, data);
    }
}
