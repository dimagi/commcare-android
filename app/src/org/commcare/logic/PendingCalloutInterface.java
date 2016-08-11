package org.commcare.logic;

import org.javarosa.core.model.FormIndex;

/**
 * Register the form index of the question widgets currently waiting for an
 * answer from an external source (image chooser, custom intent callout, etc).
 * Since calling out to external sources for answers is a serial procedure, we
 * only need to store one form index.
 *
 * The form index is also important to define the context while evaluating
 * relative paths in intent response refs.
 *
 * @author Danny Roberts (droberts@dimagi.com)
 */
public interface PendingCalloutInterface {
    FormIndex getPendingCalloutFormIndex();

    void setPendingCalloutFormIndex(FormIndex pendingCalloutFormIndex);

    boolean wasCalloutPendingAndCancelled(FormIndex calloutFormIndex);

    void setPendingCalloutAsCancelled();
}
