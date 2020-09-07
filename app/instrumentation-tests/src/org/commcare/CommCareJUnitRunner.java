package org.commcare;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;

import androidx.test.runner.AndroidJUnitRunner;

import com.facebook.testing.screenshot.ScreenshotRunner;

public class CommCareJUnitRunner extends AndroidJUnitRunner {
    @Override
    public Application newApplication(ClassLoader cl, String className, Context context) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        return super.newApplication(cl, CommCareInstrumentationTestApplication.class.getName(), context);
    }

    @Override
    public void onCreate(Bundle arguments) {
        ScreenshotRunner.onCreate(this, arguments);
        super.onCreate(arguments);
    }

    @Override
    public void finish(int resultCode, Bundle results) {
        ScreenshotRunner.onDestroy();
        super.finish(resultCode, results);
    }

}
