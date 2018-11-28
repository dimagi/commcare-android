package org.commcare.models;

import android.content.Context;

import org.commcare.android.resource.installers.XFormAndroidInstaller;
import org.commcare.android.resource.installers.XFormAndroidInstallerV1;
import org.commcare.models.database.AndroidPrototypeFactorySetup;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.util.HashSet;
import java.util.List;

/**
 * This is used for migrating the old XFormAndroidInstallerV1 to
 * new XFormAndroidInstaller while doing app DB upgrade from v8 to v9 (Commcare 2.42)
 */
public class AndroidPrototypeFactoryV1 extends AndroidPrototypeFactory {
    private static AndroidPrototypeFactoryV1 factory;

    private AndroidPrototypeFactoryV1(HashSet<String> classNames) {
        super(classNames);
    }

    // Factory method for AndroidPrototypeFactoryV1
    public static AndroidPrototypeFactoryV1 getAndroidPrototypeFactoryV1(Context c) {
        if (factory != null) {
            return factory;
        }

        try {
            List<String> classes = AndroidPrototypeFactorySetup.getClasses(c);
            classes.remove(XFormAndroidInstaller.class.getName());
            factory = new AndroidPrototypeFactoryV1(new HashSet<>(classes));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return factory;
    }

    @Override
    protected void addMigratedClasses() {
        super.addMigratedClasses();
        addMigratedClass("org.commcare.android.resource.installers.XFormAndroidInstaller", XFormAndroidInstallerV1.class);
    }
}
