package org.commcare.android.logging;

import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.models.database.SqlStorage;
import org.javarosa.core.model.User;
import org.javarosa.core.model.utils.DateUtils;
import org.kxml2.io.KXmlSerializer;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;

/**
 * This class generates and serializes a device report to either a byte array
 * or to a file as designated by a log record
 *
 * @author ctsims
 */
public class DeviceReportWriter {
    public static final String XMLNS = "http://code.javarosa.org/devicereport";

    private final XmlSerializer serializer;
    private final OutputStream os;
    private final ArrayList<DeviceReportElement> elements = new ArrayList<>();

    public DeviceReportWriter(DeviceReportRecord record) throws IOException {
        this(record.openOutputStream());
    }

    public DeviceReportWriter(OutputStream outputStream) throws IOException {
        os = outputStream;

        serializer = new KXmlSerializer();
        serializer.setOutput(os, "UTF-8");
        serializer.setPrefix("", XMLNS);

        serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
    }


    public void addReportElement(DeviceReportElement element) {
        this.elements.add(element);
    }

    public void write() throws IllegalArgumentException, IllegalStateException, IOException {
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
            } finally {
                serializer.endTag(XMLNS, "device_report");
            }

            serializer.endDocument();
        } finally {
            try {
                os.close();
            } catch (IOException e) {
            }
        }
    }

    private void writeHeader() throws IllegalArgumentException, IllegalStateException, IOException {
        CommCareApplication application = CommCareApplication._();

        String did = application.getPhoneId();
        writeText("device_id", did);
        writeText("report_date", DateUtils.formatDateTime(new Date(), DateUtils.FORMAT_ISO8601));
        writeText("app_version", application.getCurrentVersionString());
    }

    private void writeUserReport() throws IllegalArgumentException, IllegalStateException, IOException {
        SqlStorage<User> storage = CommCareApplication._().getUserStorage(User.STORAGE_KEY, User.class);

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

    private void writeText(String element, String text) throws IllegalArgumentException, IllegalStateException, IOException {
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
