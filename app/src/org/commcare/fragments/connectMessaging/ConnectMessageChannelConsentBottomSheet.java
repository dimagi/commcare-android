package org.commcare.fragments.connectMessaging;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.MessageManager;
import org.commcare.connect.database.ConnectMessagingDatabaseHelper;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.FragmentChannelConsentBottomSheetBinding;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;
import java.util.Objects;

public class ConnectMessageChannelConsentBottomSheet extends BottomSheetDialogFragment {
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        FragmentChannelConsentBottomSheetBinding binding = FragmentChannelConsentBottomSheetBinding
                .inflate(inflater, container, false);

        ConnectMessageChannelConsentBottomSheetArgs args = ConnectMessageChannelConsentBottomSheetArgs
                .fromBundle(getArguments());

        ConnectMessagingChannelRecord channel = ConnectMessagingDatabaseHelper.getMessagingChannel(requireContext(),
                args.getChannelId());

        Objects.requireNonNull(channel, "Channel not found for channel Id: "+args.getChannelId());

        binding.channelName.setText(channel.getChannelName());

        binding.acceptButton.setOnClickListener(v -> {
            channel.setAnsweredConsent(true);
            channel.setConsented(true);
            MessageManager.updateChannelConsent(requireContext(), channel, success -> {
                if(success) {
                    NavDirections directions = ConnectMessageChannelConsentBottomSheetDirections
                            .actionChannelConsentToConnectMessageFragment(channel.getChannelId());
                    NavHostFragment.findNavController(this).navigate(directions);
                } else {
                    Context context = getContext();
                    if(context != null) {
                        navigateToMessageDisplayDialog(
                                getString(R.string.error),
                                getString(R.string.connect_messaging_channel_consent_failure_msg),
                                false,
                                getString(R.string.ok)
                        );
                    }

                    NavHostFragment.findNavController(this).popBackStack();
                }
            });
        });

        binding.declineButton.setOnClickListener(v -> {
            channel.setAnsweredConsent(true);
            channel.setConsented(false);
            MessageManager.updateChannelConsent(requireContext(), channel, success -> {

            });
            NavHostFragment.findNavController(this).popBackStack();
        });

        return binding.getRoot();
    }

    private void navigateToMessageDisplayDialog(@Nullable String title, @Nullable String message, boolean isCancellable, String buttonText) {
        NavDirections navDirections = ConnectMessageChannelConsentBottomSheetDirections.actionChannelConsentToPersonalidMessageDisplayDialog(
                title, message, ConnectConstants.PERSONAL_ID_CANCEL_MESSAGE_BOTTOM_SHEET, buttonText,null).setIsCancellable(isCancellable);
        NavHostFragment.findNavController(this).navigate(navDirections);
    }
}
