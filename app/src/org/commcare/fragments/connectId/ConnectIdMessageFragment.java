package org.commcare.fragments.connectId;

import static android.app.Activity.RESULT_OK;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.commcare.activities.SettingsHelper;
import org.commcare.activities.connect.ConnectIdActivity;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.ConnectDatabaseHelper;
import org.commcare.connect.ConnectManager;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ScreenConnectMessageBinding;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ConnectIdMessageFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ConnectIdMessageFragment extends BottomSheetDialogFragment {
    private String title;
    private String message;
    private String buttonText;
    private String button2Text;
    private String userName;
    private String password;
    private int callingClass;

    private ScreenConnectMessageBinding binding;


    public ConnectIdMessageFragment() {
        // Required empty public constructor
    }

    public static ConnectIdMessageFragment newInstance() {
        return new ConnectIdMessageFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = ScreenConnectMessageBinding.inflate(inflater, container, false);
        View view = binding.getRoot();
        binding.connectMessageButton.setOnClickListener(v -> handleButtonPress(false));
        binding.connectMessageButton2.setOnClickListener(v -> handleButtonPress(true));
        title = ConnectIdMessageFragmentArgs.fromBundle(getArguments()).getTitle();
        message = ConnectIdMessageFragmentArgs.fromBundle(getArguments()).getMessage();
        buttonText = ConnectIdMessageFragmentArgs.fromBundle(getArguments()).getButtonText();
        callingClass = ConnectIdMessageFragmentArgs.fromBundle(getArguments()).getCallingClass();
        userName = ConnectIdMessageFragmentArgs.fromBundle(getArguments()).getPhone();
        password = ConnectIdMessageFragmentArgs.fromBundle(getArguments()).getPassword();
        if (ConnectIdMessageFragmentArgs.fromBundle(getArguments()).getButton2Text() != null && !ConnectIdMessageFragmentArgs.fromBundle(getArguments()).getButton2Text().isEmpty()) {
            button2Text = ConnectIdMessageFragmentArgs.fromBundle(getArguments()).getButton2Text();

        }
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        binding.connectMessageTitle.setText(title);
        binding.connectMessageMessage.setText(message);
        binding.connectMessageButton.setText(buttonText);
        setButton2Text(button2Text);
    }

    public void setButton2Text(String buttonText) {
        boolean show = buttonText != null;
        binding.connectMessageButton2.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            binding.connectMessageButton2.setText(buttonText);
        }
    }

    public void handleButtonPress(boolean secondButton) {
        finish(true, secondButton);
    }

    public void finish(boolean success, boolean secondButton) {
        NavDirections directions = null;
        ConnectUserRecord user = ConnectDatabaseHelper.getUser(getActivity());
        switch (callingClass) {
            case ConnectConstants.CONNECT_REGISTRATION_SUCCESS:
                if (success) {
                    ConnectManager.setStatus(ConnectManager.ConnectIdStatus.LoggedIn);
                    ConnectDatabaseHelper.setRegistrationPhase(getActivity(), ConnectConstants.CONNECT_NO_ACTIVITY);
                    requireActivity().setResult(RESULT_OK);
                    requireActivity().finish();

                }
                break;
            //CONNECT_RECOVERY_ALT_PHONE_MESSAGE
            case ConnectConstants.CONNECT_RECOVERY_ALT_PHONE_MESSAGE:
                if (success) {
                    if (secondButton) {
                        directions = ConnectIdMessageFragmentDirections.actionConnectidMessageSelf(getString(R.string.connect_deactivate_account), getString(R.string.connect_deactivate_account_message), ConnectConstants.CONNECT_USER_DEACTIVATE_CONFIRMATION, getString(R.string.connect_deactivate_account_delete), getString(R.string.connect_deactivate_account_go_back), userName, password);
                    } else {
                        directions = ConnectIdMessageFragmentDirections.actionConnectidMessageToConnectidPhoneVerify(ConnectConstants.CONNECT_RECOVERY_VERIFY_ALT_PHONE, String.format(Locale.getDefault(), "%d",
                                ConnectIdPhoneVerificationFragmnet.MethodRecoveryAlternate), null, ConnectIdActivity.recoverPhone, ConnectIdActivity.recoverSecret, ConnectIdActivity.recoveryAltPhone).setAllowChange(false);

                    }
                }
                break;
            case ConnectConstants.CONNECT_RECOVERY_SUCCESS:
                ConnectManager.setStatus(ConnectManager.ConnectIdStatus.LoggedIn);
                ConnectDatabaseHelper.setRegistrationPhase(getActivity(), ConnectConstants.CONNECT_NO_ACTIVITY);
                requireActivity().setResult(RESULT_OK);
                requireActivity().finish();
                break;
            case ConnectConstants.CONNECT_BIOMETRIC_ENROLL_FAIL:
                if (success) {
                    SettingsHelper.launchSecuritySettings(requireActivity());
                } else {
                    directions = ConnectIdMessageFragmentDirections.actionConnectidMessageToConnectidBiometricConfig(ConnectConstants.CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS);
                }
                break;
            case ConnectConstants.CONNECT_RECOVERY_VERIFY_PASSWORD:
                if (success) {
                    if (ConnectIdActivity.forgotPassword) {
                        directions = ConnectIdMessageFragmentDirections.actionConnectidMessageSelf(getString(R.string.connect_recovery_alt_title), getString(R.string.connect_recovery_alt_message), ConnectConstants.CONNECT_RECOVERY_ALT_PHONE_MESSAGE, getString(R.string.connect_recovery_alt_button), null, userName, password);
                    } else {
                        directions = ConnectIdMessageFragmentDirections.actionConnectidMessageToConnectidPin(ConnectConstants.CONNECT_RECOVERY_CHANGE_PIN, ConnectIdActivity.recoverPhone, ConnectIdActivity.recoverSecret).setRecover(true).setChange(true);
                    }
                } else {
                    directions = ConnectIdMessageFragmentDirections.actionConnectidMessageToConnectidPhoneVerify(ConnectConstants.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE, String.format(Locale.getDefault(), "%d",
                            ConnectIdPhoneVerificationFragmnet.MethodRecoveryPrimary), ConnectIdActivity.recoverPhone, ConnectIdActivity.recoverPhone, null, null).setAllowChange(false);
                }
            case ConnectConstants.CONNECT_UNLOCK_ALT_PHONE_MESSAGE:
                if (success) {
                    if (secondButton) {
                        directions = ConnectIdMessageFragmentDirections.actionConnectidMessageToConnectidSecondaryPhoneFragment(ConnectConstants.CONNECT_UNLOCK_ALT_PHONE_CHANGE, ConnectConstants.METHOD_CHANGE_ALTERNATE, null);
                    } else {
                        directions = ConnectIdMessageFragmentDirections.actionConnectidMessageToConnectidPhoneVerify(ConnectConstants.CONNECT_UNLOCK_VERIFY_ALT_PHONE, String.format(Locale.getDefault(), "%d",
                                ConnectIdPhoneVerificationFragmnet.MethodVerifyAlternate), null, user.getUserId(), user.getPassword(), null).setAllowChange(false);
                    }
                }
                break;
            //CONNECT_RECOVERY_WRONG_PIN
            case ConnectConstants.CONNECT_RECOVERY_WRONG_PIN:
                if (success) {
                    if (ConnectManager.getFailureAttempt() > 2) {
                        directions = ConnectIdMessageFragmentDirections.actionConnectidMessageToConnectidPhoneVerify(ConnectConstants.CONNECT_RECOVERY_VERIFY_ALT_PHONE, String.format(Locale.getDefault(), "%d",
                                ConnectIdPhoneVerificationFragmnet.MethodRecoveryAlternate), null, ConnectIdActivity.recoverPhone, ConnectIdActivity.recoverSecret, ConnectIdActivity.recoveryAltPhone).setAllowChange(false);
                        ConnectManager.setFailureAttempt(0);
                    } else {
                        directions = ConnectIdMessageFragmentDirections.actionConnectidMessageToConnectidPin(ConnectConstants.CONNECT_RECOVERY_VERIFY_PIN, ConnectIdActivity.recoverPhone, ConnectIdActivity.recoverSecret).setChange(false).setRecover(true);
                    }
                }
                break;
            case ConnectConstants.CONNECT_REGISTRATION_WRONG_PIN:
                if (success) {
                    if (ConnectManager.getFailureAttempt() > 2) {
                        directions = ConnectIdMessageFragmentDirections.actionConnectidMessageToConnectidPin(ConnectConstants.CONNECT_REGISTRATION_CHANGE_PIN, user.getPrimaryPhone(), "").setChange(true).setRecover(false);

                    } else {
                        directions = ConnectIdMessageFragmentDirections.actionConnectidMessageToConnectidPin(ConnectConstants.CONNECT_REGISTRATION_CONFIRM_PIN, user.getPrimaryPhone(), "").setChange(false).setRecover(false);
                    }
                }
                break;
            case ConnectConstants.CONNECT_VERIFY_ALT_PHONE_MESSAGE:
                if (success) {
                    if (secondButton) {
                        directions = ConnectIdMessageFragmentDirections.actionConnectidMessageToConnectidPhone(ConnectConstants.METHOD_CHANGE_ALTERNATE, user.getAlternatePhone(), ConnectConstants.CONNECT_VERIFY_ALT_PHONE_CHANGE);
                    } else {
                        directions = ConnectIdMessageFragmentDirections.actionConnectidMessageToConnectidPhoneVerify(ConnectConstants.CONNECT_VERIFY_ALT_PHONE, String.format(Locale.getDefault(), "%d",
                                ConnectIdPhoneVerificationFragmnet.MethodVerifyAlternate), null, user.getUserId(), user.getPassword(), null).setAllowChange(false);
                    }
                }
                break;
            case ConnectConstants.CONNECT_USER_DEACTIVATE_CONFIRMATION:
                if (success) {
                    if (!secondButton) {
                        initiateDeactivation();
                    }
                }
                break;
        }
        if (success) {
            if (directions != null) {
                NavHostFragment.findNavController(this).navigate(directions);
            }
        }
    }

    private void initiateDeactivation() {
        boolean isBusy = !ApiConnectId.requestInitiateAccountDeactivation(getContext(), userName, password, new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                try {
                    String responseAsString = new String(StreamsUtil.inputStreamToByteArray(responseData));
                    if (responseAsString.length() > 0) {
                        JSONObject json = new JSONObject(responseAsString);
                        if (json.getBoolean("success")) {
                            finish(true, false);
                        }
                    }
                } catch (IOException e) {
                    Logger.exception("User deactivation", e);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
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
            }

            @Override
            public void processNetworkFailure() {
            }

            @Override
            public void processOldApiError() {
            }
        });

        if (isBusy) {
            Toast.makeText(getContext(), R.string.busy_message, Toast.LENGTH_SHORT).show();
        }
    }
}