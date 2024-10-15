package org.commcare.fragments.connect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.commcare.android.database.connect.models.ConnectJobDeliveryRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.connect.ConnectManager;
import org.commcare.dalvik.R;

import java.util.ArrayList;
import java.util.List;

public class ConnectDeliveryListFragment extends Fragment {

    private static final String[] FILTERS = {"All", "Approved", "Rejected", "Pending"};

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
        ConnectJobRecord job = ConnectManager.getActiveJob();
        getActivity().setTitle(job.getTitle());

        View view = inflater.inflate(R.layout.fragment_connect_delivery_list, container, false);
        setupRecyclerView(view);
        setupFilterViews(view);

        return view;
    }

    private void setupRecyclerView(View view) {
        RecyclerView recyclerView = view.findViewById(R.id.delivery_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new DeliveryAdapter(getFilteredDeliveries());
        recyclerView.setAdapter(adapter);
        recyclerView.addItemDecoration(new DividerItemDecoration(getContext(), LinearLayoutManager.VERTICAL));
    }

    private void setupFilterViews(View view) {
        CardView[] filterCards = {
                view.findViewById(R.id.llFilterAll),
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
            case "Approved" -> R.id.llFilterApproved;
            case "Rejected" -> R.id.llFilterRejected;
            case "Pending" -> R.id.llFilterPending;
            default -> R.id.llFilterAll;
        };
    }

    private void setFilterBackground(CardView card, TextView textView, boolean isSelected) {
        card.setCardBackgroundColor(getResources().getColor(isSelected ? R.color.connect_blue_color : R.color.connect_blue_color_10));
        textView.setTextColor(getResources().getColor(isSelected ? android.R.color.white : R.color.connect_blue_color));
    }

    private List<ConnectJobDeliveryRecord> getFilteredDeliveries() {
        return new DeliveryFilter(ConnectManager.getActiveJob()).filterDeliveries(unitName, currentFilter);
    }

    private static class DeliveryAdapter extends RecyclerView.Adapter<DeliveryAdapter.VerificationViewHolder> {
        private List<ConnectJobDeliveryRecord> filteredDeliveries;

        public DeliveryAdapter(List<ConnectJobDeliveryRecord> filteredDeliveries) {
            this.filteredDeliveries = filteredDeliveries;
        }

        @NonNull
        @Override
        public VerificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VerificationViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.connect_verification_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VerificationViewHolder holder, int position) {
            ConnectJobDeliveryRecord delivery = filteredDeliveries.get(position);
            holder.bind(delivery);
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

            public VerificationViewHolder(@NonNull View itemView) {
                super(itemView);
                nameText = itemView.findViewById(R.id.delivery_item_name);
                dateText = itemView.findViewById(R.id.delivery_item_date);
                statusText = itemView.findViewById(R.id.delivery_item_status);
                reasonText = itemView.findViewById(R.id.delivery_item_reason);
            }

            public void bind(ConnectJobDeliveryRecord delivery) {
                nameText.setText(delivery.getEntityName());
                dateText.setText(ConnectManager.formatDate(delivery.getDate()));
                statusText.setText(delivery.getStatus());
                reasonText.setText(delivery.getReason());
            }
        }
    }

    private static class DeliveryFilter {
        private final ConnectJobRecord job;

        public DeliveryFilter(ConnectJobRecord job) {
            this.job = job;
        }

        public List<ConnectJobDeliveryRecord> filterDeliveries(String unitName, String filterType) {
            List<ConnectJobDeliveryRecord> deliveryProgressList = new ArrayList<>();
            if (job != null) {
                for (ConnectJobDeliveryRecord delivery : job.getDeliveries()) {
                    if (isMatchingDelivery(delivery, unitName, filterType)) {
                        deliveryProgressList.add(delivery);
                    }
                }
            }
            return deliveryProgressList;
        }

        private boolean isMatchingDelivery(ConnectJobDeliveryRecord delivery, String unitName, String filterType) {
            return delivery != null && delivery.getUnitName().equalsIgnoreCase(unitName) &&
                    (delivery.getStatus().equalsIgnoreCase(filterType) || filterType.equalsIgnoreCase("All"));
        }
    }
}
