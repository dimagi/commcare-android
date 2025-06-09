package org.commcare.fragments.personalId;

import android.Manifest;
import android.app.Activity;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
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
import org.commcare.utils.PhoneNumberHelper;

import java.util.HashMap;

public class PersonalIdPhoneFragment extends Fragment implements CommCareLocationListener,
        RuntimePermissionRequester {

    private ScreenPersonalidPhonenoBinding binding;
    private Activity activity;
    private PhoneNumberHelper phoneNumberHelper;
    private PersonalIdSessionDataViewModel personalIdSessionDataViewModel;
    private IntegrityTokenApiRequestHelper integrityTokenApiRequestHelper;
    private CommCareLocationController locationController;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;
    private ActivityResultLauncher<IntentSenderRequest> phoneHintLauncher;

    private boolean shouldShowPhoneHintDialog = true;
    private Location location;
    private String phone;
    private static final int LOCATION_SETTING_REQ = 102;

    private static final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = ScreenPersonalidPhonenoBinding.inflate(inflater, container, false);
        activity = requireActivity();

        setupInitialObjects();
        setupPhoneHintLauncher();
        setupLocationPermissionLauncher();
        setupUi();

        checkAndRequestLocationPermission();
        return binding.getRoot();
    }

    private void setupInitialObjects() {
        phoneNumberHelper = PhoneNumberHelper.getInstance(activity);
        personalIdSessionDataViewModel = new ViewModelProvider(requireActivity()).get(
                PersonalIdSessionDataViewModel.class);
        integrityTokenApiRequestHelper = new IntegrityTokenApiRequestHelper(getViewLifecycleOwner());
        locationController = CommCareLocationControllerFactory.getLocationController(requireActivity(), this);

        activity.setTitle(R.string.connect_registration_title);
        activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
    }

    private void setupUi() {
        binding.countryCode.setText(phoneNumberHelper.setDefaultCountryCode(getContext()));
        setupListeners();
        updateContinueButtonState();
    }

    private void setupPhoneHintLauncher() {
        phoneHintLauncher = registerForActivityResult(
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
                });
    }

    private void setupLocationPermissionLauncher() {
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean granted = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false)
                            || result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);
                    if (granted) {
                        locationController.start();
                    } else {
                        Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void setupListeners() {
        binding.connectConsentCheck.setOnClickListener(v -> updateContinueButtonState());
        binding.personalidPhoneContinueButton.setOnClickListener(v -> onContinueClicked());

        binding.connectPrimaryPhoneInput.setOnFocusChangeListener(this::maybeShowPhoneHint);
        binding.countryCode.setOnFocusChangeListener(this::maybeShowPhoneHint);

        binding.connectPrimaryPhoneInput.addTextChangedListener(createPhoneNumberWatcher());
        binding.countryCode.addTextChangedListener(phoneNumberHelper.getCountryCodeWatcher(binding.countryCode));
    }

    private void maybeShowPhoneHint(View v, boolean hasFocus) {
        if (hasFocus && shouldShowPhoneHintDialog) {
            PhoneNumberHelper.requestPhoneNumberHint(phoneHintLauncher, activity);
            shouldShowPhoneHintDialog = false;
        }
    }

    private TextWatcher createPhoneNumberWatcher() {
        return new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateContinueButtonState();
            }

            public void afterTextChanged(Editable s) {
            }
        };
    }

    private void updateContinueButtonState() {
        phone = PhoneNumberHelper.buildPhoneNumber(
                binding.countryCode.getText().toString(),
                binding.connectPrimaryPhoneInput.getText().toString()
        );
        boolean isValid = phoneNumberHelper.isValidPhoneNumber(phone);
        boolean consentChecked = binding.connectConsentCheck.isChecked();
        binding.personalidPhoneContinueButton.setEnabled(isValid && consentChecked);
    }

    private void checkAndRequestLocationPermission() {
        if (hasLocationPermission()) {
            locationController.start();
        } else {
            locationPermissionLauncher.launch(REQUIRED_PERMISSIONS);
        }
    }

    private boolean hasLocationPermission() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(requireContext(), permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void displayPhoneNumber(String fullNumber) {
        int defaultCode = phoneNumberHelper.getCountryCodeFromLocale(activity);
        String formattedCode = phoneNumberHelper.formatCountryCode(defaultCode);

        if (fullNumber != null && fullNumber.startsWith(formattedCode)) {
            fullNumber = fullNumber.substring(formattedCode.length());
        }

        int countryCode = phoneNumberHelper.getCountryCode(fullNumber != null ? fullNumber : "");
        String countryCodeText = phoneNumberHelper.formatCountryCode(countryCode);

        binding.connectPrimaryPhoneInput.setText(fullNumber);
        binding.countryCode.setText(countryCodeText);
    }

    private void onContinueClicked() {
        binding.personalidPhoneContinueButton.setEnabled(false);
        startConfigurationRequest();
    }

    private void startConfigurationRequest() {
        phone = PhoneNumberHelper.buildPhoneNumber(
                binding.countryCode.getText().toString(),
                binding.connectPrimaryPhoneInput.getText().toString()
        );

        HashMap<String, String> body = new HashMap<>();
        body.put("phone_number", phone);
        body.put("application_id", requireContext().getPackageName());
        body.put("gps_location", location.getLatitude()+" "+location.getLongitude());

        integrityTokenApiRequestHelper.withIntegrityToken(body, (token, hash) -> {
            if (token != null) {
                makeStartConfigurationCall(token, hash, body);
            } else {
                onConfigurationFailure();
            }
            return null;
        });
    }

    private void makeStartConfigurationCall(String token, String hash, HashMap<String, String> body) {
        new PersonalIdApiHandler() {
            protected void onSuccess(PersonalIdSessionData sessionData) {
                personalIdSessionDataViewModel.setPersonalIdSessionData(sessionData);
                sessionData.setPhoneNumber(phone);

                if (sessionData.getToken() != null) {
                    Navigation.findNavController(binding.personalidPhoneContinueButton).navigate(
                            PersonalIdPhoneFragmentDirections.actionPersonalidPhoneFragmentToPersonalidBiometricConfig());
                } else {
                    onConfigurationFailure();
                }
            }

            protected void onFailure(PersonalIdApiErrorCodes code) {
                if (code.shouldAllowRetry()) {
                    binding.personalidPhoneContinueButton.setEnabled(true);
                }
                PersonalIdApiErrorHandler.handle(requireActivity(), code);
            }
        }.makeStartConfigurationCall(requireActivity(), body, token, hash);
    }

    private void onConfigurationFailure() {
        NavDirections directions =
                PersonalIdPhoneFragmentDirections.actionPersonalidPhoneFragmentToPersonalidMessageDisplay(
                        getString(R.string.configuration_process_failed_title),
                        getString(R.string.configuration_process_failed_subtitle),
                        ConnectConstants.PERSONALID_DEVICE_CONFIGURATION_FAILED,
                        getString(R.string.ok), null).setIsCancellable(false);

        Navigation.findNavController(binding.personalidPhoneContinueButton).navigate(directions);
    }

    // Location callbacks
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
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onLocationRequestStart() {
    }

    @Override
    public void requestNeededPermissions(int requestCode) {
        ActivityCompat.requestPermissions(requireActivity(),
                REQUIRED_PERMISSIONS,
                requestCode);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (locationController != null) {
            locationController.destroy();
        }
        binding = null;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (locationController != null) {
            locationController.stop();
        }
    }

    @Override
    public void missingPermissions() {
    }
}
