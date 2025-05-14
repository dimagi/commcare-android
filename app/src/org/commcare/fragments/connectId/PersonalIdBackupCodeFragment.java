package org.commcare.fragments.connectId;

import android.app.Activity;
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

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link PersonalIdBackupCodeFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class PersonalIdBackupCodeFragment extends Fragment {
    private static final int PIN_LENGTH = 6;
    private static final String KEY_PHONE = "phone";
    private static final String KEY_RECOVERY = "is_recovery";

    private FragmentRecoveryCodeBinding binding;
    private Activity activity;

    private String phone = null;
    private String secret = null;
    private boolean isRecovery;
    private int titleId;

    @Override
    public void onResume() {
        super.onResume();
        requestInputFocus();
        validatePinInputs();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentRecoveryCodeBinding.inflate(inflater, container, false);
        activity = requireActivity();

        initArguments(savedInstanceState);
        configureUiByMode();
        setupPinInputFilters();
        setupListeners();
        clearPinFields();

        activity.setTitle(getString(titleId));
        return binding.getRoot();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_PHONE, phone);
        outState.putBoolean(KEY_RECOVERY, isRecovery);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // ─────────── UI SETUP ─────────────

    private void configureUiByMode() {
        if (isRecovery) {
            titleId = R.string.connect_pin_title_confirm;
            binding.confirmCodeLayout.setVisibility(View.GONE);
            binding.recoveryCodeTilte.setText(R.string.connect_pin_message_title);
            binding.phoneTitle.setText(R.string.connect_pin_message);
        } else {
            titleId = R.string.connect_pin_title_set;
            binding.confirmCodeLayout.setVisibility(View.VISIBLE);
        }
    }

    private void setupPinInputFilters() {
        InputFilter[] filters = new InputFilter[]{new InputFilter.LengthFilter(PIN_LENGTH)};
        binding.connectPinInput.setFilters(filters);
        binding.connectPinRepeatInput.setFilters(filters);
    }

    private void setupListeners() {
        TextWatcher pinWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                validatePinInputs();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };

        binding.connectPinInput.addTextChangedListener(pinWatcher);
        binding.connectPinRepeatInput.addTextChangedListener(pinWatcher);
        binding.connectPinButton.setOnClickListener(v -> handlePinSubmission());
    }

    private void clearPinFields() {
        binding.connectPinInput.setText("");
        binding.connectPinRepeatInput.setText("");
    }

    private void requestInputFocus() {
        KeyboardHelper.showKeyboardOnInput(activity, binding.connectPinInput);
    }

    // ─────────── VALIDATION ─────────────

    private void validatePinInputs() {
        String pin1 = binding.connectPinInput.getText().toString();
        String pin2 = binding.connectPinRepeatInput.getText().toString();

        String errorText = "";
        boolean isButtonEnabled = false;

        if (!pin1.isEmpty()) {
            if (pin1.length() != PIN_LENGTH) {
                errorText = getString(R.string.connect_pin_length, PIN_LENGTH);
            } else if (!isRecovery && !pin1.equals(pin2)) {
                errorText = getString(R.string.connect_pin_mismatch);
            } else {
                isButtonEnabled = true;
            }
        }

        binding.connectPinErrorMessage.setText(errorText);
        binding.connectPinButton.setEnabled(isButtonEnabled);
    }

    // ─────────── PIN HANDLING ─────────────

    private void handlePinSubmission() {
        if (isRecovery) {
            confirmBackupPin();
        } else {
            registerBackupPin();
        }
    }

    private void registerBackupPin() {
        String pin = binding.connectPinInput.getText().toString();
        ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(getActivity());

        ApiConnectId.setBackupCode(getActivity(), user.getUserId(), user.getPassword(), pin, new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                user.setPin(pin);
                ConnectUserDatabaseUtil.storeUser(activity, user);
                ConnectIDManager.getInstance().setFailureAttempt(0);
                finishWithNavigation(true, pin);
            }

            @Override
            public void processFailure(int responseCode) {
                handleFailedPinAttempt();
            }

            @Override
            public void processNetworkFailure() {
                ConnectNetworkHelper.showNetworkError(getActivity());
            }

            @Override
            public void processTokenUnavailableError() {
                ConnectNetworkHelper.handleTokenUnavailableException(requireActivity());
            }

            @Override
            public void processTokenRequestDeniedError() {
                ConnectNetworkHelper.handleTokenDeniedException(requireActivity());
            }

            @Override
            public void processOldApiError() {
                ConnectNetworkHelper.showOutdatedApiError(getActivity());
            }
        });
    }

    private void confirmBackupPin() {
        String pin = binding.connectPinInput.getText().toString();

        ApiConnectId.checkPin(getActivity(), phone, secret, pin, new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                try {
                    JSONObject json = new JSONObject(new String(StreamsUtil.inputStreamToByteArray(responseData)));
                    handleSuccessfulRecovery(json, pin);
                } catch (IOException | JSONException e) {
                    Logger.exception("Error parsing recovery response", e);
                    showRecoveryFailure();
                }
            }

            @Override
            public void processFailure(int responseCode) {
                handleFailedPinAttempt();
            }

            @Override
            public void processNetworkFailure() {
                ConnectNetworkHelper.showNetworkError(getActivity());
            }

            @Override
            public void processTokenUnavailableError() {
                ConnectNetworkHelper.handleTokenUnavailableException(requireActivity());
            }

            @Override
            public void processTokenRequestDeniedError() {
                ConnectNetworkHelper.handleTokenDeniedException(requireActivity());
            }

            @Override
            public void processOldApiError() {
                ConnectNetworkHelper.showOutdatedApiError(getActivity());
            }
        });
    }

    private void handleSuccessfulRecovery(JSONObject json, String pin) throws JSONException {
        String username = json.getString(ConnectConstants.CONNECT_KEY_USERNAME);
        String name = json.getString(ConnectConstants.CONNECT_KEY_NAME);

        ConnectDatabaseHelper.handleReceivedDbPassphrase(activity,
                json.getString(ConnectConstants.CONNECT_KEY_DB_KEY));
        ConnectUserRecord user = new ConnectUserRecord(phone, username, "", name, "");
        user.setPin(pin);
        user.setLastPinDate(new Date());

        boolean isSecondaryPhoneVerified = !json.has(ConnectConstants.CONNECT_KEY_VALIDATE_SECONDARY_PHONE_BY)
                || json.isNull(ConnectConstants.CONNECT_KEY_VALIDATE_SECONDARY_PHONE_BY);

        user.setSecondaryPhoneVerified(isSecondaryPhoneVerified);

        if (!isSecondaryPhoneVerified) {
            user.setSecondaryPhoneVerifyByDate(
                    DateUtils.parseDate(json.getString(ConnectConstants.CONNECT_KEY_VALIDATE_SECONDARY_PHONE_BY)));
        }

        resetUserPassword(user);
    }

    private void resetUserPassword(ConnectUserRecord user) {
        String password = ConnectIDManager.getInstance().generatePassword();
        ApiConnectId.resetPassword(activity, phone, secret, password, new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                user.setPassword(password);
                ConnectUserDatabaseUtil.storeUser(activity, user);
                finishWithNavigation(true, user.getPin());
            }

            @Override
            public void processFailure(int responseCode) {
                showRecoveryFailure();
            }

            @Override
            public void processNetworkFailure() {
                ConnectNetworkHelper.showNetworkError(getActivity());
            }

            @Override
            public void processTokenUnavailableError() {
                ConnectNetworkHelper.handleTokenUnavailableException(requireActivity());
            }

            @Override
            public void processTokenRequestDeniedError() {
                ConnectNetworkHelper.handleTokenDeniedException(requireActivity());
            }

            @Override
            public void processOldApiError() {
                ConnectNetworkHelper.showOutdatedApiError(getActivity());
            }
        });
    }

    // ─────────── HELPERS ─────────────

    private void handleFailedPinAttempt() {
        ConnectIDManager idManager = ConnectIDManager.getInstance();
        idManager.setFailureAttempt(idManager.getFailureAttempt() + 1);
        logRecoveryResult(false);
        clearPinFields();
        finishWithNavigation(false, null);
    }

    private void showRecoveryFailure() {
        Toast.makeText(activity, getString(R.string.connect_recovery_failure), Toast.LENGTH_SHORT).show();
    }

    private void finishWithNavigation(boolean success, String pin) {
        NavDirections directions;
        ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(getActivity());

        if (isRecovery) {
            directions = success ?
                    createSuccessRecoveryDirection() :
                    createFailedRecoveryDirection();
        } else {
            ((ConnectIdActivity)activity).forgotPin = false;
            directions = createRegistrationSuccessDirection();

            if (user != null) {
                user.setPin(pin);
                user.setLastPinDate(new Date());
                ConnectUserDatabaseUtil.storeUser(getActivity(), user);
            }
        }

        if (directions != null) {
            Navigation.findNavController(binding.connectPinButton).navigate(directions);
        }
    }

    private NavDirections createSuccessRecoveryDirection() {
        return createNavigationMessage(
                getString(R.string.connect_recovery_success_title),
                getString(R.string.connect_recovery_success_message),
                ConnectConstants.CONNECT_RECOVERY_SUCCESS,
                getString(R.string.connect_recovery_success_button)
        );
    }

    private NavDirections createFailedRecoveryDirection() {
        boolean exceededAttempts = ConnectIDManager.getInstance().getFailureAttempt() > 2;
        return createNavigationMessage(
                getString(R.string.connect_pin_fail_title),
                exceededAttempts ? getString(R.string.connect_pin_recovery_message)
                        : getString(R.string.connect_pin_fail_message),
                ConnectConstants.CONNECT_RECOVERY_WRONG_PIN,
                getString(R.string.connect_recovery_alt_button)
        );
    }

    private NavDirections createRegistrationSuccessDirection() {
        return createNavigationMessage(
                getString(R.string.connect_register_success_title),
                getString(R.string.connect_register_success_message),
                ConnectConstants.CONNECT_REGISTRATION_SUCCESS,
                getString(R.string.connect_register_success_button)
        );
    }

    private NavDirections createNavigationMessage(String title, String message, int phase, String buttonText) {
        return PersonalIdBackupCodeFragmentDirections
                .actionConnectidPinToConnectidMessage(title, message, phase, buttonText, null, phone, secret)
                .setIsCancellable(false);
    }

    private void logRecoveryResult(boolean success) {
        FirebaseAnalyticsUtil.reportCccRecovery(success, AnalyticsParamValue.CCC_RECOVERY_METHOD_PIN);
    }

    private void initArguments(Bundle savedInstanceState) {
        if (getArguments() != null) {
            phone = PersonalIdBackupCodeFragmentArgs.fromBundle(getArguments()).getPhone();
            isRecovery = PersonalIdBackupCodeFragmentArgs.fromBundle(getArguments()).getRecover();
        }

        if (savedInstanceState != null) {
            phone = savedInstanceState.getString(KEY_PHONE);
            isRecovery = savedInstanceState.getBoolean(KEY_RECOVERY);
        }
    }
}
