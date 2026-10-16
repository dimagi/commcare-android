package org.commcare.android.tests.caselist

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simprints.libsimprints.Constants
import com.simprints.libsimprints.Identification
import com.simprints.libsimprints.Tier
import org.commcare.CommCareApplication
import org.commcare.CommCareTestApplication
import org.commcare.activities.EntitySelectActivity
import org.commcare.adapters.EntityListAdapter
import org.commcare.android.util.ActivityLaunchUtils
import org.commcare.android.util.CaseLoadUtils
import org.commcare.android.util.TestAppInstaller
import org.commcare.android.util.TestUtils
import org.commcare.dalvik.R
import org.commcare.session.SessionInstanceBuilder
import org.commcare.tasks.EntityLoaderTask
import org.javarosa.core.util.OrderedHashtable
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.android.controller.ActivityController
import org.robolectric.android.util.concurrent.PausedExecutorService
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowPausedAsyncTask
import org.robolectric.util.ReflectionHelpers

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class EntityListCalloutBeforeLoadTest {
    private val loaderExecutor = PausedExecutorService()

    @Before
    fun setup() {
        (CommCareTestApplication.instance() as CommCareTestApplication).initWorkManager()
        TestAppInstaller.installAppAndLogin(
            "jr://resource/commcare-apps/case_list_lookup/profile.ccpr",
            "test",
            "123",
        )
        TestUtils.processResourceTransactionIntoAppDb("/commcare-apps/case_list_lookup/restore.xml")
        ShadowPausedAsyncTask.overrideExecutor(loaderExecutor)
    }

    @After
    fun tearDown() {
        loaderExecutor.shutdownNow()
        ShadowPausedAsyncTask.reset()
    }

    @Test
    fun calloutResultWhileLoaderRunning_isAppliedWhenListLoads() {
        val activity = ActivityLaunchUtils.launchEntitySelectActivity("m1-f0")
        val loader = ReflectionHelpers.getField<EntityLoaderTask>(activity, "loader")
        assertNotNull(loader)
        assertNull(adapterOf(activity))

        EntityListCalloutDataTest.performFingerprintCallout(activity)
        assertEquals(5, storedCalloutData()?.size)
        assertSame(loader, ReflectionHelpers.getField<EntityLoaderTask>(activity, "loader"))

        val adapter = finishLoading(activity)
        assertListFilteredByCallout(adapter, activity)

        activity.findViewById<View>(R.id.clear_search_button).performClick()
        assertNull(storedCalloutData())
        assertEquals(8, adapter.currentCount)
    }

    @Test
    fun calloutResultOnRecreatedActivity_isAppliedWhenListLoads() {
        val controller = startEntitySelectActivity()
        val activity = controller.get()
        assertNull(ReflectionHelpers.getField<EntityLoaderTask>(activity, "loader"))
        assertNull(adapterOf(activity))

        Shadows.shadowOf(activity).callOnActivityResult(
            EntitySelectActivity.CALLOUT,
            AppCompatActivity.RESULT_OK,
            EntityListCalloutDataTest.buildIdentificationResultIntent(),
        )
        assertEquals(5, storedCalloutData()?.size)

        controller.resume()
        assertListFilteredByCallout(finishLoading(activity), activity)
    }

    @Test
    fun calloutResultOnLoadedList_replacesStoredResultsForRecreatedActivity() {
        val activity = ActivityLaunchUtils.launchEntitySelectActivity("m1-f0")
        EntityListCalloutDataTest.performFingerprintCallout(activity)
        val adapter = finishLoading(activity)
        assertEquals(5, adapter.currentCount)

        Shadows.shadowOf(activity).callOnActivityResult(
            EntitySelectActivity.CALLOUT,
            AppCompatActivity.RESULT_OK,
            buildTwoMatchResultIntent(),
        )
        awaitCalloutFilter(adapter)
        ShadowLooper.idleMainLooper()
        assertEquals(2, adapter.currentCount)
        assertEquals(2, storedCalloutData()?.size)

        val recreated =
            Robolectric
                .buildActivity(EntitySelectActivity::class.java, activity.intent)
                .setup()
                .get()
        assertListFilteredByCallout(finishLoading(recreated), recreated, 2)
    }

    private fun startEntitySelectActivity(): ActivityController<EntitySelectActivity> {
        val entitySelectIntent =
            ActivityLaunchUtils
                .buildHomeActivityForFormEntryLaunch("m1-f0")
                .nextStartedActivity
        return Robolectric
            .buildActivity(EntitySelectActivity::class.java, entitySelectIntent)
            .create()
            .start()
    }

    private fun buildTwoMatchResultIntent(): Intent {
        val matches =
            arrayListOf(
                Identification("b319e951-03f1-4172-b662-4fb3964a0be7", 99, Tier.TIER_1),
                Identification("8e011880-602f-4017-b9d6-ed9dcbba7516", 55, Tier.TIER_3),
            )
        return Intent().putParcelableArrayListExtra(Constants.SIMPRINTS_IDENTIFICATIONS, matches)
    }

    private fun finishLoading(activity: EntitySelectActivity): EntityListAdapter {
        loaderExecutor.runAll()
        ShadowLooper.idleMainLooper()
        val adapter = adapterOf(activity)
        assertNotNull("Entity list was not loaded", adapter)
        awaitCalloutFilter(adapter!!)
        return CaseLoadUtils.loadList(activity)
    }

    private fun assertListFilteredByCallout(
        adapter: EntityListAdapter,
        activity: EntitySelectActivity,
        expectedCount: Int = 5,
    ) {
        assertEquals(expectedCount, adapter.currentCount)

        val row = adapter.getView(0, null, null) as ViewGroup
        val headerContainer = activity.findViewById<View>(R.id.entity_select_header) as LinearLayout
        val header = headerContainer.getChildAt(0) as ViewGroup
        assertEquals(row.childCount, header.childCount)
    }

    private fun adapterOf(activity: EntitySelectActivity): EntityListAdapter? =
        (activity.findViewById<View>(R.id.screen_entity_select_list) as ListView).adapter as EntityListAdapter?

    private fun awaitCalloutFilter(adapter: EntityListAdapter) {
        val filterer = ReflectionHelpers.getField<Any>(adapter, "entityFilterer")
        ReflectionHelpers.getField<Thread>(filterer, "thread").join()
    }

    @Suppress("UNCHECKED_CAST")
    private fun storedCalloutData(): OrderedHashtable<String, String>? =
        CommCareApplication
            .instance()
            .currentSession
            .getCurrentFrameStepExtra(SessionInstanceBuilder.KEY_ENTITY_LIST_EXTRA_DATA)
            as OrderedHashtable<String, String>?
}
