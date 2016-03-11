# commcare-odk

CommCare is an easily customizable, open source mobile platform that supports frontline workers in low-resource settings. By replacing paper-based forms, frontline workers can use CommCare to track and support their clients with registration forms, checklists, SMS reminders, and multimedia.

This repository represents the Android version of CommCare. It depends on the [JavaRosa](https://github.com/dimagi/javarosa) and [CommCare](https://github.com/dimagi/commcare) repositories, which are the XForm engine and case/lookup table implementations used by both the Android and J2ME versions of CommCare.

## Setup

To set up an Android dev environmnet for commcare-odk, do the following:

- Install [Android Studio](https://developer.android.com/sdk/index.html).
- Install Java 7 if you don't have it yet. For ease of test suite setup ([see below](#tests)) OpenJDK is preferred over Oracle's version of Java.

Go ahead and open Android Studio if this is your first time using it;
it may take you through some sort of setup wizard, and it's nice to get that out of the way.

Android Studio's default project space is `~/AndroidStudioProjects` so I'm going to use that in the example.
CommCare ODK depends on CommCare and JavaRosa, and CommCare ODK expects the three of them to live side by side
in your directory structure. You can acheive this with the following commands (in bash):

```bash
cd ~/AndroidStudioProjects
mkdir CommCare
cd CommCare
git clone https://github.com/dimagi/commcare-odk.git
git clone https://github.com/dimagi/commcare.git
git clone https://github.com/dimagi/javarosa.git
```

- Open Android Studio
- If this is your first time using Android Studio, click "Config" and setup the Android SDK.
- Download the Android 5.1.1 (API 22) SDK Platform and the Google APIs for 22.
- Now go back to the Android Studio Welcome dashboard and click "Import project (Eclipse ADT, Gradle, etc.)"
- Select AndroidStudioProjects > CommCare > commcare-odk and hit OK
- Click "OK" to use the Gradle wrapper
- Wait while Android Studio spins its wheels
- Download any build dependencies that the SDK Manager tells you you need.

## Running

Now you're basically ready to go. To build CommCare ODK and get it running on your phone,
plug in an android phone that

- is [in developer mode has USB debugging enabled](https://developer.android.com/tools/device.html#setting-up)
- doesn't have CommCare ODK installed on it

Alternatively, you can resign yourself to using the android emulator on your computer,
but that will be a less pleasurable experience.

In Android Studio, hit the build button (a green "play" symbol in the toolbar).
The first build will take a minute.
Then it'll ask you what device to run it on

- Make sure your screen is unlocked (or else you'll see [something like this](https://gist.github.com/dannyroberts/6d8d57ff4d5f9a1b70a5))
- select your device

Enjoy!

(or just select the emulator and cry)

## Tests

The commcare-odk repository uses [Robolectric](http://robolectric.org/), which provides mocks, allowing you to run Android specific code on your local machine. Since commcare-odk uses encrypted databases, you must either use OpenJDK or install the [Java Cryptography Extension](https://en.wikipedia.org/wiki/Java_Cryptography_Extension) for Oracle's version of Java. It is much easier to just install OpenJDK.

### Run tests from the command-line

```bash
cd commcare-odk
gradle testCommcareDebugUnitTest
```

and view the results from the output file generated.

### Run tests from Android Studio

Create a new Android Studio JUnit Build configuration using the following steps.

- Click _Run -> Edit Configruations_ and create a new JUnit configuration by pressing the green plus button.
- Set _Name_ to "commcare odk test suite"
- Set _Test kind_ to "All in directory"
- set _Directory_ to `/absolute/path/to/commcare-odk/unit-tests/src/`
- Right click on this directory and click the "Create 'All Tests'" option that should be listed more than half-way down the list.
- Set _VM options_ to `-ea -noverify`
- Click `OK` to finish creating the configuration.
- Select the "commcare odk test suite" under the configuration drop down to the left of the green play button.
- Press the green play button to run the tests.
