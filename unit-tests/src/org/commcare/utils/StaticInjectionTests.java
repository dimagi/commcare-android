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
    public void testSplitOnSpacesEquivalence() {
        String basicString = "hello my name is Sam";
        String noDelimitersString = "hellomynameisSam";
        String multipleDelimitersString = "hello   my  name is    Sam";
        String emptyString = "";

        Assert.assertTrue(identicalSplitOnSpacesResults(basicString));
        Assert.assertTrue(identicalSplitOnSpacesResults(noDelimitersString));
        Assert.assertTrue(identicalSplitOnSpacesResults(multipleDelimitersString));
        Assert.assertTrue(identicalSplitOnSpacesResults(emptyString));
    }

    private static boolean identicalSplitOnSpacesResults(String testString) {
        return Arrays.equals(getAndroidSplitOnSpaces(testString), getJ2MESplitOnSpaces(testString));
    }

    private static String[] getAndroidSplitOnSpaces(String s) {
        return (new AndroidUtil.AndroidStringSplitter()).splitOnSpaces(s);
    }

    private static String[] getJ2MESplitOnSpaces(String s) {
        return (new DataUtil.StringSplitter()).splitOnSpaces(s);
    }

    @Test
    public void testSplitOnColonEquivalence() {
        String basicString = "hello:my:name:is:Sam";
        String noDelimitersString = "hellomynameisSam";
        String multipleDelimitersString = "hello::my:::name:is::::Sam";
        String emptyString = "";

        Assert.assertTrue(identicalSplitOnColonResults(basicString));
        Assert.assertTrue(identicalSplitOnColonResults(noDelimitersString));
        Assert.assertTrue(identicalSplitOnColonResults(multipleDelimitersString));
        Assert.assertTrue(identicalSplitOnColonResults(emptyString));
    }

    private static boolean identicalSplitOnColonResults(String testString) {
        return Arrays.equals(getAndroidSplitOnColon(testString), getJ2MESplitOnColon(testString));
    }

    private static String[] getAndroidSplitOnColon(String s) {
        return (new AndroidUtil.AndroidStringSplitter()).splitOnColon(s);
    }

    private static String[] getJ2MESplitOnColon(String s) {
        return (new DataUtil.StringSplitter()).splitOnColon(s);
    }

    @Test
    public void testSplitOnDashEquivalence() {
        String basicString = "hello-my-name-is-Sam";
        String noDelimitersString = "hellomynameisSam";
        String multipleDelimitersString = "hello--my---name-is----Sam";
        String emptyString = "";

        Assert.assertTrue(identicalSplitOnDashResults(basicString));
        Assert.assertTrue(identicalSplitOnDashResults(noDelimitersString));
        Assert.assertTrue(identicalSplitOnDashResults(multipleDelimitersString));
        Assert.assertTrue(identicalSplitOnDashResults(emptyString));
    }

    private static boolean identicalSplitOnDashResults(String testString) {
        return Arrays.equals(getAndroidSplitOnDash(testString), getJ2MESplitOnDash(testString));
    }

    private static String[] getAndroidSplitOnDash(String s) {
        return (new AndroidUtil.AndroidStringSplitter()).splitOnDash(s);
    }

    private static String[] getJ2MESplitOnDash(String s) {
        return (new DataUtil.StringSplitter()).splitOnDash(s);
    }

    @Test
    public void testSplitOnPlusEquivalence() {
        String basicString = "hello+my+name+is+Sam";
        String noDelimitersString = "hellomynameisSam";
        String multipleDelimitersString = "hello++my+++name+is++++Sam";
        String emptyString = "";

        Assert.assertTrue(identicalSplitOnPlusResults(basicString));
        Assert.assertTrue(identicalSplitOnPlusResults(noDelimitersString));
        Assert.assertTrue(identicalSplitOnPlusResults(multipleDelimitersString));
        Assert.assertTrue(identicalSplitOnPlusResults(emptyString));
    }

    private static boolean identicalSplitOnPlusResults(String testString) {
        return Arrays.equals(getAndroidSplitOnPlus(testString), getJ2MESplitOnPlus(testString));
    }

    private static String[] getAndroidSplitOnPlus(String s) {
        return (new AndroidUtil.AndroidStringSplitter()).splitOnPlus(s);
    }

    private static String[] getJ2MESplitOnPlus(String s) {
        return (new DataUtil.StringSplitter()).splitOnPlus(s);
    }

}
