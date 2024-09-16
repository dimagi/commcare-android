package org.commcare.models.database;

import org.javarosa.core.model.FormIndex;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.Externalizable;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;

/**
 * Model to store info about an interrupted form
 */
public class InterruptedFormState implements Externalizable {

    private int sessionStateDescriptorId;
    private FormIndex formIndex;
    private int formRecordId = -1;

    public InterruptedFormState(int sessionStateDescriptorId, FormIndex formIndex, int formRecordId) {
        this.sessionStateDescriptorId = sessionStateDescriptorId;
        this.formIndex = formIndex;
        this.formRecordId = formRecordId;
    }

    public InterruptedFormState() {
        // serialization only
    }


    @Override
    public void readExternal(DataInputStream in, PrototypeFactory pf)
            throws IOException, DeserializationException {
        sessionStateDescriptorId = ExtUtil.readInt(in);
        formIndex = (FormIndex)ExtUtil.read(in, FormIndex.class, pf);
        try{
            formRecordId = ExtUtil.readInt(in);
        } catch(EOFException e){
            // this is to catch errors caused by EOF when updating from the previous model which didn't have the
            // formRecordId field
        }
    }

    @Override
    public void writeExternal(DataOutputStream out) throws IOException {
        ExtUtil.writeNumeric(out, sessionStateDescriptorId);
        ExtUtil.write(out, formIndex);
        ExtUtil.writeNumeric(out, formRecordId);
    }

    public int getSessionStateDescriptorId() {
        return sessionStateDescriptorId;
    }

    public FormIndex getFormIndex() {
        return formIndex;
    }

    public int getFormRecordId() {
        return formRecordId;
    }
}
