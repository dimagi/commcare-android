package org.commcare.fragments.connectMessaging;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import org.commcare.adapters.ConnectMessageAdapter;
import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord;
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectDatabaseHelper;
import org.commcare.connect.ConnectManager;
import org.commcare.connect.MessageManager;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.databinding.FragmentConnectMessageBinding;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class ConnectMessageFragment extends Fragment {

    private String channelId;
    private FragmentConnectMessageBinding binding;
    private ConnectMessageAdapter adapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentConnectMessageBinding.inflate(inflater, container, false);

        ConnectMessageFragmentArgs args = ConnectMessageFragmentArgs.fromBundle(getArguments());
        channelId = args.getChannelId();

        ConnectMessagingChannelRecord channel = ConnectDatabaseHelper.getMessagingChannel(requireContext(), channelId);
        getActivity().setTitle(channel.getChannelName());

        handleSendButtonListener();
        setChatAdapter();

        return binding.getRoot();
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
//                    binding.etMessage.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0); // Remove drawableEnd
                } else {
                    binding.imgSendMessage.setVisibility(View.GONE);
//                    binding.etMessage.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_connect_message_photo_camera, 0); // Add back drawableEnd
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        binding.imgSendMessage.setOnClickListener(v -> {
            ConnectMessagingMessageRecord message =  new ConnectMessagingMessageRecord();
            message.setMessageId(UUID.randomUUID().toString());
            message.setMessage(binding.etMessage.getText().toString());
            message.setChannelId(channelId);
            message.setTimeStamp(new Date());
            message.setIsOutgoing(true);
            message.setConfirmed(false);
            message.setUserViewed(true);

            binding.etMessage.setText("");

            ConnectDatabaseHelper.storeMessagingMessage(requireContext(), message);
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
        List<ConnectMessagingMessageRecord> messages = ConnectDatabaseHelper.getMessagingMessagesForChannel(requireContext(), channelId);

        List<ConnectMessageChatData> chats = new ArrayList<>();
        for(ConnectMessagingMessageRecord message : messages) {
            int viewType = message.getIsOutgoing() ? ConnectMessageAdapter.RIGHTVIEW : ConnectMessageAdapter.LEFTVIEW;
            chats.add(new ConnectMessageChatData(viewType,
                    message.getMessage(),
                    message.getIsOutgoing() ? "You" : "Them",
                    message.getTimeStamp(),
                    message.getConfirmed()));

            if(!message.getUserViewed()) {
                message.setUserViewed(true);
                ConnectDatabaseHelper.storeMessagingMessage(requireContext(), message);
            }
        }

        adapter.updateData(chats);
        binding.rvChat.scrollToPosition(messages.size() - 1);
    }
}

