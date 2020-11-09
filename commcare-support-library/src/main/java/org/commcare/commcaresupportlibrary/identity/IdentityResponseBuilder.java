package org.commcare.commcaresupportlibrary.identity;

import android.app.Activity;
import android.content.Intent;

import org.commcare.commcaresupportlibrary.identity.model.IdentificationMatch;
import org.commcare.commcaresupportlibrary.identity.model.MatchResult;
import org.commcare.commcaresupportlibrary.identity.model.RegistrationResult;
import org.commcare.commcaresupportlibrary.identity.model.VerificationMatch;

import java.util.ArrayList;

import static android.app.Activity.RESULT_OK;

/**
 * Class to facilitate creation of response intents for passing result data of Identity workflows back to CommCare
 */
public class IdentityResponseBuilder {

    // Constants related to Identity Provider Workflows
    final private static String REGISTRATION = "registration";
    final private static String IDENTIFICATION = "identification";
    final private static String VERIFICATION = "verification";
    final private static String REGISTRATION_DUPLICATES = "registration_duplicates";

    private Intent resultIntent;

    private IdentityResponseBuilder(Intent resultIntent) {
        this.resultIntent = resultIntent;
    }

    /**
     * Creates response for result of a new Identity Registration
     *
     * @param guid Global unique id generated as part of the new registration in the Identity Provider
     * @return IdentityResponseBuilder for a registration workflow response
     */
    public static IdentityResponseBuilder registrationResponse(String guid) {
        Intent intent = new Intent();
        intent.putExtra(REGISTRATION, new RegistrationResult(guid));
        return new IdentityResponseBuilder(intent);
    }

    /**
     * Creates response for result of a identification workflow
     *
     * @param identificationMatches list of matches to be passed back to CommCare
     * @return IdentityResponseBuilder for a identification workflow response
     */
    public static IdentityResponseBuilder identificationResponse(ArrayList<IdentificationMatch> identificationMatches) {
        Intent intent = new Intent();
        intent.putParcelableArrayListExtra(IDENTIFICATION, identificationMatches);
        return new IdentityResponseBuilder(intent);
    }

    /**
     * Creates response for result of a verification workflow
     *
     * @param guid        Identity Provider's GUID for the beneficiary the verification was performed
     * @param matchResult Result of the verification process
     * @return IdentityResponseBuilder for a verification workflow response
     */
    public static IdentityResponseBuilder verificationResponse(String guid, MatchResult matchResult) {
        Intent intent = new Intent();
        intent.putExtra(VERIFICATION, new VerificationMatch(guid, matchResult));
        return new IdentityResponseBuilder(intent);
    }

    /**
     * Creates response with a list of duplicates identified in the registration workflow
     *
     * @param identificationMatches
     * @return IdentityResponseBuilder for a registration workflow response
     */
    public static IdentityResponseBuilder registrationResponse(ArrayList<IdentificationMatch> identificationMatches) {
        Intent intent = new Intent();
        intent.putExtra(REGISTRATION_DUPLICATES, true);
        intent.putParcelableArrayListExtra(IDENTIFICATION, identificationMatches);
        return new IdentityResponseBuilder(intent);
    }

    /**
     * Adds identifications to a response. This can be used in a registration workflow to pass back possible duplicates
     * for a new registration
     *
     * @param identificationMatches list of matches against a biometric
     * @return IdentityResponseBuilder containing the list of {@code identificationMatches}
     */
    public IdentityResponseBuilder setIdentificationMatches(ArrayList<IdentificationMatch> identificationMatches) {
        resultIntent.putParcelableArrayListExtra(IDENTIFICATION, identificationMatches);
        return this;
    }

    // Can be used to get the response Intent from the IdentityResponseBuilder
    public Intent build() {
        return resultIntent;
    }

    // Finalize the response and return result back to CommCare
    public void finalizeResponse(Activity activity) {
        activity.setResult(RESULT_OK, resultIntent);
        activity.finish();
    }
}
