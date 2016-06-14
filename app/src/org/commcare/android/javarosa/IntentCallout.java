package org.commcare.android.javarosa;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.BadParcelableException;
import android.os.Bundle;
import android.util.Log;

import org.commcare.provider.SimprintsCalloutProcessing;
import org.commcare.logging.AndroidLogger;
import org.commcare.utils.FileUtil;
import org.javarosa.core.model.Constants;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.condition.Recalculate;
import org.javarosa.core.model.data.AnswerDataFactory;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.core.model.instance.AbstractTreeElement;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.Logger;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.ExtWrapList;
import org.javarosa.core.util.externalizable.ExtWrapMap;
import org.javarosa.core.util.externalizable.ExtWrapMapPoly;
import org.javarosa.core.util.externalizable.ExtWrapNullable;
import org.javarosa.core.util.externalizable.Externalizable;
import org.javarosa.core.util.externalizable.PrototypeFactory;
import org.javarosa.xpath.expr.XPathExpression;
import org.javarosa.xpath.expr.XPathFuncExpr;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

/**
 * @author ctsims
 */
public class IntentCallout implements Externalizable {
    private static final String TAG = IntentCallout.class.getSimpleName();
    private String className;
    private Hashtable<String, XPathExpression> refs;

    private Hashtable<String, Vector<TreeReference>> responseToRefMap;

    private FormDef formDef;

    private String type;
    private String component;
    private String data;
    private String buttonLabel;
    private String updateButtonLabel;
    private String appearance;
    private boolean isCancelled;

    // Generic Extra from intent callout extensions
    public static final String INTENT_RESULT_VALUE = "odk_intent_data";

    // Bundle of extra values
    public static final String INTENT_RESULT_BUNDLE = "odk_intent_bundle";

    public IntentCallout() {
        // for serialization
    }

    /**
     * @param buttonLabel Intent callout button text for initially calling the intent.
     * @param updateButtonLabel Intent callout button text for re-calling the intent to update the answer
     * @param appearance if 'quick' then intent is automatically called when question is shown, and advanced when intent answer is received
     */
    public IntentCallout(String className, Hashtable<String, XPathExpression> refs,
                         Hashtable<String, Vector<TreeReference>> responseToRefMap, String type,
                         String component, String data, String buttonLabel,
                         String updateButtonLabel, String appearance) {
        this.className = className;
        this.refs = refs;
        this.responseToRefMap = responseToRefMap;
        this.type = type;
        this.component = component;
        this.data = data;
        this.buttonLabel = buttonLabel;
        this.updateButtonLabel = updateButtonLabel;
        this.appearance = appearance;
    }

    protected void attachToForm(FormDef form) {
        this.formDef = form;
    }

    public Intent generate(EvaluationContext ec) {

        Intent i = new Intent();
        if (className != null) {
            i.setAction(className);
        }
        if (type != null) {
            i.setType(type);
        }
        if (data != null) {
            i.setData(Uri.parse(data));
        }
        if (component != null) {
            i.setComponent(new ComponentName(component, className));
        }
        if (refs != null) {
            for (Enumeration<String> en = refs.keys(); en.hasMoreElements(); ) {
                String key = en.nextElement();

                String extraVal = XPathFuncExpr.toString(refs.get(key).eval(ec));

                Log.d("In the loop", "Key: " + key + " Value: " + extraVal);
                if (extraVal != null && !"".equals(extraVal)) {
                    i.putExtra(key, extraVal);
                }
            }
        }

        Logger.log(AndroidLogger.TYPE_FORM_ENTRY, "Generated Intent: " + i.toString());
        return i;
    }

    public boolean processResponse(Intent intent, TreeReference intentQuestionRef, File destination) {
        if (intentInvalid(intent)) {
            return false;
        } else if (SimprintsCalloutProcessing.isRegistrationResponse(intent)) {
            return SimprintsCalloutProcessing.processRegistrationResponse(formDef, intent, intentQuestionRef, responseToRefMap);
        } else {
            return processOdkResponse(intent, intentQuestionRef, destination);
        }
    }

    private static boolean intentInvalid(Intent intent) {
        if (intent == null) {
            return true;
        }
        try {
            // force unparcelling to check if we are missing classes to
            // correctly process callout response
            intent.hasExtra(INTENT_RESULT_VALUE);
        } catch (BadParcelableException e) {
            Log.w(TAG, "unable to unparcel intent: " + e.getMessage());
            return true;
        }

        return false;
    }

    private boolean processOdkResponse(Intent intent, TreeReference intentQuestionRef, File destination) {
        String result = intent.getStringExtra(INTENT_RESULT_VALUE);
        setNodeValue(formDef, intentQuestionRef, result);

        //see if we have a return bundle
        Bundle response = intent.getBundleExtra(INTENT_RESULT_BUNDLE);

        //Load all of the data from the incoming bundle
        if (responseToRefMap != null && response != null) {
            for (String key : responseToRefMap.keySet()) {
                //See if the value exists at all, if not, skip it
                if (!response.containsKey(key)) {
                    continue;
                }

                //Get our response value
                String responseValue = response.getString(key);
                if (key == null) {
                    key = "";
                }

                for (TreeReference ref : responseToRefMap.get(key)) {
                    processResponseItem(ref, responseValue, intentQuestionRef, destination);
                }
            }
        }
        return (result != null);
    }

    public static void setNodeValue(FormDef formDef, TreeReference reference, String stringValue) {
        // todo: this code is very similar to SetValueAction.processAction, could be unified?
        if (stringValue != null) {
            EvaluationContext evaluationContext = new EvaluationContext(formDef.getEvaluationContext(), reference);
            AbstractTreeElement node = evaluationContext.resolveReference(reference);
            int dataType = node.getDataType();

            setValueInFormDef(formDef, reference, stringValue, dataType);
        } else {
            formDef.setValue(null, reference);
        }
    }

    public static void setValueInFormDef(FormDef formDef, TreeReference ref, String responseValue, int dataType) {
        IAnswerData val = Recalculate.wrapData(responseValue, dataType);
        if (val != null) {
            val = AnswerDataFactory.templateByDataType(dataType).cast(val.uncast());
        }
        formDef.setValue(val, ref);
    }

    private void processResponseItem(TreeReference ref, String responseValue,
                                     TreeReference contextRef, File destinationFile) {
        TreeReference fullRef = ref.contextualize(contextRef);
        EvaluationContext context = new EvaluationContext(formDef.getEvaluationContext(), contextRef);
        AbstractTreeElement node = context.resolveReference(fullRef);

        if (node == null) {
            Log.e(TAG, "Unable to resolve ref " + ref);
            return;
        }
        int dataType = node.getDataType();

        //TODO: Handle file system errors in a way that is more visible to the user
        if (dataType == Constants.DATATYPE_BINARY) {
            storePointerToFileResponse(fullRef, responseValue, destinationFile);
        } else {
            setValueInFormDef(formDef, fullRef, responseValue, dataType);
        }
    }

    private void storePointerToFileResponse(TreeReference ref, String responseValue, File destinationFile) {
        //We need to copy the binary data at this address into the appropriate location
        if (responseValue == null || responseValue.equals("")) {
            //If the response was blank, wipe out any data that was present before
            formDef.setValue(null, ref);
            return;
        }

        //Otherwise, grab that file
        File src = new File(responseValue);
        if (!src.exists()) {
            //TODO: How hard should we be failing here?
            Log.w(TAG, "CommCare received a link to a file at " + src.toString() + " to be included in the form, but it was not present on the phone!");
            //Wipe out any reference that exists
            formDef.setValue(null, ref);
        } else {

            File newFile = new File(destinationFile, src.getName());

            //Looks like our source file exists, so let's go grab it
            try {
                FileUtil.copyFile(src, newFile);
            } catch (IOException e) {
                Log.e(TAG, "IOExeception copying Intent binary.");
                e.printStackTrace();
            }

            //That code throws no errors, so we have to manually check whether the copy worked.
            if (newFile.exists() && newFile.length() == src.length()) {
                formDef.setValue(new StringData(newFile.toString()), ref);
            } else {
                Log.e(TAG, "CommCare failed to property write a file to " + newFile.toString());
                formDef.setValue(null, ref);
            }
        }
    }

    @Override
    public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
        className = ExtUtil.readString(in);
        refs = (Hashtable<String, XPathExpression>)ExtUtil.read(in, new ExtWrapMapPoly(String.class, true), pf);
        responseToRefMap = (Hashtable<String, Vector<TreeReference>>)ExtUtil.read(in, new ExtWrapMap(String.class, new ExtWrapList(TreeReference.class)), pf);
        appearance = (String)ExtUtil.read(in, new ExtWrapNullable(String.class));
        component = (String)ExtUtil.read(in, new ExtWrapNullable(String.class));
        buttonLabel = (String)ExtUtil.read(in, new ExtWrapNullable(String.class));
        updateButtonLabel = (String)ExtUtil.read(in, new ExtWrapNullable(String.class));
    }

    @Override
    public void writeExternal(DataOutputStream out) throws IOException {
        ExtUtil.writeString(out, className);
        ExtUtil.write(out, new ExtWrapMapPoly(refs));
        ExtUtil.write(out, new ExtWrapMap(responseToRefMap, new ExtWrapList()));
        ExtUtil.write(out, new ExtWrapNullable(appearance));
        ExtUtil.write(out, new ExtWrapNullable(component));
        ExtUtil.write(out, new ExtWrapNullable(buttonLabel));
        ExtUtil.write(out, new ExtWrapNullable(updateButtonLabel));
    }

    public String getButtonLabel() {
        return buttonLabel;
    }

    public String getUpdateButtonLabel() {
        return updateButtonLabel;
    }

    public String getAppearance() {
        return appearance;
    }

    public void setCancelled(boolean cancelled) {
        this.isCancelled = cancelled;
    }

    public boolean getCancelled() {
        return isCancelled;
    }
}
