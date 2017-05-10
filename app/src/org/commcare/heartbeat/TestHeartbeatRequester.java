package org.commcare.heartbeat;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by amstone326 on 5/10/17.
 */
public class TestHeartbeatRequester extends HeartbeatRequester {

    private static String nextResponseString;

    @Override
    protected void requestHeartbeat() {
        parseTestHeartbeatResponse();
    }

    public static void setNextResponseString(String s) {
        nextResponseString = s;
    }

    protected static void parseTestHeartbeatResponse() {
        try {
            parseHeartbeatResponse(new JSONObject(nextResponseString));
        } catch (JSONException e) {
            System.out.println("Test response was not properly formed JSON");
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
