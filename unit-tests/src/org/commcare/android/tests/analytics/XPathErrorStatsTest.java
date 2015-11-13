package org.commcare.android.tests.analytics;

import org.commcare.android.CommCareTestRunner;
import org.commcare.android.analytics.XPathErrorEntry;
import org.commcare.android.analytics.XPathErrorStats;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.dalvik.BuildConfig;
import org.javarosa.xpath.XPathTypeMismatchException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.ArrayList;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
@Config(application = org.commcare.dalvik.application.CommCareApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class XPathErrorStatsTest {

    @Before
    public void setup() {
        TestAppInstaller.initInstallAndLogin(
                "jr://resource/commcare-apps/archive_form_tests/profile.ccpr",
                "test", "123");
    }

    @Test
    public void testXPathErrorCollection() {
        XPathTypeMismatchException typeError = new XPathTypeMismatchException("");
        final String sourceRef = "/data/bad_data";
        typeError.setSource(sourceRef);
        XPathErrorStats.logErrorToCurrentApp(typeError);
        final String expectedMessage =
                "The problem was located in " + sourceRef + "\n XPath evaluation: type mismatch";

        ArrayList<XPathErrorEntry> errors = XPathErrorStats.getErrors();
        Assert.assertEquals(1, errors.size());
        XPathErrorEntry firstError = errors.get(0);
        Assert.assertEquals("test", firstError.username);
        Assert.assertEquals("/data/bad_data", firstError.expression);
        Assert.assertEquals(expectedMessage, firstError.errorMessage);
        Assert.assertEquals("", firstError.sessionFramePath);

        XPathErrorStats.clearStats();
        errors = XPathErrorStats.getErrors();
        Assert.assertEquals(0, errors.size());
    }
}



