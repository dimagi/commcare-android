package org.odk.collect.android.jr.extensions;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;

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
import org.javarosa.core.util.externalizable.ExtWrapMap;
import org.javarosa.core.util.externalizable.Externalizable;
import org.javarosa.core.util.externalizable.PrototypeFactory;
import org.odk.collect.android.utilities.FileUtils;
import org.odk.collect.android.views.ODKView;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

/**
 * @author ctsims
 *
 */
public class IntentCallout implements Externalizable {
    private String className;
    private Hashtable<String, TreeReference> refs;
    
    private Hashtable<String, TreeReference> responses;
    
    private FormDef form;
    
    private String type;
    private String component;
    private String data;
    private String buttonLabel;
    private boolean isQuick;

    
    // Generic Extra from intent callout extensions
    public static final String INTENT_RESULT_VALUE = "odk_intent_data";
    
    // Bundle of extra values
    public static final String INTENT_RESULT_BUNDLE = "odk_intent_bundle";

    
    public IntentCallout() {
        
    }
    
    public IntentCallout(String className, Hashtable<String, TreeReference> refs, Hashtable<String, TreeReference> responses, 
            String type, String component, String data, String buttonLabel, String appearance) {
        this.className = className;
        this.refs = refs;
        this.responses = responses;
        this.type = type;
        this.component = component;
        this.data = data;
        this.buttonLabel = buttonLabel;
        if(appearance != null && appearance.equals("quick")){
            System.out.println("0123 is quick");
            isQuick = true;
        } else{
            System.out.println("0123 is not quick");
            isQuick = false;
        }

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
        for(Enumeration<String> en = refs.keys() ; en.hasMoreElements() ;) {
            String key = en.nextElement();
            AbstractTreeElement e = ec.resolveReference(refs.get(key));
            if(e != null && e.getValue() != null) {
                i.putExtra(key, e.getValue().uncast().getString());
            }
        }
        return i;
    }
    
    public void processResponse(Intent intent, ODKView currentView, FormInstance instance, File destination) {
        String result = intent.getStringExtra(INTENT_RESULT_VALUE);
        ((ODKView) currentView).setBinaryData(result);
        
        //see if we have a return bundle
        Bundle response = intent.getBundleExtra(INTENT_RESULT_BUNDLE);
        
        //Load all of the data from the incoming bundle
        for(String key : responses.keySet()) {
            //See if the value exists at all, if not, skip it
            if(!response.containsKey(key)) { continue;}
            
            //Get our response value
            String responseValue = response.getString(key);
            if(key == null) { key = "";}
            
            //Figure out where it's going
            TreeReference ref = responses.get(key);
            
            EvaluationContext context = new EvaluationContext(form.getEvaluationContext(), ref);

            AbstractTreeElement node = context.resolveReference(ref);
            
            if(node == null) {
                //continue?
                
            }
            int dataType = node.getDataType();
            
            //TODO: Handle file system errors in a way that is more visible to the user
            
            //See if this is binary data and we'll have to do something complex...
            if(dataType == Constants.DATATYPE_BINARY) {
                //We need to copy the binary data at this address into the appropriate location
                if(responseValue == null || responseValue.equals("")) {
                    //If the response was blank, wipe out any data that was present before
                    form.setValue(null, ref);
                    continue;
                }
                
                //Otherwise, grab that file
                File src = new File(responseValue);
                if(!src.exists()) {
                    //TODO: How hard should we be failing here?
                    Log.w("FormEntryActivity-Callout", "ODK received a link to a file at " + src.toString() + " to be included in the form, but it was not present on the phone!");
                    //Wipe out any reference that exists
                    form.setValue(null, ref);
                    continue;
                }
                
                File newFile = new File(destination, src.getName());
                
                //Looks like our source file exists, so let's go grab it
                FileUtils.copyFile(src, newFile);
                
                //That code throws no errors, so we have to manually check whether the copy worked.
                if(newFile.exists() && newFile.length() == src.length()) {
                    form.setValue(new StringData(newFile.toString()), ref);
                    continue;
                } else {
                    Log.e("FormEntryActivity-Callout", "ODK Failed to property write a file to " + newFile.toString());
                    form.setValue(null, ref);
                    continue;    
                }
            }
            
            //otherwise, just load it up
            IAnswerData val = Recalculate.wrapData(responseValue, dataType);
            
            form.setValue(val == null ? null: AnswerDataFactory.templateByDataType(dataType).cast(val.uncast()), ref);
        }
    }

    /*
     * (non-Javadoc)
     * @see org.javarosa.core.util.externalizable.Externalizable#readExternal(java.io.DataInputStream, org.javarosa.core.util.externalizable.PrototypeFactory)
     */
    @Override
    public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
        className = ExtUtil.readString(in);
        refs = (Hashtable<String, TreeReference>)ExtUtil.read(in, new ExtWrapMap(String.class, TreeReference.class), pf);
        responses = (Hashtable<String, TreeReference>)ExtUtil.read(in, new ExtWrapMap(String.class, TreeReference.class), pf);
    }

    /*
     * (non-Javadoc)
     * @see org.javarosa.core.util.externalizable.Externalizable#writeExternal(java.io.DataOutputStream)
     */
    @Override
    public void writeExternal(DataOutputStream out) throws IOException {
        ExtUtil.writeString(out, className);
        ExtUtil.write(out, new ExtWrapMap(refs));
        ExtUtil.write(out, new ExtWrapMap(responses));
    }
    
    public String getButtonLabel(){
        return buttonLabel;
    }

    public boolean isQuickAppearance(){
        return isQuick;
    }

    
}
