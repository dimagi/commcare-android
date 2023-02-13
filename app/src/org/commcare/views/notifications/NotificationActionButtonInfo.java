package org.commcare.views.notifications;

public class NotificationActionButtonInfo {
    private String text;
    private ButtonAction action;

    public enum ButtonAction {
        NONE,
        LAUNCH_DATE_SETTINGS
    }

    public NotificationActionButtonInfo(String buttonText, ButtonAction buttonAction) {
        text = buttonText;
        action = buttonAction;
    }

    public String getButtonText() {
        return text;
    }

    public ButtonAction getButtonAction() {
        return action;
    }
}
