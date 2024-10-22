package org.commcare.android.database.connect.models;

public class ConnectDeliveryPaymentSummaryInfo {
    private String paymentUnitName;
    private double paymentUnitAmount;
    private double paymentUnitMaxDaily;

    public ConnectDeliveryPaymentSummaryInfo(String paymentUnitName, double paymentUnitAmount, double paymentUnitMaxDaily) {
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

    public double getPaymentUnitAmount() {
        return paymentUnitAmount;
    }

    public void setPaymentUnitAmount(double paymentUnitAmount) {
        this.paymentUnitAmount = paymentUnitAmount;
    }

    public double getPaymentUnitMaxDaily() {
        return paymentUnitMaxDaily;
    }

    public void setPaymentUnitMaxDaily(double paymentUnitMaxDaily) {
        this.paymentUnitMaxDaily = paymentUnitMaxDaily;
    }
}
