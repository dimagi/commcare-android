# CommCare Support Library

[CommCare](https://github.com/dimagi/commcare-android/) is an extensible form management application with a number of hooks
to enable integration with other Android applications that want to use CommCare as a form entry engine
or as a data backend.
The CommCare support library provides a set of utility functions for using these APIs [mobile APIs](https://github.com/dimagi/commcare-android/wiki)
without having to understand and implement the underlying Android Cursors and Intents.

### Installation

The support library is hosted on [JCenter](https://bintray.com/wsp260/commcare-support-library/commcare-support-library).
You can include this in Gradle in your `build.gradle`:

```
repositories {
    ...
    jcenter()
}
```

and:
```
dependencies {
    ...
    compile 'org.commcare:support-library:12.3'
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
