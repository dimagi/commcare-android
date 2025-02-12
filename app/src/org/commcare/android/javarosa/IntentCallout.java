package org.commcare.android.javarosa;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.BadParcelableException;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import org.commcare.CommCareApplication;
import org.commcare.provider.IdentityCalloutHandler;
import org.commcare.provider.SimprintsCalloutProcessing;
import org.commcare.util.LogTypes;
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
import org.javarosa.xpath.XPathParseTool;
import org.javarosa.xpath.expr.FunctionUtils;
import org.javarosa.xpath.expr.XPathExpression;
import org.javarosa.xpath.parser.XPathSyntaxException;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.FileChannel;
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

    private static final String OVERRIDE_PLAIN_TEXT_ASSUMPTION_PREFIX = "cc:xpath_key:";

    // Generic Extra from intent callout extensions
    public static final String INTENT_RESULT_VALUE = "odk_intent_data";

    // Bundle of extra values
    public static final String INTENT_RESULT_EXTRAS_BUNDLE = "odk_intent_bundle";

    /**
     * Intent flag to identify whether this callout should be included in attempts to compound
     * similar intents
     */
    public static final String INTENT_EXTRA_CAN_AGGREGATE = "cc:compound_include";

    public IntentCallout() {
        // for serialization
    }

    /**
     * @param buttonLabel       Intent callout button text for initially calling the intent.
     * @param updateButtonLabel Intent callout button text for re-calling the intent to update the answer
     * @param appearance        if 'quick' then intent is automatically called when question is shown, and advanced when intent answer is received
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

    public void attachToForm(FormDef form) {
        this.formDef = form;
    }

    private String parseData(EvaluationContext context) {
        boolean overridePlainTextAssumption = data.startsWith(OVERRIDE_PLAIN_TEXT_ASSUMPTION_PREFIX);
        String dataString = data.replace(OVERRIDE_PLAIN_TEXT_ASSUMPTION_PREFIX, "");

        if (!overridePlainTextAssumption) {
            return dataString;
        } else {
            try {
                return FunctionUtils.toString(XPathParseTool.parseXPath(dataString).eval(context));
            } catch (XPathSyntaxException e) {
                return null;
            }
        }
    }

    public Intent generate(EvaluationContext ec) {
        Intent i = new Intent();
        if (className != null) {
            i.setAction(className);
        }
        if (data != null) {
            String dataString = parseData(ec);

            if (dataString != null && !"".equals(dataString) && type != null) {
                // Weird hack but this call seems specifically to be needed to play video
                // http://stackoverflow.com/questions/1572107/android-intent-for-playing-video
                i.setDataAndType(Uri.parse(dataString), type);
            } else {
                if (type != null) {
                    i.setType(type);
                }
                if (data != null && !"".equals(dataString)) {
                    i.setData(Uri.parse(dataString));
                }
            }
        }
        if (component != null) {
            i.setComponent(new ComponentName(component, className));
        }
        if (refs != null) {
            for (Enumeration<String> en = refs.keys(); en.hasMoreElements(); ) {
                String key = en.nextElement();
                Object xpathResult = refs.get(key).eval(ec);

                if (INTENT_EXTRA_CAN_AGGREGATE.equals(key)) {
                    if (key != null && !"".equals(key)) {
                        i.putExtra(INTENT_EXTRA_CAN_AGGREGATE, FunctionUtils.toBoolean(xpathResult));
                    }
                } else {
                    String extraVal = FunctionUtils.toString(xpathResult);
                    if (extraVal != null && !"".equals(extraVal)) {
                        i.putExtra(key, extraVal);
                    }
                }
            }
        }
        return i;
    }

    /**
     * @return if answer was set from intent successfully
     */
    public boolean processResponse(Intent intent, TreeReference intentQuestionRef, File destination) {
        if (intentInvalid(intent)) {
            return false;
        } else if (IdentityCalloutHandler.isIdentityCalloutResponse(intent)) {
            return IdentityCalloutHandler.processIdentityCalloutResponse(formDef, intent, intentQuestionRef, responseToRefMap);
        } else if (SimprintsCalloutProcessing.isRegistrationResponse(intent)) {
            return SimprintsCalloutProcessing.processRegistrationResponse(formDef, intent, intentQuestionRef, responseToRefMap);
        } else {
            return processOdkResponse(intent, intentQuestionRef, destination) ||
                    // because print callouts don't set a result
                    isPrintIntentCallout();
        }
    }

    private boolean isPrintIntentCallout() {
        return "org.commcare.dalvik.action.PRINT".equals(this.className);
    }

    private boolean intentInvalid(Intent intent) {
        if (intent == null) {
            return true;
        }
        try {
            // force unparcelling to check if we are missing classes to
            // correctly process callout response
            intent.hasExtra(INTENT_RESULT_VALUE);
            if (responseToRefMap != null) {
                for (String key: responseToRefMap.keySet()) {
                    intent.hasExtra(key);
                }
            }
        } catch (BadParcelableException e) {
            Log.w(TAG, "unable to unparcel intent: " + e.getMessage());
            return true;
        }
        return false;
    }

    private boolean processOdkResponse(Intent intent, TreeReference intentQuestionRef, File destination) {
        String result = intent.getStringExtra(INTENT_RESULT_VALUE);
        setNodeValue(formDef, intentQuestionRef, result);

        // see if we have a return bundle
        Bundle response = intent.getBundleExtra(INTENT_RESULT_EXTRAS_BUNDLE);

        if (responseToRefMap != null) {
            if (response != null) {
                // Load all of the data from the incoming bundle
                for (String key : responseToRefMap.keySet()) {
                    // See if the value exists at all, if not, skip it
                    if (!response.containsKey(key)) {
                        continue;
                    }
                    setOdkResponseValue(intentQuestionRef, destination, response.getString(key), responseToRefMap.get(key));
                }
            } else {
                // Check if intent has response keys as extras.
                boolean hasExtra = false;
                for (String key: responseToRefMap.keySet()) {
                    if (!intent.hasExtra(key)) {
                        continue;
                    }
                    hasExtra = true;
                    setOdkResponseValue(intentQuestionRef, destination, intent.getStringExtra(key), responseToRefMap.get(key));
                }
                if (hasExtra) {
                    return true;
                }
            }
        }
        return (result != null);
    }

    private void setOdkResponseValue(TreeReference intentQuestionRef, File destination, String responseValue, Vector<TreeReference> responseRefs) {
        if (responseValue == null) {
            responseValue = "";
        }

        for (TreeReference ref : responseRefs) {
            processResponseItem(ref, responseValue, intentQuestionRef, destination);
        }
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

        try {
            File localCopy = copyFileToLocalDirectory(responseValue, destinationFile);
            formDef.setValue(new StringData(localCopy.getName()), ref);
            return;
        } catch (FileNotFoundException fnfe) {
            Log.w(TAG, "CommCare received a link to a file at " + responseValue + " to be included in the form, but it was not present on the phone!");

            //Wipe out any reference that exists
            formDef.setValue(null, ref);
        } catch (IOException ioe) {
            ioe.printStackTrace();
            Log.e(TAG,"CommCare failed to copy a binary input from an intent callout from: " + responseValue);
            Logger.exception("Failed to copy an attachment from intent callout ", ioe);
            //Wipe out any reference that exists
            formDef.setValue(null, ref);
        }
    }

    /**
     * Copy the provided input source to a local directory. Valid inputs are file paths or content
     * uris.
     *
     * @throws FileNotFoundException If the source file doesn't exist
     */
    private File copyFileToLocalDirectory(String inputSource, File destinationFile) throws IOException {
        if (FileUtil.isContentUri(inputSource)) {
            Uri uri = Uri.parse(inputSource);

            return FileUtil.copyContentFileToLocalDir(uri, destinationFile, CommCareApplication.instance().getApplicationContext());
        } else {
            File src = new File(inputSource);
            if(!src.exists()) {
                throw new FileNotFoundException(inputSource);
            }

            File newFile = new File(destinationFile, src.getName());

            //Looks like our source file exists, so let's go grab it
            FileUtil.copyFile(src, newFile);
            if (!newFile.exists() || newFile.length() != src.length()) {
                throw new IOException("Failed to copy file from src " + src.toString() + " to dest " + newFile);
            }
            return newFile;
        }
    }

    @Override
    public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
        className = ExtUtil.readString(in);
        refs = (Hashtable<String, XPathExpression>)ExtUtil.read(in, new ExtWrapMapPoly(String.class, true), pf);
        responseToRefMap = (Hashtable<String, Vector<TreeReference>>)ExtUtil.read(in, new ExtWrapMap(String.class, new ExtWrapList(TreeReference.class)), pf);
        appearance = (String)ExtUtil.read(in, new ExtWrapNullable(String.class), pf);
        component = (String)ExtUtil.read(in, new ExtWrapNullable(String.class), pf);
        buttonLabel = (String)ExtUtil.read(in, new ExtWrapNullable(String.class), pf);
        updateButtonLabel = (String)ExtUtil.read(in, new ExtWrapNullable(String.class), pf);
        type = (String)ExtUtil.read(in, new ExtWrapNullable(String.class), pf);
        data = (String)ExtUtil.read(in, new ExtWrapNullable(String.class), pf);
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
        ExtUtil.write(out, new ExtWrapNullable(type));
        ExtUtil.write(out, new ExtWrapNullable(data));
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

    public FormDef getFormDef() {
        return formDef;
    }

    public boolean isSimprintsCallout() {
        return "com.simprints.id.REGISTER".equals(className);
    }
}
