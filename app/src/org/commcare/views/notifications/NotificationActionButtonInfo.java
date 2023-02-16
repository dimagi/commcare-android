package org.commcare.views.notifications;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Date;

public class NotificationActionButtonInfo implements Parcelable {
    private final String text;
    private final ButtonAction action;

    public enum ButtonAction {
        NONE,
        LAUNCH_DATE_SETTINGS
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        String buttonText = getButtonText();
        String buttonAction = getButtonAction().name();
        dest.writeStringArray(new String[]{buttonText, buttonAction});
    }

    public static final Creator<NotificationActionButtonInfo> CREATOR = new Creator<>() {
        @Override
        public NotificationActionButtonInfo createFromParcel(Parcel source) {
            String[] array = new String[2];
            source.readStringArray(array);

            return new NotificationActionButtonInfo(array[0], ButtonAction.valueOf(array[1]));
        }

        @Override
        public NotificationActionButtonInfo[] newArray(int size) {
            return new NotificationActionButtonInfo[size];
        }
    };

    public NotificationActionButtonInfo(String buttonText, ButtonAction buttonAction) {
        text = buttonText;
        action = buttonAction;

        if (buttonText == null) {
            throw new NullPointerException("NotificationActionButtonInfo can not have null button text");
        }
    }

    public String getButtonText() {
        return text;
    }

    public ButtonAction getButtonAction() {
        return action;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof NotificationActionButtonInfo bi)) {
            return false;
        }

        if(!bi.getButtonText().equals(this.getButtonText())) {
            return false;
        }

        return bi.getButtonAction().equals(this.getButtonAction());
    }
}
