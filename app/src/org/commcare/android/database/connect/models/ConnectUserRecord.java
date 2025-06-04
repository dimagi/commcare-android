package org.commcare.android.database.connect.models;

import android.content.Intent;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.connect.ConnectConstants;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;

import java.util.Date;
import java.util.concurrent.TimeUnit;

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

    @Persisting(value = 13, nullable = true)
    private String photo;

    @Persisting(value = 14)
    private boolean isDemo;

    @Persisting(value = 15)
    private String required_lock = PersonalIdSessionData.PIN;

    public ConnectUserRecord() {
        registrationPhase = ConnectConstants.PERSONALID_NO_ACTIVITY;
        lastPasswordDate = new Date();
        connectTokenExpiration = new Date();
        secondaryPhoneVerified = true;
        verifySecondaryPhoneByDate = new Date();
        alternatePhone = "";
    }

    public ConnectUserRecord(String primaryPhone, String userId, String password, String name, String pin,
                             Date lastPinVerifyDate, String photo, boolean isDemo,String required_lock) {
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
        this.required_lock = required_lock;
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

    public String getAlternatePhone() {
        return alternatePhone;
    }

    public void setAlternatePhone(String alternatePhone) {
        this.alternatePhone = alternatePhone;
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

    public boolean getSecondaryPhoneVerified() {
        return secondaryPhoneVerified;
    }

    public void setSecondaryPhoneVerified(boolean verified) {
        secondaryPhoneVerified = verified;
    }

    public Date getSecondaryPhoneVerifyByDate() {
        return  verifySecondaryPhoneByDate;
    }

    public void setSecondaryPhoneVerifyByDate(Date date) {
        verifySecondaryPhoneByDate = date;
    }

    public boolean shouldForcePin() {
        return shouldForceRecoveryLogin() && pin != null && pin.length() > 0;
    }

    public boolean shouldForcePassword() {
        return shouldForceRecoveryLogin() && !shouldForcePin();
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

    public boolean shouldRequireSecondaryPhoneVerification() {
        if (secondaryPhoneVerified) {
            return false;
        }

        return (new Date()).after(verifySecondaryPhoneByDate);
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

    public static ConnectUserRecord fromV14(ConnectUserRecordV14 oldRecord) {
        ConnectUserRecord newRecord = new ConnectUserRecord();
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
        newRecord.photo = oldRecord.getPhoto();
        newRecord.isDemo = oldRecord.isDemo();
        return newRecord;
    }

    public void setPhoto(String photo) {
        this.photo = photo;
    }

    public String getRequired_lock() {
        return required_lock;
    }
}
