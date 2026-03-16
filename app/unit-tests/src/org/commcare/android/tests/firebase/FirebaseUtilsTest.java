package org.commcare.android.tests.firebase;

import static org.junit.Assert.assertEquals;

import org.commcare.dalvik.BuildConfig;
import org.commcare.utils.FirebaseUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.commcare.CommCareTestApplication;
import org.robolectric.annotation.Config;

@Config(application = CommCareTestApplication.class)
@RunWith(AndroidJUnit4.class)
public class FirebaseUtilsTest {

    @Test
    public void testIsFirebaseEnabled_matchesBuildConfig() {
        assertEquals(BuildConfig.FIREBASE_ENABLED, FirebaseUtils.isFirebaseEnabled());
    }
}
