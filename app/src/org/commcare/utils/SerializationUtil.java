package org.commcare.utils;

import android.content.Intent;
import android.os.Bundle;
import android.util.Base64;

import org.commcare.CommCareApplication;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.Externalizable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * @author ctsims
 */
public class SerializationUtil {
    public static byte[] serialize(Externalizable data) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            data.writeExternal(new DataOutputStream(baos));
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    public static <T extends Externalizable> T deserialize(byte[] bytes, Class<T> type) {
        T t;
        try {
            t = type.newInstance();
            t.readExternal(new DataInputStream(new ByteArrayInputStream(bytes)), CommCareApplication.instance().getPrototypeFactory(CommCareApplication.instance()));
        } catch (IOException | InstantiationException
                 | DeserializationException | IllegalAccessException e1) {
            e1.printStackTrace();
            throw new RuntimeException(e1);
        }
        return t;
    }

    public static void serializeToIntent(Intent i, String name, Externalizable data) {
        i.putExtra(name, serialize(data));
    }

    public static String serializeToString(Externalizable data) {
        return Base64.encodeToString(serialize(data), Base64.DEFAULT);
    }

    public static <T extends Externalizable> T deserializeFromString(String data, Class<T> type) {
        byte[] decodedData = Base64.decode(data, Base64.DEFAULT);
        return deserialize(decodedData, type);
    }

    public static <T extends Externalizable> T deserializeFromIntent(Intent i, String name, Class<T> type) {
        if(!i.hasExtra(name)) { return null;}
        return deserialize(i.getByteArrayExtra(name), type);
    }

    public static void serializeToBundle(Bundle b, String name, Externalizable data) {
        b.putByteArray(name, serialize(data));
    }

    public static <T extends Externalizable> T deserializeFromBundle(Bundle b, String name, Class<T> type) {
        if(!b.containsKey(name)) { return null;}
        return deserialize(b.getByteArray(name), type);
    }
}