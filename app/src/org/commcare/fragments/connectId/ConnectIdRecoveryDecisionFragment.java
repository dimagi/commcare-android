package org.commcare.fragments.connectId;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.tasks.OnSuccessListener;

import org.commcare.activities.connect.ConnectIdActivity;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ScreenConnectRecoveryDecisionBinding;
import org.commcare.utils.KeyboardHelper;
import org.commcare.utils.PhoneNumberHelper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ConnectIdRecoveryDecisionFragment#newInstance} factory method to
 * create an instance of requireActivity() fragment.
 */
public class ConnectIdRecoveryDecisionFragment extends Fragment {
    private enum ConnectRecoveryState {
        NewOrRecover,
        PhoneOrExtended
    }

    public ConnectRecoveryState state;

    private ScreenConnectRecoveryDecisionBinding binding;


    protected boolean skipPhoneNumberCheck = false;
    TextWatcher watcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            checkPhoneNumber();
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    };

    public ConnectIdRecoveryDecisionFragment() {
    }

    public static ConnectIdRecoveryDecisionFragment newInstance(String param1, String param2) {
        ConnectIdRecoveryDecisionFragment fragment = new ConnectIdRecoveryDecisionFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for requireActivity() fragment
        binding = ScreenConnectRecoveryDecisionBinding.inflate(inflater, container, false);
        View view = binding.getRoot();
        binding.connectRecoveryPhoneCountryInput.addTextChangedListener(watcher);
        binding.connectRecoveryPhoneInput.addTextChangedListener(watcher);
        binding.connectRecoveryButton1.setOnClickListener(v -> handleButton1Press());
        binding.connectRecoveryButton2.setOnClickListener(v -> handleButton2Press());
        binding.connectRecoveryMessage.setText(getString(R.string.connect_recovery_decision_new));
        binding.connectRecoveryButton1.setText(getString(R.string.connect_recovery_button_new));
        binding.connectRecoveryButton2.setText(getString(R.string.connect_recovery_button_recover));
        state = ConnectRecoveryState.NewOrRecover;
        getActivity().setTitle(getString(R.string.connect_recovery_title));
        return view;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        String phone = PhoneNumberHelper.handlePhoneNumberPickerResult(requestCode, resultCode, data, requireActivity());
        skipPhoneNumberCheck = false;
        displayNumber(phone);
    }

    public void requestPhoneNumberHint() {
        GetPhoneNumberHintIntentRequest hintRequest = GetPhoneNumberHintIntentRequest.builder().build();
        Identity.getSignInClient(requireActivity()).getPhoneNumberHintIntent(hintRequest)
                .addOnSuccessListener(new OnSuccessListener<PendingIntent>() {
                    @Override
                    public void onSuccess(PendingIntent pendingIntent) {
                        try {
                            startIntentSenderForResult(pendingIntent.getIntentSender(), ConnectConstants.CREDENTIAL_PICKER_REQUEST, null, 0, 0, 0, null);
                        } catch (IntentSender.SendIntentException e) {
                            e.printStackTrace();
                        }
                    }
                });
    }

    void displayNumber(String fullNumber) {
        int code = PhoneNumberHelper.getCountryCode(requireActivity());
        if (fullNumber != null && fullNumber.length() > 0) {
            code = PhoneNumberHelper.getCountryCode(requireActivity(), fullNumber);
        }

        String codeText = "";
        if (code > 0) {
            codeText = String.format(Locale.getDefault(), "%d", code);
        }

        if (fullNumber != null && fullNumber.startsWith("+" + codeText)) {
            fullNumber = fullNumber.substring(codeText.length() + 1);
        }
        skipPhoneNumberCheck = false;
        binding.connectRecoveryPhoneInput.setText(fullNumber);
        skipPhoneNumberCheck = true;
        binding.connectRecoveryPhoneCountryInput.setText(String.format(Locale.getDefault(), "+%d", code));
        skipPhoneNumberCheck = false;

    }

    public void finish(boolean createNew, String phone) {

        NavDirections directions;
        if (createNew) {
            directions = ConnectIdRecoveryDecisionFragmentDirections.actionConnectidRecoveryDecisionToConnectidConsent(ConnectConstants.CONNECT_REGISTRATION_CONSENT);
        } else {
            ConnectIdActivity.recoverPhone = phone;
            directions = ConnectIdRecoveryDecisionFragmentDirections.actionConnectidRecoveryDecisionToConnectidBiometricConfig(phone, ConnectConstants.CONNECT_RECOVERY_CONFIGURE_BIOMETRICS);
        }
        Navigation.findNavController(binding.connectRecoveryButton1).navigate(directions);
    }

    public void handleButton1Press() {
        switch (state) {
            case NewOrRecover -> finish(true, null);
            case PhoneOrExtended ->
                    finish(false, PhoneNumberHelper.buildPhoneNumber(binding.connectRecoveryPhoneCountryInput.getText().toString(),
                            binding.connectRecoveryPhoneInput.getText().toString()));
        }
    }

    public void handleButton2Press() {
        switch (state) {
            case NewOrRecover -> {
                state = ConnectRecoveryState.PhoneOrExtended;
                setPhoneInputVisible(true);
                requireActivity().setTitle(getString(R.string.connect_recovery_title2));
                requestPhoneNumberHint();
                int code = PhoneNumberHelper.getCountryCode(requireActivity());
                binding.connectRecoveryPhoneCountryInput.setText(String.format(Locale.getDefault(), "+%d", code));

                requestInputFocus();
                binding.connectRecoveryMessage.setText(getString(R.string.connect_recovery_decision_phone));
                binding.connectRecoveryButton1.setText(getString(R.string.connect_recovery_button_phone));
                setButton2Visible(false);
            }
            case PhoneOrExtended -> {
                Toast.makeText(requireActivity(), "Not ready yet!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void checkPhoneNumber() {
        if (!skipPhoneNumberCheck) {
            String phone = PhoneNumberHelper.buildPhoneNumber(binding.connectRecoveryPhoneCountryInput.getText().toString(),
                    binding.connectRecoveryPhoneInput.getText().toString());

            boolean valid = PhoneNumberHelper.isValidPhoneNumber(requireActivity(), phone);

            if (valid) {
                phone = phone.replaceAll("\\+", "%2b");
                binding.connectRecoveryPhoneMessage.setText(getString(R.string.connect_phone_checking));
                binding.connectRecoveryButton1.setEnabled(false);

                boolean isBusy = !ApiConnectId.checkPhoneAvailable(requireActivity(), phone,
                        new IApiCallback() {
                            @Override
                            public void processSuccess(int responseCode, InputStream responseData) {
                                binding.connectRecoveryPhoneMessage.setText(getString(R.string.connect_phone_not_found));
                                binding.connectRecoveryButton1.setEnabled(false);
                                skipPhoneNumberCheck = false;
                            }

                            @Override
                            public void processFailure(int responseCode, IOException e) {
                                skipPhoneNumberCheck = false;
                                binding.connectRecoveryPhoneMessage.setText("");
                                binding.connectRecoveryButton1.setEnabled(true);
                            }

                            @Override
                            public void processNetworkFailure() {
                                skipPhoneNumberCheck = false;
                                binding.connectRecoveryPhoneMessage.setText(getString(R.string.recovery_network_unavailable));
                                binding.connectRecoveryButton1.setEnabled(false);
                            }

                            @Override
                            public void processOldApiError() {
                                skipPhoneNumberCheck = false;
                                binding.connectRecoveryPhoneMessage.setText(getString(R.string.recovery_network_outdated));
                                binding.connectRecoveryButton1.setEnabled(false);
                            }
                        });

                if (isBusy) {
                    Toast.makeText(requireActivity(), R.string.busy_message, Toast.LENGTH_SHORT).show();
                }
            } else {
                binding.connectRecoveryPhoneMessage.setText(getString(R.string.connect_phone_invalid));
                binding.connectRecoveryButton1.setEnabled(false);
            }
        }
    }

    public void setButton2Visible(boolean visible) {
        binding.connectRecoveryButton2.setVisibility(visible ? View.VISIBLE : View.GONE);
        binding.connectRecoveryOr.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void requestInputFocus() {
        KeyboardHelper.showKeyboardOnInput(requireActivity(), binding.connectRecoveryPhoneInput);
    }

    public void setPhoneInputVisible(boolean visible) {
        binding.connectRecoveryPhoneBlock.setVisibility(visible ? View.VISIBLE : View.GONE);
        binding.connectRecoveryPhoneMessage.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

}