package org.commcare.services;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.commcare.android.database.connect.models.ConnectJobPaymentRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.ConnectJobHelper;
import org.commcare.connect.database.ConnectDatabaseHelper;
import org.commcare.connect.ConnectManager;
import org.commcare.connect.database.ConnectJobUtils;
import org.commcare.utils.FirebaseMessagingUtil;

import java.util.List;

public class PaymentAcknowledgeReceiver extends BroadcastReceiver {

    String paymentId = "";
    String opportunityId = "";
    boolean paymentStatus;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }

        opportunityId = intent.getStringExtra(ConnectConstants.OPPORTUNITY_ID);
        paymentId = intent.getStringExtra(ConnectConstants.PAYMENT_ID);
        paymentStatus = intent.getBooleanExtra(ConnectConstants.PAYMENT_STATUS, false);

        if (paymentId == null || opportunityId == null) {
            return;
        }
        CommCareFirebaseMessagingService.clearNotification(context);
        UpdatePayment(context);
    }

    private void UpdatePayment(Context context) {
        ConnectJobRecord job = ConnectJobUtils.getCompositeJob(context, Integer.parseInt(opportunityId));
        ConnectJobHelper.INSTANCE.updateDeliveryProgress(context, job, success -> {
            if (success) {
                List<ConnectJobPaymentRecord> existingPaymentList = ConnectJobUtils.getPayments(context, job.getJobId(), null);
                getPaymentsFromJobs(context, existingPaymentList);
            }
        });
    }

    /**
     * Go through job records to find the matching payment using payment-id
     *
     * @param payments    payment list fetched data from local DB
     * @param context
     */
    private void getPaymentsFromJobs(Context context, List<ConnectJobPaymentRecord> payments) {
        for (ConnectJobPaymentRecord payment : payments) {
            if (payment.getPaymentId().equals(paymentId)) {
                handlePayment(context, payment);
                return;
            }
        }
    }

    private void handlePayment(Context context, ConnectJobPaymentRecord payment) {
        ConnectJobHelper.INSTANCE.updatePaymentConfirmed(context, payment, paymentStatus, success -> {
            //Nothing to do
        });
    }
}
