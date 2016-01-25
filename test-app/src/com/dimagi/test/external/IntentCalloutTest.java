package com.dimagi.test.external;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

public class IntentCalloutTest extends Activity {

    Button getImage;
    Button returnToForm;
    EditText defaultval;
    EditText extraVal;
    TextView imageLocationText;
    TextView outputTextView;

    File location;

    public static final int KEY_REQUEST_IMAGE = 1;

    /*
     * (non-Javadoc)
     * @see android.app.Activity#onCreate(android.os.Bundle)
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_callout);
        returnToForm = (Button)this.findViewById(R.id.button_return);

        Intent intent = getIntent();

        outputTextView = (TextView)this.findViewById(R.id.textView2);

        String displayText = intent.getStringExtra("display_text");

        outputTextView.setText(displayText);

        returnToForm.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                Intent returningIntent = new Intent(getIntent());

                Bundle returnBundle = new Bundle();

                String defaultText = defaultval.getText().toString();
                if (defaultText != "") {
                    returningIntent.putExtra("odk_intent_data", defaultText);
                }

                String extraText = extraVal.getText().toString();
                if (extraText != "") {
                    returnBundle.putString("extra_text", extraText);
                }

                if (location != null && location.exists()) {
                    returnBundle.putString("phot_location", location.toString());
                }
                returningIntent.putExtra("odk_intent_bundle", returnBundle);
                IntentCalloutTest.this.setResult(Activity.RESULT_OK, returningIntent);
                finish();
            }

        });

        getImage = (Button)this.findViewById(R.id.extra_image_value);
        getImage.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);

                try {
                    location = File.createTempFile("image" + System.currentTimeMillis(), ".jpg");
                } catch (IOException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                    return;
                }

                // if this gets modified, the onActivityResult in
                // FormEntyActivity will also need to be updated.
                i.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, Uri.fromFile(location));
                try {
                    startActivityForResult(i, KEY_REQUEST_IMAGE);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(IntentCalloutTest.this, "No Camera", Toast.LENGTH_SHORT).show();
                }
            }

        });

        defaultval = (EditText)this.findViewById(R.id.default_callback_value);

        extraVal = (EditText)this.findViewById(R.id.extra_callback_value);

        imageLocationText = (TextView)this.findViewById(R.id.image_location);

        if (this.getLastNonConfigurationInstance() != null) {
            location = ((IntentCalloutTest)this.getLastNonConfigurationInstance()).location;
        }

        if (savedInstanceState != null) {
            location = new File(savedInstanceState.getString("location"));
        }
    }

    /* (non-Javadoc)
     * @see android.app.Activity#onActivityResult(int, int, android.content.Intent)
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == KEY_REQUEST_IMAGE) {
            //Go grab the image and set it's location
            if (location == null || !location.exists()) {
                location = null;
            }
        }
    }

    /* (non-Javadoc)
     * @see android.app.Activity#onResume()
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (location != null && location.exists()) {
            imageLocationText.setText("File to be sent : " + location.toString());
        } else {
            imageLocationText.setText("No Image File");
        }
    }

    /* (non-Javadoc)
     * @see android.app.Activity#onSaveInstanceState(android.os.Bundle)
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("location", location.toString());
    }

    /* (non-Javadoc)
     * @see android.app.Activity#onRetainNonConfigurationInstance()
     */
    @Override
    public Object onRetainNonConfigurationInstance() {
        return this;
    }
}
