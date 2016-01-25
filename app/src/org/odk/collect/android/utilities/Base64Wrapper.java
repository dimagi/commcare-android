package org.odk.collect.android.utilities;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Wrapper class for accessing Base64 functionality.
 * This allows API Level 7 deployment of ODK Collect while
 * enabling API Level 8 and higher phone to support encryption.
 *
 * @author mitchellsundt@gmail.com
 */
public class Base64Wrapper {

    private static final int FLAGS = 2;// NO_WRAP
    private Class<?> base64 = null;

    public Base64Wrapper() throws ClassNotFoundException {
        base64 = this.getClass().getClassLoader()
                .loadClass("android.util.Base64");
    }

    public String encodeToString(byte[] ba) {
        Class<?>[] argClassList = new Class[]{byte[].class, int.class};
        try {
            Method m = base64.getDeclaredMethod("encode", argClassList);
            Object[] argList = new Object[]{ba, FLAGS};
            Object o = m.invoke(null, argList);
            byte[] outArray = (byte[])o;
            return new String(outArray, "UTF-8");
        } catch (UnsupportedEncodingException | InvocationTargetException
                | IllegalAccessException | NoSuchMethodException
                | SecurityException e) {
            e.printStackTrace();
            throw new IllegalArgumentException(e.toString());
        }
    }

    public byte[] decode(String base64String) {
        Class<?>[] argClassList = new Class[]{String.class, int.class};
        Object o;
        try {
            Method m = base64.getDeclaredMethod("decode", argClassList);
            Object[] argList = new Object[]{base64String, FLAGS};
            o = m.invoke(null, argList);
        } catch (InvocationTargetException | IllegalAccessException
                | NoSuchMethodException | SecurityException e) {
            e.printStackTrace();
            throw new IllegalArgumentException(e.toString());
        }
        return (byte[])o;
    }
}
