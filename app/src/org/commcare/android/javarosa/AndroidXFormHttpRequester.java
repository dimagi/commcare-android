package org.commcare.android.javarosa;

import org.commcare.CommCareApplication;
import org.commcare.network.CommcareRequestGenerator;
import org.commcare.services.CommCareSessionService;
import org.commcare.util.LogTypes;
import org.commcare.utils.SessionUnavailableException;
import org.javarosa.core.model.User;
import org.javarosa.core.model.actions.FormSendCalloutHandler;
import org.javarosa.core.services.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Implements the in-form HTTP requester on the android platform.
 *
 * Uses the logged in user credentials by default, if they are available.
 *
 * Created by ctsims on 10/25/2017.
 */

public class AndroidXFormHttpRequester implements FormSendCalloutHandler {

    @Override
    public String performHttpCalloutForResponse(String url, Map<String, String> paramMap) {
        CommcareRequestGenerator generator;
        try {
            CommCareSessionService session = CommCareApplication.instance().getSession();
            User user = session.getLoggedInUser();
            generator = new CommcareRequestGenerator(user);
        } catch(SessionUnavailableException sue) {
            //auth won't be possible.
            generator = CommcareRequestGenerator.buildNoAuthGenerator();
        }

        if (paramMap == null) {
            paramMap = new HashMap<>();
        }

        try {
            Response<ResponseBody> response = generator.simpleGet(url, paramMap, new HashMap<>());
            if(response.code() != 200) {
                Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS, "HTTP Callout failed w/Response code: " + response.code() + " | and message " + response.message());
                return null;
            }

            return response.body().string();
        } catch(IOException e) {
            e.printStackTrace();
            Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS, "Error performing HTTP Callout : " +
                    e.getMessage());
            return null;
        }
    }
}
