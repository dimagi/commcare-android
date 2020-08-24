package org.commcare.models;

import android.content.Context;

import org.commcare.android.resource.installers.XFormAndroidInstaller;
import org.commcare.android.resource.installers.XFormAndroidInstallerV8;
import org.commcare.models.database.AndroidPrototypeFactorySetup;

import java.util.HashSet;
import java.util.List;

/**
 * This is used for migrating the old XFormAndroidInstallerV8 to
 * new XFormAndroidInstaller while doing app DB upgrade from v8 to v9 (Commcare 2.42)
 */
public class AndroidPrototypeFactoryV8 extends AndroidPrototypeFactory {
    private static AndroidPrototypeFactoryV8 factory;

    private AndroidPrototypeFactoryV8(HashSet<String> classNames) {
        super(classNames);
    }

    // Factory method for AndroidPrototypeFactoryV8
    public static AndroidPrototypeFactoryV8 getAndroidPrototypeFactoryV8(Context c) {
        if (factory != null) {
            return factory;
        }

        try {
            List<String> classes = AndroidPrototypeFactorySetup.getClasses(c);
            classes.remove(XFormAndroidInstaller.class.getName());
            factory = new AndroidPrototypeFactoryV8(new HashSet<>(classes));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return factory;
    }

    @Override
    protected void addMigratedClasses() {
        super.addMigratedClasses();
        addMigratedClass("org.commcare.android.resource.installers.XFormAndroidInstaller", XFormAndroidInstallerV8.class);
    }
}
