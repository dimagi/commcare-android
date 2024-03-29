package org.commcare.views.notifications;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Date;

/**
 * Notification messages are messages which are intended to be displayed to end
 * users in a best-effort per-session manner. This class is the wrapper model
 * to contain and parcelize message details.
 *
 * @author ctsims
 */
public class NotificationMessage implements Parcelable {

    private String category, title, details, actions;
    private Date date;
    private NotificationActionButtonInfo buttonInfo;

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeStringArray(new String[]{category, title, details, actions});
        dest.writeLong(date.getTime());
        if(buttonInfo != null) {
            dest.writeParcelable(buttonInfo, flags);
        }
    }

    public static final Creator<NotificationMessage> CREATOR = new Creator<>() {

        @Override
        public NotificationMessage createFromParcel(Parcel source) {
            String[] array = new String[4];
            source.readStringArray(array);
            Date date = new Date(source.readLong());

            NotificationActionButtonInfo buttonInfo = source.readParcelable(NotificationActionButtonInfo.class.getClassLoader());

            return new NotificationMessage(array[0], array[1], array[2], array[3], date, buttonInfo);
        }

        @Override
        public NotificationMessage[] newArray(int size) {
            return new NotificationMessage[size];
        }
    };

    public NotificationMessage(String context, String title, String details, String action, Date date)  {
        this(context, title, details, action, date, null);
    }

    public NotificationMessage(String context, String title, String details, String action, Date date, NotificationActionButtonInfo buttonInfo) {
        if (context == null || title == null || details == null || date == null) {
            throw new NullPointerException("None of the arguments for creating a NotificationMessage may be null except for action and buttonInfo");
        }

        this.category = context;
        this.title = title;
        this.details = details;
        this.actions = action;
        this.date = date;
        this.buttonInfo = buttonInfo;
    }

    public String getTitle() {
        return title;
    }

    public String getCategory() {
        return category;
    }

    public String getDetails() {
        return details;
    }

    public NotificationActionButtonInfo getButtonInfo() {
        return buttonInfo;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof NotificationMessage nm)) {
            return false;
        }

        if (!nm.category.equals(category) && nm.title.equals(title) && nm.details.equals(details)) {
            return false;
        }

        if (nm.actions == null && this.actions != null) {
            return false;
        }
        if (nm.actions != null && !nm.actions.equals(this.actions)) {
            return false;
        }

        if((nm.buttonInfo == null) != (this.buttonInfo == null)) {
            //One has buttonInfo while the other doesn't
            return false;
        }
        if(nm.buttonInfo != null && !nm.buttonInfo.equals(this.buttonInfo)) {
            return false;
        }

        //Date is excluded from equality
        //if(!nm.date.equals(this.date));

        return true;
    }

    public Date getDate() {
        return date;
    }

    public String getAction() {
        return actions;
    }
}
