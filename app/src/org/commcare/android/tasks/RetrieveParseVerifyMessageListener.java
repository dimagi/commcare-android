package org.commcare.android.tasks;

/**
 * Created by wpride1 on 9/25/15.
 */
public interface RetrieveParseVerifyMessageListener {
    void downloadLinkReceived(String url);

    void downloadLinkReceivedAutoInstall(String url);

    void exceptionReceived(Exception e);
}
