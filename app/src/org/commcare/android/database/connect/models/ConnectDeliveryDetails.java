package org.commcare.android.database.connect.models;

public class ConnectDeliveryDetails {
    private String deliveryName;
    private int approvedCount;
    private int pendingCount;
    private long remainingDays;
    private String totalAmount;
    private double approvedPercentage;
    private int unitId;

    // Constructor
    public ConnectDeliveryDetails(int unitId, String deliveryName, int approvedCount, int pendingCount, String totalAmount,
            long remainingDays, double approvedPercentage) {
        this.unitId = unitId;
        this.deliveryName = deliveryName;
        this.approvedCount = approvedCount;
        this.pendingCount = pendingCount;
        this.remainingDays = remainingDays;
        this.totalAmount = totalAmount;
        this.approvedPercentage = approvedPercentage;
    }

    public String getDeliveryName() {
        return deliveryName;
    }

    public void setDeliveryName(String deliveryName) {
        this.deliveryName = deliveryName;
    }

    public int getApprovedCount() {
        return approvedCount;
    }

    public void setApprovedCount(int approvedCount) {
        this.approvedCount = approvedCount;
    }

    public int getPendingCount() {
        return pendingCount;
    }

    public void setPendingCount(int pendingCount) {
        this.pendingCount = pendingCount;
    }

    public long getRemainingDays() {
        return remainingDays;
    }

    public void setRemainingDays(long remainingDays) {
        this.remainingDays = remainingDays;
    }

    public String getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(String totalAmount) {
        this.totalAmount = totalAmount;
    }

    public double getApprovedPercentage() {
        return approvedPercentage;
    }

    public void setApprovedPercentage(double approvedPercentage) {
        this.approvedPercentage = approvedPercentage;
    }

    public int getUnitId() {
        return unitId;
    }

    public void setUnitId(int unitId) {
        this.unitId = unitId;
    }
}
