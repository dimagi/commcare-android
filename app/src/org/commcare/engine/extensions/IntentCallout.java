package org.commcare.engine.extensions;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.BadParcelableException;
import android.os.Bundle;
import android.util.Log;

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

    private Hashtable<String, Vector<TreeReference>> responses;

    private FormDef form;

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
                         Hashtable<String, Vector<TreeReference>> responses, String type,
                         String component, String data, String buttonLabel,
                         String updateButtonLabel, String appearance) {
        this.className = className;
        this.refs = refs;
        this.responses = responses;
        this.type = type;
        this.component = component;
        this.data = data;
        this.buttonLabel = buttonLabel;
        this.updateButtonLabel = updateButtonLabel;
        this.appearance = appearance;
    }

    protected void attachToForm(FormDef form) {
        this.form = form;
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
                if (extraVal != null && !"".equals(extraVal)) {
                    i.putExtra(key, extraVal);
                }
            }
        }
        return i;
    }

    private void setNodeValue(TreeReference reference, String stringValue) {
        // todo: this code is very similar to SetValueAction.processAction, could be unified?
        if (stringValue != null) {
            EvaluationContext evaluationContext = new EvaluationContext(form.getEvaluationContext(), reference);
            AbstractTreeElement node = evaluationContext.resolveReference(reference);
            int dataType = node.getDataType();
            IAnswerData val = Recalculate.wrapData(stringValue, dataType);
            form.setValue(val == null ? null : AnswerDataFactory.templateByDataType(dataType).cast(val.uncast()), reference);
        } else {
            form.setValue(null, reference);
        }
    }

    public boolean processResponse(Intent intent, TreeReference context, File destination) {
        if (intentInvalid(intent)) {
            return false;
        }

        String result = intent.getStringExtra(INTENT_RESULT_VALUE);
        setNodeValue(context, result);

        //see if we have a return bundle
        Bundle response = intent.getBundleExtra(INTENT_RESULT_BUNDLE);

        //Load all of the data from the incoming bundle
        if (responses != null && response != null) {
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
                    processResponseItem(ref, responseValue, context, destination);
                }
            }
        }
        return (result != null);
    }

    private boolean intentInvalid(Intent intent) {
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

    private void processResponseItem(TreeReference ref, String responseValue,
                                     TreeReference contextRef, File destinationFile) {
        EvaluationContext context = new EvaluationContext(form.getEvaluationContext(), contextRef);
        TreeReference fullRef = ref.contextualize(contextRef);
        AbstractTreeElement node = context.resolveReference(fullRef);

        if (node == null) {
            Log.e(TAG, "Unable to resolve ref " + ref);
            return;
        }
        int dataType = node.getDataType();

        //TODO: Handle file system errors in a way that is more visible to the user

        //See if this is binary data and we'll have to do something complex...
        if (dataType == Constants.DATATYPE_BINARY) {
            //We need to copy the binary data at this address into the appropriate location
            if (responseValue == null || responseValue.equals("")) {
                //If the response was blank, wipe out any data that was present before
                form.setValue(null, fullRef);
                return;
            }

            //Otherwise, grab that file
            File src = new File(responseValue);
            if (!src.exists()) {
                //TODO: How hard should we be failing here?
                Log.w(TAG, "CommCare received a link to a file at " + src.toString() + " to be included in the form, but it was not present on the phone!");
                //Wipe out any reference that exists
                form.setValue(null, fullRef);
                return;
            }

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
                form.setValue(new StringData(newFile.toString()), fullRef);
                return;
            } else {
                Log.e(TAG, "CommCare failed to property write a file to " + newFile.toString());
                form.setValue(null, fullRef);
                return;
            }
        }

        //otherwise, just load it up
        IAnswerData val = Recalculate.wrapData(responseValue, dataType);
        form.setValue(val == null ? null : AnswerDataFactory.templateByDataType(dataType).cast(val.uncast()), fullRef);
    }

    @Override
    public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
        className = ExtUtil.readString(in);
        refs = (Hashtable<String, XPathExpression>)ExtUtil.read(in, new ExtWrapMapPoly(String.class, true), pf);
        responses = (Hashtable<String, Vector<TreeReference>>)ExtUtil.read(in, new ExtWrapMap(String.class, new ExtWrapList(TreeReference.class)), pf);
        appearance = (String)ExtUtil.read(in, new ExtWrapNullable(String.class));
        component = (String)ExtUtil.read(in, new ExtWrapNullable(String.class));
        buttonLabel = (String)ExtUtil.read(in, new ExtWrapNullable(String.class));
        updateButtonLabel = (String)ExtUtil.read(in, new ExtWrapNullable(String.class));
    }

    @Override
    public void writeExternal(DataOutputStream out) throws IOException {
        ExtUtil.writeString(out, className);
        ExtUtil.write(out, new ExtWrapMapPoly(refs));
        ExtUtil.write(out, new ExtWrapMap(responses, new ExtWrapList()));
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
