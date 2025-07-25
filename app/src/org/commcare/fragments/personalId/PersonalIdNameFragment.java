package org.commcare.fragments.personalId;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

import org.commcare.activities.connect.viewmodel.PersonalIdSessionDataViewModel;
import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.commcare.connect.network.connectId.PersonalIdApiErrorHandler;
import org.commcare.connect.network.connectId.PersonalIdApiHandler;
import org.commcare.dalvik.databinding.ScreenPersonalidNameBinding;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PersonalIdNameFragment extends BasePersonalIdFragment {
    private ScreenPersonalidNameBinding binding;
    private Activity activity;
    private PersonalIdSessionData personalIdSessionData;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = ScreenPersonalidNameBinding.inflate(inflater, container, false);
        personalIdSessionData = new ViewModelProvider(requireActivity()).get(
                PersonalIdSessionDataViewModel.class).getPersonalIdSessionData();

        activity = requireActivity();
        activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        setListeners();
        enableContinueButton(false);
        binding.nameTextValue.addTextChangedListener(createNameWatcher());
        return binding.getRoot();
    }

    private TextWatcher createNameWatcher() {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                enableContinueButton(!TextUtils.isEmpty(s) && !TextUtils.isEmpty(s.toString().trim()));
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void setListeners() {
        binding.personalidNameContinueButton.setOnClickListener(v -> verifyOrAddName());
    }

    private void enableContinueButton(boolean isEnabled) {
        binding.personalidNameContinueButton.setEnabled(isEnabled);
    }

    private void verifyOrAddName() {
        clearError();
        enableContinueButton(false);
        new PersonalIdApiHandler<PersonalIdSessionData>() {
            @Override
            public void onSuccess(PersonalIdSessionData sessionData) {
                sessionData.setUserName(binding.nameTextValue.getText().toString().trim());
                Navigation.findNavController(binding.getRoot()).navigate(navigateToBackupCodePage());
            }
            @Override
            public void onFailure(PersonalIdOrConnectApiErrorCodes failureCode, Throwable t) {
                if (handleCommonSignupFailures(failureCode)) {
                    return;
                }
                navigateFailure(failureCode, t);
            }
        }.addOrVerifyNameCall(
                requireActivity(),
                binding.nameTextValue.getText().toString().trim(),
                personalIdSessionData);
    }


    private void navigateFailure(PersonalIdApiHandler.PersonalIdOrConnectApiErrorCodes failureCode, Throwable t) {
        showError(PersonalIdApiErrorHandler.handle(requireActivity(), failureCode, t));

        if (failureCode.shouldAllowRetry()) {
            enableContinueButton(true);
        }
    }

    private void clearError() {
        binding.personalidNameError.setVisibility(View.GONE);
        binding.personalidNameError.setText("");
    }

    private void showError(String message) {
        binding.personalidNameError.setVisibility(View.VISIBLE);
        binding.personalidNameError.setText(message);
    }

    private NavDirections navigateToBackupCodePage() {
        return PersonalIdNameFragmentDirections.actionPersonalidNameToPersonalidBackupCode();
    }

    @Override
    protected void navigateToMessageDisplay(@NotNull String title, @Nullable String message, boolean isCancellable,
            int phase, int buttonText) {
        NavDirections action = PersonalIdNameFragmentDirections
                .actionPersonalidNameToPersonalidMessage(title, message, phase, getString(buttonText), null)
                .setIsCancellable(isCancellable);
        Navigation.findNavController(binding.getRoot()).navigate(action);
    }
}
