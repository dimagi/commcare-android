# Conditional Firebase Initialization - Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow CommCare Android to build and run without Firebase configuration, so open-source community members don't need proprietary Firebase setup.

**Architecture:** Add a `FIREBASE_ENABLED` BuildConfig flag (auto-detected from presence of `GOOGLE_SERVICES_API_KEY`). Disable `FirebaseInitProvider` via manifest placeholder when false. Gate all Firebase API calls behind a central `FirebaseUtils.isFirebaseEnabled()` check, extending the existing `CrashUtil` gating pattern.

**Tech Stack:** Gradle build config, Android manifest placeholders, Java/Kotlin

**Ticket:** SAAS-19401

---

## File Structure

| File | Action | Responsibility |
|------|--------|---------------|
| `app/build.gradle` | Modify | Add `FIREBASE_ENABLED` BuildConfig field + manifest placeholder |
| `app/AndroidManifest.xml` | Modify | Disable `FirebaseInitProvider` conditionally via placeholder |
| `app/src/org/commcare/utils/FirebaseUtils.java` | Create | Central `isFirebaseEnabled()` gate |
| `app/src/org/commcare/CommCareApplication.java` | Modify | Gate Firebase init in `onCreate()` and `getAnalyticsInstance()` |
| `app/src/org/commcare/utils/CrashUtil.java` | Modify | Use `FirebaseUtils.isFirebaseEnabled()` instead of `BuildConfig.USE_CRASHLYTICS` |
| `app/src/org/commcare/google/services/analytics/FirebaseAnalyticsUtil.java` | Modify | Add Firebase gate to `reportEvent()` and `setUserProperties()` |
| `app/src/org/commcare/utils/FirebaseMessagingUtil.java` | Modify | Gate `verifyToken()` behind Firebase check |
| `app/src/org/commcare/google/services/analytics/CCPerfMonitoring.kt` | Modify | Gate `startTracing()` behind Firebase check |
| `app/src/org/commcare/utils/OtpManager.java` | Modify | Guard `FirebaseAuthService` instantiation |
| `app/src/org/commcare/services/CommCareFirebaseMessagingService.java` | Modify | Early-return when Firebase disabled |
| `app/unit-tests/src/org/commcare/android/tests/firebase/FirebaseUtilsTest.java` | Create | Unit test for gate logic |

---

## Task 1: Add `FIREBASE_ENABLED` build config and manifest placeholder

**Files:**
- Modify: `app/build.gradle:166-196` (properties), `app/build.gradle:300-310` (buildConfigFields), `app/build.gradle:455-491` (buildTypes)

- [ ] **Step 1: Add `FIREBASE_ENABLED` property detection**

In `app/build.gradle`, after the existing `loadProp` calls (~line 196), add auto-detection:

```groovy
FIREBASE_ENABLED = !project.ext.GOOGLE_SERVICES_API_KEY.toString().isEmpty()
```

- [ ] **Step 2: Add BuildConfig field and manifest placeholder**

In the `defaultConfig` block (near line 306 where other BuildConfig fields are defined), add:

```groovy
buildConfigField 'boolean', 'FIREBASE_ENABLED', "${project.ext.FIREBASE_ENABLED}"
manifestPlaceholders["firebaseInitEnabled"] = project.ext.FIREBASE_ENABLED
```

- [ ] **Step 3: Build to verify no errors**

Run: `./gradlew assembleCommcareDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle
git commit -m "build: add FIREBASE_ENABLED BuildConfig flag auto-detected from API key presence"
```

---

## Task 2: Disable `FirebaseInitProvider` conditionally in manifest

**Files:**
- Modify: `app/AndroidManifest.xml`

- [ ] **Step 1: Add provider override to disable FirebaseInitProvider**

In `AndroidManifest.xml`, inside the `<application>` tag, add:

```xml
<provider
    android:name="com.google.firebase.provider.FirebaseInitProvider"
    android:authorities="${applicationId}.firebaseinitprovider"
    android:enabled="${firebaseInitEnabled}"
    android:exported="false"
    tools:replace="android:enabled" />
```

This uses the `firebaseInitEnabled` manifest placeholder from Task 1. When `GOOGLE_SERVICES_API_KEY` is empty, this disables the provider and prevents the crash.

- [ ] **Step 2: Build to verify manifest merging works**

Run: `./gradlew assembleCommcareDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/AndroidManifest.xml
git commit -m "fix: disable FirebaseInitProvider when Firebase is not configured"
```

---

## Task 3: Create central `FirebaseUtils` gate

**Files:**
- Create: `app/src/org/commcare/utils/FirebaseUtils.java`
- Create: `app/unit-tests/src/org/commcare/android/tests/firebase/FirebaseUtilsTest.java`

- [ ] **Step 1: Write the failing test**

Create test file:

```java
package org.commcare.android.tests.firebase;

import static org.junit.Assert.assertEquals;

import org.commcare.BuildConfig;
import org.commcare.utils.FirebaseUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.commcare.CommCareTestApplication;
import org.robolectric.annotation.Config;

@Config(application = CommCareTestApplication.class)
@RunWith(AndroidJUnit4.class)
public class FirebaseUtilsTest {

    @Test
    public void testIsFirebaseEnabled_matchesBuildConfig() {
        assertEquals(BuildConfig.FIREBASE_ENABLED, FirebaseUtils.isFirebaseEnabled());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testCommcareDebug --tests "org.commcare.android.tests.firebase.FirebaseUtilsTest"`
Expected: FAIL -- class not found

- [ ] **Step 3: Write implementation**

Create `app/src/org/commcare/utils/FirebaseUtils.java`:

```java
package org.commcare.utils;

import org.commcare.BuildConfig;

public class FirebaseUtils {

    private FirebaseUtils() {}

    /**
     * Returns whether Firebase services are available and configured.
     * When false, all Firebase API calls must be skipped.
     */
    public static boolean isFirebaseEnabled() {
        return BuildConfig.FIREBASE_ENABLED;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testCommcareDebug --tests "org.commcare.android.tests.firebase.FirebaseUtilsTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/org/commcare/utils/FirebaseUtils.java app/unit-tests/src/org/commcare/android/tests/firebase/FirebaseUtilsTest.java
git commit -m "feat: add FirebaseUtils central gate for conditional Firebase usage"
```

---

## Task 4: Gate Firebase calls in `CommCareApplication`

**Files:**
- Modify: `app/src/org/commcare/CommCareApplication.java:225,228,272,442-451`

- [ ] **Step 1: Gate `CrashUtil.init()`, `FirebasePerformance`, and `FirebaseMessagingUtil.verifyToken()`**

At line 225, wrap `CrashUtil.init()`:
```java
if (FirebaseUtils.isFirebaseEnabled()) {
    CrashUtil.init();
}
```

At line 227-229, update the existing `if (!BuildConfig.DEBUG)` block:
```java
if (FirebaseUtils.isFirebaseEnabled() && !BuildConfig.DEBUG) {
    FirebasePerformance.getInstance().setPerformanceCollectionEnabled(true);
}
```

At line 272, wrap `FirebaseMessagingUtil.verifyToken()`:
```java
if (FirebaseUtils.isFirebaseEnabled()) {
    FirebaseMessagingUtil.verifyToken();
}
```

- [ ] **Step 2: Gate `getAnalyticsInstance()`**

At line 442, add early return:

```java
synchronized public FirebaseAnalytics getAnalyticsInstance() {
    if (!FirebaseUtils.isFirebaseEnabled()) {
        return null;
    }
    if (analyticsInstance == null) {
        analyticsInstance = FirebaseAnalytics.getInstance(this);
    }
    analyticsInstance.setUserId(getUserIdOrNull());
    if (connectJobIdForAnalytics > 0) {
        analyticsInstance.setUserProperty("ccc_job_id", String.valueOf(connectJobIdForAnalytics));
    }
    return analyticsInstance;
}
```

Add `FirebaseUtils` import at the top of the file.

- [ ] **Step 3: Build to verify**

Run: `./gradlew assembleCommcareDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/org/commcare/CommCareApplication.java
git commit -m "feat: gate Firebase init calls in CommCareApplication behind FirebaseUtils"
```

---

## Task 5: Update `CrashUtil` to use `FirebaseUtils`

**Files:**
- Modify: `app/src/org/commcare/utils/CrashUtil.java:20`

- [ ] **Step 1: Replace BuildConfig.USE_CRASHLYTICS with FirebaseUtils gate**

Change line 20 from:
```java
private static final boolean crashlyticsEnabled = BuildConfig.USE_CRASHLYTICS;
```
to:
```java
private static final boolean crashlyticsEnabled = BuildConfig.USE_CRASHLYTICS && FirebaseUtils.isFirebaseEnabled();
```

This preserves the existing debug/release distinction while also respecting the Firebase availability flag.

- [ ] **Step 2: Build and run existing tests**

Run: `./gradlew testCommcareDebug`
Expected: PASS (existing tests should not break)

- [ ] **Step 3: Commit**

```bash
git add app/src/org/commcare/utils/CrashUtil.java
git commit -m "feat: gate CrashUtil behind FirebaseUtils in addition to USE_CRASHLYTICS"
```

---

## Task 6: Gate `FirebaseAnalyticsUtil`

**Files:**
- Modify: `app/src/org/commcare/google/services/analytics/FirebaseAnalyticsUtil.java:413`

- [ ] **Step 1: Add Firebase gate to `analyticsDisabled()`**

The `analyticsDisabled()` method (line 413) is the central guard for all analytics reporting. Update it:

```java
private static boolean analyticsDisabled() {
    return !FirebaseUtils.isFirebaseEnabled() || !MainConfigurablePreferences.isAnalyticsEnabled();
}
```

Add import for `FirebaseUtils` at the top of the file.

This gates all ~80 reporting methods that funnel through `reportEvent()`.

- [ ] **Step 2: Gate `setUserProperties()`**

The `setUserProperties()` method (around line 90) is called directly from `getAnalyticsInstance()`. Since `getAnalyticsInstance()` now returns `null` when Firebase is disabled (Task 4), callers already need null safety. But add an explicit guard for safety:

Check that `setUserProperties` and any method calling `getAnalyticsInstance()` directly handles null. The `reportEvent` method at ~line 80 calls `getAnalyticsInstance().logEvent()` -- add a null check:

```java
FirebaseAnalytics analytics = CommCareApplication.instance().getAnalyticsInstance();
if (analytics != null) {
    analytics.logEvent(eventName, params);
}
```

- [ ] **Step 3: Build to verify**

Run: `./gradlew assembleCommcareDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/org/commcare/google/services/analytics/FirebaseAnalyticsUtil.java
git commit -m "feat: gate FirebaseAnalyticsUtil behind FirebaseUtils"
```

---

## Task 7: Gate `FirebaseMessagingUtil`

**Files:**
- Modify: `app/src/org/commcare/utils/FirebaseMessagingUtil.java:99-103`

- [ ] **Step 1: Add Firebase gate to `verifyToken()`**

Update the `verifyToken()` method:

```java
public static void verifyToken() {
    if (!FirebaseUtils.isFirebaseEnabled()) {
        return;
    }
    if (!BuildConfig.DEBUG) {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(handleFCMTokenRetrieval());
    }
}
```

Add import for `FirebaseUtils`.

- [ ] **Step 2: Build to verify**

Run: `./gradlew assembleCommcareDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/org/commcare/utils/FirebaseMessagingUtil.java
git commit -m "feat: gate FirebaseMessagingUtil.verifyToken behind FirebaseUtils"
```

---

## Task 8: Gate `CCPerfMonitoring`

**Files:**
- Modify: `app/src/org/commcare/google/services/analytics/CCPerfMonitoring.kt:36-38`

- [ ] **Step 1: Add Firebase gate to `startTracing()`**

Update `startTracing()`:

```kotlin
fun startTracing(traceName: String): Trace? {
    if (!FirebaseUtils.isFirebaseEnabled()) {
        return null
    }
    try {
        val trace = FirebasePerformance.getInstance().newTrace(traceName)
        // ... rest unchanged
```

Add import for `FirebaseUtils`.

- [ ] **Step 2: Build to verify**

Run: `./gradlew assembleCommcareDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/org/commcare/google/services/analytics/CCPerfMonitoring.kt
git commit -m "feat: gate CCPerfMonitoring behind FirebaseUtils"
```

---

## Task 9: Guard `FirebaseAuthService` usage in `OtpManager`

**Files:**
- Modify: `app/src/org/commcare/utils/OtpManager.java:31`

- [ ] **Step 1: Guard FirebaseAuthService instantiation**

`FirebaseAuthService` is only created in `OtpManager` constructor (line 31). The `FirebaseAuth.getInstance()` call in its constructor will crash if Firebase isn't initialized. Update `OtpManager`:

```java
} else {
    if (!FirebaseUtils.isFirebaseEnabled()) {
        Logger.log(LogTypes.TYPE_WARNING_NETWORK,
                "Firebase Auth not available - Firebase is not configured");
        otpCallback.onVerificationFailed("Firebase is not configured. OTP via Firebase is unavailable.");
        return;
    }
    authService = new FirebaseAuthService(activity, personalIdSessionData, otpCallback);
}
```

Add imports for `FirebaseUtils` and `Logger`.

- [ ] **Step 2: Build to verify**

Run: `./gradlew assembleCommcareDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/org/commcare/utils/OtpManager.java
git commit -m "feat: guard FirebaseAuthService instantiation when Firebase is unavailable"
```

---

## Task 10: Guard `CommCareFirebaseMessagingService`

**Files:**
- Modify: `app/src/org/commcare/services/CommCareFirebaseMessagingService.java:36,61`

- [ ] **Step 1: Add early return to message handlers**

This service extends `FirebaseMessagingService` and is registered in the manifest. When Firebase is disabled, the service won't receive messages (since FCM isn't initialized), but add defensive guards:

In `onMessageReceived()` (line 36):
```java
@Override
public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
    if (!FirebaseUtils.isFirebaseEnabled()) {
        return;
    }
    // ... rest unchanged
```

In `onNewToken()` (line 61):
```java
@Override
public void onNewToken(@NonNull String token) {
    if (!FirebaseUtils.isFirebaseEnabled()) {
        return;
    }
    // ... rest unchanged
```

Add import for `FirebaseUtils`.

- [ ] **Step 2: Build to verify**

Run: `./gradlew assembleCommcareDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/org/commcare/services/CommCareFirebaseMessagingService.java
git commit -m "feat: guard CommCareFirebaseMessagingService when Firebase is disabled"
```

---

## Task 11: Final verification -- build and test without Firebase config

- [ ] **Step 1: Run full unit test suite**

Run: `./gradlew testCommcareDebug`
Expected: All tests PASS

- [ ] **Step 2: Verify build succeeds without API key**

Temporarily ensure `GOOGLE_SERVICES_API_KEY` is empty (or not set) in `~/.gradle/gradle.properties`, then:

Run: `./gradlew assembleCommcareDebug`
Expected: BUILD SUCCESSFUL with `FIREBASE_ENABLED=false`

- [ ] **Step 3: Verify build succeeds with API key**

Restore `GOOGLE_SERVICES_API_KEY` in `~/.gradle/gradle.properties`, then:

Run: `./gradlew assembleCommcareDebug`
Expected: BUILD SUCCESSFUL with `FIREBASE_ENABLED=true`

- [ ] **Step 4: Final commit (if any fixups needed)**

```bash
git commit -m "feat: conditional Firebase initialization for open-source builds (SAAS-19401)"
```
