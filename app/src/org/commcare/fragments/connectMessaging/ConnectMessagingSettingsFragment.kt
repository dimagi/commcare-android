package org.commcare.fragments.connectMessaging

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.commcare.connect.messaging.ConnectMessagingAttachmentDownloadScheduler
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.FragmentConnectMessagingSettingsBinding
import org.commcare.preferences.ConnectMessagingPreferences
import org.commcare.preferences.ConnectMessagingPreferences.AttachmentAutoDownload

class ConnectMessagingSettingsFragment : Fragment() {
    private var binding: FragmentConnectMessagingSettingsBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val settingsBinding = FragmentConnectMessagingSettingsBinding.inflate(inflater, container, false)
        binding = settingsBinding

        settingsBinding.rgAutoDownload.check(
            when (ConnectMessagingPreferences.getAttachmentAutoDownload(requireContext())) {
                AttachmentAutoDownload.ANY_NETWORK -> R.id.rbAnyNetwork
                AttachmentAutoDownload.LARGE_ON_WIFI_ONLY -> R.id.rbLargeOnWifiOnly
                AttachmentAutoDownload.MANUAL_ONLY -> R.id.rbManualOnly
            },
        )
        settingsBinding.rgAutoDownload.setOnCheckedChangeListener { _, checkedId ->
            onAutoDownloadSelected(
                when (checkedId) {
                    R.id.rbLargeOnWifiOnly -> AttachmentAutoDownload.LARGE_ON_WIFI_ONLY
                    R.id.rbManualOnly -> AttachmentAutoDownload.MANUAL_ONLY
                    else -> AttachmentAutoDownload.ANY_NETWORK
                },
            )
        }
        return settingsBinding.root
    }

    override fun onResume() {
        super.onResume()
        requireActivity().setTitle(R.string.connect_messaging_settings_title)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun onAutoDownloadSelected(selected: AttachmentAutoDownload) {
        val context = requireContext()
        if (selected == ConnectMessagingPreferences.getAttachmentAutoDownload(context)) {
            return
        }
        ConnectMessagingPreferences.setAttachmentAutoDownload(context, selected)
        ConnectMessagingAttachmentDownloadScheduler.applyAutoDownloadSetting(context)
    }
}
