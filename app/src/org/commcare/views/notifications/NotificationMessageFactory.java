/**
 *
 */
package org.commcare.views.notifications;

import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.util.NoLocalizedTextException;

import java.util.Date;

/**
 * Static holder for generating notification messages which are common
 * to the application.
 * <p/>
 * Possibly implementation should be replaced in the future with some
 * sort of external data source, but doesn't seem likely.
 *
 * @author ctsims
 */
public class NotificationMessageFactory {

    //TODO: Move these to an enum for the task that they represent the return state of
    public enum StockMessages implements MessageTag {
        /**
         * The user's credentials weren't accepted on the server *
         */
        Auth_BadCredentials("login.attempt.fail.auth"),

        /**
         * The user's username/password has changed remotely on the server, but
         * is are correct on the phone. *
         */
        Auth_RemoteCredentialsChanged("login.attempt.fail.changed"),

        /**
         * The restore data sent down a password that was different than the one used
         * to authenticate
         */
        Auth_CredentialMismatch("notification.credential.mismatch"),

        /**
         * The user entered an invalid pin for the given username
         */
        Auth_InvalidPin("login.attempt.fail.pin"),

        /**
         * Server 500 when retrieving data.
         */
        Restore_RemoteError("notification.restore.remote.error"),

        /**
         * The phone had a problem parsing the data from the server *
         */
        Remote_BadRestore("notification.restore.baddata"),

        /**
         * No network connectivity *
         */
        Remote_NoNetwork("notification.restore.nonetwork"),

        /**
         * No network connectivity, password possibly wrong *
         */
        Remote_NoNetwork_BadPass("notification.restore.nonetwork.badpass"),

        /**
         * Generic 500 error *
         */
        Remote_ServerError("notification.server.error"),

        /**
         * Network timed out *
         */
        Remote_Timeout("notification.network.timeout"),

        /**
         * Unknown error during restore *
         */
        Restore_Unknown("notification.restore.unknown"),

        /**
         * Could not retrieve Form Result *
         */
        FormEntry_Unretrievable("notification.formentry.unretrievable"),

        FormEntry_Save_Error("notification.formentry.save_error"),

        /**
         * In airplane mode while trying to sync*
         */
        Sync_AirplaneMode("notification.sync.airplane"),

        /**
         * No connections while trying to sync *
         */
        Sync_NoConnections("notification.sync.connections"),

        /**
         * One of your files on the SD was bad*
         */
        Send_MalformedFile("notification.send.malformed"),

        /**
         * Your case fitler does not match the data type *
         */
        Bad_Case_Filter("notification.case.filter"),

        /**
         * Your archive is not on the local filesystem *
         */
        Bad_Archive_File("notification.install.badarchive"),

        /**
         * The phone could not store some part of the restore payload *
         */
        Storage_Full("notification.restore.storagefull"),

        /**
         * Bad SSL Certificate *
         */
        BadSSLCertificate("notification.bad.certificate");

        StockMessages(String root) {
            this.root = root;
        }

        private final String root;

        public String getLocaleKeyBase() {
            return root;
        }

        public String getCategory() {
            return "stock";
        }

    }

    public static NotificationMessage message(MessageTag message) {
        return message(message, new String[3]);
    }

    public static NotificationMessage message(MessageTag message, String customCategory) {
        return message(message, new String[3], customCategory);
    }

    public static NotificationMessage message(MessageTag message, String[] parameters) {
        return message(message, parameters, message.getCategory());
    }

    public static NotificationMessage message(MessageTag message, String[] parameters, String customCategory) {

        String base = message.getLocaleKeyBase();
        if (base == null) {
            throw new NullPointerException("No Locale Key base for message tag!");
        }

        try {
            String title = parameters[0] == null ? Localization.get(base + ".title") : Localization.get(base + ".title", new String[]{parameters[0]});
            String detail = parameters[1] == null ? Localization.get(base + ".detail") : Localization.get(base + ".detail", new String[]{parameters[1]});

            String action = null;
            try {
                action = parameters[2] == null ? Localization.get(base + ".action") : Localization.get(base + ".action", new String[]{parameters[2]});
            } catch (Exception e) {
                //No big deal, key doesn't need to exist
            }

            return new NotificationMessage(customCategory, title, detail, action, new Date());
        }
        //TODO: Release v. debug mode for these?
        catch (NoLocalizedTextException e) {
            throw new NoLocalizedTextException("Notification Message with base " + base + " is underdefined. It does not contain the minimum set of a .title and .detail definition", e.getMissingKeyNames(), e.getLocaleMissingKey());
        } catch (Exception e) {
            //Other exceptions are bad, but we don't want to crash on them
            e.printStackTrace();
            return new NotificationMessage("system", "Bad Locale Message", "Error getting locale message for base " + message.getLocaleKeyBase(), null, new Date());
        }
    }
}
