package org.commcare.android.database.connect.models;

import android.content.Intent;

import org.commcare.activities.connect.ConnectConstants;
import org.commcare.activities.connect.ConnectTask;
import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;

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

    private boolean linkOffered1;
    private Date linkOfferDate1;

    private boolean linkOffered2;
    private Date linkOfferDate2;

    public ConnectUserRecord() {
        registrationPhase = ConnectTask.CONNECT_NO_ACTIVITY.getRequestCode();
        lastPasswordDate = new Date();
        connectTokenExpiration = new Date();
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

    public Date getLastPasswordDate() {
        return lastPasswordDate;
    }

    public boolean shouldForcePassword() {
        Date passwordDate = getLastPasswordDate();
        boolean forcePassword = passwordDate == null;
        if (!forcePassword) {
            //See how much time has passed since last password login
            long millis = (new Date()).getTime() - passwordDate.getTime();
            long days = TimeUnit.DAYS.convert(millis, TimeUnit.MILLISECONDS);
            forcePassword = days >= 7;
        }

        return forcePassword;
    }

    public void setLastPasswordDate(Date date) {
        lastPasswordDate = date;
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

    public Date getLinkOfferDate1() {
        return linkOffered1 ? linkOfferDate1 : null;
    }
    public void setLinkOfferDate1(Date date) {
        linkOffered1 = true;
        linkOfferDate1 = date;
    }

    public Date getLinkOfferDate2() {
        return linkOffered2 ? linkOfferDate2 : null;
    }
    public void setLinkOfferDate2(Date date) {
        linkOffered2 = true;
        linkOfferDate2 = date;
    }
}
