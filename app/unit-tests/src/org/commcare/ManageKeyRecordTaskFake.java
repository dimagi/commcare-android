package org.commcare;

import android.content.Context;

import org.commcare.activities.DataPullControllerMock;
import org.commcare.activities.LoginMode;
import org.commcare.core.network.FakeResponseBody;
import org.commcare.tasks.ManageKeyRecordTask;

import java.io.IOException;
import java.io.InputStream;

import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class ManageKeyRecordTaskFake extends ManageKeyRecordTask<DataPullControllerMock> {
    private final String resourcePath;

    public ManageKeyRecordTaskFake(Context c, int taskId, String username, String passwordOrPin,
                                   LoginMode loginMode, CommCareApp app,
                                   boolean restoreSession, boolean triggerMultipleUserWarning,
                                   String resourcePath) {
        super(c, taskId, username, passwordOrPin, loginMode, app, restoreSession, triggerMultipleUserWarning, false);
        this.resourcePath = resourcePath;
    }

    @Override
    protected void deliverUpdate(DataPullControllerMock dataPullControllerMock, String... update) {

    }

    @Override
    protected Response<ResponseBody> doHttpRequest() throws IOException {
        InputStream is = System.class.getResourceAsStream(resourcePath);
        ResponseBody responseBody = new FakeResponseBody(is);
        return Response.success(responseBody);
    }
}
