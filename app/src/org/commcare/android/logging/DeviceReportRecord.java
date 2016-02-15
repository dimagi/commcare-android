package org.commcare.android.logging;

import org.commcare.android.crypt.EncryptionIO;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.storage.framework.Persisted;
import org.commcare.android.storage.framework.Persisting;
import org.commcare.android.storage.framework.Table;
import org.commcare.android.util.FileUtil;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.modern.models.EncryptedModel;
import org.javarosa.core.model.utils.DateUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.util.Date;

import javax.crypto.spec.SecretKeySpec;

/**
 * A small DB record for keeping track of serialized device reports which we are planning
 * on submitting. Keeps track of the location on disk, and the key we use to encrypt it.
 * <p/>
 * Fairly similar record to what we're storing for the forms. Should possibly use that
 * one and its process
 *
 * @author ctsims
 */

@Table(DeviceReportRecord.STORAGE_KEY)
public class DeviceReportRecord extends Persisted implements EncryptedModel {
    public static final String STORAGE_KEY = "log_records";

    @Persisting(1)
    private String fileName;
    @Persisting(2)
    private byte[] aesKey;

    public DeviceReportRecord() {
        // for externalization
    }

    public DeviceReportRecord(String fileName, byte[] aesKey) {
        this.fileName = fileName;
        this.aesKey = aesKey;
    }

    public static DeviceReportRecord generateNewRecordStub() throws SessionUnavailableException {
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
}
