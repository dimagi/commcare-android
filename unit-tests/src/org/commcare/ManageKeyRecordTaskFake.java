package org.commcare;

import android.content.Context;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.commcare.activities.DataPullControllerMock;
import org.commcare.activities.LoginMode;
import org.commcare.network.HttpResponseMock;
import org.commcare.tasks.ManageKeyRecordTask;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class ManageKeyRecordTaskFake extends ManageKeyRecordTask<DataPullControllerMock> {
    public ManageKeyRecordTaskFake(Context c, int taskId, String username, String passwordOrPin,
                                   LoginMode loginMode, CommCareApp app,
                                   boolean restoreSession, boolean triggerMultipleUserWarning) {
        super(c, taskId, username, passwordOrPin, loginMode, app, restoreSession, triggerMultipleUserWarning);
    }

    @Override
    protected void deliverUpdate(DataPullControllerMock dataPullControllerMock, String... update) {

    }

    @Override
    protected HttpResponse doHttpRequest() throws ClientProtocolException, IOException {
        InputStream is = System.class.getResourceAsStream("/inputs/key_record_create.xml");
        return HttpResponseMock.buildHttpResponseMock(200, is);
    }
}
