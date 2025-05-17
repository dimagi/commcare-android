package org.commcare.android.database.connect.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.connect.ConnectConstants;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;

import java.util.Date;

@Table(ConnectUserRecordV13.STORAGE_KEY)
public class ConnectUserRecordV13 extends Persisted {

    public static final String STORAGE_KEY = "user_info";
    public static final String META_PIN = "pin";
    public static final String META_SECONDARY_PHONE_VERIFIED = "secondary_phone_verified";
    public static final String META_VERIFY_SECONDARY_PHONE_DATE = "verify_secondary_phone_by_date";

    @Persisting(1)
    private String userId;

    @Persisting(2)
    private String password;

    @Persisting(3)
    private String name;

    @Persisting(4)
    private String primaryPhone;

    @Persisting(5)
    private String alternatePhone;

    @Persisting(6)
    private int registrationPhase;

    @Persisting(7)
    private Date lastPasswordDate;

    @Persisting(value = 8, nullable = true)
    private String connectToken;

    @Persisting(value = 9, nullable = true)
    private Date connectTokenExpiration;
    @Persisting(value = 10, nullable = true)
    @MetaField(META_PIN)
    private String pin;
    @Persisting(11)
    @MetaField(META_SECONDARY_PHONE_VERIFIED)
    private boolean secondaryPhoneVerified;

    @Persisting(12)
    @MetaField(META_VERIFY_SECONDARY_PHONE_DATE)
    private Date verifySecondaryPhoneByDate;

    public ConnectUserRecordV13() {
        registrationPhase = ConnectConstants.PERSONALID_NO_ACTIVITY;
        lastPasswordDate = new Date();
        connectTokenExpiration = new Date();
        secondaryPhoneVerified = true;
        verifySecondaryPhoneByDate = new Date();
    }

    public static ConnectUserRecordV13 fromV5(ConnectUserRecordV5 oldRecord) {
        ConnectUserRecordV13 newRecord = new ConnectUserRecordV13();
        newRecord.userId = oldRecord.getUserId();
        newRecord.password = oldRecord.getPassword();
        newRecord.name = oldRecord.getName();
        newRecord.primaryPhone = oldRecord.getPrimaryPhone();
        newRecord.alternatePhone = oldRecord.getAlternatePhone();
        newRecord.registrationPhase = oldRecord.getRegistrationPhase();
        newRecord.lastPasswordDate = oldRecord.getLastPasswordDate();
        newRecord.connectToken = oldRecord.getConnectToken();
        newRecord.connectTokenExpiration = oldRecord.getConnectTokenExpiration();
        newRecord.secondaryPhoneVerified = true;
        return newRecord;
    }

    public String getUserId() {
        return userId;
    }

    public String getPassword() {
        return password;
    }

    public String getName() {
        return name;
    }

    public String getPrimaryPhone() {
        return primaryPhone;
    }

    public String getAlternatePhone() {
        return alternatePhone;
    }

    public int getRegistrationPhase() {
        return registrationPhase;
    }

    public Date getLastPasswordDate() {
        return lastPasswordDate;
    }

    public String getConnectToken() {
        return connectToken;
    }

    public Date getConnectTokenExpiration() {
        return connectTokenExpiration;
    }

    public String getPin() {
        return pin;
    }
}
