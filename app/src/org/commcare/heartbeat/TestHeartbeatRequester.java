package org.commcare.heartbeat;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by amstone326 on 5/10/17.
 */
public class TestHeartbeatRequester extends HeartbeatRequester {

    private static String nextResponseString;
    public static boolean responseWasParsed;

    @Override
    protected void requestHeartbeat() {
        try {
            parseHeartbeatResponse(new JSONObject(nextResponseString));
            responseWasParsed = true;
        } catch (JSONException e) {
            System.out.println("Test response was not properly formed JSON");
        }
    }

    public static void setNextResponseString(String s) {
        nextResponseString = s;
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
