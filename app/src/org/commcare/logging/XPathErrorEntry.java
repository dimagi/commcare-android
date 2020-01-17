package org.commcare.logging;

import org.commcare.android.javarosa.AndroidLogEntry;
import org.commcare.util.LogTypes;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Date;

/**
 * Log entry for xpath errors, capturing the expression, session, and cc app version.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class XPathErrorEntry extends AndroidLogEntry {
    public static final String STORAGE_KEY = "XPATH_ERROR";

    private String expression;

    public XPathErrorEntry() {
        // for externalization
    }

    protected XPathErrorEntry(String expression, String errorMessage) {
        super(LogTypes.TYPE_ERROR_CONFIG_STRUCTURE, errorMessage, new Date());

        if (expression == null) {
            this.expression = "";
        } else {
            this.expression = expression;
        }
    }

    public String getExpression() {
        return expression;
    }

    @Override
    public String toString() {
        return getTime().toString() + " | " +
                getMessage() + " caused by " + expression +
                "\n" + "session: " + getReadableSession();
    }

    @Override
    public void readExternal(DataInputStream in, PrototypeFactory pf)
            throws IOException, DeserializationException {
        super.readExternal(in, pf);
        expression = ExtUtil.readString(in);
    }

    @Override
    public void writeExternal(DataOutputStream out) throws IOException {
        super.writeExternal(out);
        ExtUtil.writeString(out, expression);
    }
}
