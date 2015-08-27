package org.odk.collect.android.logic;

import org.javarosa.core.model.FormIndex;

/**
 * Created by droberts on 8/7/15.
 *
 * this interface lets the callout widget tell the FormEntryActivity
 * what form index to use as the context for evaluating relative paths in intent response refs.
 *
 */
public interface PendingCalloutInterface {
    FormIndex getPendingCalloutFormIndex();
    void setPendingCalloutFormIndex(FormIndex pendingCalloutFormIndex);
}
