package org.commcare.utils;

import android.content.Context;
import android.os.AsyncTask;
import android.widget.Toast;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.tasks.LogSubmissionTask;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;
import org.javarosa.core.util.ArrayUtilities;
import org.javarosa.xpath.expr.XPathExpression;

import java.util.Vector;

/**
 * Basically Copy+Paste code from CCJ2ME that needs to be unified or re-indexed to somewhere more reasonable.
 *
 * @author ctsims
 */
public class CommCareUtil {
    public static FormInstance loadFixture(String refId, String userId) {
        IStorageUtilityIndexed<FormInstance> userFixtureStorage = CommCareApplication.instance().getUserStorage("fixture", FormInstance.class);
        IStorageUtilityIndexed<FormInstance> appFixtureStorage = CommCareApplication.instance().getAppStorage("fixture", FormInstance.class);

        Vector<Integer> userFixtures = userFixtureStorage.getIDsForValue(FormInstance.META_ID, refId);
        ///... Nooooot so clean.
        if (userFixtures.size() == 1) {
            //easy case, one fixture, use it
            return userFixtureStorage.read(userFixtures.elementAt(0));
            //TODO: Userid check anyway?
        } else if (userFixtures.size() > 1) {
            //intersect userid and fixtureid set.
            //TODO: Replace context call here with something from the session, need to stop relying on that coupling

            Vector<Integer> relevantUserFixtures = userFixtureStorage.getIDsForValue(FormInstance.META_XMLNS, userId);

            if (relevantUserFixtures.size() != 0) {
                Integer userFixture = ArrayUtilities.intersectSingle(userFixtures, relevantUserFixtures);
                if (userFixture != null) {
                    return userFixtureStorage.read(userFixture);
                }
            }
        }

        //ok, so if we've gotten here there were no fixtures for the user, let's try the app fixtures.
        Vector<Integer> appFixtures = appFixtureStorage.getIDsForValue(FormInstance.META_ID, refId);
        Integer globalFixture = ArrayUtilities.intersectSingle(appFixtureStorage.getIDsForValue(FormInstance.META_XMLNS, ""), appFixtures);
        if (globalFixture != null) {
            return appFixtureStorage.read(globalFixture);
        } else {
            //See if we have one manually placed in the suite
            Integer userFixture = ArrayUtilities.intersectSingle(appFixtureStorage.getIDsForValue(FormInstance.META_XMLNS, userId), appFixtures);
            if (userFixture != null) {
                return appFixtureStorage.read(userFixture);
            }
            //Otherwise, nothing
            return null;
        }
    }

    /**
     * Used around to count up the degree of specificity for this reference
     */
    public static int countPreds(TreeReference reference) {
        int preds = 0;

        if (reference == null) {
            return 0;
        }

        for (int i = 0; i < reference.size(); ++i) {
            Vector<XPathExpression> predicates = reference.getPredicate(i);
            if (predicates != null) {
                preds += predicates.size();
            }
        }
        return preds;
    }

    public static void triggerLogSubmission(Context c, boolean forceLogs) {
        String url = LogSubmissionTask.getSubmissionUrl(CommCareApplication.instance().getCurrentApp().getAppPreferences());

        if (url == null) {
            //This is mostly for dev purposes
            Toast.makeText(c, "Couldn't submit logs! Invalid submission URL...", Toast.LENGTH_LONG).show();
        } else {
            executeLogSubmission(true, url, forceLogs);
        }
    }

    public static void executeLogSubmission(boolean serializeCurrentLogs, String url, boolean forceLogs) {
        LogSubmissionTask reportSubmitter =
                new LogSubmissionTask(serializeCurrentLogs,
                        CommCareApplication.instance().getSession().getListenerForSubmissionNotification(R.string.submission_logs_title),
                        url,
                        forceLogs);
        // Execute on a true multithreaded chain, since this is an asynchronous process
        reportSubmitter.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
}
