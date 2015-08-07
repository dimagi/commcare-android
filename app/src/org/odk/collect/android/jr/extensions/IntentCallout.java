package org.odk.collect.android.jr.extensions;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import org.javarosa.core.model.Constants;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.condition.Recalculate;
import org.javarosa.core.model.data.AnswerDataFactory;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.core.model.instance.AbstractTreeElement;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.ExtWrapList;
import org.javarosa.core.util.externalizable.ExtWrapMap;
import org.javarosa.core.util.externalizable.ExtWrapNullable;
import org.javarosa.core.util.externalizable.Externalizable;
import org.javarosa.core.util.externalizable.PrototypeFactory;
import org.javarosa.xpath.expr.XPathExpression;
import org.javarosa.xpath.expr.XPathFuncExpr;
import org.odk.collect.android.utilities.FileUtils;
import org.odk.collect.android.views.ODKView;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;

/**
 * @author ctsims
 *
 */
public class IntentCallout implements Externalizable {
    public static final String TAG = IntentCallout.class.getSimpleName();
    private String className;
    private Hashtable<String, XPathExpression> refs;
    
    private Hashtable<String, ArrayList<TreeReference>> responses;
    
    private FormDef form;
    
    private String type;
    private String component;
    private String data;
    private String buttonLabel;
    private String appearance;
    private boolean isCancelled;

    
    // Generic Extra from intent callout extensions
    public static final String INTENT_RESULT_VALUE = "odk_intent_data";
    
    // Bundle of extra values
    public static final String INTENT_RESULT_BUNDLE = "odk_intent_bundle";
    
    public IntentCallout(String className, Hashtable<String, XPathExpression> refs,
                         Hashtable<String, ArrayList<TreeReference>> responses, String type,
                         String component, String data, String buttonLabel, String appearance) {

        this.className = className;
        this.refs = refs;
        this.responses = responses;
        this.type = type;
        this.component = component;
        this.data = data;
        this.buttonLabel = buttonLabel;
        this.appearance = appearance;

    }
    
    protected void attachToForm(FormDef form) {
        this.form = form;
    }
    
    public Intent generate(EvaluationContext ec) {
        Intent i = new Intent();
        if(className != null){
            i.setAction(className);
        } if(type != null){
            i.setType(type);
        } if(data != null){
            i.setData(Uri.parse(data));
        } if(component != null){
            i.setComponent(new ComponentName(component, className));
        }
        if(refs != null) {
            for (Enumeration<String> en = refs.keys(); en.hasMoreElements(); ) {
                String key = en.nextElement();

                String extraVal = XPathFuncExpr.toString(refs.get(key).eval(ec));
                if(extraVal != null && extraVal != "") {
                    i.putExtra(key, extraVal);
                }
            }
        }
        return i;
    }

    public boolean processResponse(Intent intent, ODKView currentView, FormInstance instance, File destination) {
        
        if(intent == null){
            return false;
        }
        
        String result = intent.getStringExtra(INTENT_RESULT_VALUE);
        ((ODKView) currentView).setBinaryData(result);
        
        //see if we have a return bundle
        Bundle response = intent.getBundleExtra(INTENT_RESULT_BUNDLE);
        
        //Load all of the data from the incoming bundle
        if(responses != null) {
            for (String key : responses.keySet()) {
                //See if the value exists at all, if not, skip it
                if (!response.containsKey(key)) {
                    continue;
                }

                //Get our response value
                String responseValue = response.getString(key);
                if (key == null) {
                    key = "";
                }

                for (TreeReference ref : responses.get(key)) {
                    processResponseItem(ref, responseValue, destination);
                }
            }
        }
        return (result != null);
    }

    private void processResponseItem(TreeReference ref, String responseValue,
                                     File destinationFile) {
        EvaluationContext context = new EvaluationContext(form.getEvaluationContext(), ref);

        AbstractTreeElement node = context.resolveReference(ref);

        if (node == null) {
            return;
        }
        int dataType = node.getDataType();

        //TODO: Handle file system errors in a way that is more visible to the user

        //See if this is binary data and we'll have to do something complex...
        if (dataType == Constants.DATATYPE_BINARY) {
            //We need to copy the binary data at this address into the appropriate location
            if (responseValue == null || responseValue.equals("")) {
                //If the response was blank, wipe out any data that was present before
                form.setValue(null, ref);
                return;
            }

            //Otherwise, grab that file
            File src = new File(responseValue);
            if (!src.exists()) {
                //TODO: How hard should we be failing here?
                Log.w(TAG, "ODK received a link to a file at " + src.toString() + " to be included in the form, but it was not present on the phone!");
                //Wipe out any reference that exists
                form.setValue(null, ref);
                return;
            }

            File newFile = new File(destinationFile, src.getName());

            //Looks like our source file exists, so let's go grab it
            FileUtils.copyFile(src, newFile);

            //That code throws no errors, so we have to manually check whether the copy worked.
            if (newFile.exists() && newFile.length() == src.length()) {
                form.setValue(new StringData(newFile.toString()), ref);
                return;
            } else {
                Log.e(TAG, "ODK Failed to property write a file to " + newFile.toString());
                form.setValue(null, ref);
                return;
            }
        }

        //otherwise, just load it up
        IAnswerData val = Recalculate.wrapData(responseValue, dataType);

        form.setValue(val == null ? null : AnswerDataFactory.templateByDataType(dataType).cast(val.uncast()), ref);
    }

    @Override
    public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
        className = ExtUtil.readString(in);
        refs = (Hashtable<String, XPathExpression>)ExtUtil.read(in, new ExtWrapMap(String.class, XPathExpression.class), pf);
        responses = (Hashtable<String, ArrayList<TreeReference>>)
                ExtUtil.read(in, new ExtWrapMap(String.class, new ExtWrapList(TreeReference.class)), pf);
        appearance = (String)ExtUtil.read(in, new ExtWrapNullable(String.class));
        component = (String)ExtUtil.read(in, new ExtWrapNullable(String.class));
        buttonLabel = (String)ExtUtil.read(in, new ExtWrapNullable(String.class));
    }

    @Override
    public void writeExternal(DataOutputStream out) throws IOException {
        ExtUtil.writeString(out, className);
        ExtUtil.write(out, new ExtWrapMap(refs));
        ExtUtil.write(out, new ExtWrapMap(responses));
        ExtUtil.write(out, new ExtWrapNullable(appearance));
        ExtUtil.write(out, new ExtWrapNullable(component));
        ExtUtil.write(out, new ExtWrapNullable(buttonLabel));
    }
    
    public String getButtonLabel(){
        return buttonLabel;
    }

    public String getAppearance(){return appearance;}

    public void setCancelled(boolean cancelled){
        this.isCancelled = cancelled;
    }
    
    public boolean getCancelled(){
        return isCancelled;
    }
    
}
