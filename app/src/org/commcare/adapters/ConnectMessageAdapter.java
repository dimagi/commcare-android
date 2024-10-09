package org.commcare.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.commcare.dalvik.databinding.ItemChatLeftViewBinding;
import org.commcare.dalvik.databinding.ItemChatRightViewBinding;
import org.commcare.fragments.connectMessaging.ConnectMessageChatData;

import java.util.ArrayList;

public class ConnectMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int LEFTVIEW = 0;
    public static final int RIGHTVIEW = 1;
    ArrayList<ConnectMessageChatData> dummyData;

    public ConnectMessageAdapter(ArrayList<ConnectMessageChatData> dummyData) {
        this.dummyData = dummyData;
    }

    public static class LeftViewHolder extends RecyclerView.ViewHolder {
        ItemChatLeftViewBinding binding;

        public LeftViewHolder(ItemChatLeftViewBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind() {

        }
    }

    public static class RightViewHolder extends RecyclerView.ViewHolder {
        ItemChatRightViewBinding binding;

        public RightViewHolder(ItemChatRightViewBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind() {

        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == LEFTVIEW) {
            ItemChatLeftViewBinding binding = ItemChatLeftViewBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new LeftViewHolder(binding);
        } else {
            ItemChatRightViewBinding binding = ItemChatRightViewBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new RightViewHolder(binding);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (getItemViewType(position) == LEFTVIEW) {
            ((LeftViewHolder) holder).bind();
        } else {
            ((RightViewHolder) holder).bind();
        }
    }

    @Override
    public int getItemCount() {
        return dummyData.size();
    }

    @Override
    public int getItemViewType(int position) {
        return dummyData.get(position).getType() == LEFTVIEW ? LEFTVIEW : RIGHTVIEW;
    }
}
