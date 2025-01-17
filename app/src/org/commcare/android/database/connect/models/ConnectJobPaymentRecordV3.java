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

@Table(ConnectJobPaymentRecordV3.STORAGE_KEY)
public class ConnectJobPaymentRecordV3 extends Persisted implements Serializable {
    /**
     * Name of database that stores app info for Connect jobs
     */
    public static final String STORAGE_KEY = "connect_payments";

    public static final String META_JOB_ID = "job_id";
    public static final String META_AMOUNT = "amount";
    public static final String META_DATE = "date_paid";

    @Persisting(1)
    @MetaField(META_JOB_ID)
    private int jobId;
    @Persisting(2)
    @MetaField(META_DATE)
    private Date date;
    @Persisting(3)
    @MetaField(META_AMOUNT)
    private String amount;

    public ConnectJobPaymentRecordV3() {}

    public int getJobId() { return jobId; }

    public Date getDate() { return date;}

    public String getAmount() { return amount; }
}
