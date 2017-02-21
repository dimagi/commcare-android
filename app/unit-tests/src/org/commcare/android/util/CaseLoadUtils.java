package org.commcare.android.util;

import android.content.Intent;
import android.widget.ListView;

import org.commcare.activities.EntitySelectActivity;
import org.commcare.adapters.EntityListAdapter;
import org.commcare.dalvik.R;
import org.junit.Assert;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowListView;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class CaseLoadUtils {

    public static EntityListAdapter loadList(EntitySelectActivity entitySelectActivity) {
        // wait for entities to load
        Robolectric.flushBackgroundThreadScheduler();
        Robolectric.flushForegroundThreadScheduler();

        ListView entityList =
                (ListView)entitySelectActivity.findViewById(R.id.screen_entity_select_list);
        EntityListAdapter adapter = (EntityListAdapter)entityList.getAdapter();

        ShadowListView shadowListView = Shadows.shadowOf(entityList);
        shadowListView.populateItems();
        return adapter;
    }

    public static EntitySelectActivity launchEntitySelectActivity(String command) {
        ShadowActivity shadowHomeActivity =
                ActivityLaunchUtils.buildHomeActivityForFormEntryLaunch(command);

        Intent entitySelectIntent = shadowHomeActivity.getNextStartedActivity();

        String intentActivityName = entitySelectIntent.getComponent().getClassName();
        Assert.assertEquals(EntitySelectActivity.class.getName(), intentActivityName);

        // start the entity select activity
        return Robolectric.buildActivity(EntitySelectActivity.class)
                .withIntent(entitySelectIntent).setup().get();
    }

}
