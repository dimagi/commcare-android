package org.commcare.android.logic;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import org.commcare.dalvik.activities.CallOutActivity;

/**
 * Created by dancluna on 3/5/15.
 */
public class DetailCalloutListenerDefaultImpl {
    // Implementing DetailCalloutListener, reusing the code from @href{org.commcare.dalvik.activities.EntityDetailActivity}
    // implementing classes can just delegate to this class if they want its default functionality
    // CommCare-159503: in awesome mode, the app crashed when trying to dial/sms a number
    //   due to EntitySelectActivity not implementing DetailCalloutListener.
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
