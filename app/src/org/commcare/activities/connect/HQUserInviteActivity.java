package org.commcare.activities.connect;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ActivityHquserInviteBinding;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

public class HQUserInviteActivity extends AppCompatActivity {

    private ActivityHquserInviteBinding binding;
    String domain;
    String inviteCode;
    String username;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHquserInviteBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Intent intent = getIntent();
        Uri data = intent.getData();

        if (data != null && "connect".equals(data.getScheme())) {
            List<String> pathSegments = data.getPathSegments();
            if (pathSegments.size() >= 3) {
                domain = pathSegments.get(0);
                inviteCode = pathSegments.get(1);
                username = pathSegments.get(2);
            }
        }

        binding.btnAcceptInvitation.setOnClickListener(view -> handleInvitation(HQUserInviteActivity.this, inviteCode, true));

        binding.btnAcceptInvitation.setOnClickListener(view -> handleInvitation(HQUserInviteActivity.this, inviteCode, false));
    }

    private void handleInvitation(Context context, String inviteCode, boolean acceptStatus) {
        IApiCallback callback = new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                try {
                    String responseAsString = new String(StreamsUtil.inputStreamToByteArray(responseData));
                    if (responseAsString.length() > 0) {
                        JSONObject json = new JSONObject(responseAsString);

                    }
                } catch (IOException | JSONException e) {
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

        boolean isBusy = !ApiConnectId.hqUserInvitation(HQUserInviteActivity.this, inviteCode, acceptStatus, callback);
        if (isBusy) {
            Toast.makeText(HQUserInviteActivity.this, R.string.busy_message, Toast.LENGTH_SHORT).show();
        }
    }

    public void setErrorMessage(String message) {
//        if (message == null) {
//            binding.connectPhoneVerifyError.setVisibility(View.GONE);
//        } else {
//            binding.connectPhoneVerifyError.setVisibility(View.VISIBLE);
//            binding.connectPhoneVerifyError.setText(message);
//        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
