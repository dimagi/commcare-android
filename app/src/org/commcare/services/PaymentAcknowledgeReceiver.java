package org.commcare.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import org.commcare.android.database.connect.models.ConnectJobPaymentRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.connect.ConnectManager;
import org.commcare.dalvik.R;
import org.commcare.utils.JobDetailsFetcher;

import java.util.List;

public class PaymentAcknowledgeReceiver extends BroadcastReceiver {

    String paymentId = "";
    boolean paymentStatus;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }

        paymentId = intent.getStringExtra(CommCareFirebaseMessagingService.PAYMENT_ID);
        paymentStatus = intent.getBooleanExtra(CommCareFirebaseMessagingService.PAYMENT_STATUS, false);

        if (paymentId == null) {
            return;
        }
        CommCareFirebaseMessagingService.clearNotification(context);
        UpdatePayment(context);
    }

    private void UpdatePayment(Context context) {
        ConnectJobRecord job = ConnectManager.getActiveJob();
        ConnectManager.updateDeliveryProgress(context, job, success -> {
            if (success) {
                JobDetailsFetcher jobDetailsFetcher = new JobDetailsFetcher(context);
                jobDetailsFetcher.getJobDetails(new JobDetailsFetcher.JobDetailsCallback() {
                    @Override
                    public void onJobDetailsFetched(List<ConnectJobRecord> jobs) {
                        if (jobs == null || jobs.isEmpty()) {
                            return;
                        }
                        getPaymentsFromJobs(context, jobs);
                    }

                    @Override
                    public void onError() {
                        Toast.makeText(context, R.string.connect_job_list_api_failure, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    /**
     * Go through job records to find the matching payment using payment-id
     *
     * @param jobs    Job list fetched data from API
     * @param context
     */
    private void getPaymentsFromJobs(Context context, List<ConnectJobRecord> jobs) {
        for (ConnectJobRecord job : jobs) {
            List<ConnectJobPaymentRecord> payments = job.getPayments();
            if (payments == null || payments.isEmpty()) {
                continue;
            }
            for (ConnectJobPaymentRecord payment : payments) {
                if (payment.getPaymentId().equals(paymentId)) {
                    handlePayment(context, payment);
                    return;
                }
            }
        }
    }

    private void handlePayment(Context context, ConnectJobPaymentRecord payment) {
        ConnectManager.updatePaymentConfirmed(context, payment, paymentStatus, success -> {
            //Nothing to do
        });
    }
}
