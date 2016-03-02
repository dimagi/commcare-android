package org.commcare.models.framework;

import org.commcare.modern.models.MetaField;
import org.javarosa.core.services.storage.IMetaData;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Hashtable;

/**
 * @author ctsims
 */
public class Persisted implements Persistable, IMetaData {

    private static final Hashtable<Class, ArrayList<Field>> fieldOrderings = new Hashtable<>();

    protected int recordId = -1;

    @Override
    public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
        recordId = ExtUtil.readInt(in);
        String currentField = null;
        try {
            for (Field f : getPersistedFieldsInOrder()) {
                currentField = f.getName();
                readVal(f, this, in);
            }
        } catch (IllegalAccessException iae) {
            throw new DeserializationException(currentField == null ? "" : (" for field" + currentField), iae);
        }
    }

    private ArrayList<Field> getPersistedFieldsInOrder() {
        ArrayList<Field> orderings;
        synchronized (fieldOrderings) {
            orderings = fieldOrderings.get(this.getClass());
            if (orderings == null) {
                orderings = new ArrayList<>();
                fieldOrderings.put(this.getClass(), orderings);
            }
        }
        synchronized (orderings) {
            if (orderings.size() == 0) {
                for (Field f : this.getClass().getDeclaredFields()) {
                    if (f.isAnnotationPresent(Persisting.class)) {
                        orderings.add(f);
                    }
                }
                Collections.sort(orderings, orderedComparator);
            }
            return orderings;
        }
    }

    private static final Comparator<Field> orderedComparator = new Comparator<Field>() {

        @Override
        public int compare(Field f1, Field f2) {
            int i1 = f1.getAnnotation(Persisting.class).value();
            int i2 = f2.getAnnotation(Persisting.class).value();
            return (i1 < i2 ? -1 : (i1 == i2 ? 0 : 1));
        }
    };

    @Override
    public void writeExternal(DataOutputStream out) throws IOException {
        ExtUtil.writeNumeric(out, recordId);
        try {
            for (Field f : getPersistedFieldsInOrder()) {
                writeVal(f, this, out);
            }
        } catch (IllegalAccessException iae) {
            throw new RuntimeException(iae);
        }
    }

    @Override
    public void setID(int ID) {
        recordId = ID;
    }

    @Override
    public int getID() {
        return recordId;
    }

    private void readVal(Field f, Object o, DataInputStream in) throws DeserializationException, IOException, IllegalAccessException {
        Persisting p = f.getAnnotation(Persisting.class);
        Class type = f.getType();
        try {
            f.setAccessible(true);

            if (type.equals(String.class)) {
                String read = ExtUtil.readString(in);
                f.set(o, p.nullable() ? ExtUtil.nullIfEmpty(read) : read);
                return;
            } else if (type.equals(Integer.TYPE)) {
                //Primitive Integers
                f.setInt(o, ExtUtil.readInt(in));
                return;
            } else if (type.equals(Date.class)) {
                f.set(o, ExtUtil.readDate(in));
                return;
            } else if (type.isArray()) {

                //We only support byte arrays for now
                if (type.getComponentType().equals(Byte.TYPE)) {
                    f.set(o, ExtUtil.readBytes(in));
                    return;
                }
            } else if (type.equals(Boolean.TYPE)) {
                f.setBoolean(o, ExtUtil.readBool(in));
                return;
            }
        } finally {
            f.setAccessible(false);
        }

        //By Default
        throw new DeserializationException("Couldn't read persisted type " + f.getType().toString());
    }

    private void writeVal(Field f, Object o, DataOutputStream out) throws IOException, IllegalAccessException {
        try {
            Persisting p = f.getAnnotation(Persisting.class);
            Class type = f.getType();
            f.setAccessible(true);

            if (type.equals(String.class)) {
                String s = (String)f.get(o);
                ExtUtil.writeString(out, p.nullable() ? ExtUtil.emptyIfNull(s) : s);
                return;
            } else if (type.equals(Integer.TYPE)) {
                ExtUtil.writeNumeric(out, f.getInt(o));
                return;
            } else if (type.equals(Date.class)) {
                ExtUtil.writeDate(out, (Date)f.get(o));
                return;
            } else if (type.isArray()) {
                //We only support byte arrays for now
                if (type.getComponentType().equals(Byte.TYPE)) {
                    ExtUtil.writeBytes(out, (byte[])f.get(o));
                    return;
                }
            } else if (type.equals(Boolean.TYPE)) {
                ExtUtil.writeBool(out, f.getBoolean(o));
                return;
            }
        } finally {
            f.setAccessible(false);
        }

        //By Default
        throw new RuntimeException("Couldn't write persisted type " + f.getType().toString());
    }

    @Override
    public String[] getMetaDataFields() {
        ArrayList<String> fields = new ArrayList<>();

        for (Field f : this.getClass().getDeclaredFields()) {
            try {
                f.setAccessible(true);

                if (f.isAnnotationPresent(MetaField.class)) {
                    MetaField mf = f.getAnnotation(MetaField.class);
                    fields.add(mf.value());
                }
            } finally {
                f.setAccessible(false);
            }

        }

        for (Method m : this.getClass().getDeclaredMethods()) {
            try {
                m.setAccessible(true);

                if (m.isAnnotationPresent(MetaField.class)) {
                    MetaField mf = m.getAnnotation(MetaField.class);
                    fields.add(mf.value());
                }
            } finally {
                m.setAccessible(false);
            }

        }
        return fields.toArray(new String[fields.size()]);
    }

    //TODO: This looks like it's gonna be sllllooowwwww
    @Override
    public Object getMetaData(String fieldName) {
        try {
            for (Field f : this.getClass().getDeclaredFields()) {
                try {
                    f.setAccessible(true);

                    if (f.isAnnotationPresent(MetaField.class)) {
                        MetaField mf = f.getAnnotation(MetaField.class);
                        if (mf.value().equals(fieldName)) {
                            return f.get(this);
                        }
                    }
                } finally {
                    f.setAccessible(false);
                }
            }

            for (Method m : this.getClass().getDeclaredMethods()) {
                try {
                    m.setAccessible(true);

                    if (m.isAnnotationPresent(MetaField.class)) {
                        MetaField mf = m.getAnnotation(MetaField.class);
                        if (mf.value().equals(fieldName)) {
                            return m.invoke(this, (Object[])null);
                        }
                    }
                } finally {
                    m.setAccessible(false);
                }

            }

        } catch (InvocationTargetException | IllegalArgumentException | IllegalAccessException e) {
            throw new RuntimeException(e.getMessage());
        }
        //If we didn't find the field
        throw new IllegalArgumentException("No metadata field " + fieldName + " in the case storage system");
    }

}
