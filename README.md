# commcare-android

CommCare is an easily customizable, open source mobile platform that supports frontline workers in low-resource settings. By replacing paper-based forms, frontline workers can use CommCare to track and support their clients with registration forms, checklists, SMS reminders, and multimedia.

This repository represents the Android version of CommCare. It depends on the [CommCare Core](https://github.com/dimagi/commcare-core) repository, which contains the XForm engine and case/lookup table implementations.

## Setup

To set up an Android dev environmnet for commcare-android, do the following:

- Install [Android Studio](https://developer.android.com/sdk/index.html).
- Install Java 8 if you don't have it yet. For ease of test suite setup ([see below](#tests)) OpenJDK is preferred over Oracle's version of Java.

Go ahead and open Android Studio if this is your first time using it;
it may take you through some sort of setup wizard, and it's nice to get that out of the way.

Android Studio's default project space is `~/AndroidStudioProjects` so I'm going to use that in the example.
CommCare Android depends on CommCare Core, and CommCare Android expects the core directory to live side by side
in your directory structure. You can acheive this with the following commands (in bash):

```bash
cd ~/AndroidStudioProjects
mkdir CommCare
cd CommCare
git clone https://github.com/dimagi/commcare-android.git
git clone https://github.com/dimagi/commcare-core.git
```

- Open Android Studio
- If this is your first time using Android Studio, click "Config" and setup the Android SDK.
- Download the Android 7 (API 24) SDK Platform and the Google APIs for 24.
- Now go back to the Android Studio Welcome dashboard and click "Import project (Eclipse ADT, Gradle, etc.)"
- Select AndroidStudioProjects > CommCare > commcare-android and hit OK
- Click "OK" to use the Gradle wrapper
- Wait while Android Studio spins its wheels
- Download any build dependencies that the SDK Manager tells you you need.
- Disable _Instant Run_ found in Settings > Build, Execution, Deployment > Instant Run. (It does not play well with multidexing, which we have enabled, or with some of the processes we have set up for Google Services)

## Building

Now you're basically ready to go. To build CommCare Android and get it running on your phone,
plug in an android phone that

- is [in developer mode has USB debugging enabled](https://developer.android.com/tools/device.html#setting-up)
- doesn't have CommCare Android installed on it

In Android Studio, hit the build button (a green "play" symbol in the toolbar).
The first build will take a minute.
Then it'll ask you what device to run it on

- Make sure your screen is unlocked (or else you'll see [something like this](https://gist.github.com/dannyroberts/6d8d57ff4d5f9a1b70a5))
- select your device

Enjoy!

### Building from the command-line

CommCare has several different build variants. The normal build variant is `commcare` and can built built from the command-line with the following command:

```bash
cd commcare-android
./gradlew assembleCommcareDebug
# the apk can now be found in the build/outputs/apk/ directory
```

## Unit Tests

The commcare-android repository uses [Robolectric](http://robolectric.org/), which provides mocks, allowing you to run Android specific code on your local machine.

### Run unit-tests from the command-line

```bash
cd commcare-android
./gradlew testCommcareDebug
```

and view the results from the output file generated.

### Run unit-tests from Android Studio

Create a new Android Studio JUnit Build configuration using the following steps.

- Click _Run -> Edit Configruations_ and create a new JUnit configuration by pressing the green plus button.
- Set _Name_ to "commcare android test suite"
- Set _Test kind_ to "All in directory"
- set _Directory_ to `/absolute/path/to/commcare-android/app/unit-tests/src/`
- Right click on this directory and click the "Create 'All Tests'" option that should be listed more than half-way down the list.
- Set _VM options_ to `-ea -noverify`
- Set _Working directory_ to `/absolute/path/to/commcare-android/app/`
- Set _Use classpath of module_ to *app*
- Click `OK` to finish creating the configuration.
- Select the "commcare android test suite" under the configuration drop down to the left of the green play button.
- Press the green play button to run the tests.

## Instrumentation Tests

The commcare-android repository uses [Espresso](https://developer.android.com/training/testing/espresso/) to write UI tests.
You need to have two keys in your `gradle.properties` before being able to run any instrumentation tests. **But make sure you never commit these keys to github.**
```
HQ_API_USERNAME=<ASK_ANOTHER_DEV_FOR_KEY>
HQ_API_PASSWORD=<ASK_ANOTHER_DEV_FOR_KEY>
```

### Run instrumentation-tests from the command-line

```bash
cd commcare-android
./gradlew connectedCommcareDebugAndroidTest
```

It's also a common requirement to run a particular test, such as when you’re fixing a bug or developing a new test. You can achieve the same in command-line using: 

```bash
./gradlew connectedCommcareDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<FULLY_QUALIFIED_NAME_OF_YOUR_TEST>
```

You can view the results from the output file generated.

### Run instrumentation-tests from Android Studio

Before running tests from Android-Studio make sure you've disabled animations in your device. Note, this is only required when you're running tests from Android Studio 
```
Go to Setting -> Developer Options, and under the Drawing section, switch all of the following options:

Window animation scale -> off
Transition animation scale -> off
Animator duration scale -> off
```

Create a new Android Studio _Android Instrumented Test_ Build configuration using the following steps.

- Click _Run -> Edit Configruations_ and create a new _Android Instrumented Test_ configuration by pressing the green plus button.
- Set _Name_ to "commcare android instrumentation tests"
- Set _Test kind_ to "All in Package"
- set _Package_ to `org.commcare.tests`
- Click `OK` to finish creating the configuration.
- Select the "commcare android instrumentation tests" under the configuration drop down to the left of the green play button.
- Press the green play button to run the tests.

### Code Style Settings

In order to comply with code style guidelines we follow, please use [Commcare Coding Style file](https://github.com/dimagi/commcare-android/blob/master/.android_studio_settings/codestyles/CommCare%20Coding%20Style.xml) and [Commcare Inspection Profile](https://github.com/dimagi/commcare-android/blob/master/.android_studio_settings/inspection/CommCare%20Inpsection%20Profile.xml) as your respective code style and inpection profile in Android Studio. To do so follow these instructions 

1. Copy the config files to your Android Studio installation as follows (Replace AndroidStudio3.0 with the respective directory for the AS version you are using) - 

```
cp .android_studio_settings/inspection/CommCare\ Inpsection\ Profile.xml ~/Library/Preferences/AndroidStudio3.0/inspection/.

cp .android_studio_settings/codestyles/CommCare\ Coding\ Style.xml ~/Library/Preferences/AndroidStudio3.0/codestyles/.  

```

2.  Restart Android Studio

3.  Go to AS preferences -> Editor -> Code Style  and select Scheme as 'Commcare Coding Style' and to AS preferences -> Editor -> Inspections and select Profile as 'Commcare Inspection Profile'

### Common Errors

#### If you experience the following exception when running the tests:
```
java.security.InvalidKeyException: Illegal key size or default parameters
```
you will need to upgrade the JCE policy files that are being used. To do so:

1. Download the JCE Unlimited Strength policy files for Java 8 (Last we checked they could be found [here](http://www.oracle.com/technetwork/java/javase/downloads/jce8-download-2133166.html), but this can change
2. Find the java home directory on your machine. On mac you can do so by entering the following into the command line: `echo $(/usr/libexec/java_home)`
3. From there, cd into `jre/lib/security`, and replace the local\_policy.jar and US\_export\_policy.jar files found there with the ones in the zip file you downloaded

NOTE that if you are running the tests from Android Studio, it may be using a different version of Java than that in your java home directory. The first line of the test output will usually show the directory from which Android Studio is running Java (Usually `/Applications/Android Studio.app/Contents/jre/jdk/Contents/Home/jre/lib/security`). If it is indeed different, you should follow the steps above for that directory as well.

#### If you experience the following exception when running individual tests from Android Studio Editor on Mac

```
No such manifest file: build/intermediates/bundles/debug/AndroidManifest.xml
```
If you are on a Mac, you will probably need to configure the default JUnit test runner configuration in order to work around a bug where IntelliJ / Android Studio does not set the working directory to the module being tested. This can be accomplished by editing the run configurations, Defaults -> JUnit and changing the working directory value to $MODULE_DIR$

#### Error on attempt to install CommCare app on phone: _Unknown failure during app install_

Android Monitor in Android Studio shows the following exceptions:
```
java.lang.RuntimeException: CommCare ran into an issue deserializing data while inflating type
    ...
Caused by: org.javarosa.core.util.externalizable.DeserializationException:
No datatype registered to serialization code [4b a9 e5 89]
```

Resolution:

- Disable _Instant Run_ found in Settings > Build, Execution, Deployment > Instant Run.
- Maybe also edit ~/.gradle/gradle.properties (may not exist) and add a line like `org.gradle.jvmargs=-Xmx1536M` if the build fails due to OOM or you see a message like the following during the build:

      To run dex in process, the Gradle daemon needs a larger heap.
      It currently has 1024 MB.
      For faster builds, increase the maximum heap size for the Gradle daemon to at least 1536 MB.
      To do this set org.gradle.jvmargs=-Xmx1536M in the project gradle.properties.

- Click _Run 'app'_ to rebuid and run on phone.
