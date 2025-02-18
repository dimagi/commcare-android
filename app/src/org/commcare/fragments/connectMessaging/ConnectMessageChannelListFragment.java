package org.commcare.fragments.connectMessaging;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.commcare.adapters.ChannelAdapter;
import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord;
import org.commcare.connect.MessageManager;
import org.commcare.connect.database.ConnectMessageUtils;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.FragmentChannelListBinding;
import org.commcare.services.CommCareFirebaseMessagingService;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

public class ConnectMessageChannelListFragment extends Fragment {

    public static boolean isActive;
    private FragmentChannelListBinding binding;
    private ChannelAdapter channelAdapter;

    public static ConnectMessageChannelListFragment newInstance() {
        ConnectMessageChannelListFragment fragment = new ConnectMessageChannelListFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentChannelListBinding.inflate(inflater, container, false);
        View view = binding.getRoot();

        requireActivity().setTitle(R.string.connect_messaging_channel_list_title);

        binding.rvChannel.setLayoutManager(new LinearLayoutManager(getContext()));

        List<ConnectMessagingChannelRecord> channels = ConnectMessageUtils.getMessagingChannels(getContext());

        channelAdapter = new ChannelAdapter(channels, this::selectChannel);

        binding.rvChannel.setAdapter(channelAdapter);

        MessageManager.retrieveMessages(requireActivity(), success -> {
            refreshUi();
        });

        MessageManager.sendUnsentMessages(requireActivity());

        String channelId = getArguments() != null ? getArguments().getString("channel_id") : null;
        if (channelId != null) {
            ConnectMessagingChannelRecord channel = ConnectMessageUtils.getMessagingChannel(requireContext(), channelId);
            selectChannel(channel);
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        isActive = true;

        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(updateReceiver,
                new IntentFilter(CommCareFirebaseMessagingService.MESSAGING_UPDATE_BROADCAST));

        MessageManager.retrieveMessages(requireActivity(), success -> {
            refreshUi();
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        isActive = false;
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(updateReceiver);
    }

    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshUi();
        }
    };

    private void selectChannel(ConnectMessagingChannelRecord channel) {
        NavDirections directions;
        if (channel.getConsented()) {
            directions = ConnectMessageChannelListFragmentDirections
                    .actionChannelListFragmentToConnectMessageFragment(channel.getChannelId());
        } else {
            //Get consent for channel
            directions = ConnectMessageChannelListFragmentDirections
                    .actionChannelListFragmentToChannelConsentBottomSheet(channel.getChannelId(),
                            channel.getChannelName());
        }

        Navigation.findNavController(binding.rvChannel).navigate(directions);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    public void refreshUi() {
        Context context = getContext();
        if (context != null) {
            List<ConnectMessagingChannelRecord> channels = ConnectMessageUtils.getMessagingChannels(context);
            channelAdapter.setChannels(channels);
        }
    }
}
