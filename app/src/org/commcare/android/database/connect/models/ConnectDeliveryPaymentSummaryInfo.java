package org.commcare.android.database.connect.models;

public class ConnectDeliveryPaymentSummaryInfo {
    public static final int NO_DAILY_LIMIT = -1;

    private String paymentUnitName;
    private int paymentUnitAmount;
    private int paymentUnitMaxDaily;

    public ConnectDeliveryPaymentSummaryInfo(String paymentUnitName, int paymentUnitAmount, int paymentUnitMaxDaily) {
        this.paymentUnitName = paymentUnitName;
        this.paymentUnitAmount = paymentUnitAmount;
        this.paymentUnitMaxDaily = paymentUnitMaxDaily;
    }

    // Getters and setters
    public String getPaymentUnitName() {
        return paymentUnitName;
    }

    public void setPaymentUnitName(String paymentUnitName) {
        this.paymentUnitName = paymentUnitName;
    }

    public int getPaymentUnitAmount() {
        return paymentUnitAmount;
    }

    public void setPaymentUnitAmount(int paymentUnitAmount) {
        this.paymentUnitAmount = paymentUnitAmount;
    }

    public int getPaymentUnitMaxDaily() {
        return paymentUnitMaxDaily;
    }

    public void setPaymentUnitMaxDaily(int paymentUnitMaxDaily) {
        this.paymentUnitMaxDaily = paymentUnitMaxDaily;
    }

    public boolean hasDailyLimit() {
        return paymentUnitMaxDaily != NO_DAILY_LIMIT;
    }
}
