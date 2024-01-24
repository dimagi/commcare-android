package org.commcare.tasks;

import org.commcare.network.RemoteDataPullResponse;
import org.commcare.util.EncryptionKeyHelper;
import org.commcare.util.LogTypes;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.javarosa.xml.ElementParser;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Keeps tracking of everything DataPullTask needs to know and do in the case that the server is
 * performing an async restore, including parsing the retry response and reporting incremental
 * server progress during the retry wait period
 */
public class AsyncRestoreHelper {

    private final DataPullTask syncTask;

    public long retryAtTime = -1;
    public int serverProgressCompletedSoFar = -1;
    private int serverProgressTotal = -1;
    private int lastReportedServerProgressValue = 0;

    public AsyncRestoreHelper(DataPullTask task) {
        this.syncTask = task;
    }

    protected ResultAndError<DataPullTask.PullTaskResult> handleRetryResponseCode(RemoteDataPullResponse response) {
        String retryHeader = response.getRetryHeader();
        if (retryHeader == null) {
            return new ResultAndError<>(DataPullTask.PullTaskResult.BAD_DATA);
        }
        try {
            long waitTimeInMilliseconds = Integer.parseInt(retryHeader) * 1000;
            Logger.log(LogTypes.TYPE_USER, "Retry-After header value was " + waitTimeInMilliseconds);
            if (waitTimeInMilliseconds <= 0) {
                throw new InvalidWaitTimeException(
                        "Server response included a Retry-After header value of " + waitTimeInMilliseconds);
            }

            retryAtTime = System.currentTimeMillis() + waitTimeInMilliseconds;
            if (!parseProgressFromRetryResult(response)) {
                return new ResultAndError<>(DataPullTask.PullTaskResult.BAD_DATA);
            }
            return new ResultAndError<>(DataPullTask.PullTaskResult.RETRY_NEEDED);
        } catch (NumberFormatException e) {
            Logger.log(LogTypes.TYPE_USER, "Invalid Retry-After header value: " + retryHeader);
            return new ResultAndError<>(DataPullTask.PullTaskResult.BAD_DATA);
        }
    }

    private boolean parseProgressFromRetryResult(RemoteDataPullResponse response) {
        InputStream stream = null;
        try {
            stream = response.writeResponseToCache(syncTask.context).retrieveCache();
            KXmlParser parser = ElementParser.instantiateParser(stream);
            parser.next();
            int eventType = parser.getEventType();
            do {
                if (eventType == KXmlParser.START_TAG) {
                    if (parser.getName().toLowerCase().equals("progress")) {
                        serverProgressCompletedSoFar = Integer.parseInt(
                                parser.getAttributeValue(null, "done"));
                        serverProgressTotal = Integer.parseInt(
                                parser.getAttributeValue(null, "total"));
                        return true;
                    }
                }
                eventType = parser.next();
            } while (eventType != KXmlParser.END_DOCUMENT);
        } catch (IOException | XmlPullParserException | EncryptionKeyHelper.EncryptionKeyException e) {
            Logger.log(LogTypes.TYPE_USER,
                    "Error while parsing progress values of retry result");
        } finally {
            StreamsUtil.closeStream(stream);
        }
        return false;
    }

    /**
     * In order to achieve the impression of the progress bar updating smoothly during the wait
     * period after a retry response is received, use a timer task to report incremental progress
     * from the state we were last in to the progress value that the server just reported back to
     * us, splitting up the wait time into uniform time periods for reporting each unit of progress.
     */
    protected void startReportingServerProgress() {
        long millisUntilNextAttempt = retryAtTime - System.currentTimeMillis();
        if (millisUntilNextAttempt <= 0) {
            Logger.log(LogTypes.TYPE_USER, "startReportingServerProgress() was called after " +
                    "retryAtTime was already reached. retryAtTime is set to: " + retryAtTime);
            // Since we're already at the retry time, just report the current progress once instead
            // of starting a timer
            syncTask.reportServerProgress(serverProgressCompletedSoFar, serverProgressTotal);
            return;
        }
        int amountOfProgressToCoverThisCycle =
                serverProgressCompletedSoFar - lastReportedServerProgressValue;
        if (amountOfProgressToCoverThisCycle <= 0) {
            // HQ occasionally sends back a progress value that is less than what was sent on the
            // last response; when this happens, just report the last value
            syncTask.reportServerProgress(lastReportedServerProgressValue, serverProgressTotal);
            return;
        }
        long intervalAllottedPerProgressUnit =
                millisUntilNextAttempt / amountOfProgressToCoverThisCycle;
        if (intervalAllottedPerProgressUnit < 1) {
            intervalAllottedPerProgressUnit = 1;
        }

        final Timer reportServerProgressTimer = new Timer();
        reportServerProgressTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if ((lastReportedServerProgressValue == serverProgressCompletedSoFar) ||
                        (lastReportedServerProgressValue == serverProgressTotal)) {
                    reportServerProgressTimer.cancel();
                    reportServerProgressTimer.purge();
                    return;
                }
                // sometimes due to invald data from HQ, mobile gets in a state where the lastReportedServerProgressValue
                // overshoots the serverProgressTotal and cause the progress bar to be increased in a fashion similar to
                // 10/10 , 11/11, 12/12 and so on. We wanna avoid that behaviour by using a "less than" check below.
                if (lastReportedServerProgressValue < serverProgressTotal) {
                    syncTask.reportServerProgress(++lastReportedServerProgressValue, serverProgressTotal);
                }
            }
        }, 0, intervalAllottedPerProgressUnit);
    }

    /**
     * If we were showing a progress bar for server progress, make it fill up before we proceed
     */
    protected void completeServerProgressBarIfShowing() {
        if (lastReportedServerProgressValue > 0) {
            syncTask.reportServerProgress(serverProgressTotal, serverProgressTotal);
        }
    }

    protected boolean retryWaitPeriodInProgress() {
        return retryAtTime != -1 && retryAtTime > System.currentTimeMillis();
    }

    private class InvalidWaitTimeException extends RuntimeException {

        public InvalidWaitTimeException(String messsage) {
            super(messsage);
        }
    }

}
