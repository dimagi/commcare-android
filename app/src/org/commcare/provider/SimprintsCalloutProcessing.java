package org.commcare.provider;

import android.content.Intent;
import android.util.Base64;
import android.util.Log;

import com.simprints.libsimprints.Identification;
import com.simprints.libsimprints.Registration;

import org.commcare.engine.extensions.IntentCallout;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.AbstractTreeElement;
import org.javarosa.core.model.instance.TreeReference;

import java.util.Hashtable;
import java.util.List;
import java.util.Vector;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class SimprintsCalloutProcessing {
    private static final String TAG = SimprintsCalloutProcessing.class.getSimpleName();

    public static boolean isIdentificationResponse(Intent intent) {
        return intent.hasExtra("identification");
    }

    public static boolean isRegistrationResponse(Intent intent) {
        return intent.hasExtra("registration");
    }

    public static List<Identification> getIdentificationData(Intent intent) {
        return (List)intent.getParcelableArrayListExtra("identification");
    }

    public static Registration getRegistrationData(Intent intent) {
        return intent.getParcelableExtra("registration");
    }

    public static boolean processRegistrationResponse(FormDef formDef, Intent intent, TreeReference intentQuestionRef,
                                                      Hashtable<String, Vector<TreeReference>> responseToRefMap) {
        Registration registration = getRegistrationData(intent);

        String result = intent.getDataString();
        IntentCallout.setNodeValue(formDef, intentQuestionRef, "Fingerprints scanned: " + result);

        Vector<TreeReference> rightIndexRef = responseToRefMap.get("rightIndex");
        Vector<TreeReference> rightThumbRef = responseToRefMap.get("rightThumb");
        Vector<TreeReference> leftIndexRef = responseToRefMap.get("leftIndex");
        Vector<TreeReference> leftThumbRef = responseToRefMap.get("leftThumb");
        if (rightIndexRef != null && !rightIndexRef.isEmpty() &&
                rightThumbRef != null && !rightThumbRef.isEmpty() &&
                leftIndexRef != null && !leftIndexRef.isEmpty() &&
                leftThumbRef != null && !leftThumbRef.isEmpty()) {
            setRefs(formDef, rightIndexRef, intentQuestionRef, registration.getTemplateRightIndex());
            setRefs(formDef, rightThumbRef, intentQuestionRef, registration.getTemplateRightThumb());
            setRefs(formDef, leftIndexRef, intentQuestionRef, registration.getTemplateLeftIndex());
            setRefs(formDef, leftThumbRef, intentQuestionRef, registration.getTemplateLeftThumb());
            return true;
        } else {
            return false;
        }
    }

    private static void setRefs(FormDef formDef, Vector<TreeReference> refs, TreeReference contextRef,  byte[] digitTemplate) {
        for (TreeReference ref : refs) {
            setDigit(formDef, ref, contextRef, digitTemplate);
        }
    }

    private static void setDigit(FormDef formDef, TreeReference ref, TreeReference contextRef, byte[] digitTemplate) {
        EvaluationContext context = new EvaluationContext(formDef.getEvaluationContext(), contextRef);
        TreeReference fullRef = ref.contextualize(contextRef);
        AbstractTreeElement node = context.resolveReference(fullRef);

        if (node == null) {
            Log.e(TAG, "Unable to resolve ref " + ref);
            return;
        }
        int dataType = node.getDataType();

        IntentCallout.setValueInFormDef(formDef, fullRef, Base64.encodeToString(digitTemplate, Base64.DEFAULT), dataType);
    }
}
