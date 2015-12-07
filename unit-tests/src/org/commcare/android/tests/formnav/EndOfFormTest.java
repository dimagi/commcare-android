package org.commcare.android.tests.formnav;

import android.app.Activity;
import android.content.Intent;
import android.os.Environment;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import org.commcare.android.CommCareTestRunner;
import org.commcare.android.models.AndroidSessionWrapper;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.android.util.TestUtils;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.dalvik.activities.CommCareHomeActivity;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.session.CommCareSession;
import org.commcare.session.SessionNavigator;
import org.javarosa.core.model.FormIndex;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.reference.ResourceReferenceFactory;
import org.javarosa.form.api.FormEntryController;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.odk.collect.android.activities.FormEntryActivity;
import org.odk.collect.android.views.ODKView;
import org.odk.collect.android.widgets.IntegerWidget;
import org.odk.collect.android.widgets.QuestionWidget;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowEnvironment;
import org.robolectric.util.ActivityController;

import java.util.HashMap;
import java.util.Hashtable;

import static junit.framework.Assert.assertTrue;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
@Config(application = org.commcare.dalvik.application.CommCareApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class EndOfFormTest {

    @Before
    public void setup() {
        // needed to resolve "jr://resource" type references
        ReferenceManager._().addReferenceFactory(new ResourceReferenceFactory());

        TestUtils.initializeStaticTestStorage();
        TestAppInstaller.setupPrototypeFactory();

        TestAppInstaller appTestInstaller =
                new TestAppInstaller("jr://resource/commcare-apps/form_nav_tests/profile.ccpr",
                        "test", "123");
        appTestInstaller.installAppAndLogin();
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED);
    }

    @Test
    public void testHiddenRepeatAtEndOfForm() {
        AndroidSessionWrapper sessionWrapper = CommCareApplication._().getCurrentSessionWrapper();
        CommCareSession session = sessionWrapper.getSession();
        session.setCommand("m0-f0");
        CommCareHomeActivity homeActivity = Robolectric.buildActivity(CommCareHomeActivity.class).create().get();
        SessionNavigator sessionNavigator = homeActivity.getSessionNavigator();
        sessionNavigator.startNextSessionStep();

        ShadowActivity shadowActivity = Shadows.shadowOf(homeActivity);
        Intent formEntryIntent = shadowActivity.getNextStartedActivity();

        // make sure the form entry activity was launched
        assertTrue(formEntryIntent.getComponent().getClassName().equals(FormEntryActivity.class.getName()));

        ActivityController<FormEntryActivity> controller = Robolectric.buildActivity(FormEntryActivity.class).withIntent(formEntryIntent).create().start();
        FormEntryActivity formEntryActivity = controller.get();
        controller.resume();
        Robolectric.flushBackgroundThreadScheduler();
        Robolectric.flushForegroundThreadScheduler();
        ImageButton nextButton = (ImageButton)formEntryActivity.findViewById(R.id.nav_btn_next);
        assertTrue(nextButton.getTag().equals(FormEntryActivity.NAV_STATE_DONE));
        ODKView odkView = formEntryActivity.getODKView();
        IntegerWidget favoriteNumber = (IntegerWidget)odkView.getWidgets().get(0);
        favoriteNumber.setAnswer("2");
        assertTrue(nextButton.getTag().equals(FormEntryActivity.NAV_STATE_NEXT));
        nextButton.performClick();
        Robolectric.flushBackgroundThreadScheduler();
        Robolectric.flushForegroundThreadScheduler();
        int currentEvent = FormEntryActivity.mFormController.getEvent();
        System.out.print("foo");
        assertTrue(currentEvent == FormEntryController.EVENT_END_OF_FORM);
        System.out.print("foo");
    }
}
