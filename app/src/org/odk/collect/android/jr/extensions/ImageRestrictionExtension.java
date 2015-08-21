package org.odk.collect.android.jr.extensions;

import org.javarosa.core.model.QuestionDataExtension;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents an extension to the "upload" question type, for setting a maximum
 * dimension for the size of an image capture
 */
public class ImageRestrictionExtension implements QuestionDataExtension {

    private int maxDimen;

    public ImageRestrictionExtension(int maxDimen) {
        this.maxDimen = maxDimen;
    }

    public int getMaxDimen() {
        return this.maxDimen;
    }

    @Override
    public void readExternal(DataInputStream dis, PrototypeFactory pf)
            throws IOException, DeserializationException {
        this.maxDimen = ExtUtil.readInt(dis);
    }

    @Override
    public void writeExternal(DataOutputStream dos) throws IOException {
        ExtUtil.writeNumeric(dos, maxDimen);
    }
}
