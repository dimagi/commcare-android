package org.commcare.models.connect;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Date;

/**
 * Model class for storing list item data
 */
public class ConnectLoginJobListModel implements Parcelable {
    private String name;
    private int id;
    private Date date;
    private String description;
    private String organization;
    private boolean isAppInstalled;
    private boolean isNew;
    private boolean isLeaningApp;
    private boolean isDeliveryApp;

    // Constructor
    public ConnectLoginJobListModel(String name, int id, Date date, String description, String organization, boolean isAppInstalled, boolean isNew, boolean isLeaningApp,boolean isDeliveryApp) {
        this.name = name;
        this.id = id;
        this.date = date;
        this.description = description;
        this.organization = organization;
        this.isAppInstalled = isAppInstalled;
        this.isNew = isNew;
        this.isLeaningApp = isLeaningApp;
        this.isDeliveryApp = isDeliveryApp;
    }

    // Default constructor
    public ConnectLoginJobListModel() {
    }

    protected ConnectLoginJobListModel(Parcel in) {
        name = in.readString();
        id = in.readInt();
        description = in.readString();
        organization = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeInt(id);
        dest.writeString(description);
        dest.writeString(organization);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ConnectLoginJobListModel> CREATOR = new Creator<>() {
        @Override
        public ConnectLoginJobListModel createFromParcel(Parcel in) {
            return new ConnectLoginJobListModel(in);
        }

        @Override
        public ConnectLoginJobListModel[] newArray(int size) {
            return new ConnectLoginJobListModel[size];
        }
    };

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
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

    public boolean isLeaningApp() {
        return isLeaningApp;
    }

    public void setLeaningApp(boolean leaningApp) {
        isLeaningApp = leaningApp;
    }

    public boolean isDeliveryApp() {
        return isDeliveryApp;
    }

    public void setDeliveryApp(boolean deliveryApp) {
        isDeliveryApp = deliveryApp;
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
