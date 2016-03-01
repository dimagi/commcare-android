package org.commcare.dalvik.activities.utils;

import android.content.Context;
import android.content.Intent;

import org.commcare.activities.EntityDetailActivity;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.session.SessionFrame;
import org.commcare.suite.model.SessionDatum;
import org.commcare.utils.SerializationUtil;
import org.javarosa.core.model.instance.TreeReference;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class EntityDetailUtils {

    /**
     * Get an intent for displaying the confirm detail screen for an element (either just populates
     * the given intent with the necessary information, or creates a new one if it is null)
     *
     * @param contextRef reference to the selected element for which to display
     *                   detailed view
     * @return The intent argument, or a newly created one, with element
     * selection information attached.
     */
    public static Intent getDetailIntent(Context context,
                                         TreeReference contextRef,
                                         Intent detailIntent,
                                         SessionDatum selectDatum,
                                         AndroidSessionWrapper asw) {
        if (detailIntent == null) {
            detailIntent = new Intent(context, EntityDetailActivity.class);
        }
        return populateDetailIntent(detailIntent, contextRef, selectDatum, asw);
    }

    /**
     * Attach all element selection information to the intent argument and return the resulting
     * intent
     */
    public static Intent populateDetailIntent(Intent detailIntent,
                                              TreeReference contextRef,
                                              SessionDatum selectDatum,
                                              AndroidSessionWrapper asw) {

        String caseId = SessionDatum.getCaseIdFromReference(
                contextRef, selectDatum, asw.getEvaluationContext());
        detailIntent.putExtra(SessionFrame.STATE_DATUM_VAL, caseId);

        // Include long datum info if present
        if (selectDatum.getLongDetail() != null) {
            detailIntent.putExtra(EntityDetailActivity.DETAIL_ID,
                    selectDatum.getLongDetail());
            detailIntent.putExtra(EntityDetailActivity.DETAIL_PERSISTENT_ID,
                    selectDatum.getPersistentDetail());
        }

        SerializationUtil.serializeToIntent(detailIntent,
                EntityDetailActivity.CONTEXT_REFERENCE, contextRef);

        return detailIntent;
    }
}
