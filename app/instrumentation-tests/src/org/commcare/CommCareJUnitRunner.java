package org.commcare;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import androidx.test.runner.AndroidJUnitRunner;

public class CommCareJUnitRunner extends AndroidJUnitRunner {

    @Override
    public void onCreate(Bundle arguments) {
        // A fix for java.lang.NoClassDefFoundError: Failed resolution of: Lkotlin/reflect/jvm/internal/KPropertyImpl;
        arguments.putString("package", "org.commcare.androidTests");
        super.onCreate(arguments);
    }

    @Override
    public Application newApplication(ClassLoader cl, String className, Context context) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        return super.newApplication(cl, CommCareInstrumentationTestApplication.class.getName(), context);
    }
}
