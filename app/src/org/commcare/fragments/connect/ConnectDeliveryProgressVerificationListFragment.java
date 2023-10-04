package org.commcare.fragments.connect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectJobDeliveryRecord;
import org.commcare.dalvik.R;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class ConnectDeliveryProgressVerificationListFragment extends Fragment {
    private DeliveryAdapter adapter;
    private ConnectJobRecord job;
    public ConnectDeliveryProgressVerificationListFragment() {
        // Required empty public constructor
    }

    public static ConnectDeliveryProgressVerificationListFragment newInstance(ConnectJobRecord job) {
        ConnectDeliveryProgressVerificationListFragment fragment = new ConnectDeliveryProgressVerificationListFragment();
        fragment.job = job;
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_connect_progress_verification_list, container, false);

        RecyclerView recyclerView = view.findViewById(R.id.verification_list);

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(linearLayoutManager);

        adapter = new DeliveryAdapter(job);
        recyclerView.setAdapter(adapter);

        recyclerView.addItemDecoration(new DividerItemDecoration(getContext(), linearLayoutManager.getOrientation()));

        return view;
    }

    public void updateView() {
        adapter.notifyDataSetChanged();
    }

    private static class DeliveryAdapter extends RecyclerView.Adapter<DeliveryAdapter.DeliveryViewHolder> {
        private ConnectJobRecord job;
        public DeliveryAdapter(ConnectJobRecord job) {
            this.job = job;
        }

        @NonNull
        @Override
        public DeliveryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.connect_delivery_item, parent, false);

            return new DeliveryViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull DeliveryViewHolder holder, int position) {

            DateFormat df = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());
            ConnectJobDeliveryRecord delivery = job.getDeliveries().get(position);
            holder.paidText.setVisibility(delivery.getIsPaid() ? View.VISIBLE : View.GONE);
            holder.nameText.setText(delivery.getName());
            holder.dateText.setText(df.format(delivery.getDate()));
            holder.statusText.setText(delivery.getStatus());
        }

        @Override
        public int getItemCount() {
            return job.getDeliveries().size();
        }

        public static class DeliveryViewHolder extends RecyclerView.ViewHolder {
            TextView nameText;
            TextView dateText;
            TextView statusText;
            TextView paidText;

            public DeliveryViewHolder(@NonNull View itemView) {
                super(itemView);

                nameText = itemView.findViewById(R.id.delivery_item_name);
                dateText = itemView.findViewById(R.id.delivery_item_date);
                statusText = itemView.findViewById(R.id.delivery_item_status);
                paidText = itemView.findViewById(R.id.delivery_item_paid);
            }
        }
    }
}
