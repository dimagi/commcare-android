package org.commcare.tasks;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.util.Pair;

import org.commcare.tasks.templates.CommCareTask;
import org.commcare.utils.GlobalConstants;
import org.commcare.utils.SigningUtil;
import org.joda.time.DateTime;

/**
 * Tries to find an install sms sent to the device of the format:
 *     [commcare app - do not delete] https://www.commcarehq.org/a/gc/sms/app_info/7c7d49fbef59b703fb468e20d52a21e4/
 *
 * If found:
 *  - Follows the http url to get app install info, which is base64-encoded
 *  - Base64 decodes that result into a string which looks like:
 *      ccapp: http://bit.ly/2cDCv0e signature: <binary signature>
 *  - Verifies the url is signed correctly using the signature and returns the
 *    url to be used to install the app
 */
public abstract class RetrieveParseVerifyMessageTask<R> extends CommCareTask<Void, Void, String, R> {

    /**
     * How many sms messages to scan over looking for commcare install link
     */
    private static final int SMS_CHECK_COUNT = 100;

    private Exception exception;
    private final RetrieveParseVerifyMessageListener listener;
    private final boolean installTriggeredManually;
    private final ContentResolver contentResolver;

    public RetrieveParseVerifyMessageTask(RetrieveParseVerifyMessageListener listener,
                                          ContentResolver contentResolver,
                                          boolean installTriggeredManually) {
        this.listener = listener;
        this.contentResolver = contentResolver;
        this.installTriggeredManually = installTriggeredManually;
    }

    @Override
    protected String doTaskBackground(Void... params) {
        // http://stackoverflow.com/questions/11301046/search-sms-inbox
        final Uri SMS_INBOX = Uri.parse("content://sms/inbox");

        DateTime oneWeekAgo = (new DateTime()).minusDays(7);
        Cursor cursor = contentResolver.query(SMS_INBOX,
                null, "date >? ",
                new String[]{"" + oneWeekAgo.getMillis()},
                "date DESC");

        int messageIterationCount = 0;
        if (cursor != null) {
            try {
                while (cursor.moveToNext() && messageIterationCount <= SMS_CHECK_COUNT) { // must check the result to prevent exception
                    messageIterationCount++;
                    String textMessageBody = cursor.getString(cursor.getColumnIndex("body"));
                    if (textMessageBody.contains(GlobalConstants.SMS_INSTALL_KEY_STRING)) {
                        return processSMS(textMessageBody);
                    }
                }
            } finally {
                cursor.close();
            }
        }
        return null;
    }

    private String processSMS(String smsText) {
        try {
            String url = SigningUtil.trimMessagePayload(smsText);
            String messagePayload = SigningUtil.convertEncodedUrlToPayload(url);
            byte[] messagePayloadBytes = SigningUtil.getBytesFromString(messagePayload);
            Pair<String, byte[]> messageAndBytes = SigningUtil.getUrlAndSignatureFromPayload(messagePayloadBytes);
            return SigningUtil.verifyMessageAndBytes(messageAndBytes.first, messageAndBytes.second);
        } catch (Exception e) {
            this.exception = e;
            return null;
        }
    }

    @Override
    protected void onPostExecute(String url) {
        super.onPostExecute(url);
        if (exception != null) {
            listener.exceptionReceived(exception, installTriggeredManually);
        }
        if (installTriggeredManually) {
            listener.downloadLinkReceivedAutoInstall(url);
        } else {
            listener.downloadLinkReceived(url);
        }
    }
}
