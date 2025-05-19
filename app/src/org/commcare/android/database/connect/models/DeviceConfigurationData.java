package org.commcare.android.database.connect.models;

public class DeviceConfigurationData {

    // Singleton instance
    private static DeviceConfigurationData instance;

    // Fields from various payloads
    private String requiredLock; // "device" or "biometric"
    private Boolean demoUser;
    private String token;
    private String failureCode;
    private String failureSubcode;

    private Boolean accountExists;
    private String photoBase64;

    private String username;
    private String dbKey;
    private String oauthPassword;
    private Boolean accountOrphaned;

    // Private constructor
    private DeviceConfigurationData() {
    }

    // Singleton accessor
    public static synchronized DeviceConfigurationData getInstance() {
        if (instance == null) {
            instance = new DeviceConfigurationData();
        }
        return instance;
    }

    // Getters and setters
    public String getRequiredLock() {
        return requiredLock;
    }

    public void setRequiredLock(String requiredLock) {
        this.requiredLock = requiredLock;
    }

    public Boolean getDemoUser() {
        return demoUser;
    }

    public void setDemoUser(Boolean demoUser) {
        this.demoUser = demoUser;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
    }

    public String getFailureSubcode() {
        return failureSubcode;
    }

    public void setFailureSubcode(String failureSubcode) {
        this.failureSubcode = failureSubcode;
    }

    public Boolean getAccountExists() {
        return accountExists;
    }

    public void setAccountExists(Boolean accountExists) {
        this.accountExists = accountExists;
    }

    public String getPhotoBase64() {
        return photoBase64;
    }

    public void setPhotoBase64(String photoBase64) {
        this.photoBase64 = photoBase64;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDbKey() {
        return dbKey;
    }

    public void setDbKey(String dbKey) {
        this.dbKey = dbKey;
    }

    public String getOauthPassword() {
        return oauthPassword;
    }

    public void setOauthPassword(String oauthPassword) {
        this.oauthPassword = oauthPassword;
    }

    public Boolean getAccountOrphaned() {
        return accountOrphaned;
    }

    public void setAccountOrphaned(Boolean accountOrphaned) {
        this.accountOrphaned = accountOrphaned;
    }

    // Optional: Clear all data
    public void clear() {
        instance = null;
    }
}
