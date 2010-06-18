package org.commcare.android.activities;

import java.io.File;
import java.io.IOException;
import java.util.Hashtable;

import org.commcare.android.R;
import org.commcare.android.database.SqlIndexedStorageUtility;
import org.commcare.android.database.TableBuilder;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.models.Case;
import org.commcare.android.models.Referral;
import org.commcare.android.models.User;
import org.commcare.android.references.JavaFileRoot;
import org.commcare.android.references.JavaHttpRoot;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.AndroidResourceInstallerFactory;
import org.commcare.android.util.CommCarePlatformProvider;
import org.commcare.android.util.DummyIndexedStorageUtility;
import org.commcare.android.util.ODKPropertyManager;
import org.commcare.data.xml.DataModelPullParser;
import org.commcare.data.xml.TransactionParser;
import org.commcare.data.xml.TransactionParserFactory;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.Profile;
import org.commcare.suite.model.Suite;
import org.commcare.xml.CaseXmlParser;
import org.commcare.xml.UserXmlParser;
import org.commcare.xml.util.InvalidStructureException;
import org.commcare.xml.util.UnfullfilledRequirementsException;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceFactory;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.reference.RootTranslator;
import org.javarosa.core.services.PropertyManager;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.storage.IStorageFactory;
import org.javarosa.core.services.storage.IStorageUtility;
import org.javarosa.core.services.storage.StorageManager;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class CommCareHomeActivity extends Activity {
	public static final int LOGIN_USER = 0;
	public static final int GET_COMMAND = 1;
	public static final int GET_CASE = 2;
	public static final int MODEL_RESULT = 2;
	
	private static ReferenceFactory http;
	private static ReferenceFactory file;
	
	View homeScreen;
	
	public AndroidCommCarePlatform platform;
	
	Button startButton;
	Button sendUnsentButton;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        initData();
        
        //viewData = (TextView)findViewById(R.id.text_view);
        
        if(savedInstanceState == null || !savedInstanceState.containsKey(GlobalConstants.USER_KEY) || savedInstanceState.getString(GlobalConstants.USER_KEY).equals("")) {
        	Intent i = new Intent(getApplicationContext(), LoginActivity.class);
        	startActivityForResult(i,LOGIN_USER);
        }
        
        // enter data button. expects a result.
        startButton = (Button) findViewById(R.id.start);
        startButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	
                Intent i = new Intent(getApplicationContext(), SuiteMenuList.class);
                Bundle b = new Bundle();
                
                i.putExtra(GlobalConstants.MENU_ID, "client-visit");
                CommCarePlatformProvider.pack(b, platform);
                i.putExtra(GlobalConstants.COMMCARE_PLATFORM, b);
                startActivityForResult(i, GET_COMMAND);

            }
        });
        
        sendUnsentButton = (Button) findViewById(R.id.sendunsent);
        sendUnsentButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
    			DataModelPullParser parser;
				try {
					parser = new DataModelPullParser(ReferenceManager._().DeriveReference("jr://file/commcare/data.xml").getStream(), new TransactionParserFactory() {
						
						public TransactionParser getParser(String name, String namespace, KXmlParser parser) {
							if(name.toLowerCase().equals("case")) {
								return new CaseXmlParser(parser, CommCareHomeActivity.this);
							} else if(name.toLowerCase().equals("registration")) {
								return new UserXmlParser(parser, CommCareHomeActivity.this);
							}
							return null;
						}
						
					});
	    			parser.parse();
				} catch (InvalidStructureException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InvalidReferenceException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (XmlPullParserException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (UnfullfilledRequirementsException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
            }
        });
        

    }
    
    private void initData() {
    	int[] version = getVersion();
        platform = new AndroidCommCarePlatform(version[0], version[1], this);
        
        createPaths();
        setRoots();
        
        
        initDb();
		
		//All of the below is on account of the fact that the installers 
		//aren't going through a factory method to handle them differently
		//per device.
		StorageManager.setStorageFactory(new IStorageFactory() {

			public IStorageUtility newStorage(String name, Class type) {
				return new DummyIndexedStorageUtility();
			}
			
		});
		
		PropertyManager.setPropertyManager(new ODKPropertyManager());
		
		ResourceTable global = platform.getGlobalResourceTable();
		
		try {
			String URL = "http://dl.dropbox.com/u/312782/";
			platform.init(URL + "profile.xml", global, false);
		} catch (UnfullfilledRequirementsException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		platform.initialize(global);
		Localization.setLocale(Localization.getGlobalLocalizerAdvanced().getAvailableLocales()[0]);
    }
    
    private void createPaths() {
    	String[] paths = new String[] {GlobalConstants.FILE_CC_ROOT, GlobalConstants.FILE_CC_INSTALL, GlobalConstants.FILE_CC_UPGRADE, GlobalConstants.FILE_CC_CACHE};
    	for(String path : paths) {
    		File f = new File(path);
    		if(!f.exists()) {
    			f.mkdir();
    		}
    	}
    }
    
	private void setRoots() {
		if(http == null) {
			http = new JavaHttpRoot();
		}
		if(file == null) {
			file = new JavaFileRoot(GlobalConstants.FILE_REF_ROOT);
		}
		
		ReferenceManager._().addReferenceFactory(http);
		ReferenceManager._().addReferenceFactory(file);
		ReferenceManager._().addRootTranslator(new RootTranslator("jr://resource/",GlobalConstants.RESOURCE_PATH));
	}

	private int versionCode() {
		try {
			PackageManager pm = getPackageManager();
			PackageInfo pi = pm.getPackageInfo("org.commcare.android", 0);
			return pi.versionCode;
		} catch(NameNotFoundException e) {
			throw new RuntimeException("Android package name not available.");
		}
	}
    
	private int[] getVersion() {
	    return versionNumbers(versionCode());
	}
	
	private int[] versionNumbers(int versionCode) {
		if(versionCode == 1) {
			return new int[] {1, 0};
		} else {
			return new int[] {-1, -1};
		}
	}
	
	private void initDb() {
		SQLiteDatabase database;
		try {
			database = SQLiteDatabase.openDatabase(GlobalConstants.DB_LOCATION, null, SQLiteDatabase.OPEN_READWRITE);
		} catch(SQLiteException e) {
			//No database
			database = createDataBase();
		}
		database.close();
	}
	
	private SQLiteDatabase createDataBase() {
		SQLiteDatabase database = SQLiteDatabase.openDatabase(GlobalConstants.DB_LOCATION, null, SQLiteDatabase.CREATE_IF_NECESSARY);
		try{
			
			database.beginTransaction();
			database.setVersion(versionCode());
			
			TableBuilder builder = new TableBuilder(Case.STORAGE_KEY);
			builder.addData(new Case());
			database.execSQL(builder.getTableCreateString());
			
			builder = new TableBuilder(Referral.STORAGE_KEY);
			builder.addData(new Referral());
			database.execSQL(builder.getTableCreateString());
			
			builder = new TableBuilder(User.STORAGE_KEY);
			builder.addData(new User());
			database.execSQL(builder.getTableCreateString());
			
			builder = new TableBuilder("GLOBAL_RESOURCE_TABLE");
			builder.addData(new Resource());
			database.execSQL(builder.getTableCreateString());
			
			database.setTransactionSuccessful();
		} finally {
			database.endTransaction();
		}
		return database;
	}
    
    /*
     * (non-Javadoc)
     * @see android.app.Activity#onActivityResult(int, int, android.content.Intent)
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
    	switch(requestCode) {
    	case LOGIN_USER:
    		if(resultCode == RESULT_CANCELED) {
    			//quit somehow.
    			this.finish();
    		} else if(resultCode == RESULT_OK) {
    			//Logged in! Let's get rolling here:
    			String name = intent.getStringExtra(GlobalConstants.USER_KEY);
    			refreshView();
    			return;
    		}
    		break;
    		
    	case GET_COMMAND:
    		if(resultCode == RESULT_CANCELED) {
    			refreshView();
    			return;
    		} else if(resultCode == RESULT_OK) {
    			//Logged in! Let's get rolling here:
    			String command = intent.getStringExtra(GlobalConstants.COMMAND_ID);
    			Entry e = platform.getMenuMap().get(command);
    			Hashtable<String,String> refs = e.getReferences();
    			if(refs.containsKey("case")) {
                    Intent i = new Intent(getApplicationContext(), EntitySelectActivity.class);
                    Bundle b = new Bundle();
                    
                    CommCarePlatformProvider.pack(b, platform);
                    i.putExtra(GlobalConstants.COMMCARE_PLATFORM, b);
                    i.putExtra(GlobalConstants.COMMAND_ID, command);
                    startActivityForResult(i, GET_CASE);
    			}
    			
    			break;
    		}
        case GET_CASE:
        	if(resultCode == RESULT_CANCELED) {
    			refreshView();
    			return;
    		} else if(resultCode == RESULT_OK) {
    			String command = intent.getStringExtra(GlobalConstants.COMMAND_ID);
        	
    			Entry e = platform.getMenuMap().get(command);
    			String path = platform.getFormPath(e.getXFormNamespace());
    			Log.i("PATH", path);
        	
    			Intent i = new Intent("org.odk.collect.android.action.FormEntry");
    			i.putExtra("formpath", path);
            
    			startActivityForResult(i, MODEL_RESULT);
    		}
    	} 
    	
    	super.onActivityResult(requestCode, resultCode, intent);
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
    }
}