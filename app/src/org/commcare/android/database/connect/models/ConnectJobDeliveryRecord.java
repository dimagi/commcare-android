package org.commcare.android.database.connect.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;
import org.commcare.utils.JsonExtensions;
import org.javarosa.core.model.utils.DateUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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
    public static final String META_FLAGS = "flags";

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

    private List<ConnectJobDeliveryFlagRecord> flags;

    public ConnectJobDeliveryRecord() {
        date = new Date();
        lastUpdate = new Date();
        flags = new ArrayList<>();
    }

    public static ConnectJobDeliveryRecord fromJson(JSONObject json, int jobId) throws JSONException {
        int deliveryId = -1;
        ConnectJobDeliveryRecord delivery = new ConnectJobDeliveryRecord();
        delivery.jobId = jobId;
        delivery.lastUpdate = new Date();

        deliveryId = json.getInt(META_ID);
        delivery.deliveryId = deliveryId;
        String dateString = json.getString(META_DATE);
        delivery.date = DateUtils.parseDateTime(dateString);
        delivery.status = json.getString(META_STATUS);
        delivery.unitName = json.getString(META_UNIT_NAME);
        delivery.slug = json.getString(META_SLUG);
        delivery.entityId = json.getString(META_ENTITY_ID);
        delivery.entityName = json.getString(META_ENTITY_NAME);
        delivery.reason = json.getString(META_REASON);
        delivery.reason = JsonExtensions.optStringSafe(json, META_REASON,"");

        return delivery;
    }

    public int getDeliveryId() {
        return deliveryId;
    }

    public Date getDate() {
        return date;
    }

    public String getStatus() {
        return status;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setLastUpdate(Date lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public int getJobId() {
        return jobId;
    }

    public String getUnitName() {
        return unitName;
    }

    public String getSlug() {
        return slug;
    }

    public String getEntityId() {
        return entityId;
    }

    public Date getLastUpdate() {
        return lastUpdate;
    }

    public String getReason() {
        return reason;
    }

    public List<ConnectJobDeliveryFlagRecord> getFlags() {
        return flags;
    }

    public static ConnectJobDeliveryRecord fromV2(ConnectJobDeliveryRecordV2 oldRecord) {
        ConnectJobDeliveryRecord newRecord = new ConnectJobDeliveryRecord();

        newRecord.jobId = oldRecord.getJobId();
        newRecord.deliveryId = oldRecord.getDeliveryId();
        newRecord.date = oldRecord.getDate();
        newRecord.status = oldRecord.getStatus();
        newRecord.unitName = oldRecord.getUnitName();
        newRecord.slug = oldRecord.getSlug();
        newRecord.entityId = oldRecord.getEntityId();
        newRecord.entityName = oldRecord.getEntityName();
        newRecord.lastUpdate = oldRecord.getLastUpdate();
        newRecord.reason = null;

        return newRecord;
    }
}
