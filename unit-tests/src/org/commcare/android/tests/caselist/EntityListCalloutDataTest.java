package org.commcare.android.tests.caselist;

import android.app.Activity;
import android.content.Intent;
import android.widget.ImageButton;
import android.widget.ListView;

import com.simprints.libsimprints.Identification;

import junit.framework.Assert;

import org.commcare.CommCareApplication;
import org.commcare.activities.EntitySelectActivity;
import org.commcare.activities.components.EntitySelectCalloutSetup;
import org.commcare.adapters.EntityListAdapter;
import org.commcare.android.CommCareTestRunner;
import org.commcare.android.util.ActivityLaunchUtils;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.android.util.TestUtils;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.session.CommCareSession;
import org.commcare.suite.model.Callout;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.EntityDatum;
import org.commcare.views.EntityView;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowListView;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test attaching callout data to entity select list
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
@Config(application = CommCareApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class EntityListCalloutDataTest {
    private EntitySelectActivity entitySelectActivity;
    private EntityListAdapter adapter;

    @Before
    public void setup() {
        String appProfileResource =
                "jr://resource/commcare-apps/case_list_lookup/profile.ccpr";
        TestAppInstaller.initInstallAndLogin(appProfileResource, "test", "123");

        TestUtils.processResourceTransactionIntoAppDb("/commcare-apps/case_list_lookup/restore.xml");
    }

    @Test
    public void testAttachCalloutResultToListTest() {
        launchEntitySelectActivity("m1-f0");

        loadList();
        // TODO PLM: This fails non-deterministically; haven't figured out how to fix it
        Assert.assertEquals(8, adapter.getCount());

        EntityView entityView = (EntityView)adapter.getView(0, null, null);
        int entityColumnCount = entityView.getChildCount();

        performFingerprintCallout();

        // ensure that the entity list is filtered by the received callout
        // result data (fingerprint identification list with confidence score)
        assertEquals(5, adapter.getCurrentCount());
        assertTrue(adapter.isFilteringByCalloutResult());
        assertTrue(adapter.hasCalloutResponseData());

        // ensure that entries in the entity list have extra data attached to them
        entityView = (EntityView)adapter.getView(0, null, null);
        assertEquals(entityColumnCount + 1, entityView.getChildCount());

        clearCalloutResults();
        entityView = (EntityView)adapter.getView(0, null, null);
        assertEquals(entityColumnCount, entityView.getChildCount());
        assertEquals(8, adapter.getCurrentCount());
    }

    @Test
    public void testCalloutResultWithNoColumnTest() {
        launchEntitySelectActivity("m1-f1");

        loadList();
        // TODO PLM: This fails non-deterministically; haven't figured out how to fix it
        Assert.assertEquals(8, adapter.getCount());

        EntityView entityView = (EntityView)adapter.getView(0, null, null);
        int entityColumnCount = entityView.getChildCount();

        performFingerprintCallout();

        // ensure that the entity list is filtered by the received callout
        // result data (fingerprint identification list with confidence score)
        assertEquals(5, adapter.getCurrentCount());
        assertTrue(adapter.isFilteringByCalloutResult());
        assertTrue(adapter.hasCalloutResponseData());

        // Ensure response data isn't shown to the user, since the width is set to 0
        entityView = (EntityView)adapter.getView(0, null, null);
        assertEquals(entityColumnCount, entityView.getChildCount());
    }

    private void launchEntitySelectActivity(String command) {
        ShadowActivity shadowHomeActivity =
                ActivityLaunchUtils.buildHomeActivityForFormEntryLaunch(command);

        Intent entitySelectIntent = shadowHomeActivity.getNextStartedActivity();

        // make sure the form entry activity should be launched
        String intentActivityName = entitySelectIntent.getComponent().getClassName();
        assertTrue(intentActivityName.equals(EntitySelectActivity.class.getName()));

        // start the entity select activity
        entitySelectActivity = Robolectric.buildActivity(EntitySelectActivity.class)
                .withIntent(entitySelectIntent).create().start().resume().get();
    }

    private void loadList() {
        // wait for entities to load
        Robolectric.flushBackgroundThreadScheduler();
        Robolectric.flushForegroundThreadScheduler();
        // wait for string filterer to run on ""
        // the string filterer probably doesn't filter at this point...
        Robolectric.flushBackgroundThreadScheduler();
        Robolectric.flushForegroundThreadScheduler();

        ListView entityList =
                (ListView)entitySelectActivity.findViewById(R.id.screen_entity_select_list);
        adapter = (EntityListAdapter)entityList.getAdapter();

        ShadowListView shadowListView = Shadows.shadowOf(entityList);
        shadowListView.populateItems();
    }

    private void performFingerprintCallout() {
        // make entity list callout to 'fingerprint identification'
        ImageButton calloutButton =
                (ImageButton)entitySelectActivity.findViewById(R.id.barcodeButton);
        calloutButton.performClick();

        // receive the (faked) callout result
        Callout identificationScanCallout = getEntitySelectCallout();
        Intent calloutIntent = EntitySelectCalloutSetup.buildCalloutIntent(identificationScanCallout);
        Intent responseIntent = buildIdentificationResultIntent();
        ShadowActivity shadowEntitySelect = Shadows.shadowOf(entitySelectActivity);
        shadowEntitySelect.receiveResult(calloutIntent, Activity.RESULT_OK, responseIntent);
    }

    private static Intent buildIdentificationResultIntent() {
        Intent i = new Intent();
        ArrayList<Identification> matchingList = new ArrayList<>();
        matchingList.add(new Identification("b319e951-03f1-4172-b662-4fb3964a0be7", 0.99f)); // stan
        matchingList.add(new Identification("8e011880-602f-4017-b9d6-ed9dcbba7516", 0.55f)); // ellen
        matchingList.add(new Identification("c44c7ade-0cec-4401-b422-4c475f0043ae", 0.25f)); // pat
        matchingList.add(new Identification("6b09e558-604c-4735-ac34-efbb2783b784", 0.22f)); // aria
        matchingList.add(new Identification("16d31048-e8f8-40d5-a3e9-b35e9cde20da", 0.10f)); // gilbert
        return i.putExtra("identification", matchingList);
    }

    private static Callout getEntitySelectCallout() {
        AndroidSessionWrapper sessionWrapper =
                CommCareApplication._().getCurrentSessionWrapper();
        CommCareSession session = sessionWrapper.getSession();
        EntityDatum selectDatum = (EntityDatum)session.getNeededDatum();
        Detail shortSelect = session.getDetail(selectDatum.getShortDetail());
        return shortSelect.getCallout();
    }

    private void clearCalloutResults() {
        // clear the callout data and make sure the extra column is removed and
        // all the entities are shown
        ImageButton clearSearchButton =
                (ImageButton)entitySelectActivity.findViewById(R.id.clear_search_button);
        clearSearchButton.performClick();
    }
}
