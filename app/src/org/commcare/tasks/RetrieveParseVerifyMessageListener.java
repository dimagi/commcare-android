package org.commcare.tasks;

/**
 * @author Will Pride (wpride@dimagi.com)
 */
public interface RetrieveParseVerifyMessageListener {
    void downloadLinkReceived(String url);

    void downloadLinkReceivedAutoInstall(String url);

    void exceptionReceived(Exception e, boolean notify);
}
