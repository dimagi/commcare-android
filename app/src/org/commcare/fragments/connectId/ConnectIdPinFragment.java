package org.commcare.fragments.connectId;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.commcare.activities.connect.ConnectIdActivity;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.ConnectDatabaseHelper;
import org.commcare.connect.ConnectManager;
import org.commcare.connect.ConnectTask;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ScreenConnectPinBinding;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.utils.KeyboardHelper;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.Date;
import java.util.Locale;

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
    public static final int PIN_FAIL = 1;
    public static final int PIN_LOCK = 2;

    private String phone = null;
    private String secret = null;

    private boolean isRecovery; //Else registration
    private boolean isChanging; //Else verifying

    private static final int MaxFailures = 3;

    private int callingClass;

    private ScreenConnectPinBinding binding;

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

    public ConnectIdPinFragment() {
        // Required empty public constructor
    }

    public static ConnectIdPinFragment newInstance() {
        return new ConnectIdPinFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

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
        binding= ScreenConnectPinBinding.inflate(inflater,container,false);
        View view = binding.getRoot();
        binding.connectPinButton.setOnClickListener(v -> handleButtonPress());
        binding.connectPinVerifyForgot.setOnClickListener(v -> handleForgotPress());
        binding.connectPinInput.addTextChangedListener(watcher);
        binding.connectPinInput.addTextChangedListener(watcher);
        clearPinFields();
        if (getArguments() != null) {
            phone = ConnectIdPinFragmentArgs.fromBundle(getArguments()).getPhone();
            secret = ConnectIdPinFragmentArgs.fromBundle(getArguments()).getSecret();
            callingClass = ConnectIdPinFragmentArgs.fromBundle(getArguments()).getCallingClass();
            isRecovery = ConnectIdPinFragmentArgs.fromBundle(getArguments()).getRecover();
            isChanging = ConnectIdPinFragmentArgs.fromBundle(getArguments()).getChange();
        }
        int titleId = isChanging ? R.string.connect_pin_title_set :
                R.string.connect_pin_title_confirm;
        requireActivity().setTitle(getString(titleId));
        binding.connectPinTitle.setText(getString(titleId));

        int messageId;
        if (isChanging) {
            messageId = R.string.connect_pin_message_set;
        } else {
            messageId = isRecovery ? R.string.connect_pin_message_repeat :
                    R.string.connect_pin_message_confirm;
        }
        binding.connectPinMessage.setText(getString(messageId));

        binding.connectPinRepeatHolder.setVisibility(isChanging ? View.VISIBLE : View.GONE);
        setPinLength(pinLength);

        binding.connectPinVerifyForgot.setVisibility(!isChanging ? View.VISIBLE : View.GONE);
        return view;
    }

    public void setPinLength(int length) {
        InputFilter[] filter = new InputFilter[]{new InputFilter.LengthFilter(length)};
        binding.connectPinInput.setFilters(filter);
        binding.connectPinInput.setFilters(filter);
    }

    public void clearPin() {
        binding.connectPinInput.setText("");
        binding.connectPinInput.setText("");
    }

    public void requestInputFocus() {
        KeyboardHelper.showKeyboardOnInput(requireActivity(), binding.connectPinInput);
    }

    public void clearPinFields() {
        binding.connectPinInput.setText("");
        binding.connectPinInput.setText("");
    }

    public void checkPin() {
        String pin1 = binding.connectPinInput.getText().toString();
        String pin2 = binding.connectPinInput.getText().toString();

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

    public void handleButtonPress() {
        String pin = binding.connectPinInput.getText().toString();
        ConnectUserRecord user = ConnectDatabaseHelper.getUser(getActivity());

        boolean isBusy = false;
        final Context context = getActivity();
        if (isChanging) {
            //Change PIN
            isBusy = !ApiConnectId.changePin(getActivity(), user.getUserId(), user.getPassword(), pin,
                    new IApiCallback() {
                        @Override
                        public void processSuccess(int responseCode, InputStream responseData) {
                            user.setPin(pin);
                            ConnectDatabaseHelper.storeUser(context, user);
                            ConnectManager.setFailureAttempt(0);
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
            isBusy = !ApiConnectId.checkPin(getActivity(), phone, secret, pin,
                    new IApiCallback() {
                        @Override
                        public void processSuccess(int responseCode, InputStream responseData) {
                            String username = null;
                            String name = null;
                            try {
                                String responseAsString = new String(
                                        StreamsUtil.inputStreamToByteArray(responseData));
                                ConnectManager.setFailureAttempt(0);
                                if (responseAsString.length() > 0) {
                                    JSONObject json = new JSONObject(responseAsString);

                                    String key = ConnectConstants.CONNECT_KEY_USERNAME;
                                    if (json.has(key)) {
                                        username = json.getString(key);
                                    }

                                    key = ConnectConstants.CONNECT_KEY_NAME;
                                    if (json.has(key)) {
                                        name = json.getString(key);
                                    }

                                    key = ConnectConstants.CONNECT_KEY_DB_KEY;
                                    if (json.has(key)) {
                                        ConnectDatabaseHelper.handleReceivedDbPassphrase(context, json.getString(key));
                                    }

                                    ConnectUserRecord user = new ConnectUserRecord(phone, username,
                                            "", name, "");
                                    user.setPin(pin);
                                    user.setLastPinDate(new Date());

                                    key = ConnectConstants.CONNECT_KEY_VALIDATE_SECONDARY_PHONE_BY;
                                    user.setSecondaryPhoneVerified(!json.has(key) || json.isNull(key));
                                    if (!user.getSecondaryPhoneVerified()) {
                                        user.setSecondaryPhoneVerifyByDate(ConnectNetworkHelper.parseDate(json.getString(key)));
                                    }

                                    resetPassword(context, phone, secret, user);
                                } else {
                                    //TODO: Show toast about error
                                }
                            } catch (IOException | JSONException | ParseException e) {
                                Logger.exception("Parsing return from OTP request", e);
                                //TODO: Show toast about error
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

        if (isBusy) {
            Toast.makeText(getActivity(), R.string.busy_message, Toast.LENGTH_SHORT).show();
        }
    }

    private void resetPassword(Context context, String phone, String secret, ConnectUserRecord user) {
        //Auto-generate and send a new password
        String password = ConnectManager.generatePassword();
        ApiConnectId.resetPassword(context, phone, secret, password, new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                //TODO: Need to get secondary phone from server
                user.setPassword(password);

                ConnectDatabaseHelper.storeUser(context, user);

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

    public void handleWrongPin() {
        ConnectManager.setFailureAttempt(ConnectManager.getFailureAttempt() + 1);
        logRecoveryResult(false);
        clearPin();
        finish(false, ConnectManager.getFailureAttempt() >= MaxFailures, null);

    }

    public void handleForgotPress() {
        finish(true, true, null);
    }

    private void logRecoveryResult(boolean success) {
        FirebaseAnalyticsUtil.reportCccRecovery(success, AnalyticsParamValue.CCC_RECOVERY_METHOD_PIN);
    }

    public void finish(boolean success, boolean forgot, String pin) {
        NavDirections directions = null;
        ConnectUserRecord user = ConnectDatabaseHelper.getUser(getActivity());
        switch (callingClass) {
            case ConnectConstants.CONNECT_UNLOCK_PIN -> {
                if (success) {
                    ConnectIdActivity.forgotPin=forgot;
                    if (forgot) {
                        directions = ConnectIdPinFragmentDirections.actionConnectidPinToConnectidPhone(ConnectConstants.CONNECT_RECOVERY_PRIMARY_PHONE, ConnectConstants.METHOD_RECOVER_PRIMARY, null);
                    } else {
                        if (user.shouldRequireSecondaryPhoneVerification()) {
                            directions = ConnectIdPinFragmentDirections.actionConnectidPinToConnectidMessage(getString(R.string.connect_recovery_alt_title), getString(R.string.connect_recovery_alt_message), ConnectConstants.CONNECT_UNLOCK_ALT_PHONE_MESSAGE, getString(R.string.connect_password_fail_button), getString(R.string.connect_recovery_alt_change_button));
                        } else {
                            requireActivity().setResult(RESULT_OK);
                            requireActivity().finish();
                        }
                    }
                } else {
                    directions = ConnectIdPinFragmentDirections.actionConnectidPinToConnectidPhoneVerify(ConnectConstants.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE, String.format(Locale.getDefault(), "%d",
                            ConnectIdPhoneVerificationFragmnet.MethodRecoveryPrimary), ConnectIdActivity.recoverPhone, ConnectIdActivity.recoverPhone, "", null);
                }
            }
            case ConnectConstants.CONNECT_REGISTRATION_CONFIGURE_PIN -> {
                if (success) {
                    ConnectIdActivity.forgotPin=false;
                    ConnectDatabaseHelper.setRegistrationPhase(getActivity(), ConnectTask.CONNECT_REGISTRATION_ALTERNATE_PHONE);
                    directions = ConnectIdPinFragmentDirections.actionConnectidPinToConnectidPhone(ConnectConstants.CONNECT_REGISTRATION_ALTERNATE_PHONE, ConnectConstants.METHOD_CHANGE_ALTERNATE, "");
                    if (user != null) {
                        user.setPin(pin);
                        user.setLastPinDate(new Date());
                        ConnectDatabaseHelper.storeUser(getActivity(), user);
                    }
                } else {
                    directions = ConnectIdPinFragmentDirections.actionConnectidPinToConnectidPhoneVerify(ConnectConstants.CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE, String.format(Locale.getDefault(), "%d",
                            ConnectManager.MethodRegistrationPrimary), user.getPrimaryPhone(), user.getUserId(), user.getPassword(), user.getAlternatePhone()).setAllowChange(true);
                }
            }
            case ConnectConstants.CONNECT_REGISTRATION_CONFIRM_PIN -> {
                ConnectIdActivity.forgotPin=forgot;
                if (success) {
                    if (forgot) {
                        ConnectDatabaseHelper.setRegistrationPhase(getActivity(), ConnectTask.CONNECT_REGISTRATION_CHANGE_PIN);
                        directions = ConnectIdPinFragmentDirections.actionConnectidPinSelf(ConnectConstants.CONNECT_REGISTRATION_CHANGE_PIN, user.getPrimaryPhone(), "").setChange(true).setRecover(false);

                    } else {
                        ConnectDatabaseHelper.setRegistrationPhase(getActivity(), ConnectTask.CONNECT_NO_ACTIVITY);
                        directions = ConnectIdPinFragmentDirections.actionConnectidPinToConnectidMessage(getString(R.string.connect_register_success_title), getString(R.string.connect_register_success_message), ConnectConstants.CONNECT_REGISTRATION_SUCCESS, getString(R.string.connect_register_success_button), null);
                    }
                } else {
                    if (!forgot) {
                        directions = ConnectIdPinFragmentDirections.actionConnectidPinToConnectidMessage(getString(R.string.connect_pin_fail_title), ConnectManager.getFailureAttempt() > 2 ? getString(R.string.connect_pin_confirm_message) : getString(R.string.connect_pin_fail_message), ConnectConstants.CONNECT_REGISTRATION_WRONG_PIN, getString(R.string.connect_recovery_alt_button), null);
                    } else {
                        directions = ConnectIdPinFragmentDirections.actionConnectidPinToConnectidPhone(ConnectConstants.CONNECT_REGISTRATION_ALTERNATE_PHONE, ConnectConstants.METHOD_CHANGE_ALTERNATE, "");
                    }
                }
            }
            case ConnectConstants.CONNECT_RECOVERY_VERIFY_PIN -> {
                if (success) {
                    ConnectIdActivity.forgotPin=forgot;
                    if (forgot) {
                        directions = ConnectIdPinFragmentDirections.actionConnectidPinToConnectidMessage(getString(R.string.connect_recovery_alt_title), getString(R.string.connect_recovery_alt_message), ConnectConstants.CONNECT_REGISTRATION_SUCCESS, getString(R.string.connect_recovery_alt_button), null);

                    } else {
                        directions = ConnectIdPinFragmentDirections.actionConnectidPinToConnectidPassword(ConnectConstants.CONNECT_RECOVERY_VERIFY_PASSWORD, ConnectIdActivity.recoverPhone, ConnectIdActivity.recoverSecret);
                    }
                } else {
                    directions = ConnectIdPinFragmentDirections.actionConnectidPinToConnectidMessage(getString(R.string.connect_pin_fail_title), ConnectManager.getFailureAttempt() > 2 ? getString(R.string.connect_pin_recovery_message) : getString(R.string.connect_pin_fail_message), ConnectConstants.CONNECT_RECOVERY_WRONG_PIN, getString(R.string.connect_recovery_alt_button), null);
                }
            }
            case ConnectConstants.CONNECT_RECOVERY_CHANGE_PIN -> {
                if (success) {
                    ConnectIdActivity.forgotPin=false;
                    if (user != null) {
                        user.setPin(pin);
                        user.setLastPinDate(new Date());
                        ConnectDatabaseHelper.storeUser(requireActivity(), user);
                    }
                    directions = ConnectIdPinFragmentDirections.actionConnectidPinToConnectidMessage(getString(R.string.connect_recovery_success_title), getString(R.string.connect_recovery_success_message), ConnectConstants.CONNECT_RECOVERY_SUCCESS, getString(R.string.connect_recovery_success_button), null);

                } else {
                    directions = ConnectIdPinFragmentDirections.actionConnectidPinToConnectidPhoneVerify(ConnectConstants.CONNECT_RECOVERY_VERIFY_ALT_PHONE, String.format(Locale.getDefault(), "%d",
                            ConnectIdPhoneVerificationFragmnet.MethodRecoveryAlternate), null, ConnectIdActivity.recoverPhone, ConnectIdActivity.recoverSecret, ConnectIdActivity.recoveryAltPhone).setAllowChange(false);
                }
            }
            case ConnectConstants.CONNECT_REGISTRATION_CHANGE_PIN -> {
                ConnectIdActivity.forgotPin=false;
                if (success) {
                    ConnectDatabaseHelper.setRegistrationPhase(getActivity(), ConnectTask.CONNECT_REGISTRATION_CONFIRM_PIN);
                }
                directions = ConnectIdPinFragmentDirections.actionConnectidPinSelf(ConnectConstants.CONNECT_REGISTRATION_CONFIRM_PIN, user.getPrimaryPhone(), "").setChange(false).setRecover(false);
            }
        }

        if (directions != null) {
            Navigation.findNavController(binding.connectPinButton).navigate(directions);
        }
    }
}