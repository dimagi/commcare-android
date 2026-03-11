package org.commcare.fragments.personalId

import android.Manifest
import android.app.Activity
import android.content.DialogInterface
import android.location.Location
import android.os.Bundle
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavDirections
import androidx.navigation.Navigation
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.play.core.integrity.StandardIntegrityManager
import com.google.android.play.core.integrity.model.IntegrityDialogResponseCode.DIALOG_SUCCESSFUL
import com.google.android.play.core.integrity.model.IntegrityDialogTypeCode
import org.commcare.activities.connect.viewmodel.PersonalIdSessionDataViewModel
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.android.integrity.IntegrityTokenApiRequestHelper
import org.commcare.android.integrity.IntegrityTokenViewModel
import org.commcare.android.logging.ReportingUtils
import org.commcare.connect.ConnectConstants
import org.commcare.connect.network.PersonalIdOrConnectApiErrorHandler
import org.commcare.connect.network.base.BaseApiHandler
import org.commcare.connect.network.connectId.PersonalIdApiHandler
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ScreenPersonalidPhonenoBinding
import org.commcare.google.services.analytics.AnalyticsParamValue
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.location.CommCareLocationController
import org.commcare.location.CommCareLocationControllerFactory
import org.commcare.location.CommCareLocationListener
import org.commcare.location.LocationRequestFailureHandler
import org.commcare.util.LogTypes
import org.commcare.utils.GeoUtils
import org.commcare.utils.KeyboardHelper
import org.commcare.utils.Permissions
import org.commcare.utils.PhoneNumberHelper
import org.javarosa.core.services.Logger

class PersonalIdPhoneFragment :
    BasePersonalIdFragment(),
    CommCareLocationListener {
    private lateinit var binding: ScreenPersonalidPhonenoBinding
    private var shouldShowPhoneHintDialog = true
    private lateinit var phoneNumberHelper: PhoneNumberHelper
    private lateinit var personalIdSessionDataViewModel: PersonalIdSessionDataViewModel
    private lateinit var integrityTokenApiRequestHelper: IntegrityTokenApiRequestHelper
    private var location: Location? = null
    private lateinit var locationController: CommCareLocationController
    private lateinit var locationPermissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var resolutionLauncher: ActivityResultLauncher<IntentSenderRequest>
    private var playServicesError: String? = null
    private lateinit var playServicesResolutionLauncher: ActivityResultLauncher<IntentSenderRequest>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = ScreenPersonalidPhonenoBinding.inflate(inflater, container, false)
        phoneNumberHelper = PhoneNumberHelper.getInstance(requireActivity())
        requireActivity().setTitle(R.string.connect_registration_title)
        personalIdSessionDataViewModel =
            ViewModelProvider(requireActivity())[PersonalIdSessionDataViewModel::class.java]
        locationController =
            CommCareLocationControllerFactory.getLocationController(requireActivity(), this)
        integrityTokenApiRequestHelper = IntegrityTokenApiRequestHelper(viewLifecycleOwner)
        initializeUi()
        registerLauncher()
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        checkGooglePlayServices()
    }

    override fun onResume() {
        super.onResume()
        if (!isOnPermissionErrorScreen()) {
            locationController.start()
        }
    }

    private fun setLocationToolTip(location: Location?) {
        binding.groupTooltip.isVisible = true

        val locationFound = location != null

        binding.ivLocation.setImageResource(
            if (locationFound) R.drawable.ic_place else R.drawable.ic_connect_delivery_rejected,
        )
        binding.tvLocation.setText(
            if (locationFound) {
                R.string.personalid_using_your_location
            } else {
                R.string.personalid_no_location_found
            },
        )

        binding.tooltipText.movementMethod = LinkMovementMethod.getInstance()
        binding.tooltipText.setText(
            if (locationFound) {
                R.string.personalid_tooltip_location_success_message
            } else {
                R.string.personalid_tooltip_location_failure_message
            },
        )
    }

    override fun onPause() {
        super.onPause()
        locationController.stop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        locationController.destroy()
        destroyKeyboardScrollListener(binding.scrollView)
    }

    private fun checkGooglePlayServices() {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val status = googleApiAvailability.isGooglePlayServicesAvailable(requireActivity())
        if (status != ConnectionResult.SUCCESS) {
            playServicesError = "play_services_$status"
            Logger.log(LogTypes.TYPE_MAINTENANCE, "Google Play Services issue:$playServicesError")
            if (googleApiAvailability.isUserResolvableError(status)) {
                GoogleApiAvailability.getInstance().showErrorDialogFragment(
                    requireActivity(),
                    status,
                    playServicesResolutionLauncher,
                ) { _ ->
                    onConfigurationFailure(
                        playServicesError!!,
                        getString(R.string.play_service_update_error),
                    )
                }
            } else {
                onConfigurationFailure(
                    playServicesError!!,
                    getString(R.string.play_service_update_error),
                )
            }
        }
    }

    private fun initializeUi() {
        binding.countryCode.setText(phoneNumberHelper.getDefaultCountryCode(context))
        binding.checkText.movementMethod = LinkMovementMethod.getInstance()
        setupKeyboardScrollListener(binding.scrollView)
        setupListeners()
        updateContinueButtonState()
    }

    private fun setupListeners() {
        binding.ivLocationInfo.setOnClickListener {
            binding.groupTooltipInfo.isVisible = !binding.groupTooltipInfo.isVisible
        }

        binding.firstLayout.setOnClickListener {
            binding.groupTooltipInfo.isVisible = false
        }

        binding.connectConsentCheck.setOnClickListener { updateContinueButtonState() }
        binding.personalidPhoneContinueButton.setOnClickListener { onContinueClicked() }

        val phoneHintLauncher = setupPhoneHintLauncher()

        val focusChangeListener =
            View.OnFocusChangeListener { _, hasFocus ->
                if (hasFocus && shouldShowPhoneHintDialog) {
                    PhoneNumberHelper.requestPhoneNumberHint(phoneHintLauncher, requireActivity())
                    shouldShowPhoneHintDialog = false
                }
            }

        binding.connectPrimaryPhoneInput.addTextChangedListener(createPhoneNumberWatcher())
        binding.countryCode.addTextChangedListener(
            phoneNumberHelper.getCountryCodeWatcher(binding.countryCode),
        )

        binding.connectPrimaryPhoneInput.onFocusChangeListener = focusChangeListener
        binding.countryCode.onFocusChangeListener = focusChangeListener
    }

    private fun setupPhoneHintLauncher(): ActivityResultLauncher<IntentSenderRequest> =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                try {
                    val phoneNumber =
                        Identity
                            .getSignInClient(requireActivity())
                            .getPhoneNumberFromIntent(result.data!!)
                    displayPhoneNumber(phoneNumber)
                } catch (e: ApiException) {
                    Toast.makeText(context, R.string.error_occured, Toast.LENGTH_SHORT).show()
                }
            } else {
                requireActivity().currentFocus?.let {
                    KeyboardHelper.showKeyboardOnInput(requireActivity(), it)
                }
            }
        }

    private fun createPhoneNumberWatcher(): TextWatcher =
        object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int,
            ) {}

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int,
            ) {
                updateContinueButtonState()
            }

            override fun afterTextChanged(s: android.text.Editable?) {}
        }

    private fun updateContinueButtonState() {
        val phone =
            PhoneNumberHelper.buildPhoneNumber(
                binding.countryCode.text.toString(),
                binding.connectPrimaryPhoneInput.text.toString(),
            )

        val isValidPhone = phoneNumberHelper.isValidPhoneNumber(phone)
        val isConsentChecked = binding.connectConsentCheck.isChecked

        enableContinueButton(isValidPhone && isConsentChecked && location != null)
    }

    private fun displayPhoneNumber(fullPhoneNumber: String?) {
        if (fullPhoneNumber.isNullOrEmpty()) return

        val countryCodeFromFullPhoneNumber = phoneNumberHelper.getCountryCode(fullPhoneNumber)
        val nationPhoneNumberFromFullPhoneNumber =
            phoneNumberHelper.getNationalNumber(fullPhoneNumber)

        if (countryCodeFromFullPhoneNumber != -1 && nationPhoneNumberFromFullPhoneNumber != -1L) {
            binding.connectPrimaryPhoneInput.setText(
                nationPhoneNumberFromFullPhoneNumber.toString(),
            )
            binding.countryCode.setText(
                phoneNumberHelper.formatCountryCode(countryCodeFromFullPhoneNumber),
            )
        }
    }

    private fun onContinueClicked() {
        FirebaseAnalyticsUtil.reportPersonalIDContinueClicked(this.javaClass.simpleName, null)
        enableContinueButton(false)
        startConfigurationRequest()
    }

    private fun enableContinueButton(isEnabled: Boolean) {
        binding.personalidPhoneContinueButton.isEnabled = isEnabled
    }

    private fun startConfigurationRequest() {
        clearError()
        val phone =
            PhoneNumberHelper.buildPhoneNumber(
                binding.countryCode.text.toString(),
                binding.connectPrimaryPhoneInput.text.toString(),
            )!!

        val body = hashMapOf<String, String>()
        body["phone_number"] = phone
        body["application_id"] = requireContext().packageName
        body["gps_location"] = GeoUtils.locationToString(location)
        body["cc_device_id"] = ReportingUtils.getDeviceId()

        integrityTokenApiRequestHelper.withIntegrityToken(
            body,
            object : IntegrityTokenViewModel.IntegrityTokenCallback {
                override fun onTokenReceived(
                    requestHash: String,
                    integrityTokenResponse: StandardIntegrityManager.StandardIntegrityToken,
                ) {
                    makeStartConfigurationCall(phone, requestHash, body, integrityTokenResponse)
                }

                override fun onTokenFailure(exception: Exception) {
                    val errorCode =
                        IntegrityTokenApiRequestHelper.getCodeForException(exception)
                    FirebaseAnalyticsUtil
                        .reportPersonalIdConfigurationIntegritySubmission(errorCode)

                    makeStartConfigurationCall(phone, null, body, null)
                }
            },
        )
    }

    override fun onLocationResult(result: Location) {
        location = result
        setLocationToolTip(location)
        updateContinueButtonState()
    }

    override fun onLocationRequestFailure(failure: CommCareLocationListener.Failure) {
        LocationRequestFailureHandler.handleFailure(
            failure,
            object : LocationRequestFailureHandler.LocationResolutionCallback {
                override fun onResolvableException(exception: com.google.android.gms.common.api.ResolvableApiException) {
                    try {
                        val request =
                            IntentSenderRequest
                                .Builder(
                                    exception.resolution,
                                ).build()
                        resolutionLauncher.launch(request)
                    } catch (e: Exception) {
                        navigateToPermissionErrorMessageDisplay(
                            R.string.personalid_location_permission_error,
                            R.string.personalid_grant_location_service,
                        )
                    }
                }

                override fun onNonResolvableFailure() {
                    handleNoLocationServiceProviders()
                }
            },
        )
    }

    private fun handleNoLocationServiceProviders() {
        val onCancelListener =
            DialogInterface.OnCancelListener {
                location = null
                navigateToPermissionErrorMessageDisplay(
                    R.string.personalid_location_permission_error,
                    R.string.personalid_grant_location_service,
                )
            }

        val onChangeListener =
            DialogInterface.OnClickListener { dialog, i ->
                when (i) {
                    DialogInterface.BUTTON_POSITIVE ->
                        GeoUtils.goToProperLocationSettingsScreen(requireActivity())

                    DialogInterface.BUTTON_NEGATIVE -> {
                        location = null
                        navigateToPermissionErrorMessageDisplay(
                            R.string.personalid_location_permission_error,
                            R.string.personalid_grant_location_service,
                        )
                    }
                }
                dialog.dismiss()
            }

        GeoUtils.showNoGpsDialog(
            requireActivity() as AppCompatActivity,
            onChangeListener,
            onCancelListener,
        )
    }

    override fun onLocationRequestStart() {}

    private fun isOnPermissionErrorScreen(): Boolean =
        Navigation
            .findNavController(requireView())
            .currentDestination
            ?.id == R.id.personalid_message_display

    private fun registerLauncher() {
        locationPermissionLauncher =
            registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) { _ ->
                val allPermissionsGranted =
                    !Permissions.missingAppPermission(requireActivity(), REQUIRED_PERMISSIONS)

                if (allPermissionsGranted) {
                    locationController.start()
                } else {
                    if (!isOnPermissionErrorScreen()) {
                        navigateToPermissionErrorMessageDisplay(
                            R.string.personalid_location_permission_error,
                            R.string.personalid_grant_location_permission,
                        )
                    }
                }
            }

        resolutionLauncher =
            registerForActivityResult(
                ActivityResultContracts.StartIntentSenderForResult(),
            ) { result ->
                setLocationToolTip(location)
                if (result.resultCode != Activity.RESULT_OK) {
                    navigateToPermissionErrorMessageDisplay(
                        R.string.personalid_location_permission_error,
                        R.string.personalid_grant_location_service,
                    )
                }
            }

        playServicesResolutionLauncher =
            registerForActivityResult(
                ActivityResultContracts.StartIntentSenderForResult(),
            ) { result ->
                if (result.resultCode != Activity.RESULT_OK) {
                    onConfigurationFailure(
                        playServicesError!!,
                        getString(R.string.play_service_update_error),
                    )
                }
            }
    }

    private fun makeStartConfigurationCall(
        phone: String,
        requestHash: String?,
        body: HashMap<String, String>,
        integrityTokenResponse: StandardIntegrityManager.StandardIntegrityToken?,
    ) {
        val token = integrityTokenResponse?.token() ?: ""
        val effectiveRequestHash = requestHash ?: ""

        object : PersonalIdApiHandler<PersonalIdSessionData>() {
            override fun onSuccess(data: PersonalIdSessionData) {
                personalIdSessionDataViewModel.personalIdSessionData = data
                personalIdSessionDataViewModel.personalIdSessionData.phoneNumber = phone

                FirebaseAnalyticsUtil.flagPersonalIDDemoUser(data.demoUser)

                if (personalIdSessionDataViewModel.personalIdSessionData.token != null) {
                    onConfigurationSuccess()
                } else {
                    val failureCode =
                        personalIdSessionDataViewModel.personalIdSessionData.sessionFailureCode
                    Logger.log(
                        LogTypes.TYPE_MAINTENANCE,
                        "Start Config API failed with $failureCode",
                    )
                    onConfigurationFailure(
                        failureCode!!,
                        getString(R.string.personalid_configuration_process_failed_subtitle),
                    )
                }
            }

            override fun onFailure(
                errorCode: BaseApiHandler.PersonalIdOrConnectApiErrorCodes,
                t: Throwable?,
            ) {
                if (handleCommonSignupFailures(errorCode)) {
                    return
                }

                when (errorCode) {
                    BaseApiHandler.PersonalIdOrConnectApiErrorCodes.FORBIDDEN_ERROR ->
                        onConfigurationFailure(
                            AnalyticsParamValue.START_CONFIGURATION_INTEGRITY_CHECK_FAILURE,
                            getString(R.string.personalid_configuration_process_failed_subtitle),
                        )

                    BaseApiHandler.PersonalIdOrConnectApiErrorCodes.INTEGRITY_ERROR -> {
                        handleIntegritySubError(
                            integrityTokenResponse,
                            personalIdSessionDataViewModel.personalIdSessionData
                                .sessionFailureSubcode!!,
                        )
                        navigateFailure(errorCode, t)
                    }

                    else -> navigateFailure(errorCode, t)
                }
            }
        }.makeStartConfigurationCall(requireActivity(), body, token, effectiveRequestHash)
    }

    private fun handleIntegritySubError(
        tokenResponse: StandardIntegrityManager.StandardIntegrityToken?,
        subError: String,
    ) {
        when (BaseApiHandler.PersonalIdApiSubErrorCodes.valueOf(subError)) {
            BaseApiHandler.PersonalIdApiSubErrorCodes.UNLICENSED_APP_ERROR ->
                showIntegrityCheckDialog(
                    tokenResponse,
                    IntegrityDialogTypeCode.GET_LICENSED,
                    subError,
                )

            else ->
                onConfigurationFailure(
                    subError,
                    getString(R.string.personalid_configuration_process_failed_subtitle),
                )
        }
    }

    private fun showIntegrityCheckDialog(
        tokenResponse: StandardIntegrityManager.StandardIntegrityToken?,
        codeType: Int,
        subError: String,
    ) {
        val integrityDialogResponseCode = tokenResponse!!.showDialog(requireActivity(), codeType)
        integrityDialogResponseCode
            .addOnSuccessListener { result ->
                if (result == DIALOG_SUCCESSFUL) {
                    enableContinueButton(true)
                } else {
                    handleIntegrityFailure(
                        subError,
                        "User has cancelled the integrity dialog $result",
                    )
                }
            }.addOnFailureListener { e ->
                handleIntegrityFailure(
                    subError,
                    "Integrity dialog failed to launch ${e.message}",
                )
            }
    }

    private fun handleIntegrityFailure(
        subError: String,
        logMessage: String,
    ) {
        Logger.log(LogTypes.TYPE_MAINTENANCE, logMessage)
        enableContinueButton(false)
        onConfigurationFailure(
            subError,
            getString(R.string.personalid_configuration_process_failed_subtitle),
        )
    }

    private fun onConfigurationSuccess() {
        Navigation
            .findNavController(binding.personalidPhoneContinueButton)
            .navigate(navigateToBiometricSetup())
    }

    private fun navigateFailure(
        failureCode: BaseApiHandler.PersonalIdOrConnectApiErrorCodes,
        t: Throwable?,
    ) {
        showError(
            PersonalIdOrConnectApiErrorHandler.handle(requireActivity(), failureCode, t),
        )
        if (failureCode.shouldAllowRetry()) {
            enableContinueButton(true)
        }
    }

    private fun clearError() {
        binding.personalidPhoneError.isVisible = false
        binding.personalidPhoneError.text = ""
    }

    private fun showError(error: String) {
        binding.personalidPhoneError.isVisible = true
        binding.personalidPhoneError.text = error
    }

    private fun navigateToBiometricSetup(): NavDirections =
        PersonalIdPhoneFragmentDirections
            .actionPersonalidPhoneFragmentToPersonalidBiometricConfig()

    override fun navigateToMessageDisplay(
        title: String,
        message: String?,
        isCancellable: Boolean,
        phase: Int,
        buttonText: Int,
    ) {
        val navDirections =
            PersonalIdPhoneFragmentDirections
                .actionPersonalidPhoneFragmentToPersonalidMessageDisplay(
                    title,
                    message ?: "",
                    phase,
                    getString(buttonText),
                    null,
                ).setIsCancellable(isCancellable)
        Navigation.findNavController(binding.personalidPhoneContinueButton).navigate(navDirections)
    }

    private fun navigateToPermissionErrorMessageDisplay(
        errorMessage: Int,
        buttonText: Int,
    ) {
        if (!isOnPermissionErrorScreen()) {
            navigateToMessageDisplay(
                getString(R.string.personalid_grant_location_service),
                requireActivity().getString(errorMessage),
                true,
                ConnectConstants.PERSONALID_LOCATION_PERMISSION_FAILURE,
                buttonText,
            )
        }
    }

    override fun missingPermissions() {
        if (!Permissions.shouldShowPermissionRationale(requireActivity(), REQUIRED_PERMISSIONS)) {
            locationPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    override fun onLocationServiceChange(locationServiceEnabled: Boolean) {
        if (!locationServiceEnabled) {
            location = null
            setLocationToolTip(null)
            updateContinueButtonState()
        }
    }

    companion object {
        private val REQUIRED_PERMISSIONS =
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
    }
}
