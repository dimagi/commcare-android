package org.commcare.fragments.connectMessaging;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.commcare.adapters.ChannelAdapter;
import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord;
import org.commcare.connect.ConnectDatabaseHelper;
import org.commcare.connect.MessageManager;
import org.commcare.dalvik.databinding.FragmentChannelListBinding;

import java.util.List;

public class ConnectMessageChannelListFragment extends Fragment {

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

        binding.rvChannel.setLayoutManager(new LinearLayoutManager(getContext()));

        List<ConnectMessagingChannelRecord> channels = ConnectDatabaseHelper.getMessagingChannels(getContext());

        channelAdapter = new ChannelAdapter(channels, this::selectChannel);

        binding.rvChannel.setAdapter(channelAdapter);

        MessageManager.retrieveMessages(requireActivity(), success -> {
            refreshUi();
        });

        MessageManager.sendUnsentMessages(requireActivity());

        String channelId = getArguments() != null ? getArguments().getString("channel_id") : null;
        if(channelId != null) {
            ConnectMessagingChannelRecord channel = ConnectDatabaseHelper.getMessagingChannel(requireContext(), channelId);
            selectChannel(channel);
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        MessageManager.retrieveMessages(requireActivity(), success -> {
            refreshUi();
        });
    }

    private void selectChannel(ConnectMessagingChannelRecord channel) {
        NavDirections directions;
        if(channel.getConsented()) {
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
        List<ConnectMessagingChannelRecord> channels = ConnectDatabaseHelper.getMessagingChannels(requireActivity());
        channelAdapter.setChannels(channels);
    }
}
