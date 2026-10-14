package org.commcare.android.util;

import android.widget.ListView;

import org.commcare.activities.EntitySelectActivity;
import org.commcare.adapters.EntityListAdapter;
import org.commcare.dalvik.R;
import org.junit.Assert;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowListView;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.TimeUnit;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class CaseLoadUtils {

    private static final long LOAD_TIMEOUT_MS = 5000;
    private static final long POLL_INTERVAL_MS = 10;

    public static EntityListAdapter loadList(EntitySelectActivity entitySelectActivity) {
        ListView entityList =
                entitySelectActivity.findViewById(R.id.screen_entity_select_list);
        waitForAdapter(entityList);
        EntityListAdapter adapter = (EntityListAdapter)entityList.getAdapter();

        ShadowListView shadowListView = Shadows.shadowOf(entityList);
        shadowListView.populateItems();
        return adapter;
    }

    private static void waitForAdapter(ListView entityList) {
        ShadowLooper.idleMainLooper();
        for (long waitedMs = 0; entityList.getAdapter() == null; waitedMs += POLL_INTERVAL_MS) {
            if (waitedMs >= LOAD_TIMEOUT_MS) {
                Assert.fail("Entity list adapter was not set within " + LOAD_TIMEOUT_MS + "ms");
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            ShadowLooper.idleMainLooper(POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
        }
    }
}
