package org.commcare.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import org.commcare.android.tasks.LogSubmissionTask;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.preferences.CommCarePreferences;
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
        IStorageUtilityIndexed<FormInstance> userFixtureStorage = CommCareApplication._().getUserStorage("fixture", FormInstance.class);
        IStorageUtilityIndexed<FormInstance> appFixtureStorage = CommCareApplication._().getAppStorage("fixture", FormInstance.class);

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

    public static void triggerLogSubmission(Context c) {
        SharedPreferences settings = CommCareApplication._().getCurrentApp().getAppPreferences();
        String url = settings.getString(CommCarePreferences.PREFS_SUBMISSION_URL_KEY, null);

        if (url == null) {
            //This is mostly for dev purposes
            Toast.makeText(c, "Couldn't submit logs! Invalid submission URL...", Toast.LENGTH_LONG).show();
        } else {
            try {
                LogSubmissionTask reportSubmitter =
                        new LogSubmissionTask(true,
                                CommCareApplication._().getSession().startDataSubmissionListener(R.string.submission_logs_title),
                                url);
                reportSubmitter.execute();
            } catch (SessionUnavailableException e) {
                Toast.makeText(c, "Couldn't submit logs! No longer logged in", Toast.LENGTH_LONG).show();
            }
        }
    }
}
