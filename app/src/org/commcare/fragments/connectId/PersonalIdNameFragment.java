package org.commcare.fragments.connectId;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.ConnectIDManager;
import org.commcare.connect.database.ConnectDatabaseHelper;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.PersonalidNameFragmentBinding;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.services.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

public class PersonalIdNameFragment extends Fragment {
    private PersonalidNameFragmentBinding binding;
    private Activity activity;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = PersonalidNameFragmentBinding.inflate(inflater, container, false);
        activity = requireActivity();
        View view = binding.getRoot();
        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        setListeners();
        updateButtonEnabled(false);
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void setListeners() {
        binding.personalidNameContinueButton.setOnClickListener(v -> handleContinueButtonPress());
    }

    public void updateButtonEnabled(boolean isEnabled) {
        binding.personalidNameContinueButton.setEnabled(isEnabled);
    }

    private void handleContinueButtonPress() {
        if (binding.nameTextValue.getText().toString().isEmpty()) {
            showError(activity.getString(R.string.name_empty_error));
        } else {
            verifyOrAddName();
        }
    }

    private void verifyOrAddName() {
        ///TODO ADD API CALL
    }


    void updateUi(String errorMessage) {
        if (errorMessage == null || errorMessage.isEmpty()) {
            clearError();
            updateButtonEnabled(true);
        } else {
            showError(errorMessage);
            updateButtonEnabled(false);
        }
    }

    private void showError(String errorMessage) {
        binding.personalidNameError.setVisibility(View.VISIBLE);
        binding.personalidNameError.setText(errorMessage);
    }

    private void clearError() {
        binding.personalidNameError.setVisibility(View.GONE);
    }

    private NavDirections navigateToBackupCodePage() {
        return PersonalIdNameFragmentDirections.actionPersonalidNameToPersonalidBackupCode("", "").setIsRecovery(
                false);
    }

}
