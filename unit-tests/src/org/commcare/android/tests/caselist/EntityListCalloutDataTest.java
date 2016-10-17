package org.commcare.android.tests.caselist;

import android.app.Activity;
import android.content.Intent;
import android.widget.ImageButton;

import com.simprints.libsimprints.Identification;
import com.simprints.libsimprints.Tier;

import org.commcare.CommCareApplication;
import org.commcare.CommCareTestApplication;
import org.commcare.activities.EntitySelectActivity;
import org.commcare.activities.components.EntitySelectCalloutSetup;
import org.commcare.adapters.EntityListAdapter;
import org.commcare.android.CommCareTestRunner;
import org.commcare.android.util.CaseLoadUtils;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.android.util.TestUtils;
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
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test attaching callout data to entity select list
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
@Config(application = CommCareTestApplication.class)
@RunWith(CommCareTestRunner.class)
public class EntityListCalloutDataTest {
    private EntitySelectActivity entitySelectActivity;
    private EntityListAdapter adapter;

    @Before
    public void setup() {
        String appProfileResource =
                "jr://resource/commcare-apps/case_list_lookup/profile.ccpr";
        TestAppInstaller.installAppAndLogin(appProfileResource, "test", "123");

        TestUtils.processResourceTransactionIntoAppDb("/commcare-apps/case_list_lookup/restore.xml");
    }

    @Test
    public void testAttachCalloutResultToListTest() {
        entitySelectActivity = CaseLoadUtils.launchEntitySelectActivity("m1-f0");

        adapter = CaseLoadUtils.loadList(entitySelectActivity);
        assertEquals(8, adapter.getCount());

        EntityView entityView = (EntityView)adapter.getView(0, null, null);
        int entityColumnCount = entityView.getChildCount();

        performFingerprintCallout();

        // ensure that the entity list is filtered by the received callout
        // result data (fingerprint identification list with confidence score)
        adapter = CaseLoadUtils.loadList(entitySelectActivity);
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
        entitySelectActivity = CaseLoadUtils.launchEntitySelectActivity("m1-f1");

        adapter = CaseLoadUtils.loadList(entitySelectActivity);
        assertEquals(8, adapter.getCount());

        EntityView entityView = (EntityView)adapter.getView(0, null, null);
        int entityColumnCount = entityView.getChildCount();

        performFingerprintCallout();

        // ensure that the entity list is filtered by the received callout
        // result data (fingerprint identification list with confidence score)
        adapter = CaseLoadUtils.loadList(entitySelectActivity);
        assertEquals(5, adapter.getCurrentCount());
        assertTrue(adapter.isFilteringByCalloutResult());
        assertTrue(adapter.hasCalloutResponseData());

        // Ensure response data isn't shown to the user, since the width is set to 0
        entityView = (EntityView)adapter.getView(0, null, null);
        assertEquals(entityColumnCount, entityView.getChildCount());
    }

    private void performFingerprintCallout() {
        // make entity list callout to 'fingerprint identification'
        entitySelectActivity.barcodeScanOnClickListener.onClick(null);

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
        matchingList.add(new Identification("b319e951-03f1-4172-b662-4fb3964a0be7", 99, Tier.TIER_1)); // stan
        matchingList.add(new Identification("8e011880-602f-4017-b9d6-ed9dcbba7516", 55, Tier.TIER_3)); // ellen
        matchingList.add(new Identification("c44c7ade-0cec-4401-b422-4c475f0043ae", 25, Tier.TIER_4)); // pat
        matchingList.add(new Identification("6b09e558-604c-4735-ac34-efbb2783b784", 22, Tier.TIER_4)); // aria
        matchingList.add(new Identification("16d31048-e8f8-40d5-a3e9-b35e9cde20da", 10, Tier.TIER_5)); // gilbert
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
