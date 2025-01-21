package org.commcare.android.database.connect.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.connect.ConnectConstants;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;
import org.jetbrains.annotations.NotNull;

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

    /** The user's unique ConnectID */
    @Persisting(1)
    private String userId;

    /** Auto-generated password used for communicating with the ConnectID server */
    @Persisting(2)
    private String password;

    /** The user's display name */
    @Persisting(3)
    private String name;

    /** The user's primary phone number */
    @Persisting(4)
    private String primaryPhone;

    /** The user's alternate phone number (for account recovery purposes) */
    @Persisting(5)
    private String alternatePhone;

    /** Stored phase during registration so the app can resume if interrupted (i.e. device restarted) */
    @Persisting(6)
    private int registrationPhase;

    /** OBSOLETE: Date when the user's password was last set */
    @Persisting(7)
    private Date lastPasswordDate;

    /** Token for authenticating with Connect server */
    @Persisting(value = 8, nullable = true)
    private String connectToken;

    /** Date that the Conncet token expires and needs to be renewed */
    @Persisting(value = 9, nullable = true)
    private Date connectTokenExpiration;

    /** The user's chosen 6-digit recovery code */
    @Persisting(value=10, nullable = true)
    @MetaField(META_PIN)
    private String pin;

    /** Whether the user has verified their secondary phone number */
    @Persisting(11)
    @MetaField(META_SECONDARY_PHONE_VERIFIED)
    private boolean secondaryPhoneVerified;

    /** Date that the user needs to verify their secondary phone by (before being blocked) */
    @Persisting(12)
    @MetaField(META_VERIFY_SECONDARY_PHONE_DATE)
    private Date verifySecondaryPhoneByDate;

    /** Official name of the user for their digital payment account */
    @Persisting(13)
    private String paymentName;

    /** Phone number for the user's digital payment account */
    @Persisting(14)
    private String paymentPhone;

    public ConnectUserRecord() {
        registrationPhase = ConnectConstants.CONNECT_NO_ACTIVITY;
        lastPasswordDate = new Date();
        connectTokenExpiration = new Date();
        secondaryPhoneVerified = true;
        verifySecondaryPhoneByDate = new Date();
    }

    public ConnectUserRecord(@NotNull String primaryPhone, @NotNull String userId,
                             @NotNull String password, @NotNull String name,
                             @NotNull String alternatePhone, @NotNull String paymentName,
                             @NotNull String paymentPhone) {
        this();
        this.primaryPhone = primaryPhone;
        this.alternatePhone = alternatePhone;
        this.userId = userId;
        this.password = password;
        this.name = name;
        this.paymentName = paymentName;
        this.paymentPhone = paymentPhone;

        connectTokenExpiration = new Date();
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

    public int getRegistrationPhase() {
        return registrationPhase;
    }

    public void setRegistrationPhase(int phase) {
        registrationPhase = phase;
    }

    public Date getLastPinDate() {
        return lastPasswordDate;
    }
    public void setLastPinDate(Date date) { lastPasswordDate = date; }

    public boolean getSecondaryPhoneVerified() {
        return secondaryPhoneVerified;
    }
    public void setSecondaryPhoneVerified(boolean verified) { secondaryPhoneVerified = verified; }
    public Date getSecondaryPhoneVerifyByDate() {
        return verifySecondaryPhoneByDate;
    }
    public void setSecondaryPhoneVerifyByDate(Date date) { verifySecondaryPhoneByDate = date; }

    public String getPaymentName() {
        return paymentName;
    }

    public void setPaymentName(String paymentName) {
        this.paymentName = paymentName;
    }

    public String getPaymentPhone() {
        return paymentPhone;
    }

    public void setPaymentPhone(String paymentPhone) {
        this.paymentPhone = paymentPhone;
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
        if(secondaryPhoneVerified) {
            return false;
        }

        return (new Date()).after(verifySecondaryPhoneByDate);
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

    public static ConnectUserRecord fromV5(ConnectUserRecordV5 oldRecord) {
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

        return newRecord;
    }
}