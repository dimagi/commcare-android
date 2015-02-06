package org.commcare.dalvik.activities;

import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.util.DetailCalloutListener;
import org.commcare.android.util.SerializationUtil;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.components.EntityDetailComponent;
import org.commcare.suite.model.Detail;
import org.commcare.util.SessionFrame;
import org.javarosa.core.model.instance.TreeReference;

import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.util.Pair;

/**
 * @author ctsims
 *
 */
public class EntityDetailActivity extends CommCareActivity implements DetailCalloutListener {
    
    private static final int CALL_OUT = 0;
    public static final String CONTEXT_REFERENCE = "eda_crid";
    public static final String DETAIL_PERSISTENT_ID = "eda_persistent_id";

    Pair<Detail, TreeReference> mEntityContext;
    
    EntityDetailComponent mDetailComponent;
    
    /*
     * (non-Javadoc)
     * @see org.commcare.android.framework.CommCareActivity#onCreate(android.os.Bundle)
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {   
        
        TreeReference treeReference = SerializationUtil.deserializeFromIntent(getIntent(), EntityDetailActivity.CONTEXT_REFERENCE, TreeReference.class);

        mDetailComponent = new EntityDetailComponent(
                CommCareApplication._().getCurrentSessionWrapper(),
                this,
                null,
                getIntent(),
                treeReference,
                getIntent().getIntExtra("entity_detail_index", -1),
                true
        );
        
        String shortDetailId = getIntent().getStringExtra(EntityDetailActivity.DETAIL_PERSISTENT_ID);
        if(shortDetailId != null) {
            Detail shortDetail = mDetailComponent.getDetail(shortDetailId);
            this.mEntityContext = new Pair<Detail, TreeReference>(shortDetail, treeReference);
        }

        super.onCreate(savedInstanceState);

        if (this.getString(R.string.panes).equals("two")) {
            if(getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                //this occurs when the screen was rotated to be vertical on the select activity. We
                //want to navigate back to that screen now.
                this.setResult(RESULT_CANCELED, this.getIntent());
                this.finish();
                return;
            }
        }
    }
    
    public Pair<Detail, TreeReference> requestEntityContext() {
        return mEntityContext;
    }
    
    /*
     * (non-Javadoc)
     * @see org.commcare.android.framework.CommCareActivity#isTopNavEnabled()
     */
    @Override
    protected boolean isTopNavEnabled() {
        return true;
    }
    

    /*
     * (non-Javadoc)
     * @see org.commcare.android.framework.CommCareActivity#getActivityTitle()
     */
    @Override
    public String getActivityTitle() {
        //Skipping this until it's a more general pattern
        return null;
//        String title = Localization.get("select.detail.title");
//        
//        try {
//            Detail detail = factory.getDetail();
//            title = detail.getTitle().evaluate();
//        } catch(Exception e) {
//            
//        }
//        
//        return title;
    }
        
    protected void loadOutgoingIntent(Intent i) {
        i.putExtra(SessionFrame.STATE_DATUM_VAL, this.getIntent().getStringExtra(SessionFrame.STATE_DATUM_VAL));
    }
    
    /*
     * (non-Javadoc)
     * @see android.app.Activity#onActivityResult(int, int, android.content.Intent)
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch(requestCode) {
        case CALL_OUT:
            if(resultCode == RESULT_CANCELED) {
                mDetailComponent.refresh();
                return;
            } else {
                long duration = intent.getLongExtra(CallOutActivity.CALL_DURATION, 0);
                
                Intent i = new Intent(EntityDetailActivity.this.getIntent());
                loadOutgoingIntent(i);
                i.putExtra(CallOutActivity.CALL_DURATION, duration);
                setResult(RESULT_OK, i);

                finish();
                return;
            }
        default:
            super.onActivityResult(requestCode, resultCode, intent);
        }
    }


    public void callRequested(String phoneNumber) {
        Intent intent = new Intent(getApplicationContext(), CallOutActivity.class);
        intent.putExtra(CallOutActivity.PHONE_NUMBER, phoneNumber);
        this.startActivityForResult(intent, CALL_OUT);
    }


    public void addressRequested(String address) {
        Intent call = new Intent(Intent.ACTION_VIEW, Uri.parse(address));
        startActivity(call);
    }
    
    public void playVideo(String videoRef) {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.parse(videoRef), "video/*");
        startActivity(intent);
    }

}
