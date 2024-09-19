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

    int sessionStateDescriptorId;
    FormIndex formIndex;
    private boolean interruptedDueToSessionExpiration = false;

    public InterruptedFormState(int sessionStateDescriptorId, FormIndex formIndex, boolean sessionExpired) {
        this.sessionStateDescriptorId = sessionStateDescriptorId;
        this.formIndex = formIndex;
        this.interruptedDueToSessionExpiration = sessionExpired;
    }

    public InterruptedFormState() {
        // serialization only
    }


    @Override
    public void readExternal(DataInputStream in, PrototypeFactory pf)
            throws IOException, DeserializationException {
        sessionStateDescriptorId = ExtUtil.readInt(in);
        formIndex = (FormIndex)ExtUtil.read(in, FormIndex.class, pf);
        try {
            interruptedDueToSessionExpiration = ExtUtil.readBool(in);
        } catch(EOFException e){
            // interruptedDueToSessionExpiration field
        }
    }

    @Override
    public void writeExternal(DataOutputStream out) throws IOException {
        ExtUtil.writeNumeric(out, sessionStateDescriptorId);
        ExtUtil.write(out, formIndex);
        ExtUtil.writeBool(out, interruptedDueToSessionExpiration);
    }

    public int getSessionStateDescriptorId() {
        return sessionStateDescriptorId;
    }

    public FormIndex getFormIndex() {
        return formIndex;
    }

    public boolean getInterruptedDueToSessionExpiration(){
        return interruptedDueToSessionExpiration;
    }
}
