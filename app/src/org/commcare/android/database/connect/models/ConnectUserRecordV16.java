package org.commcare.android.database.connect.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.connect.ConnectConstants;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;

import java.util.Date;

@Table(ConnectUserRecordV16.STORAGE_KEY)
public class ConnectUserRecordV16 extends Persisted {

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

    @Deprecated
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

    @Deprecated
    @Persisting(11)
    @MetaField(META_SECONDARY_PHONE_VERIFIED)
    private boolean secondaryPhoneVerified;
    
    @Deprecated
    @Persisting(12)
    @MetaField(META_VERIFY_SECONDARY_PHONE_DATE)
    private Date verifySecondaryPhoneByDate;

    @Persisting(value = 13, nullable = true)
    private String photo;

    @Persisting(value = 14)
    private boolean isDemo;

    @Persisting(value = 15)
    private String requiredLock;

    public ConnectUserRecordV16() {
        registrationPhase = ConnectConstants.PERSONALID_NO_ACTIVITY;
        lastPasswordDate = new Date();
        connectTokenExpiration = new Date();
        secondaryPhoneVerified = true;
        verifySecondaryPhoneByDate = new Date();
        requiredLock = PersonalIdSessionData.PIN;
    }

    public static ConnectUserRecordV16 fromV14(ConnectUserRecordV14 oldRecord) {
        ConnectUserRecordV16 newRecord = new ConnectUserRecordV16();
        newRecord.userId = oldRecord.getUserId();
        newRecord.password = oldRecord.getPassword();
        newRecord.name = oldRecord.getName();
        newRecord.primaryPhone = oldRecord.getPrimaryPhone();
        newRecord.alternatePhone = "";
        newRecord.registrationPhase = oldRecord.getRegistrationPhase();
        newRecord.lastPasswordDate = oldRecord.getLastPasswordDate();
        newRecord.connectToken = oldRecord.getConnectToken();
        newRecord.connectTokenExpiration = oldRecord.getConnectTokenExpiration();
        newRecord.secondaryPhoneVerified = true;
        newRecord.photo = oldRecord.getPhoto();
        newRecord.isDemo = oldRecord.isDemo();
        newRecord.requiredLock = PersonalIdSessionData.PIN;
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

    public boolean isDemo() {
        return isDemo;
    }

    public String getPhoto() {
        return photo;
    }

    public String getRequiredLock() {
        return requiredLock;
    }
}
