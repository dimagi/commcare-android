package org.commcare.heartbeat;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by amstone326 on 5/10/17.
 */
public class TestHeartbeatRequester extends HeartbeatRequester {

    private static List<String> nextResponseStrings = new ArrayList<>();
    public static boolean responseWasParsed;

    public TestHeartbeatRequester(boolean forRecoveryMeasures) {
        super(forRecoveryMeasures);
    }

    @Override
    public void requestHeartbeat() {
        try {
            parseStandardHeartbeatResponse(new JSONObject(nextResponseStrings.remove(0)));
            responseWasParsed = true;
        } catch (JSONException e) {
            System.out.println("Test response was not properly formed JSON");
        }
    }

    public static void setNextResponseStrings(String[] responses) {
        nextResponseStrings.clear();
        for (int i = 0; i < responses.length; i++) {
            nextResponseStrings.add(responses[i]);
        }
    }

}
