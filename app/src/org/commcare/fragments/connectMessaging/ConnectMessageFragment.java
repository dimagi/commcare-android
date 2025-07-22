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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.RecyclerView;

import org.commcare.adapters.ConnectMessageAdapter;
import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord;
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord;
import org.commcare.connect.MessageManager;
import org.commcare.connect.database.ConnectMessagingDatabaseHelper;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.FragmentConnectMessageBinding;

import org.commcare.utils.FirebaseMessagingUtil;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

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

        ConnectMessagingChannelRecord channel = ConnectMessagingDatabaseHelper.getMessagingChannel(requireContext(), channelId);
        getActivity().setTitle(channel.getChannelName());

        handleSendButtonListener();
        setChatAdapter();
        apiCallRunnable = new Runnable() {
            @Override
            public void run() {
                fetchMessagesFromNetwork(); // Perform the API call
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
                new IntentFilter(FirebaseMessagingUtil.MESSAGING_UPDATE_BROADCAST));

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

    private void fetchMessagesFromNetwork() {
        MessageManager.retrieveMessages(requireActivity(), success -> {
            if (success) {
                refreshUi();
            } else {
                Toast.makeText(requireContext(), getString(R.string.connect_messaging_retrieve_messages_fail), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleSendButtonListener() {
        binding.etMessage.addTextChangedListener(createTextWatcher());

        binding.etMessage.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                scrollToLatestMessageWithDelay();
            }
        });

        binding.imgSendMessage.setOnClickListener(v -> sendMessage());
    }

    private TextWatcher createTextWatcher() {

        return new TextWatcher() {
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
        };

    }

    private void scrollToLatestMessageWithDelay() {
        binding.rvChat.postDelayed(this::scrollToLatestMessage, 250);
    }

    private void sendMessage() {
        ConnectMessagingMessageRecord message = new ConnectMessagingMessageRecord();
        message.setMessageId(UUID.randomUUID().toString());
        message.setMessage(binding.etMessage.getText().toString());
        message.setChannelId(channelId);
        message.setTimeStamp(new Date());
        message.setIsOutgoing(true);
        message.setConfirmed(false);
        message.setUserViewed(true);

        binding.etMessage.setText("");

        ConnectMessagingDatabaseHelper.storeMessagingMessage(requireContext(), message);
        ConnectMessageChatData chat = fromMessage(message);
        adapter.addMessage(chat);
        scrollToLatestMessage();

        MessageManager.sendMessage(requireContext(), message, success -> {
            if (!success) {
                Toast.makeText(requireContext(), getString(R.string.connect_messaging_send_message_fail_msg), Toast.LENGTH_SHORT).show();
            } else {
                chat.setMessageRead(success);
                adapter.updateMessageReadStatus(chat);
                scrollToLatestMessage();
            }
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
            List<ConnectMessagingMessageRecord> messages = ConnectMessagingDatabaseHelper.getMessagingMessagesForChannel(context, channelId);

            List<ConnectMessageChatData> chats = new ArrayList<>();
            for (ConnectMessagingMessageRecord message : messages) {

                chats.add(fromMessage(message));

                if (!message.getUserViewed()) {
                    message.setUserViewed(true);
                    ConnectMessagingDatabaseHelper.storeMessagingMessage(context, message);
                }
            }

            adapter.updateData(chats);
            scrollToLatestMessage();

        }
    }

    private ConnectMessageChatData fromMessage(ConnectMessagingMessageRecord message) {
        int viewType = message.getIsOutgoing() ? ConnectMessageAdapter.RIGHTVIEW : ConnectMessageAdapter.LEFTVIEW;
        return new ConnectMessageChatData(message.getMessageId(), viewType,
                message.getMessage(),
                message.getIsOutgoing() ? getString(R.string.connect_message_you) : getString(R.string.connect_message_them),
                message.getTimeStamp(),
                message.getConfirmed());
    }

    private void scrollToLatestMessage() {
        RecyclerView.Adapter<?> adapter = binding.rvChat.getAdapter();
        if (adapter != null && adapter.getItemCount() > 0) {
            binding.rvChat.scrollToPosition(adapter.getItemCount() - 1);
        }
    }
}

