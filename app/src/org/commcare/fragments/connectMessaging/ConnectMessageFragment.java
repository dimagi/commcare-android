package org.commcare.fragments.connectMessaging;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.RecyclerView;

import org.commcare.adapters.ConnectMessageAdapter;
import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord;
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord;
import org.commcare.connect.MessageManager;
import org.commcare.connect.database.ConnectMessagingDatabaseHelper;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.FragmentConnectMessageBinding;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.utils.FirebaseMessagingUtil;
import org.commcare.views.dialogs.CustomThreeButtonAlertDialog;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import kotlin.Unit;

public class ConnectMessageFragment extends Fragment {
    private static String activeChannel;
    private String channelId;
    private FragmentConnectMessageBinding binding;
    private ConnectMessageAdapter adapter;
    private Runnable apiCallRunnable; // The task to run periodically
    private static final int INTERVAL = 30000;
    private final Handler handler = new Handler(); // To post periodic tasks

    private ConnectMessagingChannelRecord channel;
    private Map<Integer, String> menuItemsAnalyticsParamsMapping;
    private static final int MENU_UNSUBSCRIBE = Menu.FIRST;
    private static final int MENU_RESUBSCRIBE = Menu.FIRST + 1;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentConnectMessageBinding.inflate(inflater, container, false);

        ConnectMessageFragmentArgs args = ConnectMessageFragmentArgs.fromBundle(getArguments());
        channelId = args.getChannelId();

        channel = ConnectMessagingDatabaseHelper.getMessagingChannel(requireContext(), channelId);
        requireActivity().setTitle(channel.getChannelName());

        handleSendButtonListener();
        setChatAdapter();
        apiCallRunnable = new Runnable() {
            @Override
            public void run() {
                fetchMessagesFromNetwork(); // Perform the API call
                handler.postDelayed(this, INTERVAL); // Schedule the next call
            }
        };
        setupMenuItems();

        if (channel.getConsented()) {
            setChannelSubscribedState();
        } else {
            setChannelUnsubscribedState();
        }

        return binding.getRoot();
    }

    private void setupMenuItems() {
        requireActivity().addMenuProvider(
                new MenuProvider() {
                    @Override
                    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                        // TODO: Add this code back in when we fully implement "Resubscribe".
                        // TODO: Both menu items are hidden (commented out) for now.
//                        if (channel.getConsented()) {
//                            menu.add(
//                                    Menu.NONE,
//                                    MENU_UNSUBSCRIBE,
//                                    Menu.NONE,
//                                    R.string.connect_messaging_channel_menu_item_unsubscribe
//                            );
//                        } else {
//                            menu.add(
//                                    Menu.NONE,
//                                    MENU_RESUBSCRIBE,
//                                    Menu.NONE,
//                                    R.string.connect_messaging_channel_menu_item_resubscribe
//                            );
//                        }

                        menuItemsAnalyticsParamsMapping = Map.of(
                                MENU_UNSUBSCRIBE,
                                AnalyticsParamValue.CONNECT_MESSAGING_CHANNEL_MENU_UNSUBSCRIBE,
                                MENU_RESUBSCRIBE,
                                AnalyticsParamValue.CONNECT_MESSAGING_CHANNEL_MENU_RESUBSCRIBE
                        );
                    }

                    @Override
                    public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                        int menuItemId = menuItem.getItemId();

                        if (menuItemsAnalyticsParamsMapping.containsKey(menuItemId)) {
                            FirebaseAnalyticsUtil.reportOptionsMenuItemClick(
                                    ConnectMessageFragment.class,
                                    menuItemsAnalyticsParamsMapping.get(menuItemId)
                            );
                        }

                        if (menuItemId == MENU_UNSUBSCRIBE || menuItemId == MENU_RESUBSCRIBE) {
                            showDialogForMenuItem(menuItemId);
                            return true;
                        }

                        return false;
                    }
                },
                getViewLifecycleOwner(),
                Lifecycle.State.RESUMED
        );
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

    @Nullable
    public static String getActiveChannel() {
        return activeChannel;
    }

    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshUi();
        }
    };

    private void fetchMessagesFromNetwork() {
        MessageManager.retrieveMessages(requireActivity(), (success, error) -> {
            if (success) {
                refreshUi();
            } else {
                Context context = getContext();
                if (context != null) {
                    Toast.makeText(
                            context,
                            getString(R.string.connect_messaging_retrieve_messages_fail),
                            Toast.LENGTH_SHORT
                    ).show();
                }
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
                if (s.length() > 0 && channel.getConsented()) {
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

        MessageManager.sendMessage(requireContext(), message, (success, error) -> {
            if (!success) {
                Toast.makeText(requireContext(), getString(R.string.connect_messaging_send_message_fail_msg), Toast.LENGTH_SHORT).show();
            } else {
                chat.setMessageRead(success);
                adapter.updateMessageReadStatus(chat);
                scrollToLatestMessage();
                FirebaseAnalyticsUtil.reportPersonalIDMessageSent();
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

    private void showDialogForMenuItem(int menuItemId) {
        CustomThreeButtonAlertDialog dialog = null;

        if (menuItemId == MENU_UNSUBSCRIBE) {
            String titleText = getString(
                    R.string.connect_messaging_unsubscribe_dialog_title,
                    channel.getChannelName()
            );
            String messageText = getString(R.string.connect_messaging_unsubscribe_dialog_body);
            String negativeButtonText = getString(R.string.connect_messaging_unsubscribe_dialog_cancel);
            String positiveButtonText = getString(R.string.connect_messaging_unsubscribe_dialog_unsubscribe);
            String errorText = getString(R.string.connect_messaging_channel_unsubscribe_error);

            dialog = new CustomThreeButtonAlertDialog(
                    titleText,
                    messageText,
                    negativeButtonText,
                    () -> {
                        // No-op
                        return Unit.INSTANCE;
                    },
                    R.color.connect_darker_blue_color,
                    R.color.white,
                    positiveButtonText,
                    () -> {
                        binding.pbLoadingSpinner.setVisibility(View.VISIBLE);
                        channel.setConsented(false);

                        MessageManager.updateChannelConsent(
                                requireContext(),
                                channel,
                                (success, error) -> {
                                    if (isAdded()) {
                                        binding.pbLoadingSpinner.setVisibility(View.GONE);

                                        if (success) {
                                            setChannelUnsubscribedState();
                                            requireActivity().invalidateMenu();
                                        } else {
                                            channel.setConsented(true);
                                            Toast.makeText(
                                                    requireContext(),
                                                    errorText,
                                                    Toast.LENGTH_SHORT
                                            ).show();
                                        }
                                    }
                                }
                        );
                        return Unit.INSTANCE;
                    },
                    R.color.white,
                    R.color.red
            );
        } else if (menuItemId == MENU_RESUBSCRIBE) {
            String titleText = getString(
                    R.string.connect_messaging_resubscribe_dialog_title,
                    channel.getChannelName()
            );
            String messageText = getString(
                    R.string.connect_messaging_resubscribe_dialog_body,
                    channel.getChannelName()
            );
            String negativeButtonText = getString(R.string.connect_messaging_resubscribe_dialog_cancel);
            String positiveButtonText = getString(R.string.connect_messaging_resubscribe_dialog_resubscribe);

            dialog = new CustomThreeButtonAlertDialog(
                    titleText,
                    messageText,
                    negativeButtonText,
                    () -> {
                        // No-op
                        return Unit.INSTANCE;
                    },
                    R.color.connect_darker_blue_color,
                    R.color.white,
                    positiveButtonText,
                    () -> {
                        // TODO: Not implemented yet.
                        return Unit.INSTANCE;
                    },
                    R.color.white,
                    R.color.connect_blue_color
            );
        }

        if (dialog == null) {
            throw new IllegalStateException("Attempted to show alert dialog for an unsupported menu item!");
        }

        dialog.showDialog(requireContext());
    }

    private void setChannelSubscribedState() {
        binding.etMessage.setEnabled(true);
        binding.etMessage.setText("");
        binding.etMessage.setGravity(Gravity.CENTER_VERTICAL);
        binding.etMessage.setTextColor(ContextCompat.getColor(requireContext(), R.color.black));
    }

    private void setChannelUnsubscribedState() {
        binding.etMessage.setEnabled(false);
        binding.etMessage.setText(R.string.connect_messaging_channel_list_not_subscribed);
        binding.etMessage.setGravity(Gravity.CENTER);
        binding.etMessage.setTextColor(ContextCompat.getColor(requireContext(), R.color.sterling_ash));
    }
}

