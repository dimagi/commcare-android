package org.commcare.android.tests.formnav;

import android.content.Intent;
import android.widget.ImageButton;
import android.widget.TextView;

import org.commcare.CommCareTestApplication;
import org.commcare.activities.FormEntryActivity;
import org.commcare.activities.components.FormEntryConstants;
import org.commcare.android.CommCareTestRunner;
import org.commcare.android.util.ActivityLaunchUtils;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.dalvik.R;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
@Config(application = CommCareTestApplication.class)
@RunWith(CommCareTestRunner.class)
public class CalendarLocaleTest {

    @Before
    public void setup() {
        TestAppInstaller.installAppAndLogin(
                "jr://resource/commcare-apps/calendar_tests/profile.ccpr",
                "test", "123");
    }

    /**
     * Test filling out and saving a form ending in a hidden repeat group. This
     * type of form exercises uncommon end-of-form code paths
     */
    @Test
    public void testNepaliEthiopianCalendar() {
        ShadowActivity shadowActivity =
                ActivityLaunchUtils.buildHomeActivityForFormEntryLaunch("m0-f0");

        Intent formEntryIntent = shadowActivity.getNextStartedActivity();

        // make sure the form entry activity should be launched
        String intentActivityName = formEntryIntent.getComponent().getClassName();
        assertTrue(intentActivityName.equals(FormEntryActivity.class.getName()));

        navigateCalendarForm(formEntryIntent);
    }

    private void navigateCalendarForm(Intent formEntryIntent) {
        // launch form entry
        FormEntryActivity formEntryActivity =
                Robolectric.buildActivity(FormEntryActivity.class, formEntryIntent)
                        .create().start().resume().get();

        ImageButton nextButton = (ImageButton)formEntryActivity.findViewById(R.id.nav_btn_next);

        // enter an answer for the question
        TextView dayText = (TextView)formEntryActivity.findViewById(R.id.daytxt);
        TextView monthText = (TextView)formEntryActivity.findViewById(R.id.monthtxt);
        TextView yearText = (TextView)formEntryActivity.findViewById(R.id.yeartxt);

        assertEquals(monthText.getText(), "Ashadh");
        assertEquals(dayText.getText(), "19");
        assertEquals(yearText.getText(), "2073");
        assertTrue(nextButton.getTag().equals(FormEntryConstants.NAV_STATE_NEXT));

        nextButton.performClick();

        TextView ethiopianDayText = (TextView)formEntryActivity.findViewById(R.id.daytxt);
        TextView ethiopianMonthText = (TextView)formEntryActivity.findViewById(R.id.monthtxt);
        TextView ethiopianYearText = (TextView)formEntryActivity.findViewById(R.id.yeartxt);
        assertEquals("Säne",ethiopianMonthText.getText());
        assertEquals("26", ethiopianDayText.getText());
        assertEquals("2008", ethiopianYearText.getText());
    }


}
