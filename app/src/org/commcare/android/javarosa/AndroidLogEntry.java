package org.commcare.android.javarosa;

import org.javarosa.core.log.LogEntry;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.services.storage.IMetaData;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Date;

/**
 * @author ctsims
 */
public class AndroidLogEntry extends LogEntry implements Persistable, IMetaData {

    public static final String STORAGE_KEY = "commcarelogs";

    private static final String META_TYPE = "type";
    private static final String META_DATE = "date";

    private int recordId = -1;

    /**
     * Serialization only
     */
    public AndroidLogEntry() {

    }

    public AndroidLogEntry(String type, String message, Date date) {
        super(type, message, date);
    }

    @Override
    public void readExternal(DataInputStream in, PrototypeFactory pf)
            throws IOException, DeserializationException {
        recordId = ExtUtil.readInt(in);
        super.readExternal(in, pf);
    }

    @Override
    public void writeExternal(DataOutputStream out) throws IOException {
        ExtUtil.writeNumeric(out, recordId);
        super.writeExternal(out);
    }

    @Override
    public Date getTime() {
        return time;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public String[] getMetaDataFields() {
        return new String[]{META_TYPE, META_DATE};
    }

    @Override
    public Object getMetaData(String fieldName) {
        if (META_DATE.equals(fieldName)) {
            return DateUtils.formatDate(time, DateUtils.FORMAT_ISO8601);
        } else if (META_TYPE.equals(fieldName)) {
            return type;
        }
        throw new IllegalArgumentException("No metadata field " + fieldName + " for Log Entry Cache models");
    }

    @Override
    public void setID(int ID) {
        this.recordId = ID;
    }

    @Override
    public int getID() {
        return this.recordId;
    }
}
