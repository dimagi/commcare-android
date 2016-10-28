package org.commcare.utils;

import android.os.Handler;
import android.os.Message;
import android.widget.Toast;

import org.commcare.CommCareApplication;
import org.commcare.views.notifications.NotificationMessage;
import org.javarosa.core.services.locale.Localization;

import java.lang.ref.WeakReference;

/**
 * Message handler that pops-up notifications to the user via toast.
 */
public class PopupHandler extends Handler {
    /**
     * Reference to the context used to show pop-ups (the parent class).
     * Reference is weak to avoid memory leaks.
     */
    private final WeakReference<CommCareApplication> mActivity;

    /**
     * @param activity Is the context used to pop-up the toast message.
     */
    public PopupHandler(CommCareApplication activity) {
        mActivity = new WeakReference<>(activity);
    }

    /**
     * Pops up the message to the user by way of toast
     *
     * @param m Has a 'message' parcel storing pop-up message text
     */
    @Override
    public void handleMessage(Message m) {
        NotificationMessage message = m.getData().getParcelable("message");

        CommCareApplication activity = mActivity.get();

        if (activity != null && message != null) {
            Toast.makeText(activity,
                    Localization.get("notification.for.details.wrapper",
                            new String[]{message.getTitle()}),
                    Toast.LENGTH_LONG).show();
        }
    }
}
