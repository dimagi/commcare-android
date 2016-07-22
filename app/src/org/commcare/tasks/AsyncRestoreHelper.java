package org.commcare.tasks;

import org.apache.http.Header;
import org.commcare.logging.AndroidLogger;
import org.commcare.network.RemoteDataPullResponse;
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

    private DataPullTask syncTask;

    public long retryAtTime = -1;
    public int serverProgressCompletedSoFar = -1;
    protected int serverProgressTotal = -1;
    protected int lastReportedServerProgressValue = 0;

    public AsyncRestoreHelper(DataPullTask task) {
        this.syncTask = task;
    }

    protected ResultAndError<DataPullTask.PullTaskResult> handleRetryResponseCode(RemoteDataPullResponse response) {
        Header retryHeader = response.getRetryHeader();
        if (retryHeader == null) {
            return new ResultAndError<>(DataPullTask.PullTaskResult.BAD_DATA);
        }
        try {
            long waitTimeInMilliseconds = Integer.parseInt(retryHeader.getValue()) * 1000;
            retryAtTime = System.currentTimeMillis() + waitTimeInMilliseconds;
            if (!parseProgressFromRetryResult(response)) {
                return new ResultAndError<>(DataPullTask.PullTaskResult.BAD_DATA);
            }
            return new ResultAndError<>(DataPullTask.PullTaskResult.RETRY_NEEDED);
        } catch (NumberFormatException e) {
            Logger.log(AndroidLogger.TYPE_USER, "Invalid Retry-After header value: "
                    + retryHeader.getValue());
            return new ResultAndError<>(DataPullTask.PullTaskResult.BAD_DATA);
        }
    }

    protected boolean parseProgressFromRetryResult(RemoteDataPullResponse response) {
        try {
            InputStream stream = response.writeResponseToCache(syncTask.context).retrieveCache();
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
        } catch (IOException | XmlPullParserException e) {
            Logger.log(AndroidLogger.TYPE_USER,
                    "Error while parsing progress values of retry result");
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
        int amountOfProgressToCoverThisCycle =
                serverProgressCompletedSoFar - lastReportedServerProgressValue;
        if (amountOfProgressToCoverThisCycle == 0) {
            return;
        }
        long intervalAllottedPerProgressUnit =
                millisUntilNextAttempt / amountOfProgressToCoverThisCycle;

        final Timer reportServerProgressTimer = new Timer();
        reportServerProgressTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (lastReportedServerProgressValue == serverProgressCompletedSoFar) {
                    reportServerProgressTimer.cancel();
                    reportServerProgressTimer.purge();
                    return;
                }
                syncTask.reportServerProgress(++lastReportedServerProgressValue, serverProgressTotal);
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

}
