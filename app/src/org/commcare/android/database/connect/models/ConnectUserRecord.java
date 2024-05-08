package org.commcare.android.database.connect.models;

import android.content.Intent;

import org.commcare.activities.connect.ConnectConstants;
import org.commcare.activities.connect.ConnectTask;
import org.commcare.android.storage.framework.Persisted;
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
    public static final String META_REGISTRATION_DATE = "registration_date";

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
    @Persisting(value=10, nullable = true)
    @MetaField(META_PIN)
    private String pin;
    @Persisting(11)
    @MetaField(META_SECONDARY_PHONE_VERIFIED)
    private boolean secondaryPhoneVerified;

    @Persisting(12)
    @MetaField(META_REGISTRATION_DATE)
    private Date registrationDate;

    public ConnectUserRecord() {
        registrationPhase = ConnectTask.CONNECT_NO_ACTIVITY.getRequestCode();
        lastPasswordDate = new Date();
        connectTokenExpiration = new Date();
        registrationDate = new Date();
    }

    public ConnectUserRecord(String primaryPhone, String userId, String password, String name,
                             String alternatePhone) {
        this();
        this.primaryPhone = primaryPhone;
        this.alternatePhone = alternatePhone;
        this.userId = userId;
        this.password = password;
        this.name = name;

        connectTokenExpiration = new Date();
    }

    public static ConnectUserRecord getUserFromIntent(Intent intent) {
        return new ConnectUserRecord(
                intent.getStringExtra(ConnectConstants.PHONE),
                intent.getStringExtra(ConnectConstants.USERNAME),
                intent.getStringExtra(ConnectConstants.PASSWORD),
                intent.getStringExtra(ConnectConstants.NAME),
                intent.getStringExtra(ConnectConstants.ALT_PHONE));
    }

    public void putUserInIntent(Intent intent) {
        intent.putExtra(ConnectConstants.PHONE, primaryPhone);
        intent.putExtra(ConnectConstants.USERNAME, userId);
        intent.putExtra(ConnectConstants.PASSWORD, password);
        intent.putExtra(ConnectConstants.NAME, name);
        intent.putExtra(ConnectConstants.ALT_PHONE, alternatePhone);
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
    public void setPin(String pin) { this.pin = pin; }
    public String getPin() { return pin; }

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

    public ConnectTask getRegistrationPhase() {
        return ConnectTask.fromRequestCode(registrationPhase);
    }

    public void setRegistrationPhase(ConnectTask phase) {
        registrationPhase = phase.getRequestCode();
    }

    public Date getLastPinDate() {
        return lastPasswordDate;
    }
    public void setLastPinDate(Date date) { lastPasswordDate = date; }

    public boolean getSecondaryPhoneVerified() {
        return secondaryPhoneVerified;
    }
    public void setSecondaryPhoneVerified(boolean verified) { secondaryPhoneVerified = verified; }

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
        if(secondaryPhoneVerified) {
            return false;
        }

        long millis = (new Date()).getTime() - registrationDate.getTime();
        long days = TimeUnit.DAYS.convert(millis, TimeUnit.MILLISECONDS);
        return days >= 7;
    }

    public void updateConnectToken(String token, Date expirationDate) {
        connectToken = token;
        connectTokenExpiration = expirationDate;
    }

    public String getConnectToken() {
        return connectToken;
    }

    public Date getConnectTokenExpiration() {
        return connectTokenExpiration;
    }

    public Date getRegistrationDate() {
        return registrationDate;
    }

    public static ConnectUserRecord fromV5(ConnectUserRecordV5 oldRecord) {
        ConnectUserRecord newRecord = new ConnectUserRecord();

        newRecord.userId = oldRecord.getUserId();
        newRecord.password = oldRecord.getPassword();
        newRecord.name = oldRecord.getName();
        newRecord.primaryPhone = oldRecord.getPrimaryPhone();
        newRecord.alternatePhone = oldRecord.getAlternatePhone();
        newRecord.registrationPhase = oldRecord.getRegistrationPhase().getRequestCode();
        newRecord.lastPasswordDate = oldRecord.getLastPasswordDate();
        newRecord.connectToken = oldRecord.getConnectToken();
        newRecord.connectTokenExpiration = oldRecord.getConnectTokenExpiration();
        newRecord.secondaryPhoneVerified = false;

        return newRecord;
    }
}
