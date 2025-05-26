package org.commcare.connect.network;

import android.app.Activity;
import android.widget.Toast;

import org.commcare.dalvik.R;

/**
 * Utility class for handling standardized API error responses across the configuration flow.
 * <p>
 * This class provides a centralized method to interpret and react to various
 * {@link PersonalIdApiHandler.PersonalIdApiErrorCodes} returned by the network layer.
 * It ensures consistent user feedback (e.g. toasts, dialogs) and error recovery
 * flows (e.g. token management or outdated API messages) across all API interactions.
 * </p>
 *
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
     * @param activity   the context (usually the current Activity) used to display UI elements
     * @param errorCode  the specific {@link PersonalIdApiHandler.PersonalIdApiErrorCodes} to handle
     */
    public static void handle(Activity activity, PersonalIdApiHandler.PersonalIdApiErrorCodes errorCode) {
        switch (errorCode) {
            case INVALID_RESPONSE_ERROR:
            case JSON_PARSING_ERROR:
                Toast.makeText(activity, activity.getString(R.string.configuration_process_api_failed),
                        Toast.LENGTH_LONG).show();
                break;
            case NETWORK_ERROR:
                ConnectNetworkHelper.showNetworkError(activity);
                break;
            case TOKEN_UNAVAILABLE_ERROR:
                ConnectNetworkHelper.handleTokenUnavailableException(activity);
                break;
            case TOKEN_DENIED_ERROR:
                ConnectNetworkHelper.handleTokenDeniedException(activity);
                break;
            case OLD_API_ERROR:
                ConnectNetworkHelper.showOutdatedApiError(activity);
                break;
        }
    }
}
