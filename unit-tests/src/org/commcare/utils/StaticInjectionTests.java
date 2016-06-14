package org.commcare.utils;

import org.commcare.CommCareApplication;
import org.commcare.android.CommCareTestRunner;
import org.commcare.dalvik.BuildConfig;
import org.javarosa.core.util.DataUtil;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.Arrays;

/**
 * Test that the output of the j2me- and Android- implementations of methods that use static
 * injection for the Android version have identical behavior
 *
 * Created by amstone326 on 6/14/16.
 */
@Config(application = CommCareApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class StaticInjectionTests {

    @Test
    public void testStringSplitterEquivalence() {
        String basicString = "hello my name is Sam";
        String noDelimitersString = "hellomynameisSam";
        String multipleDelimitersString = "hello   my  name is    Sam";
        String emptyString = "";

        Assert.assertTrue(identicalResults(basicString));
        Assert.assertTrue(identicalResults(noDelimitersString));
        Assert.assertTrue(identicalResults(multipleDelimitersString));
        Assert.assertTrue(identicalResults(emptyString));
    }

    private static boolean identicalResults(String testString) {
        return Arrays.equals(getAndroidResult(testString), getJ2MEResult(testString));
    }

    private static String[] getAndroidResult(String s) {
        return (new AndroidUtil.AndroidStringSplitter()).splitOnSpaces(s);
    }

    private static String[] getJ2MEResult(String s) {
        return (new DataUtil.StringSplitter()).splitOnSpaces(s);
    }

}
