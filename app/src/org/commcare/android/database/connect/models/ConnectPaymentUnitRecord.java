package org.commcare.android.database.connect.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;
import org.javarosa.core.services.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

@Table(ConnectPaymentUnitRecord.STORAGE_KEY)
public class ConnectPaymentUnitRecord extends Persisted implements Serializable {
    /**
     * Name of database that stores Connect payment units
     */
    public static final String STORAGE_KEY = "connect_payment_units";

    public static final String META_JOB_ID = "job_id";
    //NOTE: Server sends id, but local DB already using unit_id
    public static final String META_ID = "id";
    public static final String META_UNIT_ID = "unit_id";
    public static final String META_NAME = "name";
    public static final String META_TOTAL = "max_total";
    public static final String META_DAILY = "max_daily";
    public static final String META_AMOUNT = "amount";
    public static final String META_JOB_UUID = ConnectJobRecord.META_JOB_UUID;
    public static final String META_PAYMENT_UNIT_UUID = "payment_unit_uuid";  // todo: The server needs to provide this payment unit UUID field name.

    @Persisting(1)
    @MetaField(META_JOB_ID)
    private int jobId;

    @Persisting(2)
    @MetaField(META_UNIT_ID)
    private int unitId;

    @Persisting(3)
    @MetaField(META_NAME)
    private String name;

    @Persisting(4)
    @MetaField(META_TOTAL)
    private int maxTotal;

    @Persisting(5)
    @MetaField(META_DAILY)
    private int maxDaily;

    @Persisting(6)
    @MetaField(META_AMOUNT)
    private int amount;

    @Persisting(7)
    @MetaField(META_JOB_UUID)
    private String jobUUID;

    @Persisting(8)
    @MetaField(META_PAYMENT_UNIT_UUID)
    private String unitUUID;

    public ConnectPaymentUnitRecord() {

    }

    public static ConnectPaymentUnitRecord fromJson(JSONObject json, ConnectJobRecord job) {
        try {
        ConnectPaymentUnitRecord paymentUnit = new ConnectPaymentUnitRecord();

            paymentUnit.jobId = job.getJobId();

            if (job.getJobUUID().isEmpty()) {
                paymentUnit.jobUUID = Integer.toString(job.getJobId());
            } else {
                paymentUnit.jobUUID = job.getJobUUID();
            }

            paymentUnit.unitId = json.getInt(META_ID);
            String unitUUID = json.optString(META_PAYMENT_UNIT_UUID, "");
            paymentUnit.unitUUID = unitUUID.isEmpty() ? Integer.toString(paymentUnit.unitId) : unitUUID;

            paymentUnit.name = json.getString(META_NAME);
            paymentUnit.maxTotal = json.getInt(META_TOTAL);
            paymentUnit.maxDaily = json.getInt(META_DAILY);
            paymentUnit.amount = json.getInt(META_AMOUNT);

            return paymentUnit;
        } catch(JSONException e) {
            Logger.exception("Error parsing Connect payment", e);
            return null;
        }
    }

    public int getJobId() {
        return jobId;
    }

    public void setJobId(int jobId) {
        this.jobId = jobId;
    }

    public String getName() {
        return name;
    }

    public int getUnitId() {
        return unitId;
    }

    public int getMaxTotal() {
        return maxTotal;
    }

    public void setMaxTotal(int max) {
        maxTotal = max;
    }

    public int getMaxDaily() {
        return maxDaily;
    }

    public int getAmount() {
        return amount;
    }


    public void setUnitId(int unitId) {
        this.unitId = unitId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setMaxDaily(int maxDaily) {
        this.maxDaily = maxDaily;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public String getJobUUID() {
        return jobUUID;
    }

    public void setJobUUID(String jobUUID) {
        this.jobUUID = jobUUID;
    }

    public String getUnitUUID() {
        return unitUUID;
    }

    public void setUnitUUID(String unitUUID) {
        this.unitUUID = unitUUID;
    }
}
