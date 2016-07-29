package org.commcare.android.tests.formnav;

import android.content.Intent;
import android.os.Environment;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.TextView;

import org.commcare.CommCareApplication;
import org.commcare.CommCareTestApplication;
import org.commcare.activities.FormEntryActivity;
import org.commcare.android.CommCareTestRunner;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.util.ActivityLaunchUtils;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.models.database.SqlStorage;
import org.commcare.views.QuestionsView;
import org.commcare.views.widgets.IntegerWidget;
import org.commcare.views.widgets.NepaliDateWidget;
import org.javarosa.core.model.data.DateData;
import org.javarosa.core.model.data.IAnswerData;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowEnvironment;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
@Config(application = CommCareTestApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class CalendarLocaleTest {

    private static final String TAG = CalendarLocaleTest.class.getSimpleName();

    @Before
    public void setup() {
        TestAppInstaller.installAppAndLogin(
                "jr://resource/commcare-apps/calendar_tests/profile.ccpr",
                "test", "123");
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED);
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
                Robolectric.buildActivity(FormEntryActivity.class).withIntent(formEntryIntent)
                        .create().start().resume().get();

        ImageButton nextButton = (ImageButton)formEntryActivity.findViewById(R.id.nav_btn_next);

        // enter an answer for the question
        TextView dayText = (TextView)formEntryActivity.findViewById(R.id.daytxt);
        TextView monthText = (TextView)formEntryActivity.findViewById(R.id.monthtxt);
        TextView yearText = (TextView)formEntryActivity.findViewById(R.id.yeartxt);

        assertEquals(monthText.getText(), "Ashadh");
        assertEquals(dayText.getText(), "19");
        assertEquals(yearText.getText(), "2073");
        assertTrue(nextButton.getTag().equals(FormEntryActivity.NAV_STATE_NEXT));

        nextButton.performClick();

        TextView ethiopianDayText = (TextView)formEntryActivity.findViewById(R.id.daytxt);
        TextView ethiopianMonthText = (TextView)formEntryActivity.findViewById(R.id.monthtxt);
        TextView ethiopianYearText = (TextView)formEntryActivity.findViewById(R.id.yeartxt);
        assertEquals(ethiopianMonthText.getText(), "Senie");
        assertEquals(ethiopianDayText.getText(), "26");
        assertEquals(ethiopianYearText.getText(), "2008");
    }


}
