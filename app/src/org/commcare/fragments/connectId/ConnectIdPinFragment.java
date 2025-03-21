package org.commcare.fragments.connectId;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import org.commcare.activities.connect.ConnectIdActivity;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.ConnectIDManager;
import org.commcare.connect.database.ConnectDatabaseHelper;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.FragmentRecoveryCodeBinding;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.utils.KeyboardHelper;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.services.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

import static android.app.Activity.RESULT_OK;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ConnectIdPinFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ConnectIdPinFragment extends Fragment {
    private static final int pinLength = 6;
    private String phone = null;
    private String secret = null;

    private boolean isRecovery; //Else registration
    private boolean isChanging; //Else verifying
    private int callingClass;

    private FragmentRecoveryCodeBinding binding;

    private static final String KEY_PHONE = "phone";
    private static final String KEY_SECRET = "secret";
    private static final String KEY_CALLING_CLASS = "calling_class";
    private static final String KEY_RECOVERY = "is_recovery";
    private static final String KEY_CHANGING = "is_changing";

    int titleId;

    TextWatcher watcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            checkPin();
        }

        @Override
        public void afterTextChanged(Editable s) {

        }
    };

    @Override
    public void onResume() {
        super.onResume();

        requestInputFocus();

        checkPin();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = FragmentRecoveryCodeBinding.inflate(inflater, container, false);
        View view = binding.getRoot();
        checkPin();
        clearPinFields();
        getArgument();
        setOnClickListener();
        getLoadState(savedInstanceState);
        titleId = isChanging ? R.string.connect_pin_title_set :
                R.string.connect_pin_title_confirm;
        setPinLength(pinLength);
        if (callingClass == ConnectConstants.CONNECT_UNLOCK_PIN ||
                callingClass == ConnectConstants.CONNECT_REGISTRATION_CONFIRM_PIN ||
                callingClass == ConnectConstants.CONNECT_RECOVERY_VERIFY_PIN
        ) {
            binding.confirmCodeLayout.setVisibility(View.GONE);
            binding.recoveryCodeTilte.setText(R.string.connect_pin_message_title);
            binding.phoneTitle.setText(R.string.connect_pin_message);

        } else {
            binding.confirmCodeLayout.setVisibility(View.VISIBLE);
        }

        binding.forgotButton.setVisibility(!isChanging ? View.VISIBLE : View.GONE);

        requireActivity().setTitle(getString(titleId));
        return view;
    }

    private void setPinLength(int length) {
        InputFilter[] filter = new InputFilter[]{new InputFilter.LengthFilter(length)};
        binding.connectPinInput.setFilters(filter);
        binding.connectPinInput.setFilters(filter);
    }

    private void requestInputFocus() {
        KeyboardHelper.showKeyboardOnInput(requireActivity(), binding.connectPinInput);
    }

    private void clearPinFields() {
        binding.connectPinInput.setText("");
        binding.connectPinRepeatInput.setText("");
    }

    private void checkPin() {
        String pin1 = binding.connectPinInput.getText().toString();
        String pin2 = binding.connectPinRepeatInput.getText().toString();

        String errorText = "";
        boolean buttonEnabled = false;
        if (pin1.length() > 0) {
            if (pin1.length() != pinLength) {
                errorText = getString(R.string.connect_pin_length, pinLength);
            } else if (isChanging && !pin1.equals(pin2)) {
                errorText = getString(R.string.connect_pin_mismatch);
            } else {
                buttonEnabled = true;
            }
        }

        binding.connectPinErrorMessage.setText(errorText);
        binding.connectPinButton.setEnabled(buttonEnabled);
    }

    private void setOnClickListener(){
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                checkPin();
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        };

        binding.connectPinInput.addTextChangedListener(watcher);
        binding.connectPinRepeatInput.addTextChangedListener(watcher);
        binding.connectPinButton.setOnClickListener(v -> verifyPin());
        binding.forgotButton.setOnClickListener(v -> onForgotPress());
        binding.connectPinInput.addTextChangedListener(watcher);
        binding.connectPinInput.addTextChangedListener(watcher);

    }

    private void getArgument(){
        if (getArguments() != null) {
            phone = ConnectIdPinFragmentArgs.fromBundle(getArguments()).getPhone();
            secret = ConnectIdPinFragmentArgs.fromBundle(getArguments()).getSecret();
            callingClass = ConnectIdPinFragmentArgs.fromBundle(getArguments()).getCallingClass();
            isRecovery = ConnectIdPinFragmentArgs.fromBundle(getArguments()).getRecover();
            isChanging = ConnectIdPinFragmentArgs.fromBundle(getArguments()).getChange();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_PHONE, phone);
        outState.putInt(KEY_CALLING_CLASS, callingClass);
        outState.putString(KEY_SECRET, secret);
        outState.putBoolean(KEY_RECOVERY, isRecovery);
        outState.putBoolean(KEY_CHANGING, isChanging);
    }

    private void getLoadState(Bundle savedInstanceState){
        if(savedInstanceState!=null) {
            phone = savedInstanceState.getString(KEY_PHONE);
            secret = savedInstanceState.getString(KEY_SECRET);
            callingClass = savedInstanceState.getInt(KEY_CALLING_CLASS);
            isRecovery = savedInstanceState.getBoolean(KEY_RECOVERY);
            isChanging = savedInstanceState.getBoolean(KEY_CHANGING);
        }
    }


    private void verifyPin() {
        String pin = binding.connectPinInput.getText().toString();
        ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(getActivity());

        final Context context = getActivity();
        if (isChanging) {
            //Change PIN
            ApiConnectId.changePin(getActivity(), user.getUserId(), user.getPassword(), pin,
                    new IApiCallback() {
                        @Override
                        public void processSuccess(int responseCode, InputStream responseData) {
                            user.setPin(pin);
                            ConnectUserDatabaseUtil.storeUser(context, user);
                            ConnectIDManager.getInstance().setFailureAttempt(0);
                            finish(true, false, pin);
                        }

                        @Override
                        public void processFailure(int responseCode, IOException e) {
                            handleWrongPin();
                        }

                        @Override
                        public void processNetworkFailure() {
                            ConnectNetworkHelper.showNetworkError(getActivity());
                        }

                        @Override
                        public void processOldApiError() {
                            ConnectNetworkHelper.showOutdatedApiError(getActivity());
                        }
                    });
        } else if (isRecovery) {
            //Confirm PIN
            ApiConnectId.checkPin(getActivity(), phone, secret, pin,
                    new IApiCallback() {
                        @Override
                        public void processSuccess(int responseCode, InputStream responseData) {
                            String username;
                            String name;
                            try {
                                String responseAsString = new String(
                                        StreamsUtil.inputStreamToByteArray(responseData));
                                ConnectIDManager.getInstance().setFailureAttempt(0);
                                if (responseAsString.length() > 0) {
                                    JSONObject json = new JSONObject(responseAsString);
                                    username = json.getString(ConnectConstants.CONNECT_KEY_USERNAME);
                                    name = json.getString(ConnectConstants.CONNECT_KEY_NAME);
                                    ConnectDatabaseHelper.handleReceivedDbPassphrase(context, json.getString(ConnectConstants.CONNECT_KEY_DB_KEY));
                                    ConnectUserRecord user = new ConnectUserRecord(phone, username,
                                            "", name, "");
                                    user.setPin(pin);
                                    user.setLastPinDate(new Date());

                                    user.setSecondaryPhoneVerified(!json.has(ConnectConstants.CONNECT_KEY_VALIDATE_SECONDARY_PHONE_BY) || json.isNull(ConnectConstants.CONNECT_KEY_VALIDATE_SECONDARY_PHONE_BY));
                                    if (!user.getSecondaryPhoneVerified()) {
                                        user.setSecondaryPhoneVerifyByDate(DateUtils.parseDate(json.getString(ConnectConstants.CONNECT_KEY_VALIDATE_SECONDARY_PHONE_BY)));
                                    }

                                    resetPassword(context, phone, secret, user);
                                } else {
                                    //TODO: Show toast about error
                                }
                            } catch (IOException e) {
                                Logger.exception("Parsing return from OTP request", e);
                                //TODO: Show toast about error
                            }catch (JSONException e){
                                throw new RuntimeException(e);
                            }
                        }

                        @Override
                        public void processFailure(int responseCode, IOException e) {
                            handleWrongPin();
                        }

                        @Override
                        public void processNetworkFailure() {
                            ConnectNetworkHelper.showNetworkError(getActivity());
                        }

                        @Override
                        public void processOldApiError() {
                            ConnectNetworkHelper.showOutdatedApiError(getActivity());
                        }
                    });
        } else if (pin.equals(user.getPin())) {
            //Local confirmation
            logRecoveryResult(true);
            finish(true, false, pin);
        } else {
            //Local failure
            handleWrongPin();
        }
    }

    private void resetPassword(Context context, String phone, String secret, ConnectUserRecord user) {
        //Auto-generate and send a new password
        String password = ConnectIDManager.getInstance().generatePassword();
        ApiConnectId.resetPassword(context, phone, secret, password, new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                user.setPassword(password);

                ConnectUserDatabaseUtil.storeUser(context, user);

                finish(true, false, user.getPin());
            }

            @Override
            public void processFailure(int responseCode, IOException e) {
                Toast.makeText(context, getString(R.string.connect_recovery_failure),
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void processNetworkFailure() {
                ConnectNetworkHelper.showNetworkError(getActivity());
            }

            @Override
            public void processOldApiError() {
                ConnectNetworkHelper.showOutdatedApiError(getActivity());
            }
        });
    }

    private void handleWrongPin() {
        int MaxFailures = 3;
        ConnectIDManager.getInstance().setFailureAttempt(ConnectIDManager.getInstance().getFailureAttempt() + 1);
        logRecoveryResult(false);
        clearPinFields();
        finish(false, ConnectIDManager.getInstance().getFailureAttempt() >= MaxFailures, null);

    }

    private void onForgotPress() {
        finish(true, true, null);
    }

    private void logRecoveryResult(boolean success) {
        FirebaseAnalyticsUtil.reportCccRecovery(success, AnalyticsParamValue.CCC_RECOVERY_METHOD_PIN);
    }

    private void finish(boolean success, boolean forgot, String pin) {
        NavDirections directions = null;
        ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(getActivity());
        ConnectIdActivity connectIdActivity= (ConnectIdActivity)requireActivity();

        switch (callingClass) {
            case ConnectConstants.CONNECT_UNLOCK_PIN -> {
                if (success) {
                    connectIdActivity.forgotPin = forgot;
                    if (forgot) {
                        directions = ConnectIdPinFragmentDirections.actionConnectidPinToConnectidPhoneNo(ConnectConstants.METHOD_RECOVER_PRIMARY, null, ConnectConstants.CONNECT_RECOVERY_PRIMARY_PHONE);
                    } else {
                        if (user.shouldRequireSecondaryPhoneVerification()) {
                            directions = ConnectIdPinFragmentDirections.actionConnectidPinToConnectidMessage(getString(R.string.connect_recovery_alt_title), getString(R.string.connect_recovery_alt_message), ConnectConstants.CONNECT_UNLOCK_ALT_PHONE_MESSAGE, getString(R.string.connect_password_fail_button), getString(R.string.connect_recovery_alt_change_button), phone, secret);
                        } else {
                            ConnectIDManager.getInstance().setStatus(ConnectIDManager.ConnectIdStatus.LoggedIn);
                            ConnectDatabaseHelper.setRegistrationPhase(getActivity(), ConnectConstants.CONNECT_NO_ACTIVITY);
                            requireActivity().setResult(RESULT_OK);
                            requireActivity().finish();
                        }
                    }
                } else {
                    directions = ConnectIdPinFragmentDirections.actionConnectidPinToConnectidPhoneVerify(ConnectConstants.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE, String.valueOf(
                            ConnectIdPhoneVerificationFragment.MethodRecoveryPrimary), connectIdActivity.recoverPhone, connectIdActivity.recoverPhone, "", null, false);
                }
            }
            case ConnectConstants.CONNECT_REGISTRATION_CONFIGURE_PIN -> {
                if (success) {
                    connectIdActivity.forgotPin = false;
                    ConnectDatabaseHelper.setRegistrationPhase(getActivity(), ConnectConstants.CONNECT_REGISTRATION_ALTERNATE_PHONE);
                    directions = ConnectIdPinFragmentDirections.actionConnectidPinToConnectidSecondaryPhoneFragment(ConnectConstants.CONNECT_REGISTRATION_ALTERNATE_PHONE, ConnectConstants.METHOD_CHANGE_ALTERNATE, "");
                    if (user != null) {
                        user.setPin(pin);
                        user.setLastPinDate(new Date());
                        ConnectUserDatabaseUtil.storeUser(getActivity(), user);
                    }
                } else {
                    directions = ConnectIdPinFragmentDirections.actionConnectidPinToConnectidPhoneVerify(ConnectConstants.CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE, String.valueOf(
                            ConnectIDManager.MethodRegistrationPrimary), user.getPrimaryPhone(), user.getUserId(), user.getPassword(), user.getAlternatePhone(), false).setAllowChange(true);
                }
            }
            case ConnectConstants.CONNECT_REGISTRATION_CONFIRM_PIN -> {
                connectIdActivity.forgotPin = forgot;
                if (success) {
                    if (forgot) {
                        ConnectDatabaseHelper.setRegistrationPhase(getActivity(), ConnectConstants.CONNECT_REGISTRATION_CHANGE_PIN);
                        directions = ConnectIdPinFragmentDirections.actionConnectidPinSelf(ConnectConstants.CONNECT_REGISTRATION_CHANGE_PIN, user.getPrimaryPhone(), "").setChange(true).setRecover(false);

                    } else {
                        ConnectDatabaseHelper.setRegistrationPhase(getActivity(), ConnectConstants.CONNECT_NO_ACTIVITY);
                        directions = ConnectIdPinFragmentDirections.actionConnectidPinToConnectidMessage(getString(R.string.connect_register_success_title), getString(R.string.connect_register_success_message), ConnectConstants.CONNECT_REGISTRATION_SUCCESS, getString(R.string.connect_register_success_button), null, phone, secret);
                    }
                } else {
                    if (!forgot) {
                        directions = ConnectIdPinFragmentDirections.actionConnectidPinToConnectidMessage(getString(R.string.connect_pin_fail_title), ConnectIDManager.getInstance().getFailureAttempt() > 2 ? getString(R.string.connect_pin_confirm_message) : getString(R.string.connect_pin_fail_message), ConnectConstants.CONNECT_REGISTRATION_WRONG_PIN, getString(R.string.connect_recovery_alt_button), null, phone, secret);
                    } else {
                        directions = ConnectIdPinFragmentDirections.actionConnectidPinToConnectidSecondaryPhoneFragment(ConnectConstants.CONNECT_REGISTRATION_ALTERNATE_PHONE, ConnectConstants.METHOD_CHANGE_ALTERNATE, "");
                    }
                }
            }
            case ConnectConstants.CONNECT_RECOVERY_VERIFY_PIN -> {
                if (success) {
                    connectIdActivity.forgotPin = forgot;
                    if (forgot) {
                        if (connectIdActivity.forgotPassword) {
                            directions = ConnectIdPinFragmentDirections.actionConnectidPinToConnectidMessage(getString(R.string.connect_recovery_alt_title), getString(R.string.connect_recovery_alt_message), ConnectConstants.CONNECT_REGISTRATION_SUCCESS, getString(R.string.connect_recovery_alt_button), null, phone, secret);
                        } else {
                            directions = ConnectIdPinFragmentDirections.actionConnectidPinToConnectidPassword(connectIdActivity.recoverPhone, connectIdActivity.recoverSecret, ConnectConstants.CONNECT_RECOVERY_VERIFY_PASSWORD);
                        }
                    } else {
                        directions = ConnectIdPinFragmentDirections.actionConnectidPinToConnectidMessage(getString(R.string.connect_recovery_success_title), getString(R.string.connect_recovery_success_message), ConnectConstants.CONNECT_RECOVERY_SUCCESS, getString(R.string.connect_recovery_success_button), null, phone, secret);
                    }
                } else {
                    directions = ConnectIdPinFragmentDirections.actionConnectidPinToConnectidMessage(getString(R.string.connect_pin_fail_title), ConnectIDManager.getInstance().getFailureAttempt() > 2 ? getString(R.string.connect_pin_recovery_message) : getString(R.string.connect_pin_fail_message), ConnectConstants.CONNECT_RECOVERY_WRONG_PIN, getString(R.string.connect_recovery_alt_button), null, phone, secret);
                }
            }
            case ConnectConstants.CONNECT_RECOVERY_CHANGE_PIN -> {
                if (success) {
                    connectIdActivity.forgotPin = false;
                    if (user != null) {
                        user.setPin(pin);
                        user.setLastPinDate(new Date());
                        ConnectUserDatabaseUtil.storeUser(requireActivity(), user);
                    }
                    directions = ConnectIdPinFragmentDirections.actionConnectidPinToConnectidMessage(getString(R.string.connect_recovery_success_title), getString(R.string.connect_recovery_success_message), ConnectConstants.CONNECT_RECOVERY_SUCCESS, getString(R.string.connect_recovery_success_button), null, phone, secret);

                } else {
                    directions = ConnectIdPinFragmentDirections.actionConnectidPinToConnectidPhoneVerify(ConnectConstants.CONNECT_RECOVERY_VERIFY_ALT_PHONE, String.valueOf(
                            ConnectIdPhoneVerificationFragment.MethodRecoveryAlternate), null, connectIdActivity.recoverPhone, connectIdActivity.recoverSecret, connectIdActivity.recoveryAltPhone, false).setAllowChange(false);
                }
            }
            case ConnectConstants.CONNECT_REGISTRATION_CHANGE_PIN -> {
                connectIdActivity.forgotPin = false;
                if (success) {
                    ConnectDatabaseHelper.setRegistrationPhase(getActivity(), ConnectConstants.CONNECT_REGISTRATION_CONFIRM_PIN);
                }
                directions = ConnectIdPinFragmentDirections.actionConnectidPinSelf(ConnectConstants.CONNECT_REGISTRATION_CONFIRM_PIN, user.getPrimaryPhone(), "").setChange(false).setRecover(false);
            }
        }

        if (directions != null) {
            Navigation.findNavController(binding.connectPinButton).navigate(directions);
        }
    }
}