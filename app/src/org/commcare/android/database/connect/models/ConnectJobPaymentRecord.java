package org.commcare.android.database.connect.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;
import org.javarosa.core.model.utils.DateUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.text.ParseException;
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
    private static final long CONFIRMATION_WINDOW_DAYS = 7;
    private static final long UNDO_WINDOW_DAYS = 1;

    @Persisting(1)
    @MetaField(META_JOB_ID)
    private int jobId;

    /**
     * Date is used to tell when the payment is created
     */
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
    /**
     * Confirm Date is used to tell when the worker has confirmed this payment is done
     */
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

    public static ConnectJobPaymentRecord fromJson(JSONObject json, int jobId) throws JSONException {
        ConnectJobPaymentRecord payment = new ConnectJobPaymentRecord();

        payment.jobId = jobId;
        payment.date = DateUtils.parseDateTime(json.getString(META_DATE));
        payment.amount = String.valueOf(json.getInt(META_AMOUNT));
        payment.paymentId = json.getString("id");
        payment.confirmed = json.optBoolean(META_CONFIRMED,false);
        try {
            payment.confirmedDate = json.has(META_CONFIRMED_DATE) && !json.isNull(META_CONFIRMED_DATE) ?
                    DateUtils.parseDate(json.getString(META_CONFIRMED_DATE)) : new Date();
        } catch (Exception e) {
            throw new JSONException("Error parsing confirmed date: " + e.getMessage());
        }

        return payment;
    }

    public String getPaymentId() {return paymentId; }
    public Date getDate() { return date;}

    public String getAmount() { return amount; }

    public boolean getConfirmed() {return confirmed; }
    public Date getConfirmedDate(){
        if(!confirmed){
            return null;
        }
        return confirmedDate;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
        if(confirmed) {
            confirmedDate = new Date();
        }
    }

    /**
     * Checks if the payment can be confirmed based on business rules:
     * - Payment must not be already confirmed
     * - Payment must be within 7 days of the payment date
     */
    public boolean allowConfirm() {
        if (confirmed) {
            return false;
        }
        long millis = (new Date()).getTime() - date.getTime();
        long days = TimeUnit.DAYS.convert(millis, TimeUnit.MILLISECONDS);
        return days < CONFIRMATION_WINDOW_DAYS;
    }
    /**
     * Checks if a confirmed payment can have its confirmation undone:
     * - Payment must be confirmed
     * - Must be within 24 hours of confirmation
     */
    public boolean allowConfirmUndo() {
        if (!confirmed) {
            return false;
        }
        long millis = (new Date()).getTime() - confirmedDate.getTime();
        long days = TimeUnit.DAYS.convert(millis, TimeUnit.MILLISECONDS);
        return days < UNDO_WINDOW_DAYS;
    }
}
