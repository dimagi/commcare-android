package org.commcare.android.database.connect.models;

import java.io.Serializable;
import java.text.ParseException;
import java.util.Date;

import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Data class for holding info related to a Connect job delivery
 *
 * @author dviggiano
 */
@Table(ConnectJobDeliveryRecord.STORAGE_KEY)
public class ConnectJobDeliveryRecord extends Persisted implements Serializable {
    /**
     * Name of database that stores info for Connect deliveries
     */
    public static final String STORAGE_KEY = "connect_deliveries";

    public static final String META_JOB_ID = "job_id";
    public static final String META_ID = "id";
    public static final String META_STATUS = "status";
    public static final String META_REASON = "reason";
    public static final String META_DATE = "visit_date";
    public static final String META_UNIT_NAME = "deliver_unit_name";
    public static final String META_SLUG = "deliver_unit_slug";
    public static final String META_ENTITY_ID = "entity_id";
    public static final String META_ENTITY_NAME = "entity_name";

    @Persisting(1)
    @MetaField(META_JOB_ID)
    private int jobId;

    @Persisting(2)
    @MetaField(META_ID)
    private int deliveryId;
    @Persisting(3)
    @MetaField(META_DATE)
    private Date date;
    @Persisting(4)
    @MetaField(META_STATUS)
    private String status;
    @Persisting(5)
    @MetaField(META_UNIT_NAME)
    private String unitName;
    @Persisting(6)
    @MetaField(META_SLUG)
    private String slug;
    @Persisting(7)
    @MetaField(META_ENTITY_ID)
    private String entityId;
    @Persisting(8)
    @MetaField(META_ENTITY_NAME)
    private String entityName;
    @Persisting(9)
    private Date lastUpdate;
    @Persisting(10)
    @MetaField(META_REASON)
    private String reason;

    public ConnectJobDeliveryRecord() {
    }

    public static ConnectJobDeliveryRecord fromJson(JSONObject json, int jobId) throws JSONException, ParseException {
        ConnectJobDeliveryRecord delivery = new ConnectJobDeliveryRecord();
        delivery.jobId = jobId;
        delivery.lastUpdate = new Date();

        delivery.deliveryId = json.has(META_ID) ? json.getInt(META_ID) : -1;
        delivery.date = json.has(META_DATE) ? ConnectNetworkHelper.convertUTCToDate(json.getString(META_DATE)): new Date();
        delivery.status = json.has(META_STATUS) ? json.getString(META_STATUS) : "";
        delivery.unitName = json.has(META_UNIT_NAME) ? json.getString(META_UNIT_NAME) : "";
        delivery.slug = json.has(META_SLUG) ? json.getString(META_SLUG) : "";
        delivery.entityId = json.has(META_ENTITY_ID) ? json.getString(META_ENTITY_ID) : "";
        delivery.entityName = json.has(META_ENTITY_NAME) ? json.getString(META_ENTITY_NAME) : "";

        delivery.reason = json.has(META_REASON) && !json.isNull(META_REASON) ? json.getString(META_REASON) : "";

        return delivery;
    }

    public int getDeliveryId() { return deliveryId; }
    public Date getDate() { return ConnectNetworkHelper.convertDateToLocal(date); }
    public String getStatus() { return status; }
    public String getEntityName() { return entityName; }
    public void setLastUpdate(Date lastUpdate) { this.lastUpdate = lastUpdate; }

    public int getJobId() { return jobId; }
    public String getUnitName() { return unitName; }
    public String getSlug() { return slug; }
    public String getEntityId() { return entityId; }
    public Date getLastUpdate() { return lastUpdate; }
    public String getReason() { return reason; }

    public static ConnectJobDeliveryRecord fromV2(ConnectJobDeliveryRecordV2 oldRecord) {
        ConnectJobDeliveryRecord newRecord = new ConnectJobDeliveryRecord();

        newRecord.jobId = oldRecord.getJobId();
        newRecord.deliveryId = oldRecord.getDeliveryId();
        newRecord.date = oldRecord.date;
        newRecord.status = oldRecord.getStatus();
        newRecord.unitName = oldRecord.getUnitName();
        newRecord.slug = oldRecord.getSlug();
        newRecord.entityId = oldRecord.getEntityId();
        newRecord.entityName = oldRecord.getEntityName();
        newRecord.lastUpdate = oldRecord.getLastUpdate();
        newRecord.reason = "";

        return newRecord;
    }
}
