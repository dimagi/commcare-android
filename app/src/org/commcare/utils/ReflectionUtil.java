package org.commcare.utils;

import android.os.Environment;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author wspride
 */
public class ReflectionUtil {
    private static Method mExternalStorageEmulated;

    static {
        initCompatibility();
    }

    private static void initCompatibility() {
        try {
            mExternalStorageEmulated =
                    Environment.class.getMethod("isExternalStorageEmulated");
           /* success, this is a newer device */
        } catch (NoSuchMethodException nsme) {
           /* failure, must be older device */
        }
    }

    /**
     * @return true if the external storage is being emulated, false otherwise.
     * Uses reflection since the isExternalStorageEmulated won't exist on
     * Android APIs 10 or below
     */
    private static boolean mIsExternalStorageEmulated() throws IOException {
        try {
            Object obj = mExternalStorageEmulated.invoke(null);
            return (Boolean)obj;
        } catch (InvocationTargetException ite) {
           /* unpack original exception when possible */
            Throwable cause = ite.getCause();
            if (cause instanceof IOException) {
                throw (IOException)cause;
            } else if (cause instanceof RuntimeException) {
                throw (RuntimeException)cause;
            } else if (cause instanceof Error) {
                throw (Error)cause;
            } else {
               /* unexpected checked exception; wrap and re-throw */
                throw new RuntimeException(ite);
            }
        } catch (IllegalAccessException | ClassCastException ie) {
            System.err.println("unexpected " + ie);
            throw new RuntimeException(ie);
        }
    }

    /**
     * Helper method for mIsExternalStorageEmulated that checks to see if the
     * method exists; if it does not, then we return false because emulation is
     * not possible in this case. If it is not null, then the storage is
     * potentially emulated, and we check whether it actually is being emualted
     * or not.  If we run into problems calling this method, we play it safe
     * and return true so that we don't make any unsafe assumptions
     */
    public static boolean mIsExternalStorageEmulatedHelper() {
        if (mExternalStorageEmulated != null) {
           /* feature is supported */
            try {
                return mIsExternalStorageEmulated();
            } catch (RuntimeException e) {
                return true;
            } catch (IOException ie) {
                return true;
            }
        } else {
           /* external SD emulation not possible, return false */
            return false;
        }
    }
}
