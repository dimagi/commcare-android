package org.commcare.models.database;

import android.content.Context;

import org.commcare.models.AndroidPrototypeFactory;
import org.javarosa.core.util.PrefixTree;
import org.javarosa.core.util.externalizable.Externalizable;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import dalvik.system.DexFile;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class AndroidPrototypeFactorySetup {
    private static final String[] packageNames = new String[]{"org.javarosa", "org.commcare", "org.odk.collect"};
    private static PrototypeFactory factory;

    /**
     * Basically this is our PrototypeManager for Android
     */
    public static PrototypeFactory getPrototypeFactory(Context c) {
        if (factory != null) {
            return factory;
        }

        PrefixTree tree = new PrefixTree();

        try {
            List<String> classes = getClasses(c);
            for (String cl : classes) {
                tree.addString(cl);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        factory = new AndroidPrototypeFactory(tree);
        return factory;
    }

    public static void setDBUtilsPrototypeFactory(PrototypeFactory factory) {
        AndroidPrototypeFactorySetup.factory = factory;
    }

    /**
     * Scans all classes accessible from the context class loader which belong to the given package and subpackages.
     */
    private static List<String> getClasses(Context c)
            throws IOException {
        ArrayList<String> classNames = new ArrayList<>();

        String zpath = c.getApplicationInfo().sourceDir;


        if (zpath == null) {
            zpath = "/data/app/org.commcare.android.apk";
        }

        DexFile df = new DexFile(new File(zpath));
        for (Enumeration<String> en = df.entries(); en.hasMoreElements(); ) {
            String cn = en.nextElement();
            loadClass(cn, classNames);
        }

        return classNames;
    }

    public static void loadClass(String cn, List<String> classNames) {
        try {
            for (String packageName : packageNames) {
                if (cn.startsWith(packageName) && !cn.contains(".test.") && !cn.contains("readystatesoftware")) {
                    //TODO: These optimize by preventing us from statically loading classes we don't need, but they take a _long_ time to run.
                    //Maybe we should skip this and/or roll it into initializing the factory itself.
                    Class prototype = Class.forName(cn);
                    if (prototype.isInterface()) {
                        continue;
                    }
                    boolean emptyc = false;
                    for (Constructor<?> cons : prototype.getConstructors()) {
                        if (cons.getParameterTypes().length == 0) {
                            emptyc = true;
                        }
                    }
                    if (!emptyc) {
                        continue;
                    }
                    if (Externalizable.class.isAssignableFrom(prototype)) {
                        classNames.add(cn);
                    }
                }
            }
        } catch (Error | Exception e) {
            e.printStackTrace();
        }
    }
}
