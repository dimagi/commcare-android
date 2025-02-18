package org.commcare.android.database.connect.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.text.ParseException;

@Table(ConnectPaymentUnitRecord.STORAGE_KEY)
public class ConnectPaymentUnitRecord extends Persisted implements Serializable {
    /**
     * Name of database that stores Connect payment units
     */
    public static final String STORAGE_KEY = "connect_payment_units";

    public static final String META_JOB_ID = "job_id";
    public static final String META_UNIT_ID = "unit_id";
    public static final String META_NAME = "name";
    public static final String META_TOTAL = "max_total";
    public static final String META_DAILY = "max_daily";
    public static final String META_AMOUNT = "amount";

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

    public ConnectPaymentUnitRecord() {

    }

    public static ConnectPaymentUnitRecord fromJson(JSONObject json, int jobId) throws JSONException, ParseException {
        ConnectPaymentUnitRecord paymentUnit = new ConnectPaymentUnitRecord();

        paymentUnit.jobId = jobId;
        paymentUnit.unitId = json.getInt(META_UNIT_ID);
        paymentUnit.name = json.getString(META_NAME);
        paymentUnit.maxTotal = json.getInt(META_TOTAL);
        paymentUnit.maxDaily = json.getInt(META_DAILY);
        paymentUnit.amount = json.getInt(META_AMOUNT);

        return paymentUnit;
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
}
