package org.commcare.fragments.personalId;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.location.Location;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.tasks.Task;
import com.google.android.play.core.integrity.StandardIntegrityManager;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.play.core.integrity.model.IntegrityDialogTypeCode;

import org.commcare.activities.connect.viewmodel.PersonalIdSessionDataViewModel;
import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.commcare.android.integrity.IntegrityTokenApiRequestHelper;
import org.commcare.android.integrity.IntegrityTokenViewModel;
import org.commcare.android.logging.ReportingUtils;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.network.base.BaseApiHandler;
import org.commcare.connect.network.connectId.PersonalIdApiErrorHandler;
import org.commcare.connect.network.connectId.PersonalIdApiHandler;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ScreenPersonalidPhonenoBinding;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.location.CommCareLocationController;
import org.commcare.location.CommCareLocationControllerFactory;
import org.commcare.location.CommCareLocationListener;
import org.commcare.location.LocationRequestFailureHandler;
import org.commcare.util.LogTypes;
import org.commcare.utils.GeoUtils;
import org.commcare.utils.Permissions;
import org.commcare.utils.PhoneNumberHelper;
import org.javarosa.core.services.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Objects;

import static android.app.ProgressDialog.show;
import static com.google.android.play.core.integrity.model.IntegrityDialogResponseCode.DIALOG_SUCCESSFUL;
import static org.commcare.utils.Permissions.shouldShowPermissionRationale;

public class PersonalIdPhoneFragment extends Fragment implements CommCareLocationListener {

    private ScreenPersonalidPhonenoBinding binding;
    private boolean shouldShowPhoneHintDialog = true;
    private PhoneNumberHelper phoneNumberHelper;
    private Activity activity;
    private PersonalIdSessionDataViewModel personalIdSessionDataViewModel;
    private IntegrityTokenApiRequestHelper integrityTokenApiRequestHelper;
    private String phone;
    private Location location;
    private CommCareLocationController locationController;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;
    private ActivityResultLauncher<IntentSenderRequest> resolutionLauncher;
    private String playServicesError;
    private ActivityResultLauncher<IntentSenderRequest> playServicesResolutionLauncher;



    private static final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION
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
        registerLauncher();
        checkGooglePlayServices();
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        locationController.start();
    }

    @Override
    public void onPause() {
        super.onPause();
        locationController.stop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        locationController.destroy();
        binding = null;
    }

    private void checkGooglePlayServices() {
        GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
        int status = googleApiAvailability.isGooglePlayServicesAvailable(requireActivity());
        if (status != ConnectionResult.SUCCESS) {
            playServicesError = "play_services_"+ status;
            Logger.log(LogTypes.TYPE_MAINTENANCE, "Google Play Services issue:" + playServicesError);
            if (googleApiAvailability.isUserResolvableError(status)) {
                GoogleApiAvailability.getInstance().showErrorDialogFragment(
                        requireActivity(),
                        status,
                        playServicesResolutionLauncher,
                        dialog -> onConfigurationFailure(playServicesError,
                                getString(R.string.play_service_update_error)));
            } else {
                onConfigurationFailure(playServicesError,
                        getString(R.string.play_service_error));
            }
        }
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

        enableContinueButton(isValidPhone && isConsentChecked && location != null);
    }

    private void displayPhoneNumber(String fullPhoneNumber) {

        if (TextUtils.isEmpty(fullPhoneNumber)) return;

        int countryCodeFromFullPhoneNumber = phoneNumberHelper.getCountryCode(fullPhoneNumber);
        long nationPhoneNumberFromFullPhoneNumber = phoneNumberHelper.getNationalNumber(fullPhoneNumber);

        if (countryCodeFromFullPhoneNumber != -1 && nationPhoneNumberFromFullPhoneNumber != -1) {
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
        body.put("cc_device_id", ReportingUtils.getDeviceId());

        integrityTokenApiRequestHelper.withIntegrityToken(body,
                new IntegrityTokenViewModel.IntegrityTokenCallback() {
                    @Override
                    public void onTokenReceived(@NotNull String requestHash,
                                                @NotNull StandardIntegrityManager.StandardIntegrityToken integrityTokenResponse) {
                        makeStartConfigurationCall(requestHash, body, integrityTokenResponse);
                    }

                    @Override
                    public void onTokenFailure(@NotNull Exception exception) {
                        onConfigurationFailure(AnalyticsParamValue.START_CONFIGURATION_INTEGRITY_DEVICE_FAILURE,
                                integrityTokenApiRequestHelper.getErrorForException(requireActivity(), exception));
                    }
                });
    }

    @Override
    public void onLocationResult(@NonNull Location result) {
        location = result;
        updateContinueButtonState();
    }

    @Override
    public void onLocationRequestFailure(@NonNull Failure failure) {
        LocationRequestFailureHandler.INSTANCE.handleFailure(failure,
                new LocationRequestFailureHandler.LocationResolutionCallback() {
                    @Override
                    public void onResolvableException(ResolvableApiException exception) {
                        try {
                            IntentSenderRequest request = new IntentSenderRequest.Builder(
                                    exception.getResolution()).build();
                            resolutionLauncher.launch(request);
                        } catch (Exception e) {
                            navigateToPermissionErrorMessageDisplay(
                                    R.string.personalid_location_service_error,
                                    R.string.personalid_grant_location_service
                            );
                        }
                    }

                    @Override
                    public void onNonResolvableFailure() {
                        handleNoLocationServiceProviders();
                    }
                });
    }

    private void handleNoLocationServiceProviders() {
        DialogInterface.OnCancelListener onCancelListener = dialog -> {
            location = null;
            navigateToPermissionErrorMessageDisplay(R.string.personalid_location_service_error,
                    R.string.personalid_grant_location_service);
        };

        DialogInterface.OnClickListener onChangeListener = (dialog, i) -> {
            switch (i) {
                case DialogInterface.BUTTON_POSITIVE:
                    GeoUtils.goToProperLocationSettingsScreen((AppCompatActivity)requireActivity());
                    break;
                case DialogInterface.BUTTON_NEGATIVE:
                    location = null;
                    navigateToPermissionErrorMessageDisplay(R.string.personalid_location_service_error,
                            R.string.personalid_grant_location_service);
                    break;
            }
            dialog.dismiss();
        };

        GeoUtils.showNoGpsDialog((AppCompatActivity)requireActivity(), onChangeListener, onCancelListener);
    }

    @Override
    public void onLocationRequestStart() {
    }

    private boolean isOnPermissionErrorScreen() {
        return Navigation.findNavController(requireView())
                .getCurrentDestination()
                .getId() == R.id.personalid_message_display;
    }

    private void registerLauncher() {
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean allPermissionsGranted = !Permissions.missingAppPermission(requireActivity(),
                            REQUIRED_PERMISSIONS);

                    if (allPermissionsGranted) {
                        locationController.start();
                    } else {
                        if (!isOnPermissionErrorScreen()) {
                            navigateToPermissionErrorMessageDisplay(R.string.personalid_location_permission_error,
                                    R.string.personalid_grant_location_permission);
                        }
                    }
                }
        );

        resolutionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        // User enabled location settings
                    } else {
                        // User cancelled or failed
                        navigateToPermissionErrorMessageDisplay(
                                R.string.personalid_location_service_error,
                                R.string.personalid_grant_location_service
                        );
                    }
                }
        );

        playServicesResolutionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(),
                result -> {
                    if (result.getResultCode() != Activity.RESULT_OK) {
                        onConfigurationFailure(playServicesError, getString(R.string.play_service_error));
                    }
                }
        );
    }


    private void makeStartConfigurationCall(String requestHash,
                                            HashMap<String, String> body,
                                            StandardIntegrityManager.@NotNull StandardIntegrityToken integrityTokenResponse) {
        new PersonalIdApiHandler<PersonalIdSessionData>() {
            @Override
            public void onSuccess(PersonalIdSessionData sessionData) {
                personalIdSessionDataViewModel.setPersonalIdSessionData(sessionData);
                personalIdSessionDataViewModel.getPersonalIdSessionData().setPhoneNumber(phone);
                if (personalIdSessionDataViewModel.getPersonalIdSessionData().getToken() != null) {
                    onConfigurationSuccess();
                } else {
                    String failureCode =
                            personalIdSessionDataViewModel.getPersonalIdSessionData().getSessionFailureCode();
                    // This is called when api returns success but with a a failure code
                    Logger.log(LogTypes.TYPE_MAINTENANCE, "Start Config API failed with " + failureCode);
                    onConfigurationFailure(failureCode,
                            getString(R.string.personalid_configuration_process_failed_subtitle));
                }
            }

            @Override
            public void onFailure(@androidx.annotation.Nullable PersonalIdOrConnectApiErrorCodes failureCode,
                                  @androidx.annotation.Nullable Throwable t) {
                if (failureCode == null) {
                    navigateFailure(null, t);
                    return;
                }

                switch (failureCode) {
                    case ACCOUNT_LOCKED_ERROR:
                        onConfigurationFailure(
                                AnalyticsParamValue.START_CONFIGURATION_LOCKED_ACCOUNT_FAILURE,
                                getString(R.string.personalid_configuration_locked_account)
                        );
                        break;

                    case FORBIDDEN_ERROR:
                        onConfigurationFailure(
                                AnalyticsParamValue.START_CONFIGURATION_INTEGRITY_CHECK_FAILURE,
                                getString(R.string.personalid_configuration_process_failed_subtitle)
                        );
                        break;
                    case INTEGRITY_ERROR:
                        handleIntegritySubError(integrityTokenResponse,
                                personalIdSessionDataViewModel.getPersonalIdSessionData().getSessionFailureSubcode());
                    default:
                        navigateFailure(failureCode, t);
                        break;
                }
            }
        }.makeStartConfigurationCall(requireActivity(), body, integrityTokenResponse.token(), requestHash);
    }

    private void handleIntegritySubError(StandardIntegrityManager.StandardIntegrityToken tokenResponse,
                                         @NonNull String subError) {
        switch (BaseApiHandler.PersonalIdApiSubErrorCodes.valueOf(subError)) {
            case UNLICENSED_APP_ERROR:
                showIntegrityCheckDialog(tokenResponse, IntegrityDialogTypeCode.GET_LICENSED, subError);
                break;
            default:
                onConfigurationFailure(subError,
                        getString(R.string.personalid_configuration_process_failed_subtitle));
                break;
        }
    }

    private void showIntegrityCheckDialog(StandardIntegrityManager.StandardIntegrityToken tokenResponse,
                                          int codeType, String subError) {
        Task<Integer> integrityDialogResponseCode = tokenResponse.showDialog(requireActivity(), codeType);
        integrityDialogResponseCode.addOnSuccessListener(result -> {
            if (result == DIALOG_SUCCESSFUL) {
                // Retry the integrity token check
                enableContinueButton(true);
            } else {
                // User canceled or some issue occurred
                handleIntegrityFailure(subError, "User has cancelled the integrity dialog " + result);
            }
        }).addOnFailureListener(e -> {
            // Dialog failed to launch or some error occurred
            handleIntegrityFailure(subError, "Integrity dialog failed to launch " + e.getMessage());
        });
    }

    private void handleIntegrityFailure(String subError, String logMessage) {
        Logger.log(LogTypes.TYPE_MAINTENANCE, logMessage);
        enableContinueButton(false);
        onConfigurationFailure(
                subError,
                getString(R.string.personalid_configuration_process_failed_subtitle)
        );
    }

    private void onConfigurationSuccess() {
        Navigation.findNavController(binding.personalidPhoneContinueButton).navigate(navigateToBiometricSetup());
    }


    private void onConfigurationFailure(String failureCause, String failureMessage) {
        FirebaseAnalyticsUtil.reportPersonalIdConfigurationFailure(failureCause);
        Navigation.findNavController(binding.personalidPhoneContinueButton).navigate(
                navigateToMessageDisplay(failureMessage, false,
                        ConnectConstants.PERSONALID_DEVICE_CONFIGURATION_FAILED, R.string.ok));
    }

    private void navigateFailure(PersonalIdApiHandler.PersonalIdOrConnectApiErrorCodes failureCode, Throwable t) {
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

    private NavDirections navigateToMessageDisplay(String errorMessage, boolean isCancellable, int phase,
                                                   int buttonText) {
        return PersonalIdPhoneFragmentDirections.actionPersonalidPhoneFragmentToPersonalidMessageDisplay(
                getString(R.string.personalid_configuration_process_failed_title),
                errorMessage,
                phase, getString(buttonText),
                null).setIsCancellable(isCancellable);
    }

    private void navigateToPermissionErrorMessageDisplay(int errorMeesage, int buttonText) {
        Navigation.findNavController(binding.personalidPhoneContinueButton).navigate(
                navigateToMessageDisplay(
                        requireActivity().getString(errorMeesage), true,
                        ConnectConstants.PERSONALID_LOCATION_PERMISSION_FAILURE, buttonText));
    }

    @Override
    public void missingPermissions() {
        if (!shouldShowPermissionRationale(requireActivity(), REQUIRED_PERMISSIONS)) {
            locationPermissionLauncher.launch(REQUIRED_PERMISSIONS);
        }
    }
}
