package org.odk.collect.android.logic;

import org.javarosa.core.model.FormIndex;

/**
 * Created by droberts on 8/7/15.
 */
public interface PendingCalloutInterface {
    public FormIndex getPendingCalloutFormIndex();
    public void setPendingCalloutFormIndex(FormIndex pendingCalloutFormIndex);
}
