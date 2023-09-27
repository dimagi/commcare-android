package org.commcare.android.database.connect.models;

import java.util.Date;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;

@Table(ConnectJobDeliveryRecord.STORAGE_KEY)
public class ConnectJobDeliveryRecord extends Persisted {
    /**
     * Name of database that stores info for Connect deliveries
     */
    public static final String STORAGE_KEY = "connect_deliveries";

    @Persisting(1)
    private final String name;
    @Persisting(2)
    private final Date date;
    @Persisting(3)
    private final String status;
    @Persisting(4)
    private final boolean isPaid;
    @Persisting(5)
    private Date lastUpdate;

    public ConnectJobDeliveryRecord(String name, Date date, String status, boolean isPaid) {
        this.name = name;
        this.date = date;
        this.status = status;
        this.isPaid = isPaid;
    }

    public String getName() { return name; }
    public Date getDate() { return date; }
    public String getStatus() { return status; }
    public boolean getIsPaid() { return isPaid; }
    public void setLastUpdate(Date lastUpdate) { this.lastUpdate = lastUpdate; }
}
