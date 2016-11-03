package org.commcare.views.dialogs;

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

    private final TextView textView;
    private final ImageView imageView;
    private final Button acceptButton;
    private final Button cancelButton;
    private final ProgressBar progressBar;
    private boolean locationFound;
    private final String locationFoundMessage;
    private final String searchingMessage;

    public GeoProgressDialog(Context context, String foundMessage, String searchMessage) {
        super(context);
        // back button doesn't cancel
        setCancelable(false);
        setContentView(R.layout.geo_progress);
        this.imageView = (ImageView)findViewById(R.id.geoImage);
        this.textView = (TextView)findViewById(R.id.geoText);
        this.acceptButton = (Button)findViewById(R.id.geoOK);
        this.cancelButton = (Button)findViewById(R.id.geoCancel);
        this.progressBar = (ProgressBar)findViewById(R.id.geoProgressBar);
        locationFound = false;
        locationFoundMessage = foundMessage;
        searchingMessage = searchMessage;
        refreshView();
    }

    public void setMessage(String txt) {
        textView.setText(txt);
    }

    public void setImage(Drawable img) {
        imageView.setImageDrawable(img);
    }

    public void setOKButton(String title, View.OnClickListener ocl) {
        acceptButton.setText(title);
        acceptButton.setOnClickListener(ocl);
    }

    public void setCancelButton(String title, View.OnClickListener ocl) {
        cancelButton.setText(title);
        cancelButton.setOnClickListener(ocl);
    }

    public void setLocationFound(boolean locationFound) {
        this.locationFound = locationFound;
        refreshView();
    }

    private void refreshView() {
        if (locationFound) {
            imageView.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.GONE);
            cancelButton.setVisibility(View.GONE);
            acceptButton.setVisibility(View.VISIBLE);
            this.setTitle(locationFoundMessage);
        } else {
            this.setTitle(searchingMessage);
            imageView.setVisibility(View.GONE);
            cancelButton.setVisibility(View.VISIBLE);
            acceptButton.setVisibility(View.GONE);
            progressBar.setVisibility(View.VISIBLE);
        }
    }


}