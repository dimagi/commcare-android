package org.commcare.provider;

import android.content.Intent;

import org.commcare.android.javarosa.IntentCallout;
import org.commcare.commcaresupportlibrary.identity.IdentityResponseBuilder;
import org.commcare.commcaresupportlibrary.identity.model.IdentificationMatch;
import org.commcare.commcaresupportlibrary.identity.model.MatchResult;
import org.commcare.commcaresupportlibrary.identity.model.MatchStrength;
import org.commcare.commcaresupportlibrary.identity.model.RegistrationResult;
import org.commcare.commcaresupportlibrary.identity.model.VerificationMatch;
import org.commcare.util.LogTypes;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.AbstractTreeElement;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.util.OrderedHashtable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;

import androidx.annotation.StringDef;

public class IdentityCalloutHandler {


    private static final String REF_GUID = "guid";
    private static final String REF_MATCH_GUID = "match_guid";
    private static final String REF_MATCH_CONFIDENCE = "match_confidence";
    private static final String REF_MATCH_STRENGTH = "match_strength";

    public static final String GENERALIZED_IDENTITY_PROVIDER = "generalized_identity_provider";


    @StringDef({GENERALIZED_IDENTITY_PROVIDER, SimprintsCalloutProcessing.SIMPRINTS_IDENTITY_PROVIDER})
    @Retention(RetentionPolicy.SOURCE)
    public @interface IdentityProvider {
    }


    public static boolean isIdentityCalloutResponse(Intent intent) {
        return isRegistrationResponse(intent) || isVerificationResponse(intent) || isIdentificationResponse(intent);
    }

    public static boolean processIdentityCalloutResponse(FormDef formDef,
                                                         Intent intent,
                                                         TreeReference intentQuestionRef,
                                                         Hashtable<String, Vector<TreeReference>> responseToRefMap) {
        if (isRegistrationResponse(intent)) {
            processRegistrationReponse(formDef, intent, intentQuestionRef, responseToRefMap);
        } else if (isVerificationResponse(intent)) {
            processVerificationResponse(formDef, intent, intentQuestionRef, responseToRefMap);
        } else if (isIdentificationResponse(intent)) {
            processIdentificationResponse(formDef, intent, intentQuestionRef, responseToRefMap);
        }
        return false;
    }

    // Used to Handle Identified Match in a Form during Registration
    private static void processIdentificationResponse(FormDef formDef,
                                                      Intent intent,
                                                      TreeReference intentQuestionRef,
                                                      Hashtable<String, Vector<TreeReference>> responseToRefMap) {
        List<IdentificationMatch> matches = getIdentificationMatches(intent);
        if (matches.size() > 0) {
            IdentificationMatch bestMatch = matches.get(0);
            storeValueFromCalloutInForm(formDef, responseToRefMap, intentQuestionRef,
                    REF_MATCH_GUID, bestMatch.getGuid());
            storeValueFromCalloutInForm(formDef, responseToRefMap, intentQuestionRef,
                    REF_MATCH_CONFIDENCE, String.valueOf(bestMatch.getMatchResult().getConfidence()));
            storeValueFromCalloutInForm(formDef, responseToRefMap, intentQuestionRef,
                    REF_MATCH_STRENGTH, bestMatch.getMatchResult().getStrength().toString().toLowerCase());

            // Empty out the registraion guid since no new registration has been performed
            storeValueFromCalloutInForm(formDef, responseToRefMap, intentQuestionRef, REF_GUID, "");
        }
    }

    private static void processVerificationResponse(FormDef formDef,
                                                    Intent intent,
                                                    TreeReference intentQuestionRef,
                                                    Hashtable<String, Vector<TreeReference>> responseToRefMap) {
        VerificationMatch verificationMatch = intent.getParcelableExtra(IdentityResponseBuilder.VERIFICATION);

        storeValueFromCalloutInForm(formDef, responseToRefMap, intentQuestionRef,
                REF_MATCH_GUID, verificationMatch.getGuid());

        MatchResult matchResult = verificationMatch.getMatchResult();

        storeValueFromCalloutInForm(formDef, responseToRefMap, intentQuestionRef,
                REF_MATCH_CONFIDENCE, String.valueOf(matchResult.getConfidence()));
        storeValueFromCalloutInForm(formDef, responseToRefMap, intentQuestionRef,
                REF_MATCH_STRENGTH, matchResult.getStrength().toString().toLowerCase());
    }

    private static void processRegistrationReponse(FormDef formDef,
                                                   Intent intent,
                                                   TreeReference intentQuestionRef,
                                                   Hashtable<String, Vector<TreeReference>> responseToRefMap) {
        RegistrationResult registrationResult = intent.getParcelableExtra(IdentityResponseBuilder.REGISTRATION);
        String guid = registrationResult.getGuid();
        storeValueFromCalloutInForm(formDef, responseToRefMap, intentQuestionRef, REF_GUID, guid);

        // Empty out any references present for duplicate handling
        storeValueFromCalloutInForm(formDef, responseToRefMap, intentQuestionRef, REF_MATCH_GUID, "");
        storeValueFromCalloutInForm(formDef, responseToRefMap, intentQuestionRef, REF_MATCH_CONFIDENCE, "");
        storeValueFromCalloutInForm(formDef, responseToRefMap, intentQuestionRef, REF_MATCH_STRENGTH, "");
    }

    public static OrderedHashtable<String, String> getConfidenceMatchesFromCalloutResponse(Intent intent) {
        List<IdentificationMatch> matches = getIdentificationMatches(intent);

        OrderedHashtable<String, String> guidToConfidenceMap = new OrderedHashtable<>();
        for (IdentificationMatch identificationMatch : matches) {
            guidToConfidenceMap.put(identificationMatch.getGuid(), getStrengthText(identificationMatch.getMatchResult().getStrength()));
        }

        return guidToConfidenceMap;
    }

    // Return matches sorted as best match first
    private static List<IdentificationMatch> getIdentificationMatches(Intent intent) {
        List<IdentificationMatch> matches = intent.getParcelableArrayListExtra(IdentityResponseBuilder.IDENTIFICATION);
        Collections.sort(matches);
        return matches;
    }

    private static String getStrengthText(MatchStrength strength) {
        switch (strength) {
            case FIVE_STARS:
                return Localization.get("fingerprint.match.100");
            case FOUR_STARS:
                return Localization.get("fingerprint.match.75");
            case THREE_STARS:
                return Localization.get("fingerprint.match.50");
            case TWO_STARS:
                return Localization.get("fingerprint.match.25");
            case ONE_STAR:
                return Localization.get("fingerprint.match.0");
            default:
                return Localization.get("fingerprint.match.unknown");
        }
    }

    public static boolean storeValueFromCalloutInForm(FormDef formDef,
                                                      Hashtable<String, Vector<TreeReference>> responseToRefMap,
                                                      TreeReference intentQuestionRef,
                                                      String keyReference,
                                                      String value) {
        Vector<TreeReference> formReferene = responseToRefMap.get(keyReference);
        if (formReferene != null && !formReferene.isEmpty() && value != null) {
            storeValueFromCalloutInForm(formDef, formReferene, intentQuestionRef, value);
            return true;
        }
        return false;
    }

    private static void storeValueFromCalloutInForm(FormDef formDef,
                                                    Vector<TreeReference> treeRefs,
                                                    TreeReference contextRef,
                                                    String responseValue) {
        for (TreeReference ref : treeRefs) {
            storeValueFromCalloutInForm(formDef, ref, contextRef, responseValue);
        }
    }

    private static void storeValueFromCalloutInForm(FormDef formDef, TreeReference ref,
                                                    TreeReference contextRef,
                                                    String responseValue) {
        EvaluationContext context = new EvaluationContext(formDef.getEvaluationContext(), contextRef);
        TreeReference fullRef = ref.contextualize(contextRef);
        AbstractTreeElement node = context.resolveReference(fullRef);

        if (node == null) {
            Logger.log(LogTypes.TYPE_MAINTENANCE, "Unable to resolve ref while processing callout response" + ref);
            return;
        }
        int dataType = node.getDataType();

        IntentCallout.setValueInFormDef(formDef, fullRef, responseValue, dataType);
    }

    /**
     * Identity identification/search response from Identity Provider
     */
    public static boolean isIdentificationResponse(Intent intent) {
        return intent.hasExtra(IdentityResponseBuilder.IDENTIFICATION);
    }

    /**
     * Identity registration response from Identity Provider
     */
    public static boolean isRegistrationResponse(Intent intent) {
        return intent.hasExtra(IdentityResponseBuilder.REGISTRATION);
    }

    /**
     * Identity verification response from Identity Provider
     */
    public static boolean isVerificationResponse(Intent intent) {
        return intent.hasExtra(IdentityResponseBuilder.VERIFICATION);
    }
}
