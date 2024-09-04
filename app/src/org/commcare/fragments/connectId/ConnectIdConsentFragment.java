package org.commcare.fragments.connectId;

import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.commcare.connect.ConnectConstants;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ScreenConnectConsentBinding;

import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

public class ConnectIdConsentFragment extends Fragment {
    
    private ScreenConnectConsentBinding binding;


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
        binding =ScreenConnectConsentBinding.inflate(inflater,container,false);
        View view = binding.getRoot();
        binding.connectConsentMessage1.setMovementMethod(LinkMovementMethod.getInstance());
        binding.connectConsentCheck.setOnClickListener(v -> updateState());
        requireActivity().setTitle(getString(R.string.connect_consent_title));
        binding.connectConsentButton.setOnClickListener(v -> handleButtonPress());

        return view;
    }

    public void updateState() {
        binding.connectConsentButton.setEnabled(binding.connectConsentCheck.isChecked());
    }

    public void finish(boolean accepted) {
        NavDirections directions;
        if (accepted) {
            directions = ConnectIdConsentFragmentDirections.actionConnectidConsentToConnectidPhone(ConnectConstants.CONNECT_REGISTRATION_PRIMARY_PHONE,ConnectConstants.METHOD_REGISTER_PRIMARY,null);
            Navigation.findNavController(binding.connectConsentButton).navigate(directions);
        }
    }

    public void handleButtonPress() {
        finish(true);
    }
}