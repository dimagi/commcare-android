package org.odk.collect.android.application;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.commcare.dalvik.R;

public class GeoProgressDialog extends Dialog {
    
    private final TextView mText;
    private final ImageView mImage;
    private final Button mAccept;
    private final Button mCancel;
    private final ProgressBar mProgress;
    private boolean locationFound;
    private final String mFoundMessage;
    private final String mSearchMessage;

    public GeoProgressDialog(Context context, String foundMessage, String searchMessage) {
        super(context);
        setContentView(R.layout.geo_progress);
        this.mImage = (ImageView)findViewById(R.id.geoImage);
        this.mText = (TextView)findViewById(R.id.geoText);
        this.mAccept=(Button)findViewById(R.id.geoOK);
        this.mCancel=(Button)findViewById(R.id.geoCancel);
        this.mProgress=(ProgressBar)findViewById(R.id.geoProgressBar);
        locationFound = false;
        mFoundMessage = foundMessage;
        mSearchMessage = searchMessage;
        refreshView();
    }
    
    public void setMessage(String txt){
        mText.setText(txt);
    }
    public void setImage(Drawable img){
        mImage.setImageDrawable(img);
    }
    
    public void setOKButton(String title, View.OnClickListener ocl){
        mAccept.setText(title);
        mAccept.setOnClickListener(ocl);
    }
    public void setCancelButton(String title, View.OnClickListener ocl){
        mCancel.setText(title);
        mCancel.setOnClickListener(ocl);
    }
    public void setLocationFound(boolean locationFound){
        this.locationFound = locationFound;
        refreshView();
    }
    
    private void refreshView(){
        if(locationFound){
            mImage.setVisibility(View.VISIBLE);
            mProgress.setVisibility(View.GONE);
            mCancel.setVisibility(View.GONE);
            mAccept.setVisibility(View.VISIBLE);
            this.setTitle(mFoundMessage);
        }
        else{
            this.setTitle(mSearchMessage);
            mImage.setVisibility(View.GONE);
            mCancel.setVisibility(View.VISIBLE);
            mAccept.setVisibility(View.GONE);
            mProgress.setVisibility(View.VISIBLE);
        }
    }
    

}