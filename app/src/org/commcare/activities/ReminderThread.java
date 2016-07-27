/**
 * 
 */
package org.commcare.activities;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;

import org.commcare.CommCareApplication;
import org.commcare.suite.model.Alert;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.DataInstance;
import org.javarosa.core.model.instance.ExternalDataInstance;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.model.xform.XPathReference;
import org.javarosa.xpath.XPathParseTool;
import org.javarosa.xpath.expr.XPathExpression;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.util.Log;

/**
 * @author ctsims, Saumya Jain
 *
 */
public class ReminderThread {
    Runnable runnable;
    Thread myThread;
    
    HashMap<String, String> keyToValue;
    HashMap<String, String> valueToKey;

    private String xPathConditionString;
    private String xPathReference;
    private String db;
    private String dbPath;

    Context mContext;
    
    Cursor c;
    
    private boolean mContinuePolling = true;
    private int sleepTime = 10;
    
    public ReminderThread(Context context) {
        
        this.mContext = context;
        
        runnable = new Runnable() {
            @Override
            public void run() {
                try{
                    evalulateAlertExpressions();
                }catch(Exception e){
                    if(!Thread.interrupted()){
                        showAlertDialog("Alerts have stopped because of an error!");
                    }
                    mContinuePolling = false;
                }
            }
            
            
        };
        
        myThread = new Thread(runnable);
    }

    private void evalulateAlertExpressions() {
        //Prepare xpath expression
        XPathExpression condition;
        try{
            condition = XPathParseTool.parseXPath(xPathConditionString);
        }catch (Exception e){
            showAlertDialog("Could not parse xPath condition! Alerts have stopped!");
            return;
        }

        //Prepare evaluation context
        Hashtable<String, DataInstance> instances = new Hashtable<>();

        instances.put(db, new ExternalDataInstance(dbPath,db));
        EvaluationContext ec = CommCareApplication._().getCurrentSessionWrapper().getEvaluationContext(instances);

        //Prepare TreeReferences

        TreeReference caseType = XPathReference.getPathExpr(xPathReference).getReference(); //This is more of an abstract reference to a bunch of stuff in the tree

        while(mContinuePolling) {
            long current = System.currentTimeMillis();
            List<TreeReference> refs = ec.expandReference(caseType); //When we expand the reference within the context ec we get the x number of references that actually "exist"

            try{
                for(TreeReference ref : refs) {
                    EvaluationContext internal = new EvaluationContext(ec, ref);
                    Boolean result = (Boolean) condition.eval(internal);
                    Log.d("Result", result.toString());
                    if(result) {
                        playNoise();
                    }
                }
            }catch(ClassCastException e){
                showAlertDialog("Alerts have been stopped because xPath expression was not boolean!");
                mContinuePolling = false;
                break;
            }catch(Exception e){
                showAlertDialog("Alerts have stopped due to an xPath error!");
            }

            long delay = (System.currentTimeMillis() - current);

            try {
                Thread.sleep(sleepTime * 1000 - delay);
                System.out.println("Sleeping for" + String.valueOf(sleepTime*1000-delay));
                sleepTime *= 2;
            } catch (InterruptedException e) {
                e.printStackTrace();
                mContinuePolling = false;
            }
        }
    }

    public void playNoise() {
        try {
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(mContext, notification);
            r.play();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
    
    public void startPolling(Vector<Alert> alerts) {

        for(Alert a: alerts){
            xPathConditionString = a.getxPathCondition();
            db = a.getDb();
            dbPath = a.getDbPath();
            xPathReference = a.getXPathRef();
        }

        synchronized(myThread) {
            if(!myThread.isAlive()) {
                myThread.start();
            }
        }
    }
    
    public void stopService() {
        synchronized(myThread) {
            mContinuePolling = false;
        }
    }

    private void showAlertDialog(String alertText){
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setMessage(alertText);
        builder.setPositiveButton(
                "Dismiss",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                }
        );

        AlertDialog warning = builder.create();
        warning.show();
    }
}
