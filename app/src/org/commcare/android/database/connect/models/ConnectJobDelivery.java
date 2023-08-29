package org.commcare.android.database.connect.models;

import java.util.Date;

public class ConnectJobDelivery {
    private final String name;
    private final Date date;
    private final String status;
    private final boolean isPaid;

    public ConnectJobDelivery(String name, Date date, String status, boolean isPaid) {
        this.name = name;
        this.date = date;
        this.status = status;
        this.isPaid = isPaid;
    }

    public String getName() { return name; }
    public Date getDate() { return date; }
    public String getStatus() { return status; }
    public boolean getIsPaid() { return isPaid; }
}
