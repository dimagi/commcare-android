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
import android.widget.Toast;

import org.commcare.activities.connect.viewmodel.PersonalIdSessionDataViewModel;
import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.connect.network.PersonalIdApiErrorHandler;
import org.commcare.connect.network.PersonalIdApiHandler;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ScreenPersonalidNameBinding;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

public class PersonalIdNameFragment extends Fragment {
    private ScreenPersonalidNameBinding binding;
    private Activity activity;
    private PersonalIdSessionDataViewModel personalIdSessionDataViewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = ScreenPersonalidNameBinding.inflate(inflater, container, false);
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
        enableContinueButton(false);
        new PersonalIdApiHandler() {
            @Override
            protected void onSuccess(PersonalIdSessionData sessionData) {
                sessionData.setUserName(binding.nameTextValue.getText().toString().trim());
                Navigation.findNavController(binding.getRoot()).navigate(navigateToBackupCodePage());
            }
            @Override
            protected void onFailure(PersonalIdApiErrorCodes failureCode) {
                navigateFailure(failureCode);
            }
        }.addOrVerifyNameCall(
                requireActivity(),
                binding.nameTextValue.getText().toString().trim(),
                personalIdSessionDataViewModel.getPersonalIdSessionData());
    }


    private void navigateFailure(PersonalIdApiHandler.PersonalIdApiErrorCodes failureCode) {
        if (failureCode.shouldAllowRetry()) {
            enableContinueButton(true);
        }
        PersonalIdApiErrorHandler.handle(requireActivity(), failureCode);
    }

    private NavDirections navigateToBackupCodePage() {
        return PersonalIdNameFragmentDirections.actionPersonalidNameToPersonalidBackupCode();
    }

}
