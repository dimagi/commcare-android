/**
 * 
 */
package org.commcare.android.activities;

import org.commcare.android.R;
import org.commcare.android.logic.GlobalConstants;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

/**
 * @author ctsims
 *
 */
public class LoginActivity extends Activity {
	Button login;
	TextView title;
	
	TextView userLabel;
	TextView passLabel;
	
	EditText username;
	EditText password;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login);
        
        login = (Button)findViewById(R.id.login_button);
        
        title = (TextView)findViewById(R.id.text_commcare_title);
        
        userLabel = (TextView)findViewById(R.id.text_username);
        
        passLabel = (TextView)findViewById(R.id.text_password);
        
        username = (EditText)findViewById(R.id.edit_username);
        
        password = (EditText)findViewById(R.id.edit_password);
        
        login.setOnClickListener(new OnClickListener() {

			public void onClick(View arg0) {
				Intent i = new Intent();
		        i.putExtra(GlobalConstants.USER_KEY, username.getText().toString());
		        setResult(RESULT_OK, i);
		        
		        finish();
			}
        });
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onResume()
     */
    @Override
    protected void onResume() {
        super.onResume();
        refreshView();
    }
    
    private void refreshView() {
    	passLabel.setText("Password:");
    	userLabel.setText("Username:");
    	title.setText("CommCare");
    	login.setText("Log In");
    }
}
