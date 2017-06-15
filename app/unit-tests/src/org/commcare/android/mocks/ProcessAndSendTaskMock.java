package org.commcare.android.mocks;

import android.content.Context;

import org.commcare.activities.SyncCapableCommCareActivity;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.tasks.ProcessAndSendTask;
import org.commcare.utils.FormUploadResult;
import org.javarosa.core.model.User;
import org.robolectric.annotation.Implementation;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by amstone326 on 6/14/17.
 */

public abstract class ProcessAndSendTaskMock extends ProcessAndSendTask<SyncCapableCommCareActivity> {

    private final List<FormUploadResult> formUploadResultsToFake = new ArrayList<>();

    public ProcessAndSendTaskMock(Context c, FormUploadResult[] resultsToFake) {
        super(c, "fake-url-that-is-not-used");
        Collections.addAll(formUploadResultsToFake, resultsToFake);
    }

    @Override
    protected FormUploadResult sendInstance(int i, File folder, FormRecord record, User user)
            throws FileNotFoundException {
        return formUploadResultsToFake.remove(0);
    }
}
