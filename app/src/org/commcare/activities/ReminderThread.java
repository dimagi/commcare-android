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

import android.content.Context;
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

    private String caseTypeString = "";
    private String xPathConditionString;

    Context mContext;
    
    Cursor c;
    
    private boolean mContinuePolling = true;
    private int sleepTime = 10;
    
    public ReminderThread(Context context) {
        valueToKey = new HashMap<>();
        keyToValue = new HashMap<>();
        
        this.mContext = context;
        
        runnable = new Runnable() {
            @Override
            public void run() {

                //Prepare xpath expression
                XPathExpression condition;
                try{
                    condition = XPathParseTool.parseXPath(xPathConditionString);
                }catch (Exception e){
                    e.printStackTrace();
                    return;
                }

                //Prepare evaluation context
                Hashtable<String, DataInstance> instances = new Hashtable<>();
                instances.put("casedb", new ExternalDataInstance("jr://instance/casedb","casedb"));
                EvaluationContext ec = CommCareApplication._().getCurrentSessionWrapper().getEvaluationContext(instances);

                //Prepare TreeReferences
                String caseXPathRef = "instance('casedb')/casedb/case[@case_type='" + caseTypeString + "']";
                TreeReference caseType = XPathReference.getPathExpr(caseXPathRef).getReference(); //This is more of an abstract reference to a bunch of stuff in the tree
                List<TreeReference> refs = ec.expandReference(caseType); //When we expand the reference within the context ec we get the x number of references that actually "exist"

                while(mContinuePolling) {
                    long current = System.currentTimeMillis();

                    //Evaluate condition
                    for(TreeReference ref : refs) {
                        EvaluationContext internal = new EvaluationContext(ec, ref);
                        Log.d("Result", condition.eval(internal).toString());

                        if((Boolean) condition.eval(internal)){
                            playNoise();
                        }
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
            
            
        };
        
        myThread = new Thread(runnable);
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
            caseTypeString = a.getCaseType();
            xPathConditionString = a.getCondition();
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
}
