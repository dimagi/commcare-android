package org.commcare.fragments.connectId;

import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ScreenPersonalidNameBinding;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;

public class PersonalIdNameFragment extends Fragment {
    private ScreenPersonalidNameBinding binding;
    private Activity activity;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = ScreenPersonalidNameBinding.inflate(inflater, container, false);
        activity = requireActivity();
        activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        setListeners();
        enableContinueButton(false);
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void setListeners() {
        binding.personalidNameContinueButton.setOnClickListener(v -> handleContinueButtonPress());
    }

    public void enableContinueButton(boolean isEnabled) {
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


    private void updateUi(String errorMessage) {
        if (TextUtils.isEmpty(errorMessage)) {
            clearError();
            enableContinueButton(true);
        } else {
            showError(errorMessage);
            enableContinueButton(false);
        }
    }

    private void showError(String errorMessage) {
        binding.personalidNameError.setVisibility(View.VISIBLE);
        binding.personalidNameError.setText(errorMessage);
    }

    private void clearError() {
        binding.personalidNameError.setText("");
        binding.personalidNameError.setVisibility(View.GONE);
    }

    private NavDirections navigateToBackupCodePage() {
        return PersonalIdNameFragmentDirections.actionPersonalidNameToPersonalidBackupCode("", "").setIsRecovery(
                false);
    }

}
