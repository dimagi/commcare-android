package org.commcare.fragments.personalId;

import android.Manifest;
import android.app.Activity;
import android.content.IntentSender;
import android.location.Location;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;

import org.commcare.activities.connect.viewmodel.PersonalIdSessionDataViewModel;
import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.commcare.android.integrity.IntegrityTokenApiRequestHelper;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.network.PersonalIdApiErrorHandler;
import org.commcare.connect.network.PersonalIdApiHandler;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ScreenPersonalidPhonenoBinding;
import org.commcare.interfaces.RuntimePermissionRequester;
import org.commcare.location.CommCareLocationController;
import org.commcare.location.CommCareLocationControllerFactory;
import org.commcare.location.CommCareLocationListener;
import org.commcare.util.LogTypes;
import org.commcare.utils.GeoUtils;
import org.commcare.utils.Permissions;
import org.commcare.utils.PhoneNumberHelper;
import org.javarosa.core.services.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

import static org.commcare.utils.Permissions.shouldShowPermissionRationale;

public class PersonalIdPhoneFragment extends Fragment implements CommCareLocationListener,
        RuntimePermissionRequester {

    private ScreenPersonalidPhonenoBinding binding;
    private boolean shouldShowPhoneHintDialog = true;
    private PhoneNumberHelper phoneNumberHelper;
    private Activity activity;
    private PersonalIdSessionDataViewModel personalIdSessionDataViewModel;
    private IntegrityTokenApiRequestHelper integrityTokenApiRequestHelper;
    private String phone;
    private Location location;
    private static final int LOCATION_SETTING_REQ = 102;
    private CommCareLocationController locationController;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;

    private static final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.ACCESS_COARSE_LOCATION
    };


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = ScreenPersonalidPhonenoBinding.inflate(inflater, container, false);
        activity = requireActivity();
        phoneNumberHelper = PhoneNumberHelper.getInstance(activity);
        activity.setTitle(R.string.connect_registration_title);
        activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        personalIdSessionDataViewModel = new ViewModelProvider(requireActivity()).get(
                PersonalIdSessionDataViewModel.class);
        locationController = CommCareLocationControllerFactory.getLocationController(requireActivity(), this);
        integrityTokenApiRequestHelper = new IntegrityTokenApiRequestHelper(getViewLifecycleOwner());
        initializeUi();
        registerLocationPermissionLauncher();
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        checkLocationPermission();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (locationController != null) {
            locationController.stop();
            locationController.destroy();
        }
        binding = null;
    }

    private void initializeUi() {
        binding.countryCode.setText(phoneNumberHelper.setDefaultCountryCode(getContext()));
        binding.checkText.setMovementMethod(LinkMovementMethod.getInstance());
        setupListeners();
        updateContinueButtonState();
    }

    private void setupListeners() {
        binding.connectConsentCheck.setOnClickListener(v -> updateContinueButtonState());
        binding.personalidPhoneContinueButton.setOnClickListener(v -> onContinueClicked());

        ActivityResultLauncher<IntentSenderRequest> phoneHintLauncher = setupPhoneHintLauncher();

        View.OnFocusChangeListener focusChangeListener = (v, hasFocus) -> {
            if (hasFocus && shouldShowPhoneHintDialog) {
                PhoneNumberHelper.requestPhoneNumberHint(phoneHintLauncher, activity);
                shouldShowPhoneHintDialog = false;
            }
        };

        binding.connectPrimaryPhoneInput.addTextChangedListener(createPhoneNumberWatcher());
        binding.countryCode.addTextChangedListener(phoneNumberHelper.getCountryCodeWatcher(binding.countryCode));

        binding.connectPrimaryPhoneInput.setOnFocusChangeListener(focusChangeListener);
        binding.countryCode.setOnFocusChangeListener(focusChangeListener);
    }

    private ActivityResultLauncher<IntentSenderRequest> setupPhoneHintLauncher() {
        return registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        try {
                            String phoneNumber = Identity.getSignInClient(activity).getPhoneNumberFromIntent(
                                    result.getData());
                            displayPhoneNumber(phoneNumber);
                        } catch (ApiException e) {
                            Toast.makeText(getContext(), R.string.error_occured, Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        binding.connectPrimaryPhoneInput.post(
                                () -> binding.connectPrimaryPhoneInput.requestFocus());
                    }
                }
        );
    }

    private TextWatcher createPhoneNumberWatcher() {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateContinueButtonState();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
    }

    private void updateContinueButtonState() {
        phone = PhoneNumberHelper.buildPhoneNumber(
                binding.countryCode.getText().toString(),
                binding.connectPrimaryPhoneInput.getText().toString()
        );

        boolean isValidPhone = phoneNumberHelper.isValidPhoneNumber(phone);
        boolean isConsentChecked = binding.connectConsentCheck.isChecked();

        enableContinueButton(isValidPhone && isConsentChecked && location!=null);
    }

    private void displayPhoneNumber(String fullPhoneNumber) {

        if(TextUtils.isEmpty(fullPhoneNumber))return;

        int countryCodeFromFullPhoneNumber = phoneNumberHelper.getCountryCode(fullPhoneNumber);
        long nationPhoneNumberFromFullPhoneNumber = phoneNumberHelper.getNationalNumber(fullPhoneNumber);

        if(countryCodeFromFullPhoneNumber!=-1 && nationPhoneNumberFromFullPhoneNumber!=-1){
            binding.connectPrimaryPhoneInput.setText(String.valueOf(nationPhoneNumberFromFullPhoneNumber));
            binding.countryCode.setText(phoneNumberHelper.formatCountryCode(countryCodeFromFullPhoneNumber));
        }

    }

    private void onContinueClicked() {
        enableContinueButton(false);
        startConfigurationRequest();
    }

    private void enableContinueButton(boolean isEnabled) {
        binding.personalidPhoneContinueButton.setEnabled(isEnabled);
    }

    private void startConfigurationRequest() {
        clearError();
        phone = PhoneNumberHelper.buildPhoneNumber(
                binding.countryCode.getText().toString(),
                binding.connectPrimaryPhoneInput.getText().toString()
        );

        HashMap<String, String> body = new HashMap<>();
        body.put("phone_number", phone);
        body.put("application_id", requireContext().getPackageName());
        body.put("gps_location", GeoUtils.locationToString(location));

        integrityTokenApiRequestHelper.withIntegrityToken(body, (integrityToken, requestHash) -> {
            if (integrityToken != null) {
                makeStartConfigurationCall(integrityToken, requestHash, body);
            } else {
                onConfigurationFailure();
            }
            return null;
        });
    }

    @Override
    public void onLocationResult(@NonNull Location result) {
        location = result;
        locationController.stop();
    }

    @Override
    public void onLocationRequestFailure(@NonNull Failure failure) {
        if (failure instanceof CommCareLocationListener.Failure.ApiException) {
            Exception exception = ((CommCareLocationListener.Failure.ApiException)failure).getException();
            if (exception instanceof ResolvableApiException) {
                try {
                    ((ResolvableApiException)exception).startResolutionForResult(requireActivity(),
                            LOCATION_SETTING_REQ);
                } catch (IntentSender.SendIntentException e) {
                    Logger.log("Location Error", e.getMessage());
                }
            }
        }
    }

    @Override
    public void onLocationRequestStart() {
    }

    @Override
    public void requestNeededPermissions(int requestCode) {
    }

    private void checkLocationPermission() {
        if (Permissions.missingAppPermission(requireActivity(), REQUIRED_PERMISSIONS)) {
            if (!shouldShowPermissionRationale(requireActivity(), REQUIRED_PERMISSIONS)) {
                locationPermissionLauncher.launch(REQUIRED_PERMISSIONS);
            } else {
                navigateToPermissionErrorMessageDisplay();
            }
        } else {
            locationController.start();
        }
    }

    private boolean isOnPermissionErrorScreen() {
        return Navigation.findNavController(requireView())
                .getCurrentDestination()
                .getId() == R.id.personalid_message_display;
    }

    private void registerLocationPermissionLauncher() {
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean allPermissionsGranted = !Permissions.missingAppPermission(requireActivity(), REQUIRED_PERMISSIONS);

                    if (allPermissionsGranted) {
                        locationController.start();
                    } else {
                        boolean permanentlyDenied = false;
                        for (String permission : REQUIRED_PERMISSIONS) {
                            if (!Permissions.shouldShowPermissionRationale(requireActivity(), permission)
                                    && Permissions.missingAppPermission(requireActivity(), permission)) {
                                permanentlyDenied = true;
                                break;
                            }
                        }

                        if (permanentlyDenied) {
                            if (!isOnPermissionErrorScreen()) {
                                navigateToPermissionErrorMessageDisplay();// Show error with navigation to settings
                            }
                        } else {
                            locationPermissionLauncher.launch(REQUIRED_PERMISSIONS); // Retry asking
                        }
                    }
                }
        );
    }


    private void makeStartConfigurationCall(@Nullable String integrityToken, String requestHash,
                                            HashMap<String, String> body) {
        Log.d("Integrity", "Token: " + integrityToken);
        Log.d("Integrity", "Hash: " + requestHash);
        new PersonalIdApiHandler() {
            @Override
            protected void onSuccess(PersonalIdSessionData sessionData) {
                personalIdSessionDataViewModel.setPersonalIdSessionData(sessionData);
                personalIdSessionDataViewModel.getPersonalIdSessionData().setPhoneNumber(phone);
                if (personalIdSessionDataViewModel.getPersonalIdSessionData().getToken() != null) {
                    onConfigurationSuccess();
                } else {
                    // This is called when api returns success but with a a failure code
                    Logger.log(LogTypes.TYPE_USER,
                            personalIdSessionDataViewModel.getPersonalIdSessionData().getSessionFailureCode());
                    onConfigurationFailure();
                }
            }

            @Override
            protected void onFailure(PersonalIdApiErrorCodes failureCode, Throwable t) {
                if(failureCode == PersonalIdApiErrorCodes.FORBIDDEN_ERROR) {
                    onConfigurationFailure();
                } else {
                    navigateFailure(failureCode, t);
                }
            }
        }.makeStartConfigurationCall(requireActivity(), body, integrityToken,requestHash);
    }


    private void onConfigurationSuccess() {
        Navigation.findNavController(binding.personalidPhoneContinueButton).navigate(navigateToBiometricSetup());
    }

    private void onConfigurationFailure() {
        String failureMessage = getString(R.string.personalid_configuration_process_failed_subtitle);
        Navigation.findNavController(binding.personalidPhoneContinueButton).navigate(
                navigateToMessageDisplay(failureMessage,false,   ConnectConstants.PERSONALID_DEVICE_CONFIGURATION_FAILED));
    }

    private void navigateFailure(PersonalIdApiHandler.PersonalIdApiErrorCodes failureCode, Throwable t) {
        showError(PersonalIdApiErrorHandler.handle(requireActivity(), failureCode, t));

        if (failureCode.shouldAllowRetry()) {
            enableContinueButton(true);
        }
    }

    private void clearError() {
        binding.personalidPhoneError.setVisibility(View.GONE);
        binding.personalidPhoneError.setText("");
    }

    private void showError(String error) {
        binding.personalidPhoneError.setVisibility(View.VISIBLE);
        binding.personalidPhoneError.setText(error);
    }

    private NavDirections navigateToBiometricSetup() {
        return PersonalIdPhoneFragmentDirections.actionPersonalidPhoneFragmentToPersonalidBiometricConfig();
    }

    private NavDirections navigateToMessageDisplay(String errorMessage, boolean isCancellable,int phase) {
        return PersonalIdPhoneFragmentDirections.actionPersonalidPhoneFragmentToPersonalidMessageDisplay(
                getString(R.string.personalid_configuration_process_failed_title),
                errorMessage,
                phase, getString(R.string.ok),
                null).setIsCancellable(isCancellable);
    }

    private void navigateToPermissionErrorMessageDisplay() {
        Navigation.findNavController(binding.personalidPhoneContinueButton).navigate(
                navigateToMessageDisplay(
                        requireActivity().getString(R.string.personalid_location_permission_error), true,
                        ConnectConstants.PERSONALID_LOCATION_PERMISSION_FAILURE));
    }

    @Override
    public void missingPermissions() {
    }
}
