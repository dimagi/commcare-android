package org.commcare.android.tests.initialization;

import org.commcare.android.CommCareTestRunner;
import org.commcare.android.database.DbUtil;
import org.commcare.android.util.LivePrototypeFactory;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.suite.model.Profile;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.reference.ResourceReferenceFactory;
import org.javarosa.core.util.externalizable.PrototypeFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/**
 * Tests that use the ability to install a CommCare app and login as a test
 * user.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
@Config(application = org.commcare.dalvik.application.CommCareApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class AppInitializationTest {

    @Before
    public void setup() {
        // needed to resolve "jr://resource" type references
        ReferenceManager._().addReferenceFactory(new ResourceReferenceFactory());

        setupPrototypeFactory();

        TestAppInstaller appTestInstaller =
                new TestAppInstaller("jr://resource/commcare-apps/flipper/profile.ccpr",
                        "fp", "123");
        appTestInstaller.installAppAndLogin();
    }

    private void setupPrototypeFactory() {
        // Sets DB to use an in-memory store for class serialization tagging.
        // This avoids the need to use apk reflection to perform read/writes
        LivePrototypeFactory prototypeFactory = new LivePrototypeFactory();
        PrototypeFactory.setStaticHasher(prototypeFactory);
        DbUtil.setDBUtilsPrototypeFactory(prototypeFactory);
    }

    @Test
    public void testAppInit() {
        Assert.assertFalse(CommCareApplication._().isUpdatePending());

        Profile p = CommCareApplication._().getCommCarePlatform().getCurrentProfile();
        Assert.assertTrue(p.getVersion() == 17);
    }
}
