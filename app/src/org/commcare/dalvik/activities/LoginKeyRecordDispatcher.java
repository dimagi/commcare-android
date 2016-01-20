package org.commcare.dalvik.activities;

import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.models.notifications.NotificationMessageFactory;
import org.commcare.android.models.notifications.NotificationMessageFactory.StockMessages;
import org.commcare.android.tasks.ManageKeyRecordListener;
import org.commcare.android.tasks.templates.HttpCalloutTask;
import org.javarosa.core.services.Logger;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class LoginKeyRecordDispatcher {
    protected static ManageKeyRecordListener<LoginActivity> buildKeyRecordListener(final boolean triggerTooManyUsers,
                                                                                   final String username) {
        return new ManageKeyRecordListener<LoginActivity>() {
            @Override
            public void keysLoginComplete(LoginActivity loginActivity) {
                if (triggerTooManyUsers) {
                    // We've successfully pulled down new user data. Should see if the user
                    // already has a sandbox and let them know that their old data doesn't transition
                    loginActivity.raiseMessage(NotificationMessageFactory.message(StockMessages.Auth_RemoteCredentialsChanged), true);
                    Logger.log(AndroidLogger.TYPE_USER, "User " + username + " has logged in for the first time with a new password. They may have unsent data in their other sandbox");
                }
                loginActivity.done();
            }

            @Override
            public void keysReadyForSync(LoginActivity loginActivity) {
                // TODO: we only wanna do this on the _first_ try. Not
                // subsequent ones (IE: On return from startOta)
                loginActivity.startOta();
            }

            @Override
            public void keysDoneOther(LoginActivity loginActivity,
                                      HttpCalloutTask.HttpCalloutOutcomes outcome) {
                switch (outcome) {
                    case AuthFailed:
                        Logger.log(AndroidLogger.TYPE_USER, "auth failed");
                        loginActivity.raiseLoginMessage(StockMessages.Auth_BadCredentials, false);
                        break;
                    case BadResponse:
                        Logger.log(AndroidLogger.TYPE_USER, "bad response");
                        loginActivity.raiseLoginMessage(StockMessages.Remote_BadRestore, true);
                        break;
                    case NetworkFailure:
                        Logger.log(AndroidLogger.TYPE_USER, "bad network");
                        loginActivity.raiseLoginMessage(StockMessages.Remote_NoNetwork, false);
                        break;
                    case NetworkFailureBadPassword:
                        Logger.log(AndroidLogger.TYPE_USER, "bad network");
                        loginActivity.raiseLoginMessage(StockMessages.Remote_NoNetwork_BadPass, true);
                        break;
                    case BadCertificate:
                        Logger.log(AndroidLogger.TYPE_USER, "bad certificate");
                        loginActivity.raiseLoginMessage(StockMessages.BadSSLCertificate, false);
                        break;
                    case UnknownError:
                        Logger.log(AndroidLogger.TYPE_USER, "unknown");
                        loginActivity.raiseLoginMessage(StockMessages.Restore_Unknown, true);
                        break;
                    default:
                        break;
                }
            }
        };
    }
}
