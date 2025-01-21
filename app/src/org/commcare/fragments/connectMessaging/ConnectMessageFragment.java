package org.commcare.fragments.connectMessaging;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import org.commcare.adapters.ConnectMessageAdapter;
import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord;
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord;
import org.commcare.connect.ConnectDatabaseHelper;
import org.commcare.connect.MessageManager;
import org.commcare.dalvik.databinding.FragmentConnectMessageBinding;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class ConnectMessageFragment extends Fragment {

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

        ConnectMessagingChannelRecord channel = ConnectDatabaseHelper.getMessagingChannel(requireContext(), channelId);
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
        // Start periodic API calls
        handler.post(apiCallRunnable);
    }

    @Override
    public void onPause() {
        super.onPause();
        // Stop the periodic API calls when the screen is not active
        handler.removeCallbacks(apiCallRunnable);
    }

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
            if(hasFocus) {

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
        Context context = getContext();
        if(context != null) {
            List<ConnectMessagingMessageRecord> messages = ConnectDatabaseHelper.getMessagingMessagesForChannel(context, channelId);

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
                    ConnectDatabaseHelper.storeMessagingMessage(context, message);
                }
            }

            adapter.updateData(chats);
            binding.rvChat.scrollToPosition(messages.size() - 1);
        }
    }
}

