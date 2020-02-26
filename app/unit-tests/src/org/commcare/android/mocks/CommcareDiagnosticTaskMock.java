package org.commcare.android.mocks;

import android.content.Context;
import android.util.Log;
import org.commcare.android.shadows.ShadowAsyncTaskNoExecutor;
import org.commcare.tasks.ConnectionDiagnosticTask;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/**
 * @author $|-|!˅@M
 */
@Implements(ConnectionDiagnosticTask.class)
public class CommcareDiagnosticTaskMock extends ShadowAsyncTaskNoExecutor {

    private static final String TAG = CommcareDiagnosticTaskMock.class.getName();

    public CommcareDiagnosticTaskMock() {
    }

    @Implementation
    public void __constructor__(Context context) {
    }

    @Implementation
    protected ConnectionDiagnosticTask.NetworkState doTaskBackground(Void... params) {
        Log.d(TAG, "faking connection diagnostic");
        return ConnectionDiagnosticTask.NetworkState.CONNECTED;
    }
}
