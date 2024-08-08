package org.commcare.fragments.connectId;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.commcare.activities.SettingsHelper;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.ConnectDatabaseHelper;
import org.commcare.connect.ConnectManager;
import org.commcare.connect.ConnectTask;
import org.commcare.dalvik.R;

import java.util.Locale;

import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

import static org.commcare.connect.ConnectIdWorkflows.completeSignIn;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ConnectIdMessageFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ConnectIdMessageFragment extends Fragment {
    private TextView titleTextView;
    private TextView messageTextView;
    private Button button;
    private Button button2;

    private String title;
    private String message;
    private String buttonText;
    private String button2Text;
    private int callingClass;

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
        View view = inflater.inflate(R.layout.screen_connect_message, container, false);
        titleTextView = view.findViewById(R.id.connect_message_title);
        messageTextView = view.findViewById(R.id.connect_message_message);
        button = view.findViewById(R.id.connect_message_button);
        button2 = view.findViewById(R.id.connect_message_button2);
        button.setOnClickListener(v -> handleButtonPress(false));
        button2.setOnClickListener(v -> handleButtonPress(true));
        title = ConnectIdMessageFragmentArgs.fromBundle(getArguments()).getTitle();
        message = ConnectIdMessageFragmentArgs.fromBundle(getArguments()).getMessage();
        buttonText = ConnectIdMessageFragmentArgs.fromBundle(getArguments()).getButtonText();
        callingClass = ConnectIdMessageFragmentArgs.fromBundle(getArguments()).getCallingClass();
        if (ConnectIdMessageFragmentArgs.fromBundle(getArguments()).getButton2Text() != null && !ConnectIdMessageFragmentArgs.fromBundle(getArguments()).getButton2Text().isEmpty()) {
            button2Text = ConnectIdMessageFragmentArgs.fromBundle(getArguments()).getButton2Text();

        }
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        titleTextView.setText(title);
        messageTextView.setText(message);
        button.setText(buttonText);
        setButton2Text(button2Text);
    }

    public void setButton2Text(String buttonText) {
        boolean show = buttonText != null;
        button2.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            button2.setText(buttonText);
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
                    ConnectDatabaseHelper.setRegistrationPhase(getActivity(), ConnectTask.CONNECT_REGISTRATION_SUCCESS);
                    completeSignIn();
                }
                break;
            case ConnectConstants.CONNECT_RECOVERY_ALT_PHONE_MESSAGE:
                if (success) {
                    if (secondButton) {
                        directions = ConnectIdMessageFragmentDirections.actionConnectidMessageToConnectidPin(ConnectConstants.CONNECT_RECOVERY_VERIFY_PIN, ConnectConstants.recoverPhone, ConnectConstants.recoverSecret).setRecover(true).setChange(false);

                    } else {
                        directions = ConnectIdMessageFragmentDirections.actionConnectidMessageToConnectidPhoneVerify(ConnectConstants.CONNECT_RECOVERY_VERIFY_ALT_PHONE, String.format(Locale.getDefault(), "%d",
                                ConnectIdPhoneVerificationFragmnet.MethodRecoveryAlternate), null, ConnectConstants.recoverPhone, ConnectConstants.recoverSecret, ConnectConstants.recoveryAlyPhone);

                    }
                }
                break;
            case ConnectConstants.CONNECT_RECOVERY_SUCCESS:
                ConnectDatabaseHelper.setRegistrationPhase(getActivity(), ConnectTask.CONNECT_RECOVERY_SUCCESS);
                completeSignIn();
                break;
            case ConnectConstants.CONNECT_BIOMETRIC_ENROLL_FAIL:
                if (success) {
                    SettingsHelper.launchSecuritySettings(requireActivity());
                } else {
                    directions = ConnectIdMessageFragmentDirections.actionConnectidMessageToConnectidBiometricConfig(ConnectConstants.CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS);
                }
                break;
            case ConnectConstants.CONNECT_UNLOCK_ALT_PHONE_MESSAGE:
                if (success) {
                    if (secondButton) {
                        directions = ConnectIdMessageFragmentDirections.actionConnectidMessageToConnectidPhone(ConnectConstants.CONNECT_UNLOCK_ALT_PHONE_CHANGE, ConnectConstants.METHOD_CHANGE_ALTERNATE, null);
                    } else {
                        directions = ConnectIdMessageFragmentDirections.actionConnectidMessageToConnectidPhoneVerify(ConnectConstants.CONNECT_UNLOCK_VERIFY_ALT_PHONE, String.format(Locale.getDefault(), "%d",
                                ConnectIdPhoneVerificationFragmnet.MethodVerifyAlternate), null, user.getUserId(), user.getPassword(), null).setAllowChange(false);
                    }
                }
                break;
            case ConnectConstants.CONNECT_RECOVERY_WRONG_PIN:
                if (success) {
                    if (ConnectManager.getFailureAttempt() > 2) {
                        directions = ConnectIdMessageFragmentDirections.actionConnectidMessageToConnectidPhoneVerify(ConnectConstants.CONNECT_RECOVERY_VERIFY_ALT_PHONE, String.format(Locale.getDefault(), "%d",
                                ConnectIdPhoneVerificationFragmnet.MethodRecoveryAlternate), null, ConnectConstants.recoverPhone, ConnectConstants.recoverSecret, ConnectConstants.recoveryAlyPhone).setAllowChange(false);
                    } else {
                        directions = ConnectIdMessageFragmentDirections.actionConnectidMessageToConnectidPin(ConnectConstants.CONNECT_RECOVERY_VERIFY_PIN, ConnectConstants.recoverPhone, ConnectConstants.recoverSecret).setChange(false).setRecover(true);
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
                break;
        }
        if (success) {

            if (directions != null) {
                Navigation.findNavController(button).navigate(directions);
            }
        }
    }


}