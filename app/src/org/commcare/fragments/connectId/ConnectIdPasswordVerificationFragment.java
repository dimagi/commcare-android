package org.commcare.fragments.connectId;

import static android.app.Activity.RESULT_OK;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

import org.commcare.activities.connect.ConnectIdActivity;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.ConnectDatabaseHelper;
import org.commcare.connect.ConnectManager;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ScreenConnectPasswordVerifyBinding;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.utils.ConnectIdAppBarUtils;
import org.commcare.utils.KeyboardHelper;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

public class ConnectIdPasswordVerificationFragment extends Fragment {
    private int callingClass;
    public static final int PASSWORD_FAIL = 1;
    public static final int PASSWORD_LOCK = 2;
    private String phone = null;
    private String secretKey = null;
    private static final int MaxFailures = 3;
    private int failureCount = 0;

    private ScreenConnectPasswordVerifyBinding binding;

    public ConnectIdPasswordVerificationFragment() {
        // Required empty public constructor
    }

    public static ConnectIdPasswordVerificationFragment newInstance() {
        ConnectIdPasswordVerificationFragment fragment = new ConnectIdPasswordVerificationFragment();
        return fragment;
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = ScreenConnectPasswordVerifyBinding.inflate(inflater, container, false);
        View view = binding.getRoot();
        phone = ConnectIdPasswordVerificationFragmentArgs.fromBundle(getArguments()).getPhone();
        secretKey = ConnectIdPasswordVerificationFragmentArgs.fromBundle(getArguments()).getSecret();
        callingClass = ConnectIdPasswordVerificationFragmentArgs.fromBundle(getArguments()).getCallingClass();
        failureCount = 0;
        binding.connectPasswordVerifyForgot.setOnClickListener(arg0 -> handleForgotPress());
        binding.connectPasswordVerifyButton.setOnClickListener(arg0 -> handleButtonPress());
        handleAppBar(view);
        return view;
    }

    private void handleAppBar(View view) {
        View appBarView = view.findViewById(R.id.commonAppBar);
        ConnectIdAppBarUtils.setTitle(appBarView, getString(R.string.connect_appbar_title_password_verification));
        ConnectIdAppBarUtils.setBackButtonWithCallBack(appBarView, R.drawable.ic_connect_arrow_back, true, click -> {
            Navigation.findNavController(appBarView).popBackStack();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        requestInputFocus();
    }

    public void finish(boolean success, boolean forgot) {
        NavDirections directions = null;
        switch (callingClass) {
            case ConnectConstants.CONNECT_RECOVERY_VERIFY_PASSWORD:
                if (success) {
                    directions = ConnectIdPasswordVerificationFragmentDirections.actionConnectidPasswordToConnectidPin(ConnectConstants.CONNECT_RECOVERY_CHANGE_PIN, ConnectIdActivity.recoverPhone, ConnectIdActivity.recoverSecret).setChange(true).setRecover(true);
                    if (forgot) {
                        directions = ConnectIdPasswordVerificationFragmentDirections.actionConnectidPasswordToConnectidMessage(getString(R.string.connect_recovery_alt_title), getString(R.string.connect_recovery_alt_message), ConnectConstants.CONNECT_RECOVERY_ALT_PHONE_MESSAGE, getString(R.string.connect_recovery_alt_button), getString(R.string.connect_deactivate_account), phone, secretKey);
                    }
                } else {
                    directions = ConnectIdPasswordVerificationFragmentDirections.actionConnectidPasswordToConnectidPhoneVerify(ConnectConstants.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE, String.format(Locale.getDefault(), "%d",
                            ConnectIdPhoneVerificationFragmnet.MethodRecoveryPrimary), ConnectIdActivity.recoverPhone, ConnectIdActivity.recoverPhone, "", null,false).setAllowChange(false);
                }
                break;
            case ConnectConstants.CONNECT_UNLOCK_PASSWORD:
                if (success) {
                    if (forgot) {
                        ConnectIdActivity.forgotPassword = true;
                        directions = ConnectIdPasswordVerificationFragmentDirections.actionConnectidPasswordToConnectidPhoneNo(ConnectConstants.METHOD_RECOVER_PRIMARY, null, ConnectConstants.CONNECT_RECOVERY_PRIMARY_PHONE);
                    } else {
                        ConnectIdActivity.forgotPassword = false;
                        FirebaseAnalyticsUtil.reportCccSignIn(AnalyticsParamValue.CCC_SIGN_IN_METHOD_PASSWORD);
                        ConnectUserRecord user = ConnectDatabaseHelper.getUser(requireActivity());
                        user.setLastPinDate(new Date());
                        ConnectDatabaseHelper.storeUser(requireActivity(), user);
                        if (user.shouldRequireSecondaryPhoneVerification()) {
                            directions = ConnectIdPasswordVerificationFragmentDirections.actionConnectidPasswordToConnectidMessage(getString(R.string.connect_recovery_alt_title), getString(R.string.connect_recovery_alt_message), ConnectConstants.CONNECT_UNLOCK_ALT_PHONE_MESSAGE, getString(R.string.connect_password_fail_button), getString(R.string.connect_recovery_alt_change_button), phone, secretKey);
                        } else {
                            ConnectManager.setStatus(ConnectManager.ConnectIdStatus.LoggedIn);
                            ConnectDatabaseHelper.setRegistrationPhase(getActivity(), ConnectConstants.CONNECT_NO_ACTIVITY);
                            requireActivity().setResult(RESULT_OK);
                            requireActivity().finish();
                        }
                    }
                }
                break;
        }
        if (directions != null) {
            Navigation.findNavController(binding.connectPasswordVerifyButton).navigate(directions);
        }
    }

    public void handleWrongPassword() {
        failureCount++;
        ConnectManager.logRecoveryResult(AnalyticsParamValue.CCC_RECOVERY_METHOD_PASSWORD, false);
        binding.connectPasswordVerifyInput.setText("");

        int message = R.string.connect_password_fail_message;

        if (failureCount >= MaxFailures) {
            message = R.string.connect_password_recovery_message;
        }
        NavDirections directions = ConnectIdPasswordVerificationFragmentDirections
                .actionConnectidPasswordToConnectidMessage(getString(R.string.connect_password_fail_title), getString(message),
                        ConnectConstants.CONNECT_RECOVERY_WRONG_PASSWORD, getString(R.string.connect_recovery_success_button), null,
                        phone, secretKey);

        Navigation.findNavController(binding.connectPasswordVerifyButton).navigate(directions);
    }

    public void handleForgotPress() {
        finish(true, true);
    }

    public void handleButtonPress() {
        String password = Objects.requireNonNull(binding.connectPasswordVerifyInput.getText()).toString();
        ConnectUserRecord user = ConnectDatabaseHelper.getUser(requireActivity());
        if (user != null) {
            //If we have the password stored locally, no need for network call
            if (password.equals(user.getPassword())) {
                ConnectManager.logRecoveryResult(AnalyticsParamValue.CCC_RECOVERY_METHOD_PASSWORD, true);
                finish(true, false);
            } else {
                handleWrongPassword();
            }
        } else {
            final Context context = requireActivity();
            ApiConnectId.checkPassword(requireActivity(), phone, secretKey, password, new IApiCallback() {
                @Override
                public void processSuccess(int responseCode, InputStream responseData) {
                    ConnectManager.handleRecoveryPackage(context,
                            AnalyticsParamValue.CCC_RECOVERY_METHOD_PASSWORD, phone, password,
                            responseData);
                    finish(true, false);
                }

                @Override
                public void processFailure(int responseCode, IOException e) {
                    handleWrongPassword();
                }

                @Override
                public void processNetworkFailure() {
                    ConnectNetworkHelper.showOutdatedApiError(requireActivity().getApplicationContext());
                }

                @Override
                public void processOldApiError() {
                    ConnectNetworkHelper.showOutdatedApiError(requireActivity().getApplicationContext());
                }
            });
        }
    }

    public void requestInputFocus() {
        KeyboardHelper.showKeyboardOnInput(requireActivity(), binding.connectPasswordVerifyInput);
    }

}