package org.commcare.fragments.connect;

import android.content.Context;
import android.os.Bundle;
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
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.commcare.android.database.connect.models.ConnectJobDeliveryFlagRecord;
import org.commcare.android.database.connect.models.ConnectJobDeliveryRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.connect.ConnectManager;
import org.commcare.dalvik.R;



import java.util.ArrayList;
import java.util.List;

public class ConnectDeliveryListFragment extends Fragment {
    private static final String ALL_IDENTIFIER = "all";
    private static final String APPROVED_IDENTIFIER = "approved";
    private static final String REJECTED_IDENTIFIER = "rejected";
    private static final String PENDING_IDENTIFIER = "pending";

    private static final String[] FILTERS = {ALL_IDENTIFIER, APPROVED_IDENTIFIER,
            REJECTED_IDENTIFIER, PENDING_IDENTIFIER};

    private String currentFilter = FILTERS[0];
    private String unitName;
    private DeliveryAdapter adapter;

    public ConnectDeliveryListFragment() {
        // Required empty public constructor
    }

    public static ConnectDeliveryListFragment newInstance() {
        return new ConnectDeliveryListFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ConnectDeliveryListFragmentArgs args = ConnectDeliveryListFragmentArgs.fromBundle(getArguments());
        unitName = args.getUnitId();
        requireActivity().setTitle(getString(R.string.connect_visit_type_title, unitName));

        View view = inflater.inflate(R.layout.fragment_connect_delivery_list, container, false);
        setupRecyclerView(view);
        setupFilterViews(view);

        return view;
    }

    private void setupRecyclerView(View view) {
        RecyclerView recyclerView = view.findViewById(R.id.delivery_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new DeliveryAdapter(getContext(), getFilteredDeliveries());
        recyclerView.setAdapter(adapter);
    }

    private void setupFilterViews(View view) {
        CardView[] filterCards = {
                view.findViewById(R.id.cvFilterAll),
                view.findViewById(R.id.llFilterApproved),
                view.findViewById(R.id.llFilterRejected),
                view.findViewById(R.id.llFilterPending)
        };

        TextView[] filterTexts = {
                view.findViewById(R.id.tvFilterAll),
                view.findViewById(R.id.tvFilterApproved),
                view.findViewById(R.id.tvFilterRejected),
                view.findViewById(R.id.tvFilterPending)
        };

        for (int i = 0; i < filterCards.length; i++) {
            final String filter = FILTERS[i];
            filterCards[i].setOnClickListener(v -> setFilter(filter, filterCards, filterTexts));
        }

        // Set the initial selected filter's background color
        setFilterBackground(filterCards[0], filterTexts[0], true);
    }

    private void setFilter(String filter, CardView[] filterCards, TextView[] filterTexts) {
        currentFilter = filter;
        for (int i = 0; i < filterCards.length; i++) {
            setFilterBackground(filterCards[i], filterTexts[i], filterCards[i].getId() == getFilterId(filter));
        }
        adapter.updateDeliveries(getFilteredDeliveries());
    }

    private int getFilterId(String filter) {
        return switch (filter) {
            case APPROVED_IDENTIFIER -> R.id.llFilterApproved;
            case REJECTED_IDENTIFIER -> R.id.llFilterRejected;
            case PENDING_IDENTIFIER -> R.id.llFilterPending;
            default -> R.id.cvFilterAll;
        };
    }

    private void setFilterBackground(CardView card, TextView textView, boolean isSelected) {
        card.setCardBackgroundColor(getResources().getColor(isSelected ? R.color.connect_blue_color : R.color.connect_blue_color_10));
        textView.setTextColor(getResources().getColor(isSelected ? android.R.color.white : R.color.connect_blue_color));
    }

    private List<ConnectJobDeliveryRecord> getFilteredDeliveries() {
        List<ConnectJobDeliveryRecord> deliveryProgressList = new ArrayList<>();
        ConnectJobRecord job = ConnectManager.requireActiveJob();
        for (ConnectJobDeliveryRecord delivery : job.getDeliveries()) {
            if (delivery.getUnitName().equalsIgnoreCase(unitName) &&
                    (currentFilter.equals(ALL_IDENTIFIER) ||
                            delivery.getStatus().equalsIgnoreCase(currentFilter))) {
                deliveryProgressList.add(delivery);
            }
        }
        return deliveryProgressList;
    }

    private static class DeliveryAdapter extends RecyclerView.Adapter<DeliveryAdapter.VerificationViewHolder> {
        private final List<ConnectJobDeliveryRecord> filteredDeliveries;
        Context context;

        public DeliveryAdapter(Context context, List<ConnectJobDeliveryRecord> filteredDeliveries) {
            this.context = context;
            this.filteredDeliveries = filteredDeliveries;
        }

        @NonNull
        @Override
        public VerificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VerificationViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.connect_delivery_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VerificationViewHolder holder, int position) {
            ConnectJobDeliveryRecord delivery = filteredDeliveries.get(position);
            holder.bind(context, delivery);
        }

        @Override
        public int getItemCount() {
            return filteredDeliveries.size();
        }

        public void updateDeliveries(List<ConnectJobDeliveryRecord> newDeliveries) {
            filteredDeliveries.clear();
            filteredDeliveries.addAll(newDeliveries);
            notifyDataSetChanged();
        }

        static class VerificationViewHolder extends RecyclerView.ViewHolder {
            final TextView nameText;
            final TextView dateText;
            final TextView statusText;
            final TextView reasonText;
            final LinearLayout llDeliveryStatus;
            final ImageView imgDeliveryStatus;

            public VerificationViewHolder(@NonNull View itemView) {
                super(itemView);
                nameText = itemView.findViewById(R.id.delivery_item_name);
                dateText = itemView.findViewById(R.id.delivery_item_date);
                statusText = itemView.findViewById(R.id.delivery_item_status);
                reasonText = itemView.findViewById(R.id.delivery_item_reason);
                llDeliveryStatus = itemView.findViewById(R.id.llDeliveryStatus);
                imgDeliveryStatus = itemView.findViewById(R.id.imgDeliveryStatus);
            }

            public void bind(Context context, ConnectJobDeliveryRecord delivery) {
                nameText.setText(delivery.getEntityName());
                dateText.setText(ConnectManager.paymentDateFormat(delivery.getDate()));
                statusText.setText(delivery.getStatus());

                String descriptionText = delivery.getReason();
                if (Strings.isNullOrEmpty(descriptionText)) {
                    if (delivery.getFlags() != null) {
                        List<String> flagStrings = new ArrayList<>();
                        for (ConnectJobDeliveryFlagRecord flag : delivery.getFlags()) {
                            flagStrings.add(flag.getDescription());
                        }
                        descriptionText = String.join(", ", flagStrings);
                    } else {
                        descriptionText = "";
                    }
                }
                reasonText.setText(descriptionText);

                handleUI(context, delivery.getStatus());
            }

            public void handleUI(Context context, String status) {
                switch (status) {
                    case PENDING_IDENTIFIER: {
                        llDeliveryStatus.setBackgroundResource(R.drawable.shape_connect_delivery_pending);
                        imgDeliveryStatus.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_connect_delivery_pending));
                        break;
                    }

                    case APPROVED_IDENTIFIER: {
                        llDeliveryStatus.setBackgroundResource(R.drawable.shape_connect_delivery_approved);
                        imgDeliveryStatus.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_connect_delivery_approved));
                        break;
                    }

                    case REJECTED_IDENTIFIER: {
                        llDeliveryStatus.setBackgroundResource(R.drawable.shape_connect_delivery_rejected);
                        imgDeliveryStatus.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_connect_delivery_rejected));
                        break;
                    }
                }
            }
        }
    }
}
