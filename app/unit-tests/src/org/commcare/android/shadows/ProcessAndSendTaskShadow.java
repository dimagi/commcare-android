package org.commcare.android.shadows;

import android.content.Context;

import org.commcare.activities.SyncCapableCommCareActivity;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.tasks.ProcessAndSendTask;
import org.commcare.utils.FormUploadResult;
import org.javarosa.core.model.User;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A shadow of ProcessAndSendTask that does not actually send the form instance, but instead just
 * returns the FormUploadResult at the top of the static stack that is available
 *
 * @author Aliza Stone
 */
@Implements(ProcessAndSendTask.class)
public abstract class ProcessAndSendTaskShadow {

    private final List<FormUploadResult> formUploadResultsToFake = new ArrayList<>();

    public void __constructor__(Context c, FormUploadResult[] resultsToFake) {
        //super(c, "fake-url-that-is-not-used");
        Collections.addAll(formUploadResultsToFake, resultsToFake);
    }

    @Implementation
    protected FormUploadResult sendInstance(int i, File folder, FormRecord record, User user)
            throws FileNotFoundException {
        return formUploadResultsToFake.remove(0);
    }
}
