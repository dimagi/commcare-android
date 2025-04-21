## Integrating CommCare with third party Identity Systems


CommCare supports [Android Callouts](https://github.com/dimagi/commcare-android/wiki/Intent-Callout-to-External-Application) based integration with third party Identity Providers that facilitate services like Biometric Enrollment and Identification. Using Android Intent Callouts, it's possible for someone to configure a CommCare app that can call a third party Android based Identity Provider and receive the enrolled case data back into CommCare app. 

If you are an Identity Provider and wants to integrate with CommCare, you can follow this guide to know about Android Intent contracts CommCare supports out of the box. 
To get access to generic CommCare data models, you need to first add the CommCare support library as a dependency [as described over here](https://github.com/dimagi/commcare-support-library#installation)


We define some of the common Identity Provider workflows below and how you can make changes in the Identity Provider Application to support these workflows with CommCare.


#### Enrolling a Beneficiary

You can have CommCare call your app in a form to register biometrics for a beneficiary. This beneficiary enrollment should create a new guid for the beneficiary in the Identity Provider.
Often you will need this generated guid to be passed back to CommCare so that it can be saved as a case property. You can use the `IdentityResponseBuilder` provided by CommCare Support library to construct a response for your registration workflow as follows -


````
IdentityResponseBuilder.registrationResponse(guid)
    .finalizeResponse(activity)
````
Alternatively, in case the biometric templates are to be stored in CommCare, use the following instead:
````
IdentityResponseBuilder.registrationResponse(guid, templates)
    .finalizeResponse(activity)
````
* `templates` is a [`Map`](https://docs.oracle.com/javase/8/docs/api/java/util/Map.html) containing all the biometric templates and whose _keys_ are [`BiometricIdentifier`](BiometricIdentifier.java) elements and _values_ are the actual biometric templates in the form of a byte array. See an example below in `kotlin`:
````
        var templates: HashMap<BiometricIdentifier, ByteArray> = HashMap(2)
        templates[BiometricIdentifier.LEFT_INDEX_FINGER] = byteArrayOf(0, 0, -21, -67, 0, -64, 25, 62, -69, -124, -91, 29, -50, -107, 58)
        templates[BiometricIdentifier.LEFT_MIDDLE_FINGER] = byteArrayOf(122, -91, 114, 62, 107, -95, -69, 28, 110, 123, 72, 71, -86, -117, 126)
````
This creates an appropriate resulting Intent for the Identity registration workflow and finish your activity after setting the response as a result to returning intent.


#### Search or Identify a Beneficiary


An Identification workflow should result into a list of matches corresponding to a beneficiary biometric. These list of matches can be passed back to CommCare as follows - 

````
ArrayList<Identification> identifications = new ArrayList<>();

// add matches to identifications
identifications.add(new IdentificationMatch(matchGuid, new MatchResult(confidence, matchStrength)));
identifications.add(new IdentificationMatch(anotherMatchGuid, new MatchResult(confidence, matchStrength)));

// Create and return response back to CommCare
IdentityResponseBuilder.identificationResponse(identifications)
    .finalizeResponse(activity)
````

An `Identification` object comprises of the Identity Provider's guid of the match and the matching result which in turn contains the matching score `confidence` and an indicator `matchStrength` based on the `confidence` that identifies if it's a good match or not.

#### Verify a Beneficiary

Verification is a 1:1 search that is used when you want to confirm whether a beneficiary is who they say are. Given a beneficiary's `guid` as input from CommCare, a verification response should be constructed as follows - 

````
IdentityResponseBuilder.verificationResponse(guid, new MatchResult(confidence, matchStrength))
    .finalizeResponse(activity)
````

#### Passing back duplicates as part of Registration

In cases when during registration the Identity provider recognises that the beneficiary for a particular biometric already exists, the Identity provider can choose to not create a new registration and instead reply back to CommCare with a set of matches that Identity provider thinks are possible duplicates.
This can be done by following the same semantics as the Identification process -


````
ArrayList<Identification> duplicates = new ArrayList<>();

// add matches to identifications
duplicates.add(new IdentificationMatch(duplicateGuid, new MatchResult(confidence, matchStrength)));


IdentityResponseBuilder.registrationResponse(duplicates)
    .finalizeResponse(activity)
````



