# CommCare Support Library

[CommCare](https://github.com/dimagi/commcare-android/) is an extensible form management application with a number of hooks
to enable integration with other Android applications that want to use CommCare as a form entry engine
or as a data backend.
The CommCare support library provides a set of utility functions for using these APIs [mobile APIs](https://github.com/dimagi/commcare-android/wiki)
without having to understand and implement the underlying Android Cursors and Intents.

CommCare Support library also provides APIs for a biometric provider to integrate with CommCare. Read more [here](https://github.com/dimagi/commcare-android/blob/master/commcare-support-library/src/main/java/org/commcare/commcaresupportlibrary/identity/identity_integration.md)

### Installation

The support library is hosted on Maven Central.
You can include this in Gradle in your `build.gradle`:

```
repositories {
    ...
    mavenCentral()
}
```

and:
```
dependencies {
    ...
    implementation 'org.commcarehq.commcare:support-library:12.4'
}
```

### Examples

#### Cases

Get the name of a case by its `case_id`:

`CaseUtils.getCaseName(Context context, String caseId)`

Get the value of a specified property of a case by its `case_id`:

`CaseUtils.getCaseProperty(Context context, String caseId, String caseProperty)`

Or get an entire list of case properties:

`CaseUtils.getCaseProperties(Context context, String caseId, ArrayList<String> caseProperties)`

Get a list of all the caseIds in the current user database:

`CaseUtils.getCaseIds(Context context)`

#### Fixtures

Get a list of IDs of all the fixtures in the current database:

`FixtureUtils.getFixtureIdList(Context context)`

Then retrieve the XML for a specific fixture from this list:

`FixtureUtils.getFixtureXml(Context context, String fixtureId`

#### CommCare Launch Helpers

Launch CommCare with a specific CommCare App:

`CommCareLauncher.launchCommCareForAppId(Context context, String appId)`

