package org.commcare.tasks;

import android.util.Pair;

import org.commcare.tasks.templates.CommCareTask;
import org.commcare.utils.SigningUtil;

public abstract class RetrieveParseVerifyMessageTask<R> extends CommCareTask<String, Void, String, R> {

    private Exception exception;
    private final RetrieveParseVerifyMessageListener listener;
    private final boolean installTriggeredManually;

    public RetrieveParseVerifyMessageTask(RetrieveParseVerifyMessageListener listener,
                                          boolean installTriggeredManually) {
        this.listener = listener;
        this.installTriggeredManually = installTriggeredManually;
    }

    @Override
    protected String doTaskBackground(String[] params) {
        try {
            String url = SigningUtil.trimMessagePayload(params[0]);
            String messagePayload = SigningUtil.convertUrlToPayload(url);
            byte[] messagePayloadBytes = SigningUtil.getBytesFromString(messagePayload);
            Pair<String, byte[]> messageAndBytes = SigningUtil.getUrlAndSignatureFromPayload(messagePayloadBytes);
            return SigningUtil.verifyMessageAndBytes(messageAndBytes.first, messageAndBytes.second);
        } catch (Exception e) {
            this.exception = e;
            return null;
        }
    }

    protected void onPostExecute(String url) {
        if (exception != null) {
            listener.exceptionReceived(exception);
        }
        if (installTriggeredManually) {
            listener.downloadLinkReceivedAutoInstall(url);
        } else {
            listener.downloadLinkReceived(url);
        }
    }
}