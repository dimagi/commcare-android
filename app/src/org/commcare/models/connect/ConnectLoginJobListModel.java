package org.commcare.models.connect;

import org.commcare.android.database.connect.models.ConnectJobRecord;

import java.util.Date;

/**
 * Model class for storing list item data
 */
public class ConnectLoginJobListModel {
    public enum JobListEntryType {
        LEARNING,
        DELIVERY,
        NEW_OPPORTUNITY
    }
    private String name;
    private String id;
    private String appId;
    private Date date;
    private String description;
    private String organization;
    private boolean isAppInstalled;
    private boolean isNew;
    private boolean isLearningApp;
    private boolean isDeliveryApp;
    private Date lastAccessed;
    private int learningProgress;
    private int deliveryProgress;
    private JobListEntryType jobType;
    private String appType;
    private ConnectJobRecord job;

    // Constructor
    public ConnectLoginJobListModel(
            String name,
            String id,
            String appId,
            Date date,
            String description,
            String organization,
            boolean isAppInstalled,
            boolean isNew,
            boolean isLearningApp,
            boolean isDeliveryApp,
            Date lastAccessed,
            int learningProgress,
            int deliveryProgress,
            JobListEntryType jobType,
            String appType,
            ConnectJobRecord job
    ) {
        this.name = name;
        this.id = id;
        this.appId = appId;
        this.date = date;
        this.description = description;
        this.organization = organization;
        this.isAppInstalled = isAppInstalled;
        this.isNew = isNew;
        this.isLearningApp = isLearningApp;
        this.isDeliveryApp = isDeliveryApp;
        this.lastAccessed = lastAccessed;
        this.learningProgress = learningProgress;
        this.deliveryProgress = deliveryProgress;
        this.jobType = jobType;
        this.appType = appType;
        this.job = job;
    }

    // Default constructor
    public ConnectLoginJobListModel() {
    }

    public ConnectLoginJobListModel(
            String name,
            ConnectJobRecord job
    ) {
        this.name = name;
        this.job = job;
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public boolean isAppInstalled() {
        return isAppInstalled;
    }

    public void setAppInstalled(boolean appInstalled) {
        isAppInstalled = appInstalled;
    }

    public boolean isNew() {
        return isNew;
    }

    public void setNew(boolean aNew) {
        isNew = aNew;
    }

    public boolean isLearningApp() {
        return isLearningApp;
    }

    public void setLearningApp(boolean learningApp) {
        isLearningApp = learningApp;
    }

    public boolean isDeliveryApp() {
        return isDeliveryApp;
    }

    public void setDeliveryApp(boolean deliveryApp) {
        isDeliveryApp = deliveryApp;
    }

    public Date getLastAccessed() {
        return lastAccessed;
    }

    public void setLastAccessed(Date lastAccessed) {
        this.lastAccessed = lastAccessed;
    }

    public int getLearningProgress() {
        return learningProgress;
    }

    public void setLearningProgress(int learningProgress) {
        this.learningProgress = learningProgress;
    }

    public int getDeliveryProgress() {
        return deliveryProgress;
    }

    public void setDeliveryProgress(int deliveryProgress) {
        this.deliveryProgress = deliveryProgress;
    }

    public JobListEntryType getJobType() {
        return jobType;
    }

    public void setJobType(JobListEntryType jobType) {
        this.jobType = jobType;
    }

    public String getAppType() {
        return appType;
    }

    public void setAppType(String appType) {
        this.appType = appType;
    }

    public ConnectJobRecord getJob() {
        return job;
    }

    public void setJob(ConnectJobRecord job) {
        this.job = job;
    }

    // Optionally, override toString for easy logging or display
    @Override
    public String toString() {
        return "AddListItem{" +
                "name='" + name + '\'' +
                ", id=" + id +
                ", date=" + date +
                ", description='" + description + '\'' +
                ", organization='" + organization + '\'' +
                '}';
    }
}
