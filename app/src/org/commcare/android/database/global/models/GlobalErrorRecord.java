package org.commcare.android.database.global.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;

import java.util.Date;

@Table(GlobalErrorRecord.STORAGE_KEY)
public class GlobalErrorRecord extends Persisted {
    public static final String STORAGE_KEY = "global_error";

    public static final String DATE = "date";
    public static final String ERROR_CODE = "error_code";

    @Persisting(1)
    @MetaField(DATE)
    private Date date;

    @Persisting(2)
    @MetaField(ERROR_CODE)
    private int errorCode;

    public GlobalErrorRecord()
    {
        //Required empty constructor
    }

    public GlobalErrorRecord(Date date, int errorCode) {
        this.date = date;
        this.errorCode = errorCode;
    }

    public Date getDate() {
        return date;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
