package org.commcare.android.analytics;

import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.util.SessionStateUninitException;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.session.CommCareSession;
import org.javarosa.core.log.LogEntry;
import org.javarosa.core.model.User;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.services.storage.IMetaData;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Date;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class XPathErrorEntry extends LogEntry implements Persistable, IMetaData {
    public static final String STORAGE_KEY = "XPATH_ERROR";
    private static final String TAG = XPathErrorEntry.class.getSimpleName();
    private static final String META_DATE = "date";

    private int recordId = -1;
    private int appVersion;
    private String expression;
    private String sessionFramePath;

    public XPathErrorEntry() {
        // for externalization
    }

    protected XPathErrorEntry(String expression, String errorMessage) {
        super(AndroidLogger.TYPE_ERROR_CONFIG_STRUCTURE, errorMessage, new Date());

        this.expression = expression;
        this.sessionFramePath = getCurrentSession();
        this.appVersion = getAppVersion();
    }

    private static String getCurrentSession() {
        CommCareSession currentSession;
        try {
            currentSession = CommCareApplication._().getCurrentSession();
            // TODO PLM:
            // return currentSession.getFrame().sessionPathString();
            return "";
        } catch (SessionStateUninitException e) {
            return "";
        }
    }

    private int getAppVersion() {
        CommCareApp app = CommCareApplication._().getCurrentApp();

        if (app != null) {
            return app.getCommCarePlatform().getCurrentProfile().getVersion();
        }

        return -1;
    }

    public String getExpression() {
        return expression;
    }

    public String getSessionPath() {
        return sessionFramePath;
    }

    @Override
    public String toString() {
        return getTime().toString() + " | " +
                getMessage() + " caused by " + expression +
                "\n" + "session: " + sessionFramePath;
    }

    @Override
    public void setID(int ID) {
        recordId = ID;
    }

    @Override
    public int getID() {
        return recordId;
    }

    @Override
    public void readExternal(DataInputStream in, PrototypeFactory pf)
            throws IOException, DeserializationException {
        super.readExternal(in, pf);

        recordId = ExtUtil.readInt(in);
        appVersion = ExtUtil.readInt(in);
        expression = ExtUtil.readString(in);
        sessionFramePath = ExtUtil.readString(in);
    }

    @Override
    public void writeExternal(DataOutputStream out) throws IOException {
        super.writeExternal(out);

        ExtUtil.writeNumeric(out, recordId);
        ExtUtil.writeNumeric(out, appVersion);
        ExtUtil.writeString(out, expression);
        ExtUtil.writeString(out, sessionFramePath);
    }

    @Override
    public String[] getMetaDataFields() {
        return new String[]{META_DATE};
    }

    @Override
    public Object getMetaData(String fieldName) {
        if (META_DATE.equals(fieldName)) {
            return DateUtils.formatDate(getTime(), DateUtils.FORMAT_ISO8601);
        }

        throw new IllegalArgumentException("No metadata field " + fieldName + " for " + TAG + " model");
    }
}
