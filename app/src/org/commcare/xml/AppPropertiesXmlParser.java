package org.commcare.xml;

import org.commcare.CommCareApplication;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.data.xml.TransactionParser;
import org.commcare.models.encryption.AndroidSignedPermissionVerifier;
import org.commcare.suite.model.Profile;
import org.commcare.suite.model.SignedPermission;
import org.javarosa.xml.ElementParser;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

/**
 * Created by amstone326 on 4/12/16.
 */
public class AppPropertiesXmlParser extends TransactionParser<HashMap<String, Object>> {

    private static final int CURRENT_VERSION = 1;

    // True if any properties from the file being parsed were not committed because their signed
    // values could not be successfully validated
    private boolean propertiesRejected;

    public AppPropertiesXmlParser(InputStream inputStream) throws IOException {
        super(ElementParser.instantiateParser(inputStream));
    }

    @Override
    public void commit(HashMap<String, Object> parsed) throws IOException {
        for (String key : parsed.keySet()) {
            Object plainValueOrSignedPermission = parsed.get(key);
            switch (key) {
                case SignedPermission.KEY_MULTIPLE_APPS_COMPATIBILITY:
                    updateMultipleAppsCompatibility((SignedPermission)plainValueOrSignedPermission);
            }
        }
    }

    @Override
    public HashMap<String, Object> parse() throws InvalidStructureException, IOException, XmlPullParserException, UnfullfilledRequirementsException {
        checkNode("AppProperties");
        int version = Integer.parseInt(parser.getAttributeValue(null, "version"));
        if (version != CURRENT_VERSION) {
            throw new UnsupportedVersionException();
        }

        HashMap<String, Object> propertyMapping = new HashMap<String, Object>();
        int eventType = parser.next();

        do {
            if (eventType == KXmlParser.START_TAG) {
                if (parser.getName().toLowerCase().equals("property")) {
                    parseAndVerifyProperty(propertyMapping);
                }
            }
            eventType = parser.next();
        } while (eventType != KXmlParser.END_DOCUMENT);

        return propertyMapping;
    }

    private void parseAndVerifyProperty(HashMap<String, Object> properties) {
        String key = parser.getAttributeValue(null, "key");
        String value = parser.getAttributeValue(null, "value");
        String signature = parser.getAttributeValue(null, "signature");

        if (signature != null) {
            SignedPermission permission = new SignedPermission(key, value, signature);
            if (permission.verifyValue(new AndroidSignedPermissionVerifier())) {
                // If a signed property doesn't verify properly, we don't want to update based
                // upon it at all
                properties.put(key, permission);
            } else {
                propertiesRejected = true;
            }
        } else {
            properties.put(key, value);
        }
    }

    private static void updateMultipleAppsCompatibility(SignedPermission permission) {
        ApplicationRecord currentAppRecord = CommCareApplication._().getCurrentApp().getAppRecord();
        currentAppRecord.setMultipleAppsCompatibility(permission.getVerifiedValue());
        CommCareApplication._().getGlobalStorage(ApplicationRecord.class).write(currentAppRecord);

        Profile currentProfile = CommCareApplication._().getCurrentApp().getCommCarePlatform()
                .getCurrentProfile();
        currentProfile.addSignedPermission(permission);
    }

    public boolean somePropertiesFailedAuthentication() {
        return propertiesRejected;
    }

    public class UnsupportedVersionException extends RuntimeException {

    }
}
