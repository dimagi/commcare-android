package org.commcare.android.database.connect.models;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;
import org.json.JSONException;
import org.json.JSONObject;

@Table(ConnectJobDeliveryRecord.STORAGE_KEY)
public class ConnectJobDeliveryRecord extends Persisted implements Serializable {
    /**
     * Name of database that stores info for Connect deliveries
     */
    public static final String STORAGE_KEY = "connect_deliveries";

    public static final String META_JOB_ID = "job_id";
    public static final String META_ID = "id";
    public static final String META_STATUS = "status";
    public static final String META_DATE = "visit_date";
    public static final String META_FORM_NAME = "deliver_form_name";
    public static final String META_XML_NS = "deliver_form_xmlns";

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
    @MetaField(META_FORM_NAME)
    private String formName;
    @Persisting(6)
    @MetaField(META_XML_NS)
    private String formXmlNs;
    @Persisting(7)
    private Date lastUpdate;

    public ConnectJobDeliveryRecord() {
    }

    public static ConnectJobDeliveryRecord fromJson(JSONObject json, int jobId) throws JSONException, ParseException {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        ConnectJobDeliveryRecord delivery = new ConnectJobDeliveryRecord();
        delivery.jobId = jobId;
        delivery.lastUpdate = new Date();

        delivery.deliveryId = json.has(META_ID) ? json.getInt(META_ID) : -1;
        delivery.date = json.has(META_DATE) ? df.parse(json.getString(META_DATE)) : new Date();
        delivery.status = json.has(META_STATUS) ? json.getString(META_STATUS) : null;
        delivery.formName = json.has(META_FORM_NAME) ? json.getString(META_FORM_NAME) : null;
        delivery.formXmlNs = json.has(META_XML_NS) ? json.getString(META_XML_NS) : null;

        return delivery;
    }

    public int getDeliveryId() { return deliveryId; }
    public Date getDate() { return date; }
    public String getStatus() { return status; }
    public String getFormName() { return formName; }
    public void setLastUpdate(Date lastUpdate) { this.lastUpdate = lastUpdate; }
}
