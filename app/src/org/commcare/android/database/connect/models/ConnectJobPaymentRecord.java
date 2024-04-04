package org.commcare.android.database.connect.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Table(ConnectJobPaymentRecord.STORAGE_KEY)
public class ConnectJobPaymentRecord extends Persisted implements Serializable {
    /**
     * Name of database that stores app info for Connect jobs
     */
    public static final String STORAGE_KEY = "connect_payments";

    public static final String META_JOB_ID = "job_id";
    public static final String META_AMOUNT = "amount";
    public static final String META_DATE = "date_paid";
    public static final String META_PAYMENT_ID = "payment_id";
    public static final String META_CONFIRMED = "confirmed";
    public static final String META_CONFIRMED_DATE = "date_confirmed";

    @Persisting(1)
    @MetaField(META_JOB_ID)
    private int jobId;

    @Persisting(2)
    @MetaField(META_DATE)
    private Date date;

    @Persisting(3)
    @MetaField(META_AMOUNT)
    private String amount;

    @Persisting(4)
    @MetaField(META_PAYMENT_ID)
    private String paymentId;
    @Persisting(5)
    @MetaField(META_CONFIRMED)
    private boolean confirmed;

    @Persisting(6)
    @MetaField(META_CONFIRMED_DATE)
    private Date confirmedDate;

    public ConnectJobPaymentRecord() {}

    public static ConnectJobPaymentRecord fromV3(ConnectJobPaymentRecordV3 oldRecord) {
        ConnectJobPaymentRecord newRecord = new ConnectJobPaymentRecord();

        newRecord.jobId = oldRecord.getJobId();
        newRecord.date = oldRecord.getDate();
        newRecord.amount = oldRecord.getAmount();

        newRecord.paymentId = "-1";
        newRecord.confirmed = false;
        newRecord.confirmedDate = new Date();

        return newRecord;
    }

    public static ConnectJobPaymentRecord fromJson(JSONObject json, int jobId) throws JSONException, ParseException {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        ConnectJobPaymentRecord payment = new ConnectJobPaymentRecord();

        payment.jobId = jobId;
        payment.date = json.has(META_DATE) ? df.parse(json.getString(META_DATE)) : new Date();
        payment.amount = String.format(Locale.ENGLISH, "%d", json.has(META_AMOUNT) ? json.getInt(META_AMOUNT) : 0);

        payment.paymentId = json.has("id") ? json.getString("id") : "";
        payment.confirmed = json.has(META_CONFIRMED) && json.getBoolean(META_CONFIRMED);
        payment.confirmedDate = json.has(META_CONFIRMED_DATE) && !json.isNull(META_CONFIRMED_DATE) ? df.parse(json.getString(META_CONFIRMED_DATE)) : new Date();

        return payment;
    }

    public String getPaymentId() {return paymentId; }
    public Date getDate() { return date;}

    public String getAmount() { return amount; }

    public boolean getConfirmed() {return confirmed; }
    public Date getConfirmedDate() {return confirmedDate; }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
        if(confirmed) {
            confirmedDate = new Date();
        }
    }

    public boolean allowConfirm() {
        if (confirmed) {
            return false;
        }

        long millis = (new Date()).getTime() - date.getTime();
        long days = TimeUnit.DAYS.convert(millis, TimeUnit.MILLISECONDS);
        return days < 7;
    }

    public boolean allowConfirmUndo() {
        if (!confirmed) {
            return false;
        }

        long millis = (new Date()).getTime() - confirmedDate.getTime();
        long days = TimeUnit.DAYS.convert(millis, TimeUnit.MILLISECONDS);
        return days < 1;
    }
}
