package org.commcare.fragments.connectId;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import org.commcare.connect.ConnectConstants;
import org.commcare.dalvik.R;

public class ConnectIdConsentFragment extends Fragment {

    private TextView messageText;
    private CheckBox checkbox;
    private Button button;

    public ConnectIdConsentFragment() {
        // Required empty public constructor
    }
    public static ConnectIdConsentFragment newInstance(String param1, String param2) {
        ConnectIdConsentFragment fragment = new ConnectIdConsentFragment();
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
        // Inflate the layout for this fragment
        View view= inflater.inflate(R.layout.screen_connect_consent, container, false);
        messageText= view.findViewById(R.id.connect_consent_message_1);
        checkbox= view.findViewById(R.id.connect_consent_check);
        button= view.findViewById(R.id.connect_consent_button);
        messageText.setMovementMethod(LinkMovementMethod.getInstance());
        checkbox.setOnClickListener(v -> updateState());
        requireActivity().setTitle(getString(R.string.connect_consent_title));
        button.setOnClickListener(v -> handleButtonPress());

        return view;
    }

    public void updateState() {
        button.setEnabled(checkbox.isChecked());
    }

    public void finish(boolean accepted) {
        NavDirections directions;
        if (accepted) {
            directions = ConnectIdConsentFragmentDirections.actionConnectidConsentToConnectidPhone(ConnectConstants.CONNECT_REGISTRATION_PRIMARY_PHONE,ConnectConstants.METHOD_REGISTER_PRIMARY,null);
            Navigation.findNavController(button).navigate(directions);
        }
    }

    public void handleButtonPress() {
        finish(true);
    }
}