package org.commcare.android.database.connect.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.connect.ConnectConstants;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

/**
 * DB model for a ConnectID user and their info
 *
 * @author dviggiano
 */
@Table(ConnectUserRecord.STORAGE_KEY)
public class ConnectUserRecord extends Persisted {
    /**
     * Name of database that stores Connect user records
     */
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
    /**
     * @Deprecated should no longer be used, just here to avoid a db migration for the moment
     */
    private boolean secondaryPhoneVerified;

    @Persisting(12)
    @MetaField(META_VERIFY_SECONDARY_PHONE_DATE)
    @Deprecated
    /**
     * @Deprecated should no longer be used, just here to avoid a db migration for the moment
     */
    private Date verifySecondaryPhoneByDate;

    @Persisting(value = 13, nullable = true)
    private String photo;

    @Persisting(value = 14)
    private boolean isDemo;

    @Persisting(value = 15)
    private String requiredLock = PersonalIdSessionData.PIN;

    @Persisting(value = 16)
    private boolean hasConnectAccess;

    public ConnectUserRecord() {
        registrationPhase = ConnectConstants.PERSONALID_NO_ACTIVITY;
        lastPasswordDate = new Date();
        connectTokenExpiration = new Date();
        secondaryPhoneVerified = true;
        verifySecondaryPhoneByDate = new Date();
        alternatePhone = "";
    }

    public ConnectUserRecord(String primaryPhone, String userId, String password, String name, String pin,
                             Date lastPinVerifyDate, String photo, boolean isDemo,String requiredLock, boolean hasConnectAccess) {
        this();
        this.primaryPhone = primaryPhone;
        this.userId = userId;
        this.password = password;
        this.name = name;
        this.pin = pin;
        this.lastPasswordDate = lastPinVerifyDate;
        this.photo = photo;
        this.isDemo = isDemo;
        connectTokenExpiration = new Date();
        this.requiredLock = requiredLock;
        this.hasConnectAccess = hasConnectAccess;
    }

    public String getUserId() {
        return userId;
    }

    public String getPrimaryPhone() {
        return primaryPhone;
    }

    public void setPrimaryPhone(String primaryPhone) {
        this.primaryPhone = primaryPhone;
    }

    public void setPin(String pin) {
        this.pin = pin;
    }

    public String getPin() {
        return pin;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getRegistrationPhase() {
        return registrationPhase;
    }

    public void setRegistrationPhase(int phase) {
        registrationPhase = phase;
    }

    public Date getLastPinDate() {
        return lastPasswordDate;
    }

    public void setLastPinDate(Date date) {
        lastPasswordDate = date;
    }


    public boolean shouldForcePin() {
        return shouldForceRecoveryLogin() && pin != null && pin.length() > 0;
    }

    private boolean shouldForceRecoveryLogin() {
        Date pinDate = getLastPinDate();
        boolean forcePin = pinDate == null;
        if (!forcePin) {
            //See how much time has passed since last PIN login
            long millis = (new Date()).getTime() - pinDate.getTime();
            long days = TimeUnit.DAYS.convert(millis, TimeUnit.MILLISECONDS);
            forcePin = days >= 7;
        }

        return forcePin;
    }

    public void updateConnectToken(String token, Date expirationDate) {
        connectToken = token;
        connectTokenExpiration = expirationDate;
    }

    public void clearConnectToken() {
        connectToken = null;
        connectTokenExpiration = new Date();
    }

    public String getConnectToken() {
        return connectToken;
    }

    public Date getConnectTokenExpiration() {
        return connectTokenExpiration;
    }

    public static ConnectUserRecord fromV16(ConnectUserRecordV16 oldRecord, boolean hasConnectAccess) {
        ConnectUserRecord newRecord = new ConnectUserRecord();
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
        newRecord.requiredLock = oldRecord.getRequiredLock();
        newRecord.hasConnectAccess = hasConnectAccess;
        return newRecord;
    }

    public void setPhoto(String photo) {
        this.photo = photo;
    }

    @Nullable
    public String getPhoto() {
        return photo;
    }

    public String getRequiredLock() {
        return requiredLock;
    }
}
