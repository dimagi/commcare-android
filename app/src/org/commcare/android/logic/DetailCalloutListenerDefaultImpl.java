package org.commcare.android.logic;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import org.commcare.dalvik.activities.CallOutActivity;

/**
 * Created by dancluna on 3/5/15.
 */
public class DetailCalloutListenerDefaultImpl {
    // CommCare-159503: implementing DetailCalloutListener so it will not crash the app when requesting call/sms
    // implementing classes can just delegate to this class if they want its default functionality
    public static final int CALL_OUT = 0;

    public static void callRequested(Activity act, String phoneNumber) {
        Intent intent = new Intent(act, CallOutActivity.class);
        intent.putExtra(CallOutActivity.PHONE_NUMBER, phoneNumber);
        act.startActivityForResult(intent, CALL_OUT);
    }


    public static void addressRequested(Activity act,String address) {
        Intent call;
        call = new Intent(Intent.ACTION_VIEW, Uri.parse(address));
        act.startActivity(call);
    }

    public static void playVideo(Activity act,String videoRef) {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.parse(videoRef), "video/*");
        act.startActivity(intent);
    }
}
