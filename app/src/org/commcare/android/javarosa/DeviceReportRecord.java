package org.commcare.android.javarosa;

import org.commcare.CommCareApplication;
import org.commcare.android.storage.framework.PersistedPlain;
import org.commcare.models.encryption.EncryptionIO;
import org.commcare.models.framework.Table;
import org.commcare.modern.models.EncryptedModel;
import org.commcare.utils.FileUtil;
import org.commcare.utils.GlobalConstants;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.services.storage.IMetaData;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;

import javax.crypto.spec.SecretKeySpec;

/**
 * A small DB record for keeping track of serialized device reports which we are planning
 * on submitting. Keeps track of the location on disk, and the key we use to encrypt it.
 *
 * Fairly similar record to what we're storing for the forms. Should possibly use that
 * one and its process
 *
 * @author ctsims
 */
@Table(DeviceReportRecord.STORAGE_KEY)
public class DeviceReportRecord extends PersistedPlain implements IMetaData, EncryptedModel {
    public static final String STORAGE_KEY = "log_records";

    private String fileName;
    private byte[] aesKey;

    public DeviceReportRecord() {
        // for externalization
    }

    public DeviceReportRecord(String fileName, byte[] aesKey) {
        this.fileName = fileName;
        this.aesKey = aesKey;
    }

    public static DeviceReportRecord generateNewRecordStub() {
        DeviceReportRecord slr = new DeviceReportRecord();
        slr.fileName = new File(
                CommCareApplication._().getCurrentApp().fsPath((GlobalConstants.FILE_CC_LOGS))
                        + FileUtil.SanitizeFileName(File.separator
                        + DateUtils.formatDateTime(new Date(), DateUtils.FORMAT_ISO8601)) + ".xml").getAbsolutePath();
        slr.aesKey = CommCareApplication._().createNewSymmetricKey().getEncoded();
        return slr;
    }

    @Override
    public boolean isEncrypted(String data) {
        return false;
    }

    @Override
    public boolean isBlobEncrypted() {
        return true;
    }

    public byte[] getKey() {
        return aesKey;
    }

    public String getFilePath() {
        return fileName;
    }

    public final OutputStream openOutputStream() throws FileNotFoundException {
        return EncryptionIO.createFileOutputStream(getFilePath(),
                new SecretKeySpec(getKey(), "AES"));
    }

    @Override
    public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
        super.readExternal(in, pf);

        fileName = ExtUtil.readString(in);
        aesKey = ExtUtil.readBytes(in);
    }

    @Override
    public void writeExternal(DataOutputStream out) throws IOException {
        super.writeExternal(out);

        ExtUtil.writeString(out, fileName);
        ExtUtil.writeBytes(out, aesKey);
    }

    @Override
    public String[] getMetaDataFields() {
        return new String[]{};
    }

    @Override
    public Object getMetaData(String fieldName) {
        throw new IllegalArgumentException("No metadata field " + fieldName + " in the storage system");
    }
}
