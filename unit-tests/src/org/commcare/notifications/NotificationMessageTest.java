package org.commcare.notifications;

import android.os.Parcel;

import org.commcare.CommCareTestApplication;
import org.commcare.android.CommCareTestRunner;
import org.commcare.dalvik.BuildConfig;
import org.commcare.views.notifications.NotificationMessage;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.Date;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
@Config(application = CommCareTestApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class NotificationMessageTest {
    /**
     * Write NotificationMessage to parcel and read it back out again
     */
    @Test
    public void notificationParcellingTest() {
        NotificationMessage sampleNotification =
                new NotificationMessage("ctx", "title", "message", "action", new Date());
        Parcel parcel = Parcel.obtain();
        sampleNotification.writeToParcel(parcel, 0);

        parcel.setDataPosition(0);

        NotificationMessage createdFromParcel =
                NotificationMessage.CREATOR.createFromParcel(parcel);
        Assert.assertEquals(sampleNotification, createdFromParcel);
    }
}
