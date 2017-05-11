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


    @Override
    protected void requestHeartbeat() {
        try {
            parseHeartbeatResponse(new JSONObject(nextResponseStrings.remove(0)));
            responseWasParsed = true;
        } catch (JSONException e) {
            System.out.println("Test response was not properly formed JSON");
        }
    }

    public static void setNextResponseStrings(String[] responses) {
        for (int i = 0; i < responses.length; i++) {
            nextResponseStrings.add(responses[i]);
        }
    }

    protected static void simulateRequestGettingStuck() {
        System.out.println("Before sleeping");
        try {
            Thread.sleep(5*1000);
        } catch (InterruptedException e) {
            System.out.println("TEST ERROR: sleep was interrupted");
        }
        System.out.println("After sleeping");
    }
}
