package org.commcare.android.logic;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import org.commcare.activities.CallOutActivity;
import org.commcare.suite.model.CalloutData;

import java.util.Hashtable;

/**
 * Created by dancluna on 3/5/15.
 */
public class DetailCalloutListenerDefaultImpl {
    // Implementing DetailCalloutListener, reusing the code from @href{org.commcare.activities.EntityDetailActivity}
    // implementing classes can just delegate to this class if they want its default functionality
    // CommCare-159503: in awesome mode, the app crashed when trying to dial/sms a number
    //   due to EntitySelectActivity not implementing DetailCalloutListener.
    public static final int CALL_OUT = 0;

    public static void callRequested(Activity act, String phoneNumber) {
        Intent intent = new Intent(act, CallOutActivity.class);
        intent.putExtra(CallOutActivity.PHONE_NUMBER, phoneNumber);
        act.startActivityForResult(intent, CALL_OUT);
    }


    public static void addressRequested(Activity act, String address) {
        Intent call;
        call = new Intent(Intent.ACTION_VIEW, Uri.parse(address));
        act.startActivity(call);
    }

    public static void playVideo(Activity act, String videoRef) {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.parse(videoRef), "video/*");
        act.startActivity(intent);
    }

    public static void performCallout(Activity act, CalloutData callout, int id) {
        Intent i = new Intent(callout.getActionName());

        Hashtable<String, String> extras = callout.getExtras();

        for (String key : extras.keySet()) {
            i.putExtra(key, extras.get(key));
        }
        try {
            act.startActivityForResult(i, id);
        } catch (ActivityNotFoundException anfe) {
            Toast.makeText(act,
                    "No application found for action: " + callout.getActionName(),
                    Toast.LENGTH_LONG).show();
        }
    }
}
