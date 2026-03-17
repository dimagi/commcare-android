package org.commcare.logging;

import android.os.Build;

import com.google.common.io.CountingOutputStream;
import com.google.firebase.perf.metrics.Trace;

import org.commcare.AppUtils;
import org.commcare.CommCareApplication;
import org.commcare.android.javarosa.DeviceReportRecord;
import org.commcare.google.services.analytics.CCPerfMonitoring;
import org.commcare.models.database.SqlStorage;
import org.javarosa.core.model.User;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.util.PropertyUtils;
import org.kxml2.io.KXmlSerializer;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;

import static org.commcare.utils.FileUtil.XML_EXTENSION;

/**
 * This class generates and serializes a device report to either a byte array
 * or to a file as designated by a log record
 *
 * @author ctsims
 */
public class DeviceReportWriter {
    public static final String XMLNS = "http://code.javarosa.org/devicereport";

    private final XmlSerializer serializer;
    private final CountingOutputStream countingOutputStream;
    private final ArrayList<DeviceReportElement> elements = new ArrayList<>();
    private final boolean encryptionWithKeystore;
    private final boolean skipPerfTracing;

    public DeviceReportWriter(DeviceReportRecord record) throws IOException {
        this(record.openOutputStream(), record.shouldUseKeystoreKey(), false);
    }

    /**
     * Constructor for in-memory report generation where the output stream is a ByteArrayOutputStream and no file
     * encryption occurs. When skipTracing is true, file encryption performance tracing is omitted
     */
    public DeviceReportWriter(OutputStream outputStream, boolean skipPerfTracing) throws IOException {
        this(outputStream, false, skipPerfTracing);
    }

    private DeviceReportWriter(
            OutputStream outputStream,
            boolean keystoreEncrypted,
            boolean skipPerfTracing
    ) throws IOException {
        countingOutputStream = new CountingOutputStream(outputStream);
        encryptionWithKeystore = keystoreEncrypted;
        this.skipPerfTracing = skipPerfTracing;

        serializer = new KXmlSerializer();
        serializer.setOutput(countingOutputStream, "UTF-8");
        serializer.setPrefix("", XMLNS);

        serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
    }


    public void addReportElement(DeviceReportElement element) {
        this.elements.add(element);
    }

    public void write() throws IllegalArgumentException, IllegalStateException, IOException {
        Trace trace = null;
        if (!skipPerfTracing) {
            CCPerfMonitoring.INSTANCE.startTracing(CCPerfMonitoring.TRACE_FILE_ENCRYPTION_TIME);
        }
        try {
            serializer.startDocument("UTF-8", null);
            serializer.startTag(XMLNS, "device_report");
            try {
                //All inner elements are supposed to catch their errors and wrap them, so we
                //can safely catch any of the processing issues
                try {
                    writeHeader();
                } catch (Exception e) {
                }
                try {
                    writeUserReport();
                } catch (Exception e) {
                }

                for (DeviceReportElement element : elements) {
                    try {
                        element.writeToDeviceReport(serializer);
                    } catch (Exception e) {
                    }
                }

                writeMetaBlock();
            } finally {
                serializer.endTag(XMLNS, "device_report");
            }

            serializer.endDocument();
        } finally {
            try {
                CCPerfMonitoring.INSTANCE.stopFileEncryptionTracing(
                        trace,
                        countingOutputStream.getCount(),
                        XML_EXTENSION,
                        encryptionWithKeystore
                );
                countingOutputStream.close();
            } catch (IOException e) {
            }
        }
    }

    private void writeMetaBlock() throws IllegalArgumentException, IllegalStateException, IOException {
        serializer.startTag(XMLNS, "meta");
        writeText("instanceID", PropertyUtils.genUUID());
        serializer.endTag(XMLNS, "meta");
    }

    private void writeHeader() throws IllegalArgumentException, IllegalStateException, IOException {
        CommCareApplication application = CommCareApplication.instance();

        String did = application.getPhoneId();
        writeText("device_id", did);
        writeText("report_date", DateUtils.formatDateTime(new Date(), DateUtils.FORMAT_ISO8601));
        writeText("app_version", AppUtils.getCurrentVersionString());
        writeText("device_model", Build.MODEL);
        writeText("android_version", Build.VERSION.RELEASE);
    }

    private void writeUserReport() throws IllegalArgumentException, IllegalStateException, IOException {
        SqlStorage<User> storage = CommCareApplication.instance().getUserStorage(User.STORAGE_KEY, User.class);

        serializer.startTag(XMLNS, "user_subreport");

        try {
            for (User u : storage) {
                writeUser(u);
            }
        } finally {
            serializer.endTag(XMLNS, "user_subreport");
        }
    }

    private void writeUser(User user) throws IllegalArgumentException, IllegalStateException, IOException {
        serializer.startTag(XMLNS, "user");

        try {
            writeText("username", user.getUsername());
            writeText("user_id", user.getUniqueId());
            writeText("sync_token", user.getLastSyncToken());
        } finally {
            serializer.endTag(XMLNS, "user");
        }
    }

    private void writeText(String element, String text) throws IllegalArgumentException, IllegalStateException,
            IOException {
        serializer.startTag(XMLNS, element);
        try {
            serializer.text(text);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            serializer.endTag(XMLNS, element);
        }
    }
}
