package org.commcare.fragments.connect;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.common.base.Strings;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.commcare.android.database.connect.models.ConnectJobDeliveryFlagRecord;
import org.commcare.android.database.connect.models.ConnectJobDeliveryRecord;
import org.commcare.connect.ConnectDateUtils;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.FragmentConnectDeliveryListBinding;

import java.util.ArrayList;
import java.util.List;

public class ConnectDeliveryListFragment extends ConnectJobFragment {
    private static final String ALL_IDENTIFIER = "all";
    private static final String APPROVED_IDENTIFIER = "approved";
    private static final String REJECTED_IDENTIFIER = "rejected";
    private static final String PENDING_IDENTIFIER = "pending";
    private static final String[] FILTERS = {
            ALL_IDENTIFIER, APPROVED_IDENTIFIER, REJECTED_IDENTIFIER, PENDING_IDENTIFIER
    };

    private FragmentConnectDeliveryListBinding binding;
    private String currentFilter = ALL_IDENTIFIER;
    private String unitName;
    private DeliveryAdapter adapter;

    public static ConnectDeliveryListFragment newInstance() {
        return new ConnectDeliveryListFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentConnectDeliveryListBinding.inflate(inflater, container, false);
        unitName = ConnectDeliveryListFragmentArgs.fromBundle(getArguments()).getUnitId();
        requireActivity().setTitle(getString(R.string.connect_visit_type_title, unitName));

        setupRecyclerView();
        setupFilterControls();
        return binding.getRoot();
    }

    private void setupRecyclerView() {
        binding.deliveryList.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new DeliveryAdapter(getContext(), getFilteredDeliveries());
        binding.deliveryList.setAdapter(adapter);
    }

    private void setupFilterControls() {
        CardView[] filterCards = {
                binding.allFilterButton, binding.approvedFilterButton, binding.rejectedFilterButton, binding.pendingFilterButton
        };

        TextView[] filterLabels = {
                binding.allTextView, binding.approvedTextView, binding.rejectedTextView, binding.pendingTextView
        };

        for (int i = 0; i < filterCards.length; i++) {
            final String filter = FILTERS[i];
            filterCards[i].setOnClickListener(v -> onFilterSelected(filter, filterCards, filterLabels));
        }

        setFilterHighlight(filterCards[0], filterLabels[0], true);
    }

    private void onFilterSelected(String selectedFilter, CardView[] filterCards, TextView[] filterLabels) {
        currentFilter = selectedFilter;
        for (int i = 0; i < filterCards.length; i++) {
            setFilterHighlight(filterCards[i], filterLabels[i], filterCards[i].getId() == getFilterCardId(selectedFilter));
        }
        adapter.updateDeliveries(getFilteredDeliveries());
    }

    private int getFilterCardId(String filter) {
        return switch (filter) {
            case APPROVED_IDENTIFIER -> R.id.approvedFilterButton;
            case REJECTED_IDENTIFIER -> R.id.rejectedFilterButton;
            case PENDING_IDENTIFIER -> R.id.pendingFilterButton;
            default -> R.id.allFilterButton;
        };
    }

    private void setFilterHighlight(CardView card, TextView label, boolean selected) {
        int bgColor = ContextCompat.getColor(requireContext(), selected ?
                R.color.connect_blue_color : R.color.connect_blue_color_10);
        int textColor = ContextCompat.getColor(requireContext(), selected ?
                android.R.color.white : R.color.connect_blue_color);
        card.setCardBackgroundColor(bgColor);
        label.setTextColor(textColor);
    }

    private List<ConnectJobDeliveryRecord> getFilteredDeliveries() {
        List<ConnectJobDeliveryRecord> filteredList = new ArrayList<>();

        for (ConnectJobDeliveryRecord delivery : job.getDeliveries()) {
            boolean matchesUnit = delivery.getUnitName().equalsIgnoreCase(unitName);
            boolean matchesFilter = currentFilter.equals(ALL_IDENTIFIER) || delivery.getStatus().equalsIgnoreCase(currentFilter);
            if (matchesUnit && matchesFilter) {
                filteredList.add(delivery);
            }
        }
        return filteredList;
    }

    private static class DeliveryAdapter extends RecyclerView.Adapter<DeliveryAdapter.VerificationViewHolder> {
        private List<ConnectJobDeliveryRecord> deliveries;
        private final Context context;

        public DeliveryAdapter(Context context, List<ConnectJobDeliveryRecord> deliveries) {
            this.context = context;
            this.deliveries = new ArrayList<>(deliveries);
        }

        @NonNull
        @Override
        public VerificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.connect_delivery_item, parent, false);
            return new VerificationViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VerificationViewHolder holder, int position) {
            holder.bind(context, deliveries.get(position));
        }

        @Override
        public int getItemCount() {
            return deliveries.size();
        }

        public void updateDeliveries(List<ConnectJobDeliveryRecord> updatedList) {
            deliveries = new ArrayList<>(updatedList);
            notifyDataSetChanged();
        }

        static class VerificationViewHolder extends RecyclerView.ViewHolder {
            final TextView nameText, dateText, statusText, reasonText;
            final LinearLayout statusLayout;
            final ImageView statusIcon;

            public VerificationViewHolder(@NonNull View itemView) {
                super(itemView);
                nameText = itemView.findViewById(R.id.delivery_item_name);
                dateText = itemView.findViewById(R.id.delivery_item_date);
                statusText = itemView.findViewById(R.id.delivery_item_status);
                reasonText = itemView.findViewById(R.id.delivery_item_reason);
                statusLayout = itemView.findViewById(R.id.deliveryStatus);
                statusIcon = itemView.findViewById(R.id.imgDeliveryStatus);
            }

            public void bind(Context context, ConnectJobDeliveryRecord delivery) {
                nameText.setText(delivery.getEntityName());
                dateText.setText(ConnectDateUtils.INSTANCE.paymentDateFormat(delivery.getDate()));
                statusText.setText(delivery.getStatus());
                reasonText.setText(getDisplayReason(delivery));
                updateStatusUI(context, delivery.getStatus());
            }

            private String getDisplayReason(ConnectJobDeliveryRecord delivery) {
                if (!Strings.isNullOrEmpty(delivery.getReason())) {
                    return delivery.getReason();
                }
                if (delivery.getFlags() != null) {
                    List<String> flagDescriptions = new ArrayList<>();
                    for (ConnectJobDeliveryFlagRecord flag : delivery.getFlags()) {
                        flagDescriptions.add(flag.getDescription());
                    }
                    return TextUtils.join(", ", flagDescriptions);
                }
                return "";
            }

            private void updateStatusUI(Context context, String status) {
                int bgResId, iconResId;
                switch (status.toLowerCase()) {
                    case APPROVED_IDENTIFIER :
                        bgResId = R.drawable.shape_connect_delivery_approved;
                        iconResId = R.drawable.ic_connect_delivery_approved;
                        break;
                    case REJECTED_IDENTIFIER :
                        bgResId = R.drawable.shape_connect_delivery_rejected;
                        iconResId = R.drawable.ic_connect_delivery_rejected;
                        break;
                    default :
                        bgResId = R.drawable.shape_connect_delivery_pending;
                        iconResId = R.drawable.ic_connect_delivery_pending;
                }
                statusLayout.setBackgroundResource(bgResId);
                statusIcon.setImageDrawable(ContextCompat.getDrawable(context, iconResId));
            }
        }
    }
}