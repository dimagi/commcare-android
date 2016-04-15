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
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.util.OrderedHashtable;

import java.util.Collections;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Vector;

/**
 * Process simprints fingerprint registration and identification callout results.
 *
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

    public static OrderedHashtable<String, String> getIdentificationData(Intent intent) {
        List<Identification> idReadings = (List)intent.getParcelableArrayListExtra("identification");

        Collections.sort(idReadings);

        OrderedHashtable<String, String> guidToDataMap = new OrderedHashtable<>();
        for (Identification id : idReadings) {
            guidToDataMap.put(id.getGuid(), id.getConfidence() + "");
        }

        return guidToDataMap;
    }

    public static Registration getRegistrationData(Intent intent) {
        return intent.getParcelableExtra("registration");
    }

    public static boolean processRegistrationResponse(FormDef formDef, Intent intent, TreeReference intentQuestionRef,
                                                      Hashtable<String, Vector<TreeReference>> responseToRefMap) {
        Registration registration = getRegistrationData(intent);

        Vector<TreeReference> rightIndexRef = responseToRefMap.get("rightIndex");
        Vector<TreeReference> rightThumbRef = responseToRefMap.get("rightThumb");
        Vector<TreeReference> leftIndexRef = responseToRefMap.get("leftIndex");
        Vector<TreeReference> leftThumbRef = responseToRefMap.get("leftThumb");
        int numOfFingersScanned = (registration.getTemplateLeftIndex() == null || registration.getTemplateLeftIndex().length == 0 ? 0 : 1) +
                (registration.getTemplateRightIndex() == null || registration.getTemplateRightIndex().length == 0 ? 0 : 1) +
                (registration.getTemplateLeftThumb() == null || registration.getTemplateLeftThumb().length == 0 ? 0 : 1) +
                (registration.getTemplateRightThumb() == null || registration.getTemplateRightThumb().length == 0 ? 0 : 1);

        IntentCallout.setNodeValue(formDef, intentQuestionRef, Localization.get("fingerprints.scanned", new String[] {"" + numOfFingersScanned}));

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

    private static void setRefs(FormDef formDef, Vector<TreeReference> refs, TreeReference contextRef, byte[] digitTemplate) {
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
