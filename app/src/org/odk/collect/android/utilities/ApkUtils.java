package org.odk.collect.android.utilities;

import android.content.Context;

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
 * @author ctsims
 */
public class ApkUtils {
    private static PrototypeFactory factory;

    public static PrototypeFactory getPrototypeFactory(Context c) {
        if (factory != null) {
            return factory;
        }

        PrefixTree tree = new PrefixTree();

        try {
            List<String> classes = getClasses("org.javarosa", c);
            for (String cl : classes) {
                tree.addString(cl);
            }

            //TODO: This was pulled from the source, should probably have a better
            //way to extend this utility
            classes = getClasses("org.commcare", c);
            for (String cl : classes) {
                tree.addString(cl);
            }


            classes = getClasses("org.odk.collect", c);
            for (String cl : classes) {
                tree.addString(cl);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }


        factory = new PrototypeFactory(tree);
        return factory;

    }

    /**
     * Scans all classes accessible from the context class loader which belong to the given package and subpackages.
     *
     * @param packageName The base package
     */
    @SuppressWarnings("unchecked")
    private static List<String> getClasses(String packageName, Context c)
            throws IOException {
        ArrayList<String> classNames = new ArrayList<>();

        String zpath = c.getApplicationInfo().sourceDir;

        if (zpath == null) {
            zpath = "/data/app/org.odk.collect.apk";
        }

        DexFile df = new DexFile(new File(zpath));
        for (Enumeration<String> en = df.entries(); en.hasMoreElements(); ) {
            String cn = en.nextElement();
            if (cn.startsWith(packageName) && !cn.contains(".test.")) {
                try {
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
                } catch (Throwable e) {
                    //nothing should every make this crash
                }
            }
        }

        return classNames;
    }
}
