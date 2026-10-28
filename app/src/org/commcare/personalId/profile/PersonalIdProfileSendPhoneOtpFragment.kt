package org.commcare.personalId.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.FragmentPersonalidSendOtpBinding
import org.commcare.fragments.personalId.BasePersonalIdFragment

class PersonalIdProfileSendPhoneOtpFragment : BasePersonalIdFragment() {
    private val args by lazy { PersonalIdProfileSendPhoneOtpFragmentArgs.fromBundle(requireArguments()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val binding = FragmentPersonalidSendOtpBinding.inflate(inflater, container, false)
        setTitle(R.string.connect_verify_phone_title)
        binding.sendOtpTitle.setText(R.string.personalid_send_phone_otp_title)
        binding.sendOtpIcon.setImageResource(R.drawable.ic_outline_phone_24)
        binding.sendOtpAddressLabel.setText(R.string.personalid_profile_field_phone)
        binding.sendOtpAddress.text = maskPhone(ConnectUserDatabaseUtil.getUser().primaryPhone)
        binding.sendOtpButton.setOnClickListener { navigateToPhoneVerification() }
        return binding.root
    }

    private fun navigateToPhoneVerification() {
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.personalid_profile_send_phone_otp_fragment) return
        navController.navigate(
            PersonalIdProfileSendPhoneOtpFragmentDirections
                .actionProfileSendPhoneOtpToProfilePhoneVerification(args.pendingEmail),
        )
    }

    override fun navigateToMessageDisplay(
        title: String,
        message: String?,
        isCancellable: Boolean,
        phase: Int,
        buttonText: Int,
    ): Unit = throw IllegalStateException("navigateToMessageDisplay should not have a call path in this fragment")

    companion object {
        private const val VISIBLE_CHARS = 3
        private const val MASK_CHAR = "•"

        fun maskPhone(phone: String?): String {
            val value = phone.orEmpty()
            val visible = if (value.length > VISIBLE_CHARS) VISIBLE_CHARS else 0
            return MASK_CHAR.repeat(value.length - visible) + value.takeLast(visible)
        }
    }
}
