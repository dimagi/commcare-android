package org.commcare.connect.network;

import android.content.Context;

import androidx.annotation.Nullable;

import org.commcare.connect.network.base.BaseApiHandler;
import org.commcare.dalvik.R;
import org.javarosa.core.services.Logger;

import java.util.EnumSet;

/**
 * Utility class for handling standardized API error responses across the configuration flow.
 * <p>
 * This class provides a centralized method to interpret and react to various
 * {@link BaseApiHandler.PersonalIdOrConnectApiErrorCodes} returned by the network layer.
 * It ensures consistent user feedback (e.g. toasts, dialogs) and error recovery
 * flows (e.g. token management or outdated API messages) across all API interactions.
 * </p>
 * <p>
 * Usage Example:
 * <pre>
 *     PersonalIdOrConnectApiErrorHandler.handle(requireActivity(), failureCode);
 * </pre>
 */
public class PersonalIdOrConnectApiErrorHandler {

    /**
     * Handles an API error by interpreting the error code and taking an appropriate
     * user-facing action, such as displaying a toast or triggering a token renewal.
     *
     * @param context   the context (usually the current Activity) used to display UI elements
     * @param errorCode the specific {@link BaseApiHandler.PersonalIdOrConnectApiErrorCodes} to handle
     * @param t         the exception that was thrown, if any; can be null
     */
    public static String handle(
            Context context,
            BaseApiHandler.PersonalIdOrConnectApiErrorCodes errorCode,
            @Nullable Throwable t
    ) {
        switch (errorCode) {
            case NETWORK_ERROR:
                return context.getString(R.string.recovery_network_unavailable);
            case TOKEN_UNAVAILABLE_ERROR:
                return context.getString(R.string.recovery_network_token_unavailable);
            case RATE_LIMIT_EXCEEDED_ERROR:
                return context.getString(R.string.recovery_network_cooldown);
            case FAILED_AUTH_ERROR:
                return context.getString(R.string.recovery_network_unauthorized);
            case SERVER_ERROR:
                return context.getString(R.string.recovery_network_server_error);
            case TOKEN_DENIED_ERROR:
                TokenExceptionHandler.INSTANCE.handleTokenDeniedException();
                return "";
            case OLD_API_ERROR:
                return context.getString(R.string.recovery_network_outdated);
            case ACCOUNT_LOCKED_ERROR:
                return context.getString(R.string.personalid_configuration_locked_account);
            case FORBIDDEN_ERROR:
                return context.getString(R.string.network_forbidden_error);
            case UNKNOWN_ERROR:
                return context.getString(R.string.recovery_network_unknown);
            case INCORRECT_OTP_ERROR:
                return context.getString(R.string.personalid_incorrect_otp);
            default:
                if (t != null) {
                    Logger.exception("Unhandled throwable passed with API error code: " + errorCode, t);
                    throw new RuntimeException(t);
                } else {
                    Logger.exception("Unhandled API error code", new RuntimeException("Unhandled API error code: " + errorCode));
                    return context.getString(R.string.recovery_network_unknown);
                }
        }
    }

    /**
     * Errors for which UI must show a blocking experience.
     */
    private static final EnumSet<BaseApiHandler.PersonalIdOrConnectApiErrorCodes> BLOCKING_ERRORS =
            EnumSet.of(
                    BaseApiHandler.PersonalIdOrConnectApiErrorCodes.NETWORK_ERROR,
                    BaseApiHandler.PersonalIdOrConnectApiErrorCodes.TOKEN_UNAVAILABLE_ERROR,
                    BaseApiHandler.PersonalIdOrConnectApiErrorCodes.RATE_LIMIT_EXCEEDED_ERROR,
                    BaseApiHandler.PersonalIdOrConnectApiErrorCodes.FAILED_AUTH_ERROR,
                    BaseApiHandler.PersonalIdOrConnectApiErrorCodes.SERVER_ERROR,
                    BaseApiHandler.PersonalIdOrConnectApiErrorCodes.TOKEN_DENIED_ERROR,
                    BaseApiHandler.PersonalIdOrConnectApiErrorCodes.ACCOUNT_LOCKED_ERROR,
                    BaseApiHandler.PersonalIdOrConnectApiErrorCodes.FORBIDDEN_ERROR
            );

    public static boolean isBlockingError(BaseApiHandler.PersonalIdOrConnectApiErrorCodes error) {
        return BLOCKING_ERRORS.contains(error);
    }
}
