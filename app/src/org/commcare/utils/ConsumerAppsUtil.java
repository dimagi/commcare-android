package org.commcare.utils;

import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareHomeActivity;
import org.commcare.engine.resource.installers.SingleAppInstallation;
import org.commcare.views.dialogs.CustomProgressDialog;
import org.javarosa.core.model.User;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.xml.ElementParser;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by amstone326 on 5/12/16.
 */
public class ConsumerAppsUtil {

    public static void checkForChangedLocalRestoreFile(CommCareHomeActivity context) {
        User loggedInUser = CommCareApplication._().getSession().getLoggedInUser();
        if (!loggedInUser.getLastSyncToken().equals(ConsumerAppsUtil.getSyncTokenOfLocalRestoreFile())) {
            context.getFormAndDataSyncer().performLocalRestore(
                    context, loggedInUser.getUsername(), loggedInUser.getCachedPwd());
        }
    }

    private static String getSyncTokenOfLocalRestoreFile() {
        try {
            InputStream is = ReferenceManager._().DeriveReference(SingleAppInstallation.LOCAL_RESTORE_REFERENCE).getStream();
            KXmlParser parser = ElementParser.instantiateParser(is);
            parser.next();
            int eventType = parser.getEventType();
            do {
                if (eventType == KXmlParser.START_TAG) {
                    if (parser.getName().toLowerCase().equals("restore_id")) {
                        parser.next();
                        return parser.getText();
                    }
                }
                eventType = parser.next();
            } while (eventType != KXmlParser.END_DOCUMENT);
        } catch (IOException | XmlPullParserException | InvalidReferenceException e) {
            return null;
        }
        return null;
    }

    public static CustomProgressDialog getGenericConsumerAppsProgressDialog(int taskId, boolean addProgressBar) {
        CustomProgressDialog d = CustomProgressDialog
                .newInstance("Starting Up", "Initializing your application...", taskId);
        if (addProgressBar) {
            d.addProgressBar();
        }
        return d;
    }
}
