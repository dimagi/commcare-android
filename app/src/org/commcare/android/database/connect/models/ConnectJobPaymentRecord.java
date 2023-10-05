package org.commcare.android.database.connect.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

@Table(ConnectJobPaymentRecord.STORAGE_KEY)
public class ConnectJobPaymentRecord extends Persisted implements Serializable {
    /**
     * Name of database that stores app info for Connect jobs
     */
    public static final String STORAGE_KEY = "connect_payments";

    public static final String META_JOB_ID = "job_id";
    public static final String META_AMOUNT = "amount";
    public static final String META_DATE = "date";

    @Persisting(1)
    @MetaField(META_JOB_ID)
    private int jobId;

    @Persisting(2)
    @MetaField(META_DATE)
    private Date date;

    @Persisting(3)
    @MetaField(META_AMOUNT)
    private String amount;

    public ConnectJobPaymentRecord() {}

    public static ConnectJobPaymentRecord fromJson(JSONObject json, int jobId) throws JSONException, ParseException {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        ConnectJobPaymentRecord payment = new ConnectJobPaymentRecord();

        payment.jobId = jobId;
        payment.date = json.has(META_DATE) ? df.parse(json.getString(META_DATE)) : new Date();
        payment.amount = String.format(Locale.getDefault(), "%.02f", json.has(META_AMOUNT) ? json.getDouble(META_AMOUNT) : 0);

        return payment;
    }

    public Date getDate() { return date;}

    public String getAmount() { return amount; }
}
