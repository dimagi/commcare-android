/*
 * Copyright (C) 2009 University of Washington
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.odk.collect.android.activities;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.text.InputFilter;
import android.text.Spanned;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.ContextThemeWrapper;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.util.StringUtils;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.javarosa.core.model.Constants;
import org.javarosa.core.model.FormIndex;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.locale.Localizer;
import org.javarosa.form.api.FormEntryController;
import org.javarosa.form.api.FormEntryPrompt;
import org.javarosa.model.xform.XFormsModule;
import org.javarosa.xpath.XPathException;
import org.javarosa.xpath.XPathTypeMismatchException;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.jr.extensions.IntentCallout;
import org.odk.collect.android.listeners.AdvanceToNextListener;
import org.odk.collect.android.listeners.FormLoaderListener;
import org.odk.collect.android.listeners.FormSavedListener;
import org.odk.collect.android.listeners.WidgetChangedListener;
import org.odk.collect.android.logic.FormController;
import org.odk.collect.android.logic.PropertyManager;
import org.odk.collect.android.preferences.PreferencesActivity;
import org.odk.collect.android.preferences.PreferencesActivity.ProgressBarMode;
import org.odk.collect.android.provider.FormsProviderAPI.FormsColumns;
import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;
import org.odk.collect.android.tasks.FormLoaderTask;
import org.odk.collect.android.tasks.SaveToDiskTask;
import org.odk.collect.android.utilities.Base64Wrapper;
import org.odk.collect.android.utilities.FileUtils;
import org.odk.collect.android.utilities.GeoUtils;
import org.odk.collect.android.views.ODKView;
import org.odk.collect.android.views.ResizingImageView;
import org.odk.collect.android.widgets.DateTimeWidget;
import org.odk.collect.android.widgets.IntentWidget;
import org.odk.collect.android.widgets.QuestionWidget;
import org.odk.collect.android.widgets.TimeWidget;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import javax.crypto.spec.SecretKeySpec;

/**
 * FormEntryActivity is responsible for displaying questions, animating transitions between
 * questions, and allowing the user to enter data.
 * 
 * @author Carl Hartung (carlhartung@gmail.com)
 */
public class FormEntryActivity extends FragmentActivity implements AnimationListener, FormLoaderListener,
        FormSavedListener, AdvanceToNextListener, OnGestureListener, WidgetChangedListener {
    private static final String t = "FormEntryActivity";

    // Defines for FormEntryActivity
    private static final boolean EXIT = true;
    private static final boolean DO_NOT_EXIT = false;
    private static final boolean EVALUATE_CONSTRAINTS = true;
    private static final boolean DO_NOT_EVALUATE_CONSTRAINTS = false;

    // Request codes for returning data from specified intent.
    public static final int IMAGE_CAPTURE = 1;
    public static final int BARCODE_CAPTURE = 2;
    public static final int AUDIO_CAPTURE = 3;
    public static final int VIDEO_CAPTURE = 4;
    public static final int LOCATION_CAPTURE = 5;
    public static final int HIERARCHY_ACTIVITY = 6;
    public static final int IMAGE_CHOOSER = 7;
    public static final int AUDIO_CHOOSER = 8;
    public static final int VIDEO_CHOOSER = 9;
    public static final int INTENT_CALLOUT = 10;
    public static final int HIERARCHY_ACTIVITY_FIRST_START = 11;
    public static final int SIGNATURE_CAPTURE = 12;

    // Extra returned from gp activity
    public static final String LOCATION_RESULT = "LOCATION_RESULT";

    // Identifies the gp of the form used to launch form entry
    public static final String KEY_FORMPATH = "formpath";
    public static final String KEY_INSTANCEDESTINATION = "instancedestination";
    public static final String KEY_INSTANCES = "instances";
    public static final String KEY_SUCCESS = "success";
    public static final String KEY_ERROR = "error";
    
    public static final String KEY_FORM_CONTENT_URI = "form_content_uri";
    public static final String KEY_INSTANCE_CONTENT_URI = "instance_content_uri";
    
    public static final String KEY_AES_STORAGE_KEY = "key_aes_storage";
    
    public static final String KEY_HEADER_STRING = "form_header";
    
    public static final String KEY_INCOMPLETE_ENABLED = "org.odk.collect.form.management";
    
    public static final String KEY_RESIZING_ENABLED = "org.odk.collect.resizing.enabled";
    
    public static final String KEY_HAS_SAVED = "org.odk.collect.form.has.saved";

    // Identifies whether this is a new form, or reloading a form after a screen
    // rotation (or similar)
    private static final String NEWFORM = "newform";

    private static final int MENU_LANGUAGES = Menu.FIRST;
    private static final int MENU_HIERARCHY_VIEW = Menu.FIRST + 1;
    private static final int MENU_SAVE = Menu.FIRST + 2;
    private static final int MENU_PREFERENCES = Menu.FIRST + 3;

    private static final int PROGRESS_DIALOG = 1;
    private static final int SAVING_DIALOG = 2;

    // Random ID
    private static final int DELETE_REPEAT = 654321;

    private String mFormPath;
    public static String mInstancePath;
    private String mInstanceDestination;
    private GestureDetector mGestureDetector;
    
    private SecretKeySpec symetricKey = null;

    public static FormController mFormController;

    private Animation mInAnimation;
    private Animation mOutAnimation;

    private ViewGroup mViewPane;
    private View mCurrentView;

    private AlertDialog mRepeatDialog;
    private AlertDialog mAlertDialog;
    private ProgressDialog mProgressDialog;
    private String mErrorMessage;
    
    private boolean mIncompleteEnabled = true;

    // used to limit forward/backward swipes to one per question
    private boolean mBeenSwiped;

    private FormLoaderTask mFormLoaderTask;
    private SaveToDiskTask mSaveToDiskTask;
    
    private Uri formProviderContentURI = FormsColumns.CONTENT_URI;
    private Uri instanceProviderContentURI = InstanceColumns.CONTENT_URI;
    
    private static String mHeaderString;
    
    public boolean hasSaved = false;
    
    private BroadcastReceiver mNoGPSReceiver;

    enum AnimationType {
        LEFT, RIGHT, FADE
    }


    /*
     * (non-Javadoc)
     * @see android.support.v4.app.FragmentActivity#onCreate(android.os.Bundle)
     */
    @Override
    @SuppressLint("NewApi")
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);        

        // See if this form needs GPS to be turned on
        mNoGPSReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                context.removeStickyBroadcast(intent);
                LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
                Set<String> providers = GeoUtils.evaluateProviders(manager);
                if (providers.isEmpty()) {
                    DialogInterface.OnClickListener onChangeListener = new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int i) {
                            if (i == DialogInterface.BUTTON_POSITIVE) {
                                Intent intent = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                startActivity(intent);
                            }
                        }
                    };
                    GeoUtils.showNoGpsDialog(FormEntryActivity.this, onChangeListener);
                }
            }
        };
        registerReceiver(mNoGPSReceiver, new IntentFilter(GeoUtils.ACTION_CHECK_GPS_ENABLED));
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            String fragmentClass = this.getIntent().getStringExtra("odk_title_fragment");
            if(fragmentClass != null) {
                FragmentManager fm = this.getSupportFragmentManager();

                //Add breadcrumb bar                
                Fragment bar = (Fragment) fm.findFragmentByTag(TITLE_FRAGMENT_TAG);
                // If the state holder is null, create a new one for this activity
                if (bar == null) {
                    try {
                        bar = ((Class<Fragment>)Class.forName(fragmentClass)).newInstance();
                        //the bar will set this up for us again if we need.

                        getActionBar().setDisplayShowCustomEnabled(true);
                        getActionBar().setDisplayShowTitleEnabled(false);
                        fm.beginTransaction().add(bar, TITLE_FRAGMENT_TAG).commit();
                    } catch(Exception e) {
                        Log.w("odk-collect", "couldn't instantiate fragment: " + fragmentClass);
                    }
                }
            }
        }

        // must be at the beginning of any activity that can be called from an external intent
        try {
            Collect.createODKDirs();
        } catch (RuntimeException e) {
            Logger.exception(e);
            createErrorDialog(e.getMessage(), EXIT);
            return;
        }

        setContentView(R.layout.screen_form_entry);
        setNavBarVisibility();
        
        ImageButton nextButton = (ImageButton)this.findViewById(R.id.nav_btn_next);
        ImageButton prevButton = (ImageButton)this.findViewById(R.id.nav_btn_prev);
        
        nextButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if(!"done".equals(v.getTag())) {
					FormEntryActivity.this.showNextView();
				} else {
					FormEntryActivity.this.triggerUserFormComplete();
				}
			}
        	
        });
        
        prevButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if(!"quit".equals(v.getTag())) {
					FormEntryActivity.this.showPreviousView();
				} else {
					FormEntryActivity.this.triggerUserQuitInput();
				}
			}
        	
        });

        mViewPane = (ViewGroup)findViewById(R.id.form_entry_pane);

        mBeenSwiped = false;
        mAlertDialog = null;
        mCurrentView = null;
        mInAnimation = null;
        mOutAnimation = null;
        mGestureDetector = new GestureDetector(this);

        // Load JavaRosa modules. needed to restore forms.
        new XFormsModule().registerModule();

        // needed to override rms property manager
        org.javarosa.core.services.PropertyManager.setPropertyManager(new PropertyManager(
                getApplicationContext()));

        Boolean newForm = true;
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(KEY_FORMPATH)) {
                mFormPath = savedInstanceState.getString(KEY_FORMPATH);
            }
            if (savedInstanceState.containsKey(NEWFORM)) {
                newForm = savedInstanceState.getBoolean(NEWFORM, true);
            }
            if (savedInstanceState.containsKey(KEY_ERROR)) {
                mErrorMessage = savedInstanceState.getString(KEY_ERROR);
            }
            if (savedInstanceState.containsKey(KEY_FORM_CONTENT_URI)) {
                formProviderContentURI = Uri.parse(savedInstanceState.getString(KEY_FORM_CONTENT_URI));
            }
            if (savedInstanceState.containsKey(KEY_INSTANCE_CONTENT_URI)) {
                instanceProviderContentURI = Uri.parse(savedInstanceState.getString(KEY_INSTANCE_CONTENT_URI));
            }
            if (savedInstanceState.containsKey(KEY_INSTANCEDESTINATION)) {
                mInstanceDestination = savedInstanceState.getString(KEY_INSTANCEDESTINATION);
            } 
            if(savedInstanceState.containsKey(KEY_INCOMPLETE_ENABLED)) {
                mIncompleteEnabled = savedInstanceState.getBoolean(KEY_INCOMPLETE_ENABLED);
            }
            if(savedInstanceState.containsKey(KEY_RESIZING_ENABLED)) {
                ResizingImageView.resizeMethod = savedInstanceState.getString(KEY_RESIZING_ENABLED);
            }
            if (savedInstanceState.containsKey(KEY_AES_STORAGE_KEY)) {
                 String base64Key = savedInstanceState.getString(KEY_AES_STORAGE_KEY);
                 try {
                    byte[] storageKey = new Base64Wrapper().decode(base64Key);
                    symetricKey = new SecretKeySpec(storageKey, "AES");
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException("Base64 encoding not available on this platform");
                }
            }
            if(savedInstanceState.containsKey(KEY_HEADER_STRING)) {
                mHeaderString = savedInstanceState.getString(KEY_HEADER_STRING);
            }
            
            if(savedInstanceState.containsKey(KEY_HAS_SAVED)) {
                hasSaved = savedInstanceState.getBoolean(KEY_HAS_SAVED);
            }
           
        }

        // If a parse error message is showing then nothing else is loaded
        // Dialogs mid form just disappear on rotation.
        if (mErrorMessage != null) {
            createErrorDialog(mErrorMessage, EXIT);
            return;
        }

        // Check to see if this is a screen flip or a new form load.
        Object data = this.getLastCustomNonConfigurationInstance();
        if (data instanceof FormLoaderTask) {
            mFormLoaderTask = (FormLoaderTask) data;
        } else if (data instanceof SaveToDiskTask) {
            mSaveToDiskTask = (SaveToDiskTask) data;
        } else if (data == null) {
            if (!newForm) {
                refreshCurrentView();
                return;
            }
            boolean readOnly = false;

            // Not a restart from a screen orientation change (or other).
            mFormController = null;
            mInstancePath = null;

            Intent intent = getIntent();
            if (intent != null) {
                Uri uri = intent.getData();
                
                if(intent.hasExtra(KEY_FORM_CONTENT_URI)) {
                    this.formProviderContentURI = Uri.parse(intent.getStringExtra(KEY_FORM_CONTENT_URI));
                }
                if(intent.hasExtra(KEY_INSTANCE_CONTENT_URI)) {
                    this.instanceProviderContentURI = Uri.parse(intent.getStringExtra(KEY_INSTANCE_CONTENT_URI));
                }
                if(intent.hasExtra(KEY_INSTANCEDESTINATION)) {
                    this.mInstanceDestination = intent.getStringExtra(KEY_INSTANCEDESTINATION);
                } else {
                    mInstanceDestination = Collect.INSTANCES_PATH;
                }
                if(intent.hasExtra(KEY_AES_STORAGE_KEY)) {
                    String base64Key = intent.getStringExtra(KEY_AES_STORAGE_KEY);
                    try {
                        byte[] storageKey = new Base64Wrapper().decode(base64Key);
                        symetricKey = new SecretKeySpec(storageKey, "AES");
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException("Base64 encoding not available on this platform");
                    }
                    
                }
                if(intent.hasExtra(KEY_HEADER_STRING)) {
                    this.mHeaderString = intent.getStringExtra(KEY_HEADER_STRING);
                }
                
                if(intent.hasExtra(KEY_INCOMPLETE_ENABLED)) {
                    this.mIncompleteEnabled = intent.getBooleanExtra(KEY_INCOMPLETE_ENABLED, true);
                }
                
                if(intent.hasExtra(KEY_RESIZING_ENABLED)) {
                    ResizingImageView.resizeMethod = intent.getStringExtra(KEY_RESIZING_ENABLED);
                    
                }
                
                if(mHeaderString != null) {
                    setTitle(mHeaderString);
                } else {
                    setTitle(StringUtils.getStringRobust(this, R.string.app_name) + " > " + StringUtils.getStringRobust(this, R.string.loading_form));
                }
                
                
                //csims@dimagi.com - Jan 24, 2012
                //Since these are parceled across the content resolver, there's no guarantee of reference equality.
                //We need to manually check value equality on the type 
                
                String contentType = getContentResolver().getType(uri);
                
                Uri formUri = null;;

                if (contentType.equals(InstanceColumns.CONTENT_ITEM_TYPE)) {
                    Cursor instanceCursor = this.managedQuery(uri, null, null, null, null);
                    if (instanceCursor.getCount() != 1) {
                        this.createErrorDialog("Bad URI: " + uri, EXIT);
                        return;
                    } else {
                        instanceCursor.moveToFirst();
                        mInstancePath =
                            instanceCursor.getString(instanceCursor
                                    .getColumnIndex(InstanceColumns.INSTANCE_FILE_PATH));

                        String jrFormId =
                            instanceCursor.getString(instanceCursor
                                    .getColumnIndex(InstanceColumns.JR_FORM_ID));
                        
                        
                        //If this form is both already completed 
                        if(InstanceProviderAPI.STATUS_COMPLETE.equals(instanceCursor.getString(instanceCursor.getColumnIndex(InstanceColumns.STATUS)))) {
                            if(!Boolean.parseBoolean(instanceCursor.getString(instanceCursor.getColumnIndex(InstanceColumns.CAN_EDIT_WHEN_COMPLETE)))) {
                                readOnly = true;
                            }
                        }
                        

                        String[] selectionArgs = {
                            jrFormId
                        };
                        String selection = FormsColumns.JR_FORM_ID + " like ?";

                        Cursor formCursor =
                            managedQuery(formProviderContentURI, null, selection, selectionArgs,null);
                        if (formCursor.getCount() == 1) {
                            formCursor.moveToFirst();
                            mFormPath =
                                formCursor.getString(formCursor
                                        .getColumnIndex(FormsColumns.FORM_FILE_PATH));
                            formUri = ContentUris.withAppendedId(formProviderContentURI, formCursor.getLong(formCursor.getColumnIndex(FormsColumns._ID)));
                        } else if (formCursor.getCount() < 1) {
                            this.createErrorDialog("Parent form does not exist", EXIT);
                            return;
                        } else if (formCursor.getCount() > 1) {
                            this.createErrorDialog("More than one possible parent form", EXIT);
                            return;
                        }

                    }

                } else if (contentType.equals(FormsColumns.CONTENT_ITEM_TYPE)) {
                    Cursor c = this.managedQuery(uri, null, null, null, null);
                    if (c.getCount() != 1) {
                        this.createErrorDialog("Bad URI: " + uri, EXIT);
                        return;
                    } else {
                        c.moveToFirst();
                        mFormPath = c.getString(c.getColumnIndex(FormsColumns.FORM_FILE_PATH));
                        formUri = uri;
                    }
                } else {
                    Log.e(t, "unrecognized URI");
                    this.createErrorDialog("unrecognized URI: " + uri, EXIT);
                    return;
                }
                if(formUri == null) {
                    Log.e(t, "unrecognized URI");
                    this.createErrorDialog("couldn't locate FormDB entry for the item at: " + uri, EXIT);
                    return;
                }

                mFormLoaderTask = new FormLoaderTask(this, symetricKey, readOnly);
                mFormLoaderTask.execute(formUri);
                showDialog(PROGRESS_DIALOG);
            }
        }
    }

    private void setClickListenersForEverything() {
        if (BuildConfig.DEBUG) {
            ViewGroup layout = (ViewGroup) findViewById(android.R.id.content);
            LinkedList<View> views = new LinkedList<View>();
            views.add(layout);
            for (int i = 0; !views.isEmpty(); i++) {
                View child = views.getFirst();
                views.removeFirst();
                Log.i("GetID", "Adding onClickListener to view " + child);
                child.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String vid;
                        try {
                            vid = "View id is: " + v.getResources().getResourceName(v.getId()) + " ( " + v.getId() + " )";
                        } catch (Resources.NotFoundException excp) {
                            vid = "View id is: " + v.getId();
                        }
                        Log.i("CLK", vid);
                    }
                });
                if(child instanceof ViewGroup) {
                    ViewGroup vg = (ViewGroup) child;
                    for (int j = 0; j < vg.getChildCount(); j++) {
                        View gchild = vg.getChildAt(j);
                        if (!views.contains(gchild)) views.add(gchild);
                    }
                }
            }
        }
    }

    public static final String TITLE_FRAGMENT_TAG = "odk_title_fragment";

    /*
     * (non-Javadoc)
     * @see android.support.v4.app.FragmentActivity#onSaveInstanceState(android.os.Bundle)
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_FORMPATH, mFormPath);
        outState.putBoolean(NEWFORM, false);
        outState.putString(KEY_ERROR, mErrorMessage);
        outState.putString(KEY_FORM_CONTENT_URI, formProviderContentURI.toString());
        outState.putString(KEY_INSTANCE_CONTENT_URI, instanceProviderContentURI.toString());
        outState.putString(KEY_INSTANCEDESTINATION, mInstanceDestination);
        outState.putBoolean(KEY_INCOMPLETE_ENABLED, mIncompleteEnabled);
        outState.putBoolean(KEY_HAS_SAVED, hasSaved);
        outState.putString(KEY_RESIZING_ENABLED, ResizingImageView.resizeMethod);
        
        if(symetricKey != null) {
            try {
                outState.putString(KEY_AES_STORAGE_KEY, new Base64Wrapper().encodeToString(symetricKey.getEncoded()));
            } catch (ClassNotFoundException e) {
                // we can't really get here anyway, since we couldn't have decoded the string to begin with
                throw new RuntimeException("Base 64 encoding unavailable! Can't pass storage key");
            }
        }
    }


    /*
     * (non-Javadoc)
     * @see android.support.v4.app.FragmentActivity#onActivityResult(int, int, android.content.Intent)
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (resultCode == RESULT_CANCELED) {
            
            if(requestCode == HIERARCHY_ACTIVITY_FIRST_START) {
                //they pressed 'back' on the first heirarchy screen. we should assume they want to
                //back out of form entry all together
                finishReturnInstance(false);
            } else if(requestCode == INTENT_CALLOUT){
                processIntentResponse(intent, true);
            }
            
            // request was canceled, so do nothing
            return;
        }

        ContentValues values;
        Uri imageURI;
        switch (requestCode) {
            case BARCODE_CAPTURE:
                String sb = intent.getStringExtra("SCAN_RESULT");
                ((ODKView) mCurrentView).setBinaryData(sb);
                saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
                break;
            case INTENT_CALLOUT:
                processIntentResponse(intent);
                break;
            case IMAGE_CAPTURE:
            case SIGNATURE_CAPTURE:
                /*
                 * We saved the image to the tempfile_path, but we really want it to be in:
                 * /sdcard/odk/instances/[current instnace]/something.jpg so we move it there before
                 * inserting it into the content provider. Once the android image capture bug gets
                 * fixed, (read, we move on from Android 1.6) we want to handle images the audio and
                 * video
                 */
                // The intent is empty, but we know we saved the image to the temp file
                File fi = new File(Collect.TMPFILE_PATH);

                String mInstanceFolder =
                    mInstancePath.substring(0, mInstancePath.lastIndexOf("/") + 1);
                String s = mInstanceFolder + "/" + System.currentTimeMillis() + ".jpg";

                File nf = new File(s);
                if (!fi.renameTo(nf)) {
                    Log.e(t, "Failed to rename " + fi.getAbsolutePath());
                } else {
                    Log.i(t, "renamed " + fi.getAbsolutePath() + " to " + nf.getAbsolutePath());
                }

                // Add the new image to the Media content provider so that the
                // viewing is fast in Android 2.0+
                values = new ContentValues(6);
                values.put(Images.Media.TITLE, nf.getName());
                values.put(Images.Media.DISPLAY_NAME, nf.getName());
                values.put(Images.Media.DATE_TAKEN, System.currentTimeMillis());
                values.put(Images.Media.MIME_TYPE, "image/jpeg");
                values.put(Images.Media.DATA, nf.getAbsolutePath());

                imageURI = getContentResolver().insert(Images.Media.EXTERNAL_CONTENT_URI, values);
                Log.i(t, "Inserting image returned uri = " + imageURI.toString());

                ((ODKView) mCurrentView).setBinaryData(imageURI);
                saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
                refreshCurrentView();
                break;
            case IMAGE_CHOOSER:
                /*
                 * We have a saved image somewhere, but we really want it to be in:
                 * /sdcard/odk/instances/[current instnace]/something.jpg so we move it there before
                 * inserting it into the content provider. Once the android image capture bug gets
                 * fixed, (read, we move on from Android 1.6) we want to handle images the audio and
                 * video
                 */

                // get gp of chosen file
                String sourceImagePath = null;
                Uri selectedImage = intent.getData();
                
                sourceImagePath = FileUtils.getPath(this, selectedImage);

                // Copy file to sdcard
                String mInstanceFolder1 =
                    mInstancePath.substring(0, mInstancePath.lastIndexOf("/") + 1);
                String destImagePath = mInstanceFolder1 + "/" + System.currentTimeMillis() + ".jpg";

                File source = new File(sourceImagePath);
                File newImage = new File(destImagePath);
                FileUtils.copyFile(source, newImage);

                if (newImage.exists()) {
                    // Add the new image to the Media content provider so that the
                    // viewing is fast in Android 2.0+
                    values = new ContentValues(6);
                    values.put(Images.Media.TITLE, newImage.getName());
                    values.put(Images.Media.DISPLAY_NAME, newImage.getName());
                    values.put(Images.Media.DATE_TAKEN, System.currentTimeMillis());
                    values.put(Images.Media.MIME_TYPE, "image/jpeg");
                    values.put(Images.Media.DATA, newImage.getAbsolutePath());

                    imageURI =
                        getContentResolver().insert(Images.Media.EXTERNAL_CONTENT_URI, values);
                    Log.i(t, "Inserting image returned uri = " + imageURI.toString());

                    ((ODKView) mCurrentView).setBinaryData(imageURI);
                    saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
                } else {
                    Log.e(t, "NO IMAGE EXISTS at: " + source.getAbsolutePath());
                }
                refreshCurrentView();
                break;
            case AUDIO_CAPTURE:
            case VIDEO_CAPTURE:
            case AUDIO_CHOOSER:
            case VIDEO_CHOOSER:
                // For audio/video capture/chooser, we get the URI from the content provider
                // then the widget copies the file and makes a new entry in the content provider.
                Uri media = intent.getData();
                ((ODKView) mCurrentView).setBinaryData(media);
                saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
                refreshCurrentView();
                break;
            case LOCATION_CAPTURE:
                String sl = intent.getStringExtra(LOCATION_RESULT);
                ((ODKView) mCurrentView).setBinaryData(sl);
                saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
                break;
            case HIERARCHY_ACTIVITY:
                // We may have jumped to a new index in hierarchy activity, so refresh
                refreshCurrentView(false);
                break;

        }
    }
    
    private void processIntentResponse(Intent response){
        processIntentResponse(response, false);
    }
    
    private void processIntentResponse(Intent response, boolean cancelled) {
        
        // keep track of whether we should auto advance
        boolean advance = false;
        boolean quick = false;
        
        //We need to go grab our intent callout object to process the results here
        
        IntentWidget bestMatch = null;
        
        //Ugh, copied from the odkview mostly, that's stupid
        for(QuestionWidget q : ((ODKView)mCurrentView).getWidgets()) {
            //Figure out if we have a pending intent widget
            if (q instanceof IntentWidget) {
                if(((IntentWidget) q).isWaitingForBinaryData() || bestMatch == null) {
                    bestMatch = (IntentWidget)q;
                }
            }
        }
        
        if(bestMatch != null) {
            //Set our instance destination for binary data if needed
            String destination = mInstancePath.substring(0, mInstancePath.lastIndexOf("/") + 1);
            
            //get the original intent callout
            IntentCallout ic = bestMatch.getIntentCallout();
            
            quick = ic.isQuickAppearance();
            
            //And process it 
            advance = ic.processResponse(response, (ODKView)mCurrentView, mFormController.getInstance(), new File(destination));
            
            ic.setCancelled(cancelled);
            
        }
        
        saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
        
        // auto advance if we got a good result and are in quick mode
        if(advance && quick){
            showNextView();
        }
        
    }


    public void updateFormRelevencies(){
        
        saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
        
        if(!(mCurrentView instanceof ODKView)){
            throw new RuntimeException("Tried to update form relevency not on compound view");
        }
        
        ODKView oldODKV = (ODKView)mCurrentView;
        
        FormEntryPrompt[] newValidPrompts = mFormController.getQuestionPrompts();
        Set<FormEntryPrompt> used = new HashSet<FormEntryPrompt>();
        
        ArrayList<QuestionWidget> oldWidgets = oldODKV.getWidgets();

        ArrayList<Integer> removeList = new ArrayList<Integer>();

           for(int i=0;i<oldWidgets.size();i++){
            QuestionWidget oldWidget = oldWidgets.get(i);
            boolean stillRelevent = false;

            for(FormEntryPrompt prompt : newValidPrompts) {
            	if(prompt.getIndex().equals(oldWidget.getPrompt().getIndex())) {
            		stillRelevent = true;
            		used.add(prompt);
            	}
            }
            if(!stillRelevent){
                removeList.add(Integer.valueOf(i));
            }
        }
           // remove "atomically" to not mess up iterations
        oldODKV.removeQuestionsFromIndex(removeList);
        
        
        //Now go through add add any new prompts that we need
        for(int i = 0 ; i < newValidPrompts.length; ++i) {
        	FormEntryPrompt prompt = newValidPrompts[i]; 
        	if(used.contains(prompt)) {
        		//nothing to do here
        		continue;
        	} 
        	oldODKV.addQuestionToIndex(prompt, mFormController.getWidgetFactory(), i);
        }
    }
	
	private class NavigationDetails {
		public int totalQuestions = 0;
		public int completedQuestions = 0;
        public boolean relevantBeforeCurrentScreen = false;
        public boolean isFirstScreen = false;
        
        public int answeredOnScreen = 0;
        public int requiredOnScreen = 0;
        
        public int relevantAfterCurrentScreen = 0;
        public FormIndex currentScreenExit = null;

		
	}
	
	private NavigationDetails calculateNavigationStatus() {
		NavigationDetails details = new NavigationDetails();

        FormIndex userFormIndex = mFormController.getFormIndex();
        FormIndex currentFormIndex = FormIndex.createBeginningOfFormIndex();
        mFormController.expandRepeats(currentFormIndex);
        int event = mFormController.getEvent(currentFormIndex);
        try {

            // keep track of whether there is a question that exists before the
            // current screen
            boolean onCurrentScreen = false;

            while (event != FormEntryController.EVENT_END_OF_FORM) {
                int comparison = currentFormIndex.compareTo(userFormIndex);

                if (comparison == 0) {
                    onCurrentScreen = true;
                    details.currentScreenExit = mFormController.getNextFormIndex(currentFormIndex, true);
                }
                if (onCurrentScreen && currentFormIndex.equals(details.currentScreenExit)) {
                    onCurrentScreen = false;
                }

                // Figure out if there are any events before this screen (either
                // new repeat or relevant questions are valid)
                if (event == FormEntryController.EVENT_QUESTION
                        || event == FormEntryController.EVENT_PROMPT_NEW_REPEAT) {
                    // Figure out whether we're on the last screen
                    if (!details.relevantBeforeCurrentScreen && !details.isFirstScreen) {

                        // We got to the current screen without finding a
                        // relevant question,
                        // I guess we're on the first one.
                        if (onCurrentScreen
                                && !details.relevantBeforeCurrentScreen) {
                            details.isFirstScreen = true;
                        } else {
                            // We're using the built in steps (and they take
                            // relevancy into account)
                            // so if there are prompts they have to be relevant
                            details.relevantBeforeCurrentScreen = true;
                        }
                    }
                }

                if (event == FormEntryController.EVENT_QUESTION) {
                    FormEntryPrompt[] prompts = mFormController.getQuestionPrompts(currentFormIndex);

                    if (!onCurrentScreen && details.currentScreenExit != null) {
                        details.relevantAfterCurrentScreen += prompts.length;
                    }

                    details.totalQuestions += prompts.length;
                    // Current questions are complete only if they're answered.
                    // Past questions are always complete.
                    // Future questions are never complete.
                    if (onCurrentScreen) {
                        for (FormEntryPrompt prompt : prompts) {
                            if (this.mCurrentView instanceof ODKView) {
                                ODKView odkv = (ODKView) this.mCurrentView;
                                prompt = getOnScreenPrompt(prompt, odkv);
                            }
                            boolean isAnswered = prompt.getAnswerValue() != null
                                    || prompt.getDataType() == Constants.DATATYPE_NULL;

                            if (prompt.isRequired()) {
                                details.requiredOnScreen++;

                                if (isAnswered) {
                                    details.answeredOnScreen++;
                                }
                            }

                            if (isAnswered) {
                                details.completedQuestions++;
                            }
                        }
                    } else if (comparison < 0) {
                        // For previous questions, consider all "complete"
                        details.completedQuestions += prompts.length;
                        // TODO: This doesn't properly capture state to
                        // determine whether we will end up out of the form if
                        // we hit back!
                        // Need to test _until_ we get a question that is
                        // relevant, then we can skip the relevancy tests
                    }
                }

                else if (event == FormEntryController.EVENT_PROMPT_NEW_REPEAT) {
                    // If we've already passed the current screen, this repeat
                    // junction is coming up in the future and we will need to
                    // know
                    // about it
                    if (!onCurrentScreen && details.currentScreenExit != null) {
                        details.totalQuestions++;
                        details.relevantAfterCurrentScreen++;

                    } else {
                        // Otherwise we already passed it and it no longer
                        // affects the count
                    }
                }
                currentFormIndex = mFormController.getNextFormIndex(currentFormIndex, FormController.STEP_INTO_GROUP, false);
                event = mFormController.getEvent(currentFormIndex);
            }
        } catch (XPathTypeMismatchException e) {
            Logger.exception(e);
            FormEntryActivity.this.createErrorDialog(e.getMessage(), EXIT);
        }

        // Set form back to correct state
        mFormController.jumpToIndex(userFormIndex);

        return details;
    }	

    /**
     * Update progress bar's max and value, and the various buttons and navigation cues
     * associated with navigation
     * 
     * @param view ODKView to update
     */
    public void updateNavigationCues(View view) {
        updateFloatingLabels(view);

        ProgressBarMode mode = PreferencesActivity.getProgressBarMode(this);
        
        setNavBarVisibility();
        
        if(mode == ProgressBarMode.None) { return; }
        
        NavigationDetails details = calculateNavigationStatus();
        
        if(mode == ProgressBarMode.ProgressOnly && view instanceof ODKView) {
            ((ODKView)view).updateProgressBar(details.completedQuestions, details.totalQuestions);
            return;
        }

    	ProgressBar progressBar = (ProgressBar)this.findViewById(R.id.nav_prog_bar);
    	
        ImageButton nextButton = (ImageButton)this.findViewById(R.id.nav_btn_next);
        ImageButton prevButton = (ImageButton)this.findViewById(R.id.nav_btn_prev);
        
        if(!details.relevantBeforeCurrentScreen) {
        	prevButton.setImageResource(R.drawable.icon_exit);
        	prevButton.setTag("quit");
        } else {
        	prevButton.setImageResource(R.drawable.icon_back);
        	prevButton.setTag("back");
        }
        
        //Apparently in Android 2.3 setting the drawable resource for the progress bar 
        //causes it to lose it bounds. It's a bit cheaper to keep them around than it
        //is to invalidate the view, though.
        Rect bounds = progressBar.getProgressDrawable().getBounds(); //Save the drawable bound

        if(details.relevantAfterCurrentScreen == 0 && (details.requiredOnScreen == details.answeredOnScreen || details.requiredOnScreen < 1)) {
        	nextButton.setImageResource(R.drawable.icon_done);
        	
        	//TODO: _really_? This doesn't seem right
            nextButton.setTag("done");
        	
        	progressBar.setProgressDrawable(this.getResources().getDrawable(R.drawable.progressbar_full));
        } else {
        	nextButton.setImageResource(R.drawable.icon_next);
        	
        	//TODO: _really_? This doesn't seem right
            nextButton.setTag("next");
        	
        	progressBar.setProgressDrawable(this.getResources().getDrawable(R.drawable.progressbar));
        }
        
        progressBar.getProgressDrawable().setBounds(bounds);  //Set the bounds to the saved value
        
        progressBar.setMax(details.totalQuestions);
        progressBar.setProgress(details.completedQuestions);
        
        
        //We should probably be doing this based on the widgets, maybe, not the model? Hard to call.
        updateBadgeInfo(details.requiredOnScreen, details.answeredOnScreen);
    }
    private void setNavBarVisibility() {
        
        //Make sure the nav bar visibility is set
        int navBarVisibility = PreferencesActivity.getProgressBarMode(this).useNavigationBar() ? View.VISIBLE : View.GONE;
        View nav = this.findViewById(R.id.nav_pane);
        if(nav.getVisibility() != navBarVisibility) {
            nav.setVisibility(navBarVisibility);
            this.findViewById(R.id.nav_badge_border_drawer).setVisibility(navBarVisibility);
            this.findViewById(R.id.nav_badge).setVisibility(navBarVisibility);
        }
    }
    enum FloatingLabel {
        good ("floating-good", R.drawable.label_floating_good, R.color.cc_attention_positive_text),
        caution ("floating-caution", R.drawable.label_floating_caution, R.color.cc_light_warm_accent_color),
        bad ("floating-bad", R.drawable.label_floating_bad, R.color.cc_attention_negative_color);
        
        String label;
        int resourceId;
        int colorId;
        FloatingLabel(String label, int resourceId, int colorId) {
            this.label = label;
            this.resourceId = resourceId;
            this.colorId = colorId;
        }
        
        public String getAppearance() { return label;}
        public int getBackgroundDrawable() { return resourceId; }
        public int getColorId() { return colorId; }
    };
    
    private void updateFloatingLabels(View currentView) {
        //TODO: this should actually be set up to scale per screen size.
        ArrayList<Pair<String, FloatingLabel>> smallLabels = new ArrayList<Pair<String, FloatingLabel>>();
        ArrayList<Pair<String, FloatingLabel>> largeLabels = new ArrayList<Pair<String, FloatingLabel>>();
        
        FloatingLabel[] labelTypes = FloatingLabel.values();
        
        if(currentView instanceof ODKView) {
            for(QuestionWidget widget : ((ODKView)currentView).getWidgets()) {
                String hint = widget.getPrompt().getAppearanceHint();
                if(hint == null) { continue; }
                for(FloatingLabel type : labelTypes) {
                    if(type.getAppearance().equals(hint)) {
                        String widgetText = widget.getPrompt().getQuestionText();
                        if(widgetText != null && widgetText.length() < 15) {
                            smallLabels.add(new Pair(widgetText, type));
                        } else {
                            largeLabels.add(new Pair(widgetText, type));
                        }
                    }
                }
            }
        }
    
        
        ViewGroup parent = (ViewGroup)this.findViewById(R.id.form_entry_label_layout);
        parent.removeAllViews();

        int pixels = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());

        //Ok, now go ahead and add all of the small labels
        for(int i = 0 ; i < smallLabels.size(); i = i + 2 ) {
            if(i + 1 < smallLabels.size()) {
                LinearLayout.LayoutParams lpp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
                lpp.setMargins(0, pixels, 0, pixels);
                LinearLayout layout = new LinearLayout(this);
                layout.setOrientation(LinearLayout.HORIZONTAL);
                layout.setLayoutParams(lpp);
//                layout.setWeightSum(2);

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
                TextView left = (TextView)View.inflate(this, R.layout.component_floating_label, null);
                left.setLayoutParams(lp);
                left.setText(smallLabels.get(i).first + ";" + smallLabels.get(i + 1).first);
                left.setBackgroundResource(smallLabels.get(i).second.resourceId);
                left.setPadding(pixels, 2 * pixels, pixels, 2 * pixels);
                left.setTextColor(smallLabels.get(i).second.colorId);
                layout.addView(left);

//                TextView left = (TextView)View.inflate(this, R.layout.component_floating_label, null);
//                left.setLayoutParams(lp);
//                left.setText(smallLabels.get(i).first);
//                left.setBackgroundResource(smallLabels.get(i).second.resourceId);
//                layout.addView(left);
//
//                lp.setMargins(1, 0,0,0);
//
//                TextView right = (TextView)View.inflate(this, R.layout.component_floating_label, null);
//                right.setLayoutParams(lp);
//                right.setText(smallLabels.get(i+1).first);
//                right.setBackgroundResource(smallLabels.get(i+1).second.resourceId);
//                layout.addView(right);
//                parent.addView(layout);
            } else {
                largeLabels.add(smallLabels.get(i));
            }
        }
        for(int i = 0 ; i < largeLabels.size(); ++i ) {
            TextView view = (TextView)View.inflate(this, R.layout.component_floating_label, null);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 1, 0, 0);
            view.setLayoutParams(lp);
            view.setText(largeLabels.get(i).first);
            view.setBackgroundResource(largeLabels.get(i).second.resourceId);
            view.setTextColor(largeLabels.get(i).second.colorId);
            parent.addView(view);
        }
    }


    private void updateBadgeInfo(int requiredOnScreen, int answeredOnScreen) {
    	View badgeBorder = this.findViewById(R.id.nav_badge_border_drawer);
    	TextView badge = (TextView)this.findViewById(R.id.nav_badge);
    	
    	//If we don't need this stuff, just bail
    	if(requiredOnScreen <= 1) {
    		//Hide all badge related items
    		badgeBorder.setVisibility(View.INVISIBLE);
    		badge.setVisibility(View.INVISIBLE);
    		return;
    	}
  		//Otherwise, update badge stuff
		badgeBorder.setVisibility(View.VISIBLE);
		badge.setVisibility(View.VISIBLE);
		
		if(requiredOnScreen - answeredOnScreen == 0) {
			//Unicode checkmark
			badge.setText("\u2713");
			badge.setBackgroundResource(R.drawable.badge_background_complete);
		} else {
			badge.setBackgroundResource(R.drawable.badge_background);
			badge.setText(String.valueOf(requiredOnScreen - answeredOnScreen));
		}
    }

    /**
     * Takes in a form entry prompt that is obtained generically and if there
     * is already one on screen (which, for isntance, may have cached some of its data)
     * returns the object in use currently.
     * 
     * @param prompt
     * @return
     */
    private FormEntryPrompt getOnScreenPrompt(FormEntryPrompt prompt, ODKView view) {
    	FormIndex index = prompt.getIndex();
    	for(QuestionWidget widget : view.getWidgets()) {
    		if(widget.getFormId().equals(index)) {
    			return widget.getPrompt();
    		}
    	}
    	return prompt;
	}


	/**
     * Refreshes the current view. the controller and the displayed view can get out of sync due to
     * dialogs and restarts caused by screen orientation changes, so they're resynchronized here.
     */
    public void refreshCurrentView() {
        refreshCurrentView(true);
    }
    
    /**
     * Refreshes the current view. the controller and the displayed view can get out of sync due to
     * dialogs and restarts caused by screen orientation changes, so they're resynchronized here.
     */
    public void refreshCurrentView(boolean animateLastView) {
        if(mFormController == null) { throw new RuntimeException("Form state is lost! Cannot refresh current view. This shouldn't happen, please submit a bug report."); }
        int event = mFormController.getEvent();

        // When we refresh, repeat dialog state isn't maintained, so step back to the previous
        // question.
        // Also, if we're within a group labeled 'field list', step back to the beginning of that
        // group.
        // That is, skip backwards over repeat prompts, groups that are not field-lists,
        // repeat events, and indexes in field-lists that is not the containing group.
        while (event == FormEntryController.EVENT_PROMPT_NEW_REPEAT
                || (event == FormEntryController.EVENT_GROUP && !mFormController.indexIsInFieldList())
                || event == FormEntryController.EVENT_REPEAT
                || (mFormController.indexIsInFieldList() && !(event == FormEntryController.EVENT_GROUP))) {
            event = mFormController.stepToPreviousEvent();
        }
        
        //If we're at the beginning of form event, but don't show the screen for that, we need 
        //to get the next valid screen
        if(event == FormEntryController.EVENT_BEGINNING_OF_FORM && !PreferencesActivity.showFirstScreen(this)) {
            this.showNextView(true);
        } else {
            View current = createView(event);
            showView(current, AnimationType.FADE, animateLastView);
        }

    }


    /*
     * (non-Javadoc)
     * @see android.app.Activity#onPrepareOptionsMenu(android.view.Menu)
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.removeItem(MENU_LANGUAGES);
        menu.removeItem(MENU_HIERARCHY_VIEW);
        menu.removeItem(MENU_SAVE);
        menu.removeItem(MENU_PREFERENCES);

        if(mIncompleteEnabled) {
            menu.add(0, MENU_SAVE, 0, StringUtils.getStringRobust(this, R.string.save_all_answers)).setIcon(
                    android.R.drawable.ic_menu_save);
        }
        menu.add(0, MENU_HIERARCHY_VIEW, 0, StringUtils.getStringRobust(this, R.string.view_hierarchy)).setIcon(
                R.drawable.ic_menu_goto);
        
        menu.add(0, MENU_LANGUAGES, 0, StringUtils.getStringRobust(this, R.string.change_language))
                .setIcon(R.drawable.ic_menu_start_conversation)
                .setEnabled(
                        (mFormController == null || mFormController.getLanguages() == null || mFormController.getLanguages().length == 1) ? false
                                : true);
        
        
        menu.add(0, MENU_PREFERENCES, 0, StringUtils.getStringRobust(this, R.string.general_preferences)).setIcon(
                android.R.drawable.ic_menu_preferences);
        return true;
    }


    /*
     * (non-Javadoc)
     * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_LANGUAGES:
                createLanguageDialog();
                return true;
            case MENU_SAVE:
                // don't exit
                saveDataToDisk(DO_NOT_EXIT, isInstanceComplete(false), null);
                return true;
            case MENU_HIERARCHY_VIEW:
                if (currentPromptIsQuestion()) {
                    saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
                }
                Intent i = new Intent(this, FormHierarchyActivity.class);
                startActivityForResult(i, HIERARCHY_ACTIVITY);
                return true;
            case MENU_PREFERENCES:
                Intent pref = new Intent(this, PreferencesActivity.class);
                startActivity(pref);
                return true;
            case android.R.id.home:
                triggerUserQuitInput();
                return true;

        }
        return super.onOptionsItemSelected(item);
    }


    /**
     * @return true If the current index of the form controller contains questions
     */
    private boolean currentPromptIsQuestion() {
        return (mFormController.getEvent() == FormEntryController.EVENT_QUESTION || mFormController
                .getEvent() == FormEntryController.EVENT_GROUP);
    }

    
    private boolean saveAnswersForCurrentScreen(boolean evaluateConstraints) {
        return saveAnswersForCurrentScreen(evaluateConstraints, true);
    }

    /**
     * Attempt to save the answer(s) in the current screen to into the data model.
     * 
     * @param evaluateConstraints
     * @param failOnRequired Whether or not the constraint evaluation should return false if the question
     * is only required. (this is helpful for incomplete saves)
     * @return false if any error occurs while saving (constraint violated, etc...), true otherwise.
     */
    private boolean saveAnswersForCurrentScreen(boolean evaluateConstraints, boolean failOnRequired) {
        // only try to save if the current event is a question or a field-list group
        if (mFormController.getEvent() == FormEntryController.EVENT_QUESTION
                || (mFormController.getEvent() == FormEntryController.EVENT_GROUP && mFormController
                        .indexIsInFieldList())) {
            if(mCurrentView instanceof ODKView) {
                HashMap<FormIndex, IAnswerData> answers = ((ODKView) mCurrentView).getAnswers();
                Set<FormIndex> indexKeys = answers.keySet();
                for (FormIndex index : indexKeys) {
                    // Within a group, you can only save for question events
                    if (mFormController.getEvent(index) == FormEntryController.EVENT_QUESTION) {
                        int saveStatus = saveAnswer(answers.get(index), index, evaluateConstraints);
                        if (evaluateConstraints && (saveStatus != FormEntryController.ANSWER_OK &&
                                                    (failOnRequired || saveStatus != FormEntryController.ANSWER_REQUIRED_BUT_EMPTY))) {
                            createConstraintToast(index, mFormController.getQuestionPrompt(index).getConstraintText(), saveStatus);
                            return false;
                        }
                    } else {
                        Log.w(t,
                            "Attempted to save an index referencing something other than a question: "
                                    + index.getReference());
                    }
                }
            } else {
                Log.w(t, "Unknown view type rendered while current event was question or group! View type: " + mCurrentView == null ? "null" : mCurrentView.getClass().toString());
            }    
        }
        return true;
    }


    /**
     * Clears the answer on the screen.
     */
    private void clearAnswer(QuestionWidget qw) {
        qw.clearAnswer();
    }


    /*
     * (non-Javadoc)
     * @see android.app.Activity#onCreateContextMenu(android.view.ContextMenu, android.view.View, android.view.ContextMenu.ContextMenuInfo)
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
            menu.add(0, v.getId(), 0, StringUtils.getStringRobust(this, R.string.clear_answer));
        if (mFormController.indexContainsRepeatableGroup()) {
            menu.add(0, DELETE_REPEAT, 0, StringUtils.getStringRobust(this, R.string.delete_repeat));
        }
        menu.setHeaderTitle(StringUtils.getStringRobust(this, R.string.edit_prompt));
    }


    /*
     * (non-Javadoc)
     * @see android.app.Activity#onContextItemSelected(android.view.MenuItem)
     */
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        /*
         * We don't have the right view here, so we store the View's ID as the item ID and loop
         * through the possible views to find the one the user clicked on.
         */
        for (QuestionWidget qw : ((ODKView) mCurrentView).getWidgets()) {
            if (item.getItemId() == qw.getId()) {
                createClearDialog(qw);
            }
        }
        if (item.getItemId() == DELETE_REPEAT) {
            createDeleteRepeatConfirmDialog();
        }

        return super.onContextItemSelected(item);
    }


    /*
     * (non-Javadoc)
     * @see android.support.v4.app.FragmentActivity#onRetainCustomNonConfigurationInstance()
     * 
     * If we're loading, then we pass the loading thread to our next instance.
     */
    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        // if a form is loading, pass the loader task
        if (mFormLoaderTask != null && mFormLoaderTask.getStatus() != AsyncTask.Status.FINISHED)
            return mFormLoaderTask;

        // if a form is writing to disk, pass the save to disk task
        if (mSaveToDiskTask != null && mSaveToDiskTask.getStatus() != AsyncTask.Status.FINISHED)
            return mSaveToDiskTask;

        // mFormEntryController is static so we don't need to pass it.
        if (mFormController != null && currentPromptIsQuestion()) {
            saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
        }
        return null;
    }
    
    private String getHeaderString() {
        if(mHeaderString != null) {
            //Localization?
            return mHeaderString;
        } else {
            return StringUtils.getStringRobust(this, R.string.app_name) + " > " + mFormController.getFormTitle();
        }

    }

    /**
     * Creates a view given the View type and an event
     * 
     * @param event
     * @return newly created View
     */
    private View createView(int event) {
        boolean isGroup = false;

        setTitle(getHeaderString());
        switch (event) {
            case FormEntryController.EVENT_BEGINNING_OF_FORM:
                View startView = View.inflate(this, R.layout.form_entry_start, null);
                setTitle(getHeaderString());
                
                ((TextView) startView.findViewById(R.id.description)).setText(StringUtils.getStringRobust(this, R.string.enter_data_description, mFormController.getFormTitle()));

                ((CheckBox) startView.findViewById(R.id.screen_form_entry_start_cbx_dismiss)).setText(StringUtils.getStringRobust(this, R.string.form_entry_start_hide));

                ((TextView) startView.findViewById(R.id.screen_form_entry_advance_text)).setText(StringUtils.getStringRobust(this, R.string.advance));

                ((TextView) startView.findViewById(R.id.screen_form_entry_backup_text)).setText(StringUtils.getStringRobust(this, R.string.backup));

                Drawable image = null;
                String[] projection = {
                    FormsColumns.FORM_MEDIA_PATH
                };
                String selection = FormsColumns.FORM_FILE_PATH + "=?";
                String[] selectionArgs = {
                    mFormPath
                };
                Cursor c =
                    managedQuery(formProviderContentURI, projection, selection, selectionArgs,
                        null);
                String mediaDir = null;
                if (c.getCount() < 1) {
                    createErrorDialog("form Doesn't exist", true);
                    return new View(this);
                } else {
                    c.moveToFirst();
                    mediaDir = c.getString(c.getColumnIndex(FormsColumns.FORM_MEDIA_PATH));
                }

                BitmapDrawable bitImage = null;
                // attempt to load the form-specific logo...
                // this is arbitrarily silly
                bitImage = new BitmapDrawable(mediaDir + "/form_logo.png");

                if (bitImage != null && bitImage.getBitmap() != null
                        && bitImage.getIntrinsicHeight() > 0 && bitImage.getIntrinsicWidth() > 0) {
                    image = bitImage;
                }

                if (image == null) {
                    // show the opendatakit zig...
                    // image = getResources().getDrawable(R.drawable.opendatakit_zig);
                    ((ImageView) startView.findViewById(R.id.form_start_bling))
                            .setVisibility(View.GONE);
                } else {
                    ((ImageView) startView.findViewById(R.id.form_start_bling))
                            .setImageDrawable(image);
                }

                return startView;
            case FormEntryController.EVENT_END_OF_FORM:
                View endView = View.inflate(this, R.layout.form_entry_end, null);
                ((TextView) endView.findViewById(R.id.description)).setText(StringUtils.getStringRobust(this, R.string.save_enter_data_description,
                         mFormController.getFormTitle()));

                // checkbox for if finished or ready to send
                final CheckBox instanceComplete = ((CheckBox) endView.findViewById(R.id.mark_finished));
                instanceComplete.setText(StringUtils.getStringRobust(this, R.string.mark_finished));

                        //If incomplete is not enabled, make sure this box is checked.
                        instanceComplete.setChecked(!mIncompleteEnabled || isInstanceComplete(true));
                
                if(mFormController.isFormReadOnly() || !mIncompleteEnabled) {
                    instanceComplete.setVisibility(View.GONE);
                }

                // edittext to change the displayed name of the instance
                final EditText saveAs = (EditText) endView.findViewById(R.id.save_name);
                
                //TODO: Figure this out based on the content provider or some part of the context
                saveAs.setVisibility(View.GONE);
                endView.findViewById(R.id.save_form_as).setVisibility(View.GONE);

                // disallow carriage returns in the name
                InputFilter returnFilter = new InputFilter() {
                    public CharSequence filter(CharSequence source, int start, int end,
                            Spanned dest, int dstart, int dend) {
                        for (int i = start; i < end; i++) {
                            if (Character.getType((source.charAt(i))) == Character.CONTROL) {
                                return "";
                            }
                        }
                        return null;
                    }
                };
                saveAs.setFilters(new InputFilter[] {
                    returnFilter
                });

                String saveName = getDefaultFormTitle();
                
                saveAs.setText(saveName);

                // Create 'save' button
                Button button = (Button) endView.findViewById(R.id.save_exit_button);
                if(mFormController.isFormReadOnly()) {
                    button.setText(StringUtils.getStringRobust(this, R.string.exit));
                            button.setOnClickListener(new OnClickListener() {
                                /*
                                 * (non-Javadoc)
                                 * @see android.view.View.OnClickListener#onClick(android.view.View)
                                 */
                                @Override
                                public void onClick(View v) {
                                    finishReturnInstance();
                                }
                            });

                } else {
                    button.setText(StringUtils.getStringRobust(this, R.string.quit_entry));
                            button.setOnClickListener(new OnClickListener() {
                                /*
                                 * (non-Javadoc)
                                 * @see android.view.View.OnClickListener#onClick(android.view.View)
                                 */
                                @Override
                                public void onClick(View v) {
                                    // Form is marked as 'saved' here.
                                    if (saveAs.getText().length() < 1) {
                                        Toast.makeText(FormEntryActivity.this, StringUtils.getStringRobust(FormEntryActivity.this, R.string.save_as_error),
                                                Toast.LENGTH_SHORT).show();
                                    } else {
                                        saveDataToDisk(EXIT, instanceComplete.isChecked(), saveAs
                                                .getText().toString());
                                    }
                                }
                            });

                }

                return endView;
            case FormEntryController.EVENT_GROUP:
                isGroup = true;
            case FormEntryController.EVENT_QUESTION:
            
                ODKView odkv = null;
                // should only be a group here if the event_group is a field-list
                try {
                    odkv =
                        new ODKView(this, mFormController.getQuestionPrompts(),
                                mFormController.getGroupsForCurrentIndex(),
                                mFormController.getWidgetFactory(), this, isGroup);
                    Log.i(t, "created view for group");
                } catch (RuntimeException e) {
                    Logger.exception(e);
                    createErrorDialog(e.getMessage(), EXIT);
                    // this is badness to avoid a crash.
                    // really a next view should increment the formcontroller, create the view
                    // if the view is null, then keep the current view and pop an error.
                    return new View(this);
                }

                // Makes a "clear answer" menu pop up on long-click
                for (QuestionWidget qw : odkv.getWidgets()) {
                    if (!qw.getPrompt().isReadOnly() && !mFormController.isFormReadOnly()) {
                        registerForContextMenu(qw);
                    }
                }
                
                updateNavigationCues(odkv);
                
                return odkv;
            default:
                Log.e(t, "Attempted to create a view that does not exist.");
                return null;
        }
    }


    /*
     * (non-Javadoc)
     * @see android.app.Activity#dispatchTouchEvent(android.view.MotionEvent)
     */
    @SuppressLint("NewApi")
    @Override
    public boolean dispatchTouchEvent(MotionEvent mv) {
        //We need to ignore this even if it's processed by the action
        //bar (if one exists)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            View customView = getActionBar().getCustomView();
            if(customView != null) {
                if(customView.dispatchTouchEvent(mv)) {
                    return true;
                }
            }
        }

        
        
        boolean handled = mGestureDetector.onTouchEvent(mv);
        if (!handled) {
            return super.dispatchTouchEvent(mv);
        }

        return handled; // this is always true
    }


    /**
     * Determines what should be displayed on the screen. Possible options are: a question, an ask
     * repeat dialog, or the submit screen. Also saves answers to the data model after checking
     * constraints.
     */
    private void showNextView() { showNextView(false); }
    private void showNextView(boolean resuming) {
        
        if(!resuming && mFormController.getEvent() == FormEntryController.EVENT_BEGINNING_OF_FORM) {
            //See if we should stop displaying the start screen
            CheckBox stopShowingIntroScreen = (CheckBox)mCurrentView.findViewById(R.id.screen_form_entry_start_cbx_dismiss);
            //Not sure why it would, but maybe timing issues?
            if(stopShowingIntroScreen != null) {
                if(stopShowingIntroScreen.isChecked()) {
                    //set it!
                    SharedPreferences sharedPreferences =  PreferenceManager.getDefaultSharedPreferences(this);
                    sharedPreferences.edit().putBoolean(PreferencesActivity.KEY_SHOW_START_SCREEN, false).commit();
                }
            }
        }
        
        if (currentPromptIsQuestion()) {
            if (!saveAnswersForCurrentScreen(EVALUATE_CONSTRAINTS)) {
                // A constraint was violated so a dialog should be showing.
                return;
            }
        }

        if (mFormController.getEvent() != FormEntryController.EVENT_END_OF_FORM) {
            int event;
            
            try{
            
            group_skip: do {
                event = mFormController.stepToNextEvent(FormController.STEP_OVER_GROUP);
                switch (event) {
                    case FormEntryController.EVENT_QUESTION:
                    case FormEntryController.EVENT_END_OF_FORM:
                        View next = createView(event);
                        if(!resuming) {
                            showView(next, AnimationType.RIGHT);
                        } else {
                            showView(next, AnimationType.FADE, false);
                        }
                        break group_skip;
                    case FormEntryController.EVENT_PROMPT_NEW_REPEAT:
                        createRepeatDialog();
                        break group_skip;
                    case FormEntryController.EVENT_GROUP:
                    	//We only hit this event if we're at the _opening_ of a field
                    	//list, so it seems totally fine to do it this way, technically
                    	//though this should test whether the index is the field list
                    	//host.
                        if (mFormController.indexIsInFieldList()
                                && mFormController.getQuestionPrompts().length != 0) {
                            View nextGroupView = createView(event);
                            if(!resuming) {
                                showView(nextGroupView, AnimationType.RIGHT);
                            } else {
                                showView(nextGroupView, AnimationType.FADE, false);
                            }
                            break group_skip;
                        }
                        // otherwise it's not a field-list group, so just skip it
                        break;
                    case FormEntryController.EVENT_REPEAT:
                        Log.i(t, "repeat: " + mFormController.getFormIndex().getReference());
                        // skip repeats
                        break;
                    case FormEntryController.EVENT_REPEAT_JUNCTURE:
                        Log.i(t, "repeat juncture: "
                                + mFormController.getFormIndex().getReference());
                        // skip repeat junctures until we implement them
                        break;
                    default:
                        Log.w(t,
                            "JavaRosa added a new EVENT type and didn't tell us... shame on them.");
                        break;
                }
            } while (event != FormEntryController.EVENT_END_OF_FORM);
            }catch(XPathTypeMismatchException e){
                Logger.exception(e);
                FormEntryActivity.this.createErrorDialog(e.getMessage(), EXIT);
            }

        } else {
            mBeenSwiped = false;
        }
    }


    /**
     * Determines what should be displayed between a question, or the start screen and displays the
     * appropriate view. Also saves answers to the data model without checking constraints.
     */
    private void showPreviousView() {
        // The answer is saved on a back swipe, but question constraints are ignored.
        if (currentPromptIsQuestion()) {
            saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
        }
        
        FormIndex startIndex = mFormController.getFormIndex();
        FormIndex lastValidIndex = startIndex;

        if (mFormController.getEvent() != FormEntryController.EVENT_BEGINNING_OF_FORM) {
            int event = mFormController.stepToPreviousEvent();

            //Step backwards until we either find a question, the beginning of the form,
            //or a field list with valid questions inside
            while (event != FormEntryController.EVENT_BEGINNING_OF_FORM
                    && event != FormEntryController.EVENT_QUESTION
                    && !(event == FormEntryController.EVENT_GROUP
                            && mFormController.indexIsInFieldList() && mFormController
                            .getQuestionPrompts().length != 0)) {
                event = mFormController.stepToPreviousEvent();
                lastValidIndex = mFormController.getFormIndex();
            }
                        
            //check if we're at the beginning and not doing the whole "First screen" thing
            if(event == FormEntryController.EVENT_BEGINNING_OF_FORM && !PreferencesActivity.showFirstScreen(this)) {
                
                //If so, we can't go all the way back here, so we've gotta hit the last index that was valid
                mFormController.jumpToIndex(lastValidIndex);
                
                //Did we jump at all? (not sure how we could have, but there might be a mismatch)
                if(lastValidIndex.equals(startIndex)) {
                    //If not, don't even bother changing the view. 
                    //NOTE: This needs to be the same as the
                    //exit condition below, in case either changes
                    mBeenSwiped = false;
                    FormEntryActivity.this.triggerUserQuitInput();
                    return;
                }
                
                //We might have walked all the way back still, which isn't great, 
                //so keep moving forward again until we find it
                if(lastValidIndex.isBeginningOfFormIndex()) {
                    //there must be a repeat between where we started and the beginning of hte form, walk back up to it
                    this.showNextView(true);
                    return;
                }
            }
            View next = createView(event);
            showView(next, AnimationType.LEFT);

        } else {
            //NOTE: this needs to match the exist condition above
            //when there is no start screen
            mBeenSwiped = false;
            FormEntryActivity.this.triggerUserQuitInput();
        }
    }


    /**
     * Displays the View specified by the parameter 'next', animating both the current view and next
     * appropriately given the AnimationType. Also updates the progress bar.
     */
    public void showView(View next, AnimationType from) { showView(next, from, true); }
    public void showView(View next, AnimationType from, boolean animateLastView) {
        switch (from) {
            case RIGHT:
                mInAnimation = AnimationUtils.loadAnimation(this, R.anim.push_left_in);
                mOutAnimation = AnimationUtils.loadAnimation(this, R.anim.push_left_out);
                break;
            case LEFT:
                mInAnimation = AnimationUtils.loadAnimation(this, R.anim.push_right_in);
                mOutAnimation = AnimationUtils.loadAnimation(this, R.anim.push_right_out);
                break;
            case FADE:
                mInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in);
                mOutAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_out);
                break;
        }

        if (mCurrentView != null) {
            if(animateLastView) {
                mCurrentView.startAnimation(mOutAnimation);
            }
        	mViewPane.removeView(mCurrentView);
        }

        mInAnimation.setAnimationListener(this);

        RelativeLayout.LayoutParams lp =
            new RelativeLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);

        mCurrentView = next;
        mViewPane.addView(mCurrentView, lp);

        mCurrentView.startAnimation(mInAnimation);
        if (mCurrentView instanceof ODKView)
            ((ODKView) mCurrentView).setFocus(this);
        else {
            InputMethodManager inputManager =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.hideSoftInputFromWindow(mCurrentView.getWindowToken(), 0);
        }
//        setClickListenersForEverything();
    }


    // Hopefully someday we can use managed dialogs when the bugs are fixed
    /*
     * Ideally, we'd like to use Android to manage dialogs with onCreateDialog() and
     * onPrepareDialog(), but dialogs with dynamic content are broken in 1.5 (cupcake). We do use
     * managed dialogs for our static loading ProgressDialog. The main issue we noticed and are
     * waiting to see fixed is: onPrepareDialog() is not called after a screen orientation change.
     * http://code.google.com/p/android/issues/detail?id=1639
     */

    //
    /**
     * Creates and displays a dialog displaying the violated constraint.
     */
    private void createConstraintToast(FormIndex index, String constraintText, int saveStatus) {
        switch (saveStatus) {
            case FormEntryController.ANSWER_CONSTRAINT_VIOLATED:
                if (constraintText == null) {
                    constraintText = StringUtils.getStringRobust(this, R.string.invalid_answer_error);
                }
                break;
            case FormEntryController.ANSWER_REQUIRED_BUT_EMPTY:
                constraintText = StringUtils.getStringRobust(this, R.string.required_answer_error);
                break;
        }
        
        boolean displayed = false;
        //We need to see if question in violation is on the screen, so we can show this cleanly.
        for(QuestionWidget q : ((ODKView)mCurrentView).getWidgets()) {
            if(index.equals(q.getFormId())) {
                q.notifyInvalid(constraintText);
                displayed = true;
                break;
            }
        }

        if(!displayed) {
            showCustomToast(constraintText, Toast.LENGTH_SHORT);
        }
        mBeenSwiped = false;
    }


    /**
     * Creates a toast with the specified message.
     * 
     * @param message
     */
    private void showCustomToast(String message, int duration) {
        LayoutInflater inflater =
            (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        View view = inflater.inflate(R.layout.toast_view, null);

        // set the text in the view
        TextView tv = (TextView) view.findViewById(R.id.message);
        tv.setText(message);

        Toast t = new Toast(this);
        t.setView(view);
        t.setDuration(duration);
        t.setGravity(Gravity.CENTER, 0, 0);
        t.show();
    }


    /**
     * Creates and displays a dialog asking the user if they'd like to create a repeat of the
     * current group.
     */
    private void createRepeatDialog() {
        ContextThemeWrapper wrapper = new ContextThemeWrapper(this, R.style.DialogBaseTheme);
        
        View view = LayoutInflater.from(wrapper).inflate(R.layout.component_repeat_new_dialog, null);


        mRepeatDialog = new AlertDialog.Builder(wrapper).create();
        
        final AlertDialog theDialog = mRepeatDialog;
        
        mRepeatDialog.setView(view);
        
        mRepeatDialog.setIcon(android.R.drawable.ic_dialog_info);
        
        boolean navBar = PreferencesActivity.getProgressBarMode(this).useNavigationBar();
        
        //this is super gross...
        NavigationDetails details = null;
        if(navBar) {
            details = calculateNavigationStatus();
        }
        
        final boolean backExitsForm = navBar && !details.relevantBeforeCurrentScreen;
        
        final boolean nextExitsForm = navBar && details.relevantAfterCurrentScreen == 0;
        
        Button back = (Button)view.findViewById(R.id.component_repeat_back);
        
        back.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if(backExitsForm) {
					FormEntryActivity.this.triggerUserQuitInput();
				} else {
					theDialog.dismiss();
		            FormEntryActivity.this.refreshCurrentView(false);
				}
			}
        	
        });
        
        Button newButton = (Button)view.findViewById(R.id.component_repeat_new);

        newButton.setOnClickListener(new OnClickListener() {
                        @Override
			public void onClick(View v) {
                            theDialog.dismiss();
                            try {
                                mFormController.newRepeat();
                            } catch (XPathTypeMismatchException e) {
                                Logger.exception(e);
                                FormEntryActivity.this.createErrorDialog(e.getMessage(), EXIT);
                                return;
                            }
                            showNextView();				
			}
        });
        
        Button skip = (Button)view.findViewById(R.id.component_repeat_skip);
        
        skip.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				theDialog.dismiss();
            	if(!nextExitsForm) {
            		showNextView();
            	} else {
            		FormEntryActivity.this.triggerUserFormComplete();
            	}
			}
        	
        });
        
        
        
        
        back.setText(StringUtils.getStringRobust(this, R.string.repeat_go_back));

                //Load up our icons
                Drawable exitIcon = getResources().getDrawable(R.drawable.icon_exit);
        exitIcon.setBounds(0, 0, exitIcon.getIntrinsicWidth(), exitIcon.getIntrinsicHeight());

        Drawable doneIcon = getResources().getDrawable(R.drawable.icon_done);
        doneIcon.setBounds(0, 0, doneIcon.getIntrinsicWidth(), doneIcon.getIntrinsicHeight());
        
        
        if (mFormController.getLastRepeatCount() > 0) {
            mRepeatDialog.setTitle(StringUtils.getStringRobust(this, R.string.leaving_repeat_ask));
                    mRepeatDialog.setMessage(StringUtils.getStringRobust(this, R.string.add_another_repeat,
                            mFormController.getLastGroupText()));
            newButton.setText(StringUtils.getStringRobust(this, R.string.add_another));
            if(!nextExitsForm) {
            	skip.setText(StringUtils.getStringRobust(this, R.string.leave_repeat_yes));
            } else {
            	skip.setText(StringUtils.getStringRobust(this, R.string.leave_repeat_yes_exits));
            }

        } else {
            mRepeatDialog.setTitle(StringUtils.getStringRobust(this, R.string.entering_repeat_ask));
                    mRepeatDialog.setMessage(StringUtils.getStringRobust(this, R.string.add_repeat,
                            mFormController.getLastGroupText()));
            newButton.setText(StringUtils.getStringRobust(this, R.string.entering_repeat));
            if(!nextExitsForm) {
            	skip.setText(StringUtils.getStringRobust(this, R.string.add_repeat_no));
            } else {
            	skip.setText(StringUtils.getStringRobust(this, R.string.add_repeat_no_exits));

            }
        }
        
        mRepeatDialog.setCancelable(false);
        mRepeatDialog.show();

        if(nextExitsForm) {
        	skip.setCompoundDrawables(null, doneIcon, null, null);
        } 
        
        if(backExitsForm) {
        	back.setCompoundDrawables(null, exitIcon, null, null);
        }
        mBeenSwiped = false;
    }


    /**
     * Creates and displays dialog with the given errorMsg.
     */
    private void createErrorDialog(String errorMsg, final boolean shouldExit) {
        mErrorMessage = errorMsg;
        mAlertDialog = new AlertDialog.Builder(this).create();
        mAlertDialog.setIcon(android.R.drawable.ic_dialog_info);
        mAlertDialog.setTitle(StringUtils.getStringRobust(this, R.string.error_occured));
                mAlertDialog.setMessage(errorMsg);
        DialogInterface.OnClickListener errorListener = new DialogInterface.OnClickListener() {
            /*
             * (non-Javadoc)
             * @see android.content.DialogInterface.OnClickListener#onClick(android.content.DialogInterface, int)
             */
            @Override
            public void onClick(DialogInterface dialog, int i) {
                switch (i) {
                    case DialogInterface.BUTTON1:
                        if (shouldExit) {
                            setResult(RESULT_CANCELED);
                            finish();
                        }
                        break;
                }
            }
        };
        mAlertDialog.setCancelable(false);
        mAlertDialog.setButton(StringUtils.getStringRobust(this, R.string.ok), errorListener);
                mAlertDialog.show();
    }


    /**
     * Creates a confirm/cancel dialog for deleting repeats.
     */
    private void createDeleteRepeatConfirmDialog() {
        mAlertDialog = new AlertDialog.Builder(this).create();
        mAlertDialog.setIcon(android.R.drawable.ic_dialog_info);
        String name = mFormController.getLastRepeatedGroupName();
        int repeatcount = mFormController.getLastRepeatedGroupRepeatCount();
        if (repeatcount != -1) {
            name += " (" + (repeatcount + 1) + ")";
        }
        mAlertDialog.setTitle(StringUtils.getStringRobust(this, R.string.delete_repeat_ask, name));
                mAlertDialog.setMessage(StringUtils.getStringRobust(this, R.string.delete_repeat_confirm, name));
                        DialogInterface.OnClickListener quitListener = new DialogInterface.OnClickListener() {
                            /*
                             * (non-Javadoc)
                             * @see android.content.DialogInterface.OnClickListener#onClick(android.content.DialogInterface, int)
                             */
                            @Override
                            public void onClick(DialogInterface dialog, int i) {
                                switch (i) {
                                    case DialogInterface.BUTTON1: // yes
                                        mFormController.deleteRepeat();
                                        showPreviousView();
                                        break;
                                    case DialogInterface.BUTTON2: // no
                                        break;
                                }
                            }
                        };
        mAlertDialog.setCancelable(false);
        mAlertDialog.setButton(StringUtils.getStringRobust(this, R.string.discard_group), quitListener);
                mAlertDialog.setButton2(StringUtils.getStringRobust(this, R.string.delete_repeat_no), quitListener);
                        mAlertDialog.show();
    }


    /**
     * Saves data and writes it to disk. If exit is set, program will exit after save completes.
     * Complete indicates whether the user has marked the isntancs as complete. If updatedSaveName
     * is non-null, the instances content provider is updated with the new name
     */
    private boolean saveDataToDisk(boolean exit, boolean complete, String updatedSaveName) {
        // save current answer
        if (!saveAnswersForCurrentScreen(EVALUATE_CONSTRAINTS, complete)) {
            Toast.makeText(this, StringUtils.getStringRobust(this, R.string.data_saved_error), Toast.LENGTH_SHORT).show();
            return false;
        }

        mSaveToDiskTask =
            new SaveToDiskTask(getIntent().getData(), exit, complete, updatedSaveName, this, instanceProviderContentURI, symetricKey);
        mSaveToDiskTask.setFormSavedListener(this);
        mSaveToDiskTask.execute();
        showDialog(SAVING_DIALOG);

        return true;
    }


    /**
     * Create a dialog with options to save and exit, save, or quit without saving
     */
    private void createQuitDialog() {
        final String[] items = mIncompleteEnabled ?  
                new String[] {StringUtils.getStringRobust(this, R.string.keep_changes), StringUtils.getStringRobust(this, R.string.do_not_save)} :
                new String[] {StringUtils.getStringRobust(this, R.string.do_not_save)};

                        mAlertDialog =
                                new AlertDialog.Builder(this)
                                        .setIcon(android.R.drawable.ic_dialog_info)
                                        .setTitle(StringUtils.getStringRobust(this, R.string.quit_application, mFormController.getFormTitle()))
                                                .setNeutralButton(StringUtils.getStringRobust(this, R.string.do_not_exit),
                        new DialogInterface.OnClickListener() {
                            /*
                             * (non-Javadoc)
                             * @see android.content.DialogInterface.OnClickListener#onClick(android.content.DialogInterface, int)
                             */
                            @Override
                            public void onClick(DialogInterface dialog, int id) {

                                dialog.cancel();

                            }
                        }).setItems(items, new DialogInterface.OnClickListener() {
                        /*
                         * (non-Javadoc)
                         * @see android.content.DialogInterface.OnClickListener#onClick(android.content.DialogInterface, int)
                         */
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            switch (which) {

                                case 0: // save and exit
                                    if(items.length == 1) {
                                        discardChangesAndExit();
                                    } else {
                                        saveDataToDisk(EXIT, isInstanceComplete(false), null);
                                    }
                                    break;

                                case 1: // discard changes and exit
                                    discardChangesAndExit();
                                    break;

                                case 2:// do nothing
                                    break;
                            }
                        }
                    }).create();
        mAlertDialog.getListView().setSelector(R.drawable.selector);
        mAlertDialog.show();
    }
    
    private void discardChangesAndExit() {

        String selection =
            InstanceColumns.INSTANCE_FILE_PATH + " like '"
                    + mInstancePath + "'";
        Cursor c =
            FormEntryActivity.this.managedQuery(
                instanceProviderContentURI, null, selection, null,
                null);

        // if it's not already saved, erase everything
        if (c.getCount() < 1) {
            int images = 0;
            int audio = 0;
            int video = 0;
            // delete media first
            String instanceFolder =
                mInstancePath.substring(0,
                    mInstancePath.lastIndexOf("/") + 1);
            Log.i(t, "attempting to delete: " + instanceFolder);

            String where =
                Images.Media.DATA + " like '" + instanceFolder + "%'";

            String[] projection = {
                Images.ImageColumns._ID
            };

            // images
            Cursor imageCursor = null;
            try {
                imageCursor = getContentResolver().query(
                            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            projection, where, null, null);
                if (imageCursor.getCount() > 0) {
                    imageCursor.moveToFirst();
                    String id =
                        imageCursor.getString(imageCursor
                                .getColumnIndex(Images.ImageColumns._ID));

                    Log.i(
                        t,
                        "attempting to delete: "
                                + Uri.withAppendedPath(
                                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                    id));
                    images =
                        getContentResolver()
                                .delete(
                                    Uri.withAppendedPath(
                                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                        id), null, null);
                }
            } finally {
                if ( imageCursor != null ) {
                    imageCursor.close();
                }
            }

            // audio
            Cursor audioCursor = null;
            try {
                audioCursor = getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection, where, null, null);
                if (audioCursor.getCount() > 0) {
                    audioCursor.moveToFirst();
                    String id =
                        audioCursor.getString(imageCursor
                                .getColumnIndex(Images.ImageColumns._ID));

                    Log.i(
                        t,
                        "attempting to delete: "
                                + Uri.withAppendedPath(
                                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                    id));
                    audio =
                        getContentResolver()
                                .delete(
                                    Uri.withAppendedPath(
                                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                        id), null, null);
                }
            } finally {
                if ( audioCursor != null ) {
                    audioCursor.close();
                }
            }

            // video
            Cursor videoCursor = null;
            try {
                videoCursor = getContentResolver().query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection, where, null, null);
                if (videoCursor.getCount() > 0) {
                    videoCursor.moveToFirst();
                    String id =
                        videoCursor.getString(imageCursor
                                .getColumnIndex(Images.ImageColumns._ID));

                    Log.i(
                        t,
                        "attempting to delete: "
                                + Uri.withAppendedPath(
                                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                    id));
                    video =
                        getContentResolver()
                                .delete(
                                    Uri.withAppendedPath(
                                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                        id), null, null);
                }
            } finally {
                if ( videoCursor != null ) {
                    videoCursor.close();
                }
            }

            Log.i(t, "removed from content providers: " + images
                    + " image files, " + audio + " audio files,"
                    + " and " + video + " video files.");
            File f = new File(instanceFolder);
            if (f.exists() && f.isDirectory()) {
                for (File del : f.listFiles()) {
                    Log.i(t, "deleting file: " + del.getAbsolutePath());
                    del.delete();
                }
                f.delete();
            }
        }

        finishReturnInstance(false);
    }


    /**
     * Confirm clear answer dialog
     */
    private void createClearDialog(final QuestionWidget qw) {
        mAlertDialog = new AlertDialog.Builder(this).create();
        mAlertDialog.setIcon(android.R.drawable.ic_dialog_info);

        mAlertDialog.setTitle(StringUtils.getStringRobust(this, R.string.clear_answer_ask));

        String question = qw.getPrompt().getLongText();

        if (question.length() > 50) {
            question = question.substring(0, 50) + "...";
        }

        mAlertDialog.setMessage(StringUtils.getStringRobust(this, R.string.clearanswer_confirm, question));

        DialogInterface.OnClickListener quitListener = new DialogInterface.OnClickListener() {

                    /*
                     * (non-Javadoc)
                     * @see android.content.DialogInterface.OnClickListener#onClick(android.content.DialogInterface, int)
                     */
                    @Override
                    public void onClick(DialogInterface dialog, int i) {
                        switch (i) {
                            case DialogInterface.BUTTON1: // yes
                                clearAnswer(qw);
                                saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
                                break;
                            case DialogInterface.BUTTON2: // no
                                break;
                        }
                    }
                };
        mAlertDialog.setCancelable(false);
        mAlertDialog.setButton(StringUtils.getStringRobust(this, R.string.discard_answer), quitListener);
                mAlertDialog.setButton2(StringUtils.getStringRobust(this, R.string.clear_answer_no), quitListener);
                        mAlertDialog.show();
    }


    /**
     * Creates and displays a dialog allowing the user to set the language for the form.
     */
    private void createLanguageDialog() {
        final String[] languages = mFormController.getLanguages();
        int selected = -1;
        if (languages != null) {
            String language = mFormController.getLanguage();
            for (int i = 0; i < languages.length; i++) {
                if (language.equals(languages[i])) {
                    selected = i;
                }
            }
        }
        mAlertDialog =
            new AlertDialog.Builder(this)
                    .setSingleChoiceItems(languages, selected,
                        new DialogInterface.OnClickListener() {
                            /*
                             * (non-Javadoc)
                             * @see android.content.DialogInterface.OnClickListener#onClick(android.content.DialogInterface, int)
                             */
                            @Override
                            public void onClick(DialogInterface dialog, int whichButton) {
                                // Update the language in the content provider when selecting a new
                                // language
                                ContentValues values = new ContentValues();
                                values.put(FormsColumns.LANGUAGE, languages[whichButton]);
                                String selection = FormsColumns.FORM_FILE_PATH + "=?";
                                String selectArgs[] = {
                                    mFormPath
                                };
                                int updated =
                                    getContentResolver().update(formProviderContentURI, values,
                                        selection, selectArgs);
                                Log.i(t, "Updated language to: " + languages[whichButton] + " in "
                                        + updated + " rows");

                                mFormController.setLanguage(languages[whichButton]);
                                dialog.dismiss();
                                if (currentPromptIsQuestion()) {
                                    saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
                                }
                                refreshCurrentView();
                            }
                        })
                    .setTitle(StringUtils.getStringRobust(this, R.string.change_language))
                    .setNegativeButton(StringUtils.getStringRobust(this, R.string.do_not_change),
                        new DialogInterface.OnClickListener() {
                            /*
                             * (non-Javadoc)
                             * @see android.content.DialogInterface.OnClickListener#onClick(android.content.DialogInterface, int)
                             */
                            @Override
                            public void onClick(DialogInterface dialog, int whichButton) {
                            }
                        }).create();
        mAlertDialog.show();
    }


    /*
     * (non-Javadoc)
     * @see android.app.Activity#onCreateDialog(int)
     * 
     * We use Android's dialog management for loading/saving progress dialogs
     */
    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case PROGRESS_DIALOG:
                mProgressDialog = new ProgressDialog(this);
                DialogInterface.OnClickListener loadingButtonListener =
                    new DialogInterface.OnClickListener() {
                    /*
                     * (non-Javadoc)
                     * @see android.content.DialogInterface.OnClickListener#onClick(android.content.DialogInterface, int)
                     */
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            mFormLoaderTask.setFormLoaderListener(null);
                            mFormLoaderTask.cancel(true);
                            finish();
                        }
                    };
                mProgressDialog.setIcon(android.R.drawable.ic_dialog_info);
                mProgressDialog.setTitle(StringUtils.getStringRobust(this, R.string.loading_form));
                        mProgressDialog.setMessage(StringUtils.getStringRobust(this, R.string.please_wait));
                                mProgressDialog.setIndeterminate(true);
                mProgressDialog.setCancelable(false);
                mProgressDialog.setButton(StringUtils.getStringRobust(this, R.string.cancel_loading_form),
                        loadingButtonListener);
                return mProgressDialog;
            case SAVING_DIALOG:
                mProgressDialog = new ProgressDialog(this);
                DialogInterface.OnClickListener savingButtonListener =
                    new DialogInterface.OnClickListener() {
                        /*
                         * (non-Javadoc)
                         * @see android.content.DialogInterface.OnClickListener#onClick(android.content.DialogInterface, int)
                         */
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            mSaveToDiskTask.setFormSavedListener(null);
                            mSaveToDiskTask.cancel(true);
                        }
                    };
                mProgressDialog.setIcon(android.R.drawable.ic_dialog_info);
                mProgressDialog.setTitle(StringUtils.getStringRobust(this, R.string.saving_form));
                        mProgressDialog.setMessage(StringUtils.getStringRobust(this, R.string.please_wait));
                                mProgressDialog.setIndeterminate(true);
                mProgressDialog.setCancelable(false);
                mProgressDialog.setButton(StringUtils.getStringRobust(this, R.string.cancel), savingButtonListener);
                        mProgressDialog.setButton(StringUtils.getStringRobust(this, R.string.cancel_saving_form),
                                savingButtonListener);
                return mProgressDialog;
        }
        return null;
    }


    /**
     * Dismiss any showing dialogs that we manually manage.
     */
    private void dismissDialogs() {
        if (mAlertDialog != null && mAlertDialog.isShowing()) {
            mAlertDialog.dismiss();
        }
        if(mRepeatDialog != null && mRepeatDialog.isShowing()) {
        	mRepeatDialog.dismiss();
        }
    }


    /*
     * (non-Javadoc)
     * @see android.support.v4.app.FragmentActivity#onPause()
     */
    @Override
    protected void onPause() {
        dismissDialogs();
        if (mCurrentView != null && currentPromptIsQuestion()) {
            saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
        }
        super.onPause();
    }


    /*
     * (non-Javadoc)
     * @see android.support.v4.app.FragmentActivity#onResume()
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (mFormLoaderTask != null) {
            mFormLoaderTask.setFormLoaderListener(this);
            if (mFormController != null && mFormLoaderTask.getStatus() == AsyncTask.Status.FINISHED) {
                dismissDialog(PROGRESS_DIALOG);
                refreshCurrentView();
            }
        }
        if (mSaveToDiskTask != null) {
            mSaveToDiskTask.setFormSavedListener(this);
        }
        if (mErrorMessage != null && (mAlertDialog != null && !mAlertDialog.isShowing())) {
            createErrorDialog(mErrorMessage, EXIT);
            return;
        }
        
        //csims@dimagi.com - 22/08/2012 - For release only, fix immediately.
        //There is a _horribly obnoxious_ bug in TimePickers that messes up how they work
        //on screen rotation. We need to re-do any setAnswers that we perform on them after
        //onResume.
        try {
            if(mCurrentView instanceof ODKView) {
                ODKView ov = ((ODKView) mCurrentView);
                if(ov.getWidgets() != null) {
                    for(QuestionWidget qw : ov.getWidgets()) {
                        if(qw instanceof DateTimeWidget) {
                            ((DateTimeWidget)qw).setAnswer();
                        } else if(qw instanceof TimeWidget) {
                            ((TimeWidget)qw).setAnswer();
                        }
                    }
                }
            }
        } catch(Exception e) {
            //if this fails, we _really_ don't want to mess anything up. this is a last minute
            //fix
        }
        
    }

    /**
     * Call when the user provides input that they want to quit the form
     */
    private void triggerUserQuitInput() {
        //If we're just reviewing a read only form, don't worry about saving
        //or what not, just quit
        if(mFormController.isFormReadOnly()) {
            //It's possible we just want to "finish" here, but
            //I don't really wanna break any c compatibility
            finishReturnInstance();
        } else {
            createQuitDialog();
        }
    }
    
    /**
     * Get the default title for ODK's "Form title" field
     *  
     * @return
     */
    private String getDefaultFormTitle() {
        String saveName = mFormController.getFormTitle();
        if (getContentResolver().getType(getIntent().getData()) == InstanceColumns.CONTENT_ITEM_TYPE) {
            Uri instanceUri = getIntent().getData();
            Cursor instance = managedQuery(instanceUri, null, null, null, null);
            if (instance.getCount() == 1) {
                instance.moveToFirst();
                saveName =
                    instance.getString(instance
                            .getColumnIndex(InstanceColumns.DISPLAY_NAME));
            }
        }
        return saveName;
    }
    
    /**
     * Call when the user is ready to save and return the current form as complete 
     */
    private void triggerUserFormComplete() {
        saveDataToDisk(EXIT, true, getDefaultFormTitle());
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
            	triggerUserQuitInput();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (event.isAltPressed() && !mBeenSwiped) {
                    mBeenSwiped = true;
                    showNextView();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (event.isAltPressed() && !mBeenSwiped) {
                    mBeenSwiped = true;
                    showPreviousView();
                    return true;
                }
                break;
        }
        return super.onKeyDown(keyCode, event);
    }


    /*
     * (non-Javadoc)
     * @see android.support.v4.app.FragmentActivity#onDestroy()
     */
    @Override
    protected void onDestroy() {
        if (mFormLoaderTask != null) {
            mFormLoaderTask.setFormLoaderListener(null);
            // We have to call cancel to terminate the thread, otherwise it
            // lives on and retains the FEC in memory.
            // but only if it's done, otherwise the thread never returns
            if (mFormLoaderTask.getStatus() == AsyncTask.Status.FINISHED) {
                mFormLoaderTask.cancel(true);
                mFormLoaderTask.destroy();
            }
        }
        if (mSaveToDiskTask != null) {
            mSaveToDiskTask.setFormSavedListener(null);
            // We have to call cancel to terminate the thread, otherwise it
            // lives on and retains the FEC in memory.
            if (mSaveToDiskTask.getStatus() == AsyncTask.Status.FINISHED) {
                mSaveToDiskTask.cancel(false);
            }
        }
        if (mNoGPSReceiver != null) {
            unregisterReceiver(mNoGPSReceiver);
        }

        super.onDestroy();

    }


    /*
     * (non-Javadoc)
     * @see android.view.animation.Animation.AnimationListener#onAnimationEnd(android.view.animation.Animation)
     */
    @Override
    public void onAnimationEnd(Animation arg0) {
        mBeenSwiped = false;
    }


    /*
     * (non-Javadoc)
     * @see android.view.animation.Animation.AnimationListener#onAnimationRepeat(android.view.animation.Animation)
     */
    @Override
    public void onAnimationRepeat(Animation animation) {
        // Added by AnimationListener interface.
    }


    /*
     * (non-Javadoc)
     * @see android.view.animation.Animation.AnimationListener#onAnimationStart(android.view.animation.Animation)
     */
    @Override
    public void onAnimationStart(Animation animation) {
        // Added by AnimationListener interface.
    }


    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.listeners.FormLoaderListener#loadingComplete(org.odk.collect.android.logic.FormController)
     * 
     * loadingComplete() is called by FormLoaderTask once it has finished loading a form.
     */
    @SuppressLint("NewApi")
    @Override
    public void loadingComplete(FormController fc) {
        dismissDialog(PROGRESS_DIALOG);

        mFormController = fc;
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB){
            // Newer menus may have already built the menu, before all data was ready
            invalidateOptionsMenu();
        }
        
        Localizer mLocalizer = Localization.getGlobalLocalizerAdvanced();
        
        if(mLocalizer != null){
            String mLocale = mLocalizer.getLocale();
            
            if (mLocale != null && fc.getLanguages() != null && Arrays.asList(fc.getLanguages()).contains(mLocale)){
                fc.setLanguage(mLocale);
            }
            else{
                Logger.log("formloader", "The current locale is not set");
            }
        } else{
            Logger.log("formloader", "Could not get the localizer");
        }

        // Set saved answer path
        if (mInstancePath == null) {

            // Create new answer folder.
            String time =
                new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")
                        .format(Calendar.getInstance().getTime());
            String file =
                mFormPath.substring(mFormPath.lastIndexOf('/') + 1, mFormPath.lastIndexOf('.'));
            String path = mInstanceDestination + "/" + file + "_" + time;
            if (FileUtils.createFolder(path)) {
                mInstancePath = path + "/" + file + "_" + time + ".xml";
            }
        } else {
            // we've just loaded a saved form, so start in the hierarchy view
            Intent i = new Intent(this, FormHierarchyActivity.class);
            startActivityForResult(i, HIERARCHY_ACTIVITY_FIRST_START);
            return; // so we don't show the intro screen before jumping to the hierarchy
        }
        
        //mFormController.setLanguage(mFormController.getLanguage());
        
        /* here was code that loaded cached language preferences fin the
         * collect code. we've overridden that to use our language
         * from the shared preferences
         */

        refreshCurrentView();
        updateNavigationCues(this.mCurrentView);
    }


    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.listeners.FormLoaderListener#loadingError(java.lang.String)
     * 
     * called by the FormLoaderTask if something goes wrong.
     */
    @Override
    public void loadingError(String errorMsg) {
        dismissDialog(PROGRESS_DIALOG);
        if (errorMsg != null) {
            createErrorDialog(errorMsg, EXIT);
        } else {
            createErrorDialog(StringUtils.getStringRobust(this, R.string.parse_error), EXIT);
        }
    }


    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.listeners.FormSavedListener#savingComplete(int)
     * 
     * Called by SavetoDiskTask if everything saves correctly.
     */
    @Override
    public void savingComplete(int saveStatus) {
        dismissDialog(SAVING_DIALOG);
        switch (saveStatus) {
            case SaveToDiskTask.SAVED:
                Toast.makeText(this, StringUtils.getStringRobust(this, R.string.data_saved_ok), Toast.LENGTH_SHORT).show();
                        hasSaved = true;
                break;
            case SaveToDiskTask.SAVED_AND_EXIT:
                Toast.makeText(this, StringUtils.getStringRobust(this, R.string.data_saved_ok), Toast.LENGTH_SHORT).show();
                hasSaved = true;
                finishReturnInstance();
                break;
            case SaveToDiskTask.SAVE_ERROR:
                Toast.makeText(this, StringUtils.getStringRobust(this, R.string.data_saved_error), Toast.LENGTH_LONG)
                        .show();
                break;
            case FormEntryController.ANSWER_CONSTRAINT_VIOLATED:
            case FormEntryController.ANSWER_REQUIRED_BUT_EMPTY:
                refreshCurrentView();
                // an answer constraint was violated, so do a 'swipe' to the next
                // question to display the proper toast(s)
                next();
                break;
        }
    }


    /**
     * Attempts to save an answer to the specified index.
     * 
     * @param answer
     * @param index
     * @param evaluateConstraints
     * @return status as determined in FormEntryController
     */
    public int saveAnswer(IAnswerData answer, FormIndex index, boolean evaluateConstraints) {
        try {
            if (evaluateConstraints) {
                return mFormController.answerQuestion(index, answer);
            } else {
                mFormController.saveAnswer(index, answer);
                return FormEntryController.ANSWER_OK;
            }
        } catch(XPathException e) {
            //this is where runtime exceptions get triggered after the form has loaded
            createErrorDialog("There is a bug in one of your form's XPath Expressions \n" + e.getMessage(), EXIT);
            //We're exiting anyway
            return FormEntryController.ANSWER_OK;
        }
    }


    /**
     * Checks the database to determine if the current instance being edited has already been
     * 'marked completed'. A form can be 'unmarked' complete and then resaved.
     * 
     * @return true if form has been marked completed, false otherwise.
     */
    private boolean isInstanceComplete(boolean end) {
        // default to false if we're mid form
        boolean complete = false;

        // if we're at the end of the form, then check the preferences
        if (end) {
            // First get the value from the preferences
            SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(this);
            complete =
                sharedPreferences.getBoolean(PreferencesActivity.KEY_COMPLETED_DEFAULT, true);
        }

        // Then see if we've already marked this form as complete before
        String selection = InstanceColumns.INSTANCE_FILE_PATH + "=?";
        String[] selectionArgs = {
            mInstancePath
        };
        Cursor c =
            getContentResolver().query(instanceProviderContentURI, null, selection, selectionArgs,
                null);
        startManagingCursor(c);
        if (c != null && c.getCount() > 0) {
            c.moveToFirst();
            String status = c.getString(c.getColumnIndex(InstanceColumns.STATUS));
            if (InstanceProviderAPI.STATUS_COMPLETE.compareTo(status) == 0) {
                complete = true;
            }
        }
        return complete;
    }


    public void next() {
        if (!mBeenSwiped) {
            mBeenSwiped = true;
            showNextView();
        }
    }

    
    private void finishReturnInstance() {
        finishReturnInstance(true);
    }

    /**
     * Returns the instance that was just filled out to the calling activity, if requested.
     */
    private void finishReturnInstance(boolean reportSaved) {
        String action = getIntent().getAction();
        if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_EDIT.equals(action)) {
            // caller is waiting on a picked form
            String selection = InstanceColumns.INSTANCE_FILE_PATH + "=?";
            String[] selectionArgs = {
                mInstancePath
            };
            Cursor c =
                managedQuery(instanceProviderContentURI, null, selection, selectionArgs, null);
            if (c.getCount() > 0) {
                // should only be one...
                c.moveToFirst();
                String id = c.getString(c.getColumnIndex(InstanceColumns._ID));
                Uri instance = Uri.withAppendedPath(instanceProviderContentURI, id);
                if(reportSaved || hasSaved) {
                    setResult(RESULT_OK, new Intent().setData(instance));
                } else {
                    setResult(RESULT_CANCELED, new Intent().setData(instance));
                }
            }
        }
        this.dismissDialogs();
        finish();
    }


    /*
     * (non-Javadoc)
     * @see android.view.GestureDetector.OnGestureListener#onDown(android.view.MotionEvent)
     */
    @Override
    public boolean onDown(MotionEvent e) {
        return false;
    }

    /*
     * Looks for user swipes. If the user has swiped, move to the appropriate screen.
     * (non-Javadoc)
     * @see android.view.GestureDetector.OnGestureListener#onFling(android.view.MotionEvent, android.view.MotionEvent, float, float)
     */
    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (CommCareActivity.isHorizontalSwipe(this, e1, e2)) {
            mBeenSwiped = true;
            if (velocityX > 0) {
                showPreviousView();
            } else {
                showNextView();
            }
            return true;
        }

        return false;
    }


    /*
     * (non-Javadoc)
     * @see android.view.GestureDetector.OnGestureListener#onLongPress(android.view.MotionEvent)
     */
    @Override
    public void onLongPress(MotionEvent e) {
    }


    /*
     * (non-Javadoc)
     * @see android.view.GestureDetector.OnGestureListener#onScroll(android.view.MotionEvent, android.view.MotionEvent, float, float)
     */
    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        // The onFling() captures the 'up' event so our view thinks it gets long pressed.
        // We don't wnat that, so cancel it.
        mCurrentView.cancelLongPress();
        return false;
    }


    /*
     * (non-Javadoc)
     * @see android.view.GestureDetector.OnGestureListener#onShowPress(android.view.MotionEvent)
     */
    @Override
    public void onShowPress(MotionEvent e) {
    }


    /*
     * (non-Javadoc)
     * @see android.view.GestureDetector.OnGestureListener#onSingleTapUp(android.view.MotionEvent)
     */
    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }


    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.listeners.AdvanceToNextListener#advance()
     */
    @Override
    public void advance() {
        next();
    }


    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.listeners.WidgetChangedListener#widgetEntryChanged()
     */
    @Override
    public void widgetEntryChanged() {
        updateFormRelevencies();
        updateNavigationCues(this.mCurrentView);
        
    }
}
