package org.commcare.connect.network.connectId;

import android.app.Activity;

import androidx.annotation.Nullable;

import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.dalvik.R;
import org.javarosa.core.services.Logger;

/**
 * Utility class for handling standardized API error responses across the configuration flow.
 * <p>
 * This class provides a centralized method to interpret and react to various
 * {@link PersonalIdApiHandler.PersonalIdOrConnectApiErrorCodes} returned by the network layer.
 * It ensures consistent user feedback (e.g. toasts, dialogs) and error recovery
 * flows (e.g. token management or outdated API messages) across all API interactions.
 * </p>
 * <p>
 * Usage Example:
 * <pre>
 *     PersonalIdApiErrorHandler.handle(requireActivity(), failureCode);
 * </pre>
 */
public class PersonalIdApiErrorHandler {

    /**
     * Handles an API error by interpreting the error code and taking an appropriate
     * user-facing action, such as displaying a toast or triggering a token renewal.
     *
     * @param activity  the context (usually the current Activity) used to display UI elements
     * @param errorCode the specific {@link PersonalIdApiHandler.PersonalIdOrConnectApiErrorCodes} to handle
     * @param t         the exception that was thrown, if any; can be null
     */
    public static String handle(Activity activity, PersonalIdApiHandler.PersonalIdOrConnectApiErrorCodes errorCode,
            @Nullable Throwable t) {
        switch (errorCode) {
            case NETWORK_ERROR:
                return activity.getString(R.string.recovery_network_unavailable);
            case TOKEN_UNAVAILABLE_ERROR:
                return activity.getString(R.string.recovery_network_token_unavailable);
            case RATE_LIMIT_EXCEEDED_ERROR:
                return activity.getString(R.string.recovery_network_cooldown);
            case FAILED_AUTH_ERROR:
                return activity.getString(R.string.recovery_network_unauthorized);
            case SERVER_ERROR:
                return activity.getString(R.string.recovery_network_server_error);
            case TOKEN_DENIED_ERROR:
                ConnectNetworkHelper.handleTokenDeniedException();
                return "";
            case OLD_API_ERROR:
                return activity.getString(R.string.recovery_network_outdated);
            case ACCOUNT_LOCKED_ERROR:
                return activity.getString(R.string.personalid_configuration_locked_account);
            case FORBIDDEN_ERROR:
                return activity.getString(R.string.personalid_configuration_process_failed_subtitle);
            case UNKNOWN_ERROR:
                return activity.getString(R.string.recovery_network_unknown);
            default:
                if (t != null) {
                    Logger.exception("Unhandled throwable passed with API error code: " + errorCode, t);
                    throw new RuntimeException(t);
                } else {
                    Logger.exception("Unhandled API error code", new RuntimeException("Unhandled API error code: " + errorCode));
                    return activity.getString(R.string.recovery_network_unknown);
                }
        }
    }
}
