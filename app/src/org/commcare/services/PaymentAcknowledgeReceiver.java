package org.commcare.services;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.commcare.android.database.connect.models.ConnectJobPaymentRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.ConnectJobHelper;
import org.commcare.connect.database.ConnectJobUtils;

import java.util.List;

public class PaymentAcknowledgeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }

        String opportunityId = intent.getStringExtra(ConnectConstants.OPPORTUNITY_ID);
        String paymentId = intent.getStringExtra(ConnectConstants.PAYMENT_ID);
        boolean paymentStatus = intent.getBooleanExtra(ConnectConstants.PAYMENT_STATUS, false);

        if (paymentId == null || opportunityId == null) {
            return;
        }
        CommCareFirebaseMessagingService.clearNotification(context);
        updatePayment(context, opportunityId, paymentId, paymentStatus);
    }

    private void updatePayment(Context context, String opportunityId, String paymentId, boolean paymentStatus) {
        ConnectJobRecord job = ConnectJobUtils.getCompositeJob(context, Integer.parseInt(opportunityId));
        ConnectJobHelper.INSTANCE.updateDeliveryProgress(context, job, success -> {
            if (success) {
                List<ConnectJobPaymentRecord> existingPaymentList = ConnectJobUtils.getPayments(context, job.getJobId(), null);
                getPaymentsFromJobs(context, existingPaymentList, paymentId, paymentStatus);
            }
        });
    }

    /**
     * Go through job records to find the matching payment using payment-id
     *
     * @param payments    payment list fetched data from local DB
     * @param context     Parent context
     */
    private void getPaymentsFromJobs(Context context, List<ConnectJobPaymentRecord> payments,
                                     String paymentId, boolean paymentStatus) {
        for (ConnectJobPaymentRecord payment : payments) {
            if (payment.getPaymentId().equals(paymentId)) {
                handlePayment(context, payment, paymentStatus);
                return;
            }
        }
    }

    private void handlePayment(Context context, ConnectJobPaymentRecord payment, boolean paymentStatus) {
        ConnectJobHelper.INSTANCE.updatePaymentConfirmed(context, payment, paymentStatus, success -> {
            //Nothing to do
        });
    }
}
