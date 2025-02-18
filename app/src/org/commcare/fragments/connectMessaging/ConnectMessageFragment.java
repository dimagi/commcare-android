package org.commcare.fragments.connectMessaging;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.commcare.adapters.ConnectMessageAdapter;
import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord;
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord;
import org.commcare.connect.MessageManager;
import org.commcare.connect.database.ConnectDatabaseHelper;
import org.commcare.connect.database.ConnectMessageUtils;
import org.commcare.dalvik.databinding.FragmentConnectMessageBinding;
import org.commcare.services.CommCareFirebaseMessagingService;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.RecyclerView;

public class ConnectMessageFragment extends Fragment {
    public static String activeChannel;
    private String channelId;
    private FragmentConnectMessageBinding binding;
    private ConnectMessageAdapter adapter;
    private Runnable apiCallRunnable; // The task to run periodically
    private static final int INTERVAL = 30000;
    private final Handler handler = new Handler(); // To post periodic tasks


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentConnectMessageBinding.inflate(inflater, container, false);

        ConnectMessageFragmentArgs args = ConnectMessageFragmentArgs.fromBundle(getArguments());
        channelId = args.getChannelId();

        ConnectMessagingChannelRecord channel = ConnectMessageUtils.getMessagingChannel(requireContext(), channelId);
        getActivity().setTitle(channel.getChannelName());

        handleSendButtonListener();
        setChatAdapter();
        apiCallRunnable = new Runnable() {
            @Override
            public void run() {
                makeApiCall(); // Perform the API call
                handler.postDelayed(this, INTERVAL); // Schedule the next call
            }
        };

        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        activeChannel = channelId;

        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(updateReceiver,
                new IntentFilter(CommCareFirebaseMessagingService.MESSAGING_UPDATE_BROADCAST));

        // Start periodic API calls
        handler.post(apiCallRunnable);
    }

    @Override
    public void onPause() {
        super.onPause();
        activeChannel = null;

        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(updateReceiver);

        // Stop the periodic API calls when the screen is not active
        handler.removeCallbacks(apiCallRunnable);
    }

    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshUi();
        }
    };

    private void makeApiCall() {
        MessageManager.retrieveMessages(requireActivity(), success -> {
            refreshUi();
        });
    }

    private void handleSendButtonListener() {
        binding.etMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) {
                    binding.imgSendMessage.setVisibility(View.VISIBLE);
                } else {
                    binding.imgSendMessage.setVisibility(View.GONE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        binding.etMessage.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {

                binding.rvChat.postDelayed(() -> {
                    RecyclerView.Adapter<?> adapter = binding.rvChat.getAdapter();
                    if (adapter != null) {
                        int numItems = adapter.getItemCount();
                        if (numItems > 0) {
                            binding.rvChat.scrollToPosition(numItems - 1);
                        }
                    }
                }, 250);
            }
        });

        binding.imgSendMessage.setOnClickListener(v -> {
            ConnectMessagingMessageRecord message = new ConnectMessagingMessageRecord();
            message.setMessageId(UUID.randomUUID().toString());
            message.setMessage(binding.etMessage.getText().toString());
            message.setChannelId(channelId);
            message.setTimeStamp(new Date());
            message.setIsOutgoing(true);
            message.setConfirmed(false);
            message.setUserViewed(true);

            binding.etMessage.setText("");

            ConnectMessageUtils.storeMessagingMessage(requireContext(), message);
            refreshUi();

            MessageManager.sendMessage(requireContext(), message, success -> {
                refreshUi();
            });
        });
    }

    private void setChatAdapter() {
        List<ConnectMessageChatData> messages = new ArrayList<>();

        adapter = new ConnectMessageAdapter(messages);
        binding.rvChat.setAdapter(adapter);

        refreshUi();
    }

    public void refreshUi() {
        Context context = getContext();
        if (context != null) {
            List<ConnectMessagingMessageRecord> messages = ConnectMessageUtils.getMessagingMessagesForChannel(context, channelId);

            List<ConnectMessageChatData> chats = new ArrayList<>();
            for (ConnectMessagingMessageRecord message : messages) {
                int viewType = message.getIsOutgoing() ? ConnectMessageAdapter.RIGHTVIEW : ConnectMessageAdapter.LEFTVIEW;
                chats.add(new ConnectMessageChatData(viewType,
                        message.getMessage(),
                        message.getIsOutgoing() ? "You" : "Them",
                        message.getTimeStamp(),
                        message.getConfirmed()));

                if (!message.getUserViewed()) {
                    message.setUserViewed(true);
                    ConnectMessageUtils.storeMessagingMessage(context, message);
                }
            }

            adapter.updateData(chats);
            binding.rvChat.scrollToPosition(messages.size() - 1);
        }
    }
}

