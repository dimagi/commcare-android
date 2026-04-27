# Add Email to PersonalID Signup / Recovery Flow — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Insert an optional email-entry + OTP-verification step into the PersonalID signup and recovery flows, positioned after Backup Code. Also offer a non-blocking email-collection prompt to existing users who completed signup without an email.

**Architecture:** New Kotlin fragments (`PersonalIdEmailFragment`, `PersonalIdEmailVerificationFragment`) extend the existing `BasePersonalIdFragment` pattern. Both fragments accept an `isRecovery` nav arg (in addition to the existing `isLegacyFlow` arg). `PersonalIdBackupCodeFragment` stops calling `handleSuccessfulRecovery()` / `navigateToPhoto()` directly after backup-code validation — in both modes it stores the backup code on `PersonalIdSessionData` and navigates to the Email fragment (passing `isRecovery`). The Email/Email-OTP fragments branch on exit: signup continues to Photo Capture (which writes the record's email field from session data); recovery invokes a new `PersonalIdRecoveryCompleter` helper that runs the original recovery-finalization logic (DB passphrase, write record with email, recovery analytics, second-device notification) and then navigates to the existing recovery-success message destination. **Persistence split:** only `email` is added to `ConnectUserRecord` (new DB column in v25). The other three pieces of state — `emailVerified`, `emailOfferCount`, `lastEmailOfferDate` — live in a dedicated `personalid_prefs` SharedPreferences file accessed through a new `PersonalIDPreferences` Kotlin object. All three have null-aware semantics (absent key = "never set") and are wiped together on logout. Existing users without a verified email are handled by the legacy prompt (two-offer-with-30-day-gap) read/written via `PersonalIDPreferences`.

**Tech Stack:** Kotlin (new files), Java (existing files modified), AndroidX Navigation Safe Args, Retrofit 2 / OkHttp, SQLCipher (versioned migration), Robolectric unit tests.

---

## File Structure

### Files to Create
| File | Purpose |
|------|---------|
| `app/src/org/commcare/android/database/connect/models/ConnectUserRecordV24.java` | Snapshot of v24 ConnectUserRecord (for DB migration) |
| `app/src/org/commcare/fragments/personalId/PersonalIdEmailFragment.kt` | Email entry UI fragment |
| `app/src/org/commcare/fragments/personalId/PersonalIdEmailVerificationFragment.kt` | Email OTP verification UI fragment |
| `app/res/layout/fragment_personalid_email.xml` | Layout for email entry screen |
| `app/res/layout/fragment_personalid_email_verification.xml` | Layout for email OTP screen |
| `app/src/org/commcare/connect/network/connectId/parser/SendEmailOtpResponseParser.kt` | Parses send-email-OTP server response |
| `app/src/org/commcare/connect/network/connectId/parser/VerifyEmailOtpResponseParser.kt` | Parses verify-email-OTP server response |
| `app/src/org/commcare/connect/PersonalIdRecoveryCompleter.kt` | Helper holding the account-recovery finalization extracted from `PersonalIdBackupCodeFragment` (DB passphrase, write record, analytics, second-device notification) |
| `app/src/org/commcare/connect/PersonalIDPreferences.kt` | SharedPreferences wrapper holding `emailVerified`, `emailOfferCount`, `lastEmailOfferDate` with null-aware getters/setters and `clear()` for logout |
| `app/unit-tests/src/org/commcare/connect/PersonalIdEmailOfferTest.kt` | Unit tests for evaluateEmailOffer logic (Robolectric-backed, since prefs need a Context) |
| `app/unit-tests/src/org/commcare/connect/PersonalIDPreferencesTest.kt` | Unit tests for the prefs wrapper (null semantics, clear, round-trip) |
| `app/unit-tests/src/org/commcare/connect/PersonalIdEmailValidationTest.kt` | Unit tests for client-side email format validation |

### Files to Modify
| File | Change |
|------|--------|
| `app/src/org/commcare/android/database/connect/models/ConnectUserRecord.java` | Add `email` field only (the other three flags live in `PersonalIDPreferences`) |
| `app/src/org/commcare/android/database/connect/models/PersonalIdSessionData.kt` | Add email and emailVerified fields |
| `app/src/org/commcare/fragments/personalId/PersonalIdPhotoCaptureFragment.java` | Read email/emailVerified from session data when constructing ConnectUserRecord (signup path only) |
| `app/src/org/commcare/fragments/personalId/PersonalIdBackupCodeFragment.java` | In both signup and recovery modes, store the backup code on session data and navigate to Email (passing `isRecovery`) instead of calling `navigateToPhoto()` / `handleSuccessfulRecovery()` directly. Extract the recovery-finalization body into `PersonalIdRecoveryCompleter`. |
| `app/src/org/commcare/models/database/connect/DatabaseConnectOpenHelper.java` | Bump CONNECT_DB_VERSION to 25 |
| `app/src/org/commcare/models/database/connect/ConnectDatabaseUpgrader.java` | Add upgradeTwentyFourTwentyFive() |
| `app/src/org/commcare/connect/network/ApiEndPoints.java` | Add sendEmailOtp and verifyEmailOtp endpoints |
| `app/src/org/commcare/connect/network/ApiService.java` | Add sendEmailOtp and verifyEmailOtp service methods |
| `app/src/org/commcare/connect/network/ApiPersonalId.java` | Add sendEmailOtp() and verifyEmailOtp() static methods |
| `app/src/org/commcare/connect/network/connectId/PersonalIdApiHandler.java` | Add sendEmailOtpCall() and verifyEmailOtpCall() |
| `app/res/navigation/nav_graph_personalid.xml` | Add email and email-OTP destinations (with `isLegacyFlow` and `isRecovery` args); reroute BackupCode → Email (both modes); add Email/Email-OTP → PhotoCapture (signup) and Email/Email-OTP → MessageDisplay (recovery success) |
| `app/src/org/commcare/activities/connect/PersonalIdActivity.java` | Handle EXTRA_LEGACY_EMAIL_FLOW intent extra |
| `app/src/org/commcare/connect/PersonalIdManager.java` | Add checkEmailCollection() and shouldOfferEmail() — both read/write via `PersonalIDPreferences`. Also call `PersonalIDPreferences.clear(context)` in the PersonalID logout path (see Task 8b). |
| `app/src/org/commcare/activities/LoginActivity.java` | Call checkEmailCollection() after PersonalID signup/login returns (post-signup path only) |
| `app/src/org/commcare/activities/StandardHomeActivity.java` | Call checkEmailCollection() in `onCreate` (legacy-user path — fires on every CommCare app open) |

---

## Chunk 1: Database Layer

### Task 1: Create ConnectUserRecordV24 (migration snapshot)

**Files:**
- Create: `app/src/org/commcare/android/database/connect/models/ConnectUserRecordV24.java`

- [ ] **Step 1.1: Write the failing unit test**

  Create `app/unit-tests/src/org/commcare/connect/ConnectUserRecordMigrationV24Test.kt`:

  ```kotlin
  package org.commcare.connect

  import org.junit.Assert.assertNull
  import org.junit.Test

  class ConnectUserRecordMigrationV24Test {

      @Test
      fun `fromV24 copies all fields and sets email to null`() {
          val old = ConnectUserRecordV24().apply {
              // Verify V24 compiles with fields 1-16 only (no email fields)
          }
          val new = ConnectUserRecord.fromV24(old)
          assertNull(new.email)
      }
  }
  ```

  > **Note:** `emailVerified`, `emailOfferCount`, and `lastEmailOfferDate` are NOT fields on `ConnectUserRecord` — they live in `PersonalIDPreferences` (added in Task 3b) and are not touched by this migration.

  Run: `./gradlew testCommcareDebug --tests "org.commcare.connect.ConnectUserRecordMigrationV24Test"`
  Expected: FAIL — `ConnectUserRecordV24` and `ConnectUserRecord.fromV24` don't exist yet.

- [ ] **Step 1.2: Create ConnectUserRecordV24.java**

  This is a verbatim copy of the current `ConnectUserRecord.java` with the class name changed and `STORAGE_KEY` kept. It will only ever be read, not written.

  ```java
  package org.commcare.android.database.connect.models;

  import org.commcare.android.storage.framework.Persisted;
  import org.commcare.connect.ConnectConstants;
  import org.commcare.models.framework.Persisting;
  import org.commcare.modern.database.Table;
  import org.commcare.modern.models.MetaField;
  import java.util.Date;

  @Table(ConnectUserRecordV24.STORAGE_KEY)
  public class ConnectUserRecordV24 extends Persisted {
      public static final String STORAGE_KEY = "user_info";
      public static final String META_PIN = "pin";

      @Persisting(1) private String userId;
      @Persisting(2) private String password;
      @Persisting(3) private String name;
      @Persisting(4) private String primaryPhone;
      @Deprecated @Persisting(5) private String alternatePhone;
      @Persisting(6) private int registrationPhase;
      @Persisting(7) private Date lastPasswordDate;
      @Persisting(value = 8, nullable = true) private String connectToken;
      @Persisting(value = 9, nullable = true) private Date connectTokenExpiration;
      @Persisting(value = 10, nullable = true) @MetaField(META_PIN) private String pin;
      @Deprecated @Persisting(11) private boolean secondaryPhoneVerified;
      @Deprecated @Persisting(12) private Date verifySecondaryPhoneByDate;
      @Persisting(value = 13, nullable = true) private String photo;
      @Persisting(value = 14) private boolean isDemo;
      @Persisting(value = 15) private String requiredLock = PersonalIdSessionData.PIN;
      @Persisting(value = 16) private boolean hasConnectAccess;

      public ConnectUserRecordV24() {
          registrationPhase = ConnectConstants.PERSONALID_NO_ACTIVITY;
          lastPasswordDate = new Date();
          connectTokenExpiration = new Date();
          secondaryPhoneVerified = true;
          verifySecondaryPhoneByDate = new Date();
          alternatePhone = "";
      }

      // Getters for all fields (copy from ConnectUserRecord)
      public String getUserId() { return userId; }
      public String getPassword() { return password; }
      public String getName() { return name; }
      public String getPrimaryPhone() { return primaryPhone; }
      public int getRegistrationPhase() { return registrationPhase; }
      public Date getLastPasswordDate() { return lastPasswordDate; }
      public String getConnectToken() { return connectToken; }
      public Date getConnectTokenExpiration() { return connectTokenExpiration; }
      public String getPin() { return pin; }
      public String getPhoto() { return photo; }
      public boolean isDemo() { return isDemo; }
      public String getRequiredLock() { return requiredLock; }
      public boolean hasConnectAccess() { return hasConnectAccess; }
  }
  ```

- [ ] **Step 1.3: Add email field and fromV24() to ConnectUserRecord.java**

  In `ConnectUserRecord.java`, after field `@Persisting(value = 16)`:

  ```java
  public static final String META_EMAIL = "email";

  @Persisting(value = 17, nullable = true)
  @MetaField(META_EMAIL)
  private String email;
  ```

  Add getter and setter:

  ```java
  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }
  ```

  > **Note:** `emailVerified`, `emailOfferCount`, and `lastEmailOfferDate` are NOT fields on `ConnectUserRecord`. They live in `PersonalIDPreferences` (Task 3b) — do not add them here.

  Add the migration factory method:

  ```java
  public static ConnectUserRecord fromV24(ConnectUserRecordV24 oldRecord) {
      ConnectUserRecord newRecord = new ConnectUserRecord();
      newRecord.userId = oldRecord.getUserId();
      newRecord.password = oldRecord.getPassword();
      newRecord.name = oldRecord.getName();
      newRecord.primaryPhone = oldRecord.getPrimaryPhone();
      newRecord.alternatePhone = "";
      newRecord.registrationPhase = oldRecord.getRegistrationPhase();
      newRecord.lastPasswordDate = oldRecord.getLastPasswordDate();
      newRecord.connectToken = oldRecord.getConnectToken();
      newRecord.connectTokenExpiration = oldRecord.getConnectTokenExpiration();
      newRecord.secondaryPhoneVerified = true;
      newRecord.photo = oldRecord.getPhoto();
      newRecord.isDemo = oldRecord.isDemo();
      newRecord.requiredLock = oldRecord.getRequiredLock();
      newRecord.hasConnectAccess = oldRecord.hasConnectAccess();
      // email defaults to null (the other flags live in PersonalIDPreferences, not this record)
      return newRecord;
  }
  ```

- [ ] **Step 1.4: Run the test to verify it passes**

  Run: `./gradlew testCommcareDebug --tests "org.commcare.connect.ConnectUserRecordMigrationV24Test"`
  Expected: PASS

- [ ] **Step 1.5: Commit**

  ```bash
  git add app/src/org/commcare/android/database/connect/models/ConnectUserRecordV24.java \
          app/src/org/commcare/android/database/connect/models/ConnectUserRecord.java \
          app/unit-tests/src/org/commcare/connect/ConnectUserRecordMigrationV24Test.kt
  git commit -m "[AI] Add email field to ConnectUserRecord with V24 snapshot for migration"
  ```

---

### Task 2: Add DB migration v24 → v25

**Files:**
- Modify: `app/src/org/commcare/models/database/connect/DatabaseConnectOpenHelper.java`
- Modify: `app/src/org/commcare/models/database/connect/ConnectDatabaseUpgrader.java`

- [ ] **Step 2.1: Bump CONNECT_DB_VERSION to 25**

  In `DatabaseConnectOpenHelper.java`, line 70:

  ```java
  // Before:
  private static final int CONNECT_DB_VERSION = 24;

  // After:
  private static final int CONNECT_DB_VERSION = 25;
  ```

  Also add a version comment in the header block:
  ```java
  // V.25 - Added email column to ConnectUserRecord (emailVerified / offerCount / lastOfferDate live in PersonalIDPreferences, not the DB)
  ```

- [ ] **Step 2.2: Add import for ConnectUserRecordV24 in ConnectDatabaseUpgrader.java**

  In `ConnectDatabaseUpgrader.java`, in the imports block, add:
  ```java
  import org.commcare.android.database.connect.models.ConnectUserRecordV24;
  ```

- [ ] **Step 2.3: Add upgradeTwentyFourTwentyFive() to ConnectDatabaseUpgrader.java**

  First, add the call in the `upgrade()` method after the existing v23→v24 block:

  ```java
  if (oldVersion == 24) {
      upgradeTwentyFourTwentyFive(db);
      oldVersion = 25;
  }
  ```

  Then add the method:

  ```java
  private void upgradeTwentyFourTwentyFive(IDatabase db) {
      db.beginTransaction();
      try {
          DbUtil.addColumnToTable(db, ConnectUserRecord.STORAGE_KEY,
                  ConnectUserRecord.META_EMAIL, "TEXT");

          // Migrate existing records: read V24, write new ConnectUserRecord
          SqlStorage<ConnectUserRecordV24> oldStorage = new SqlStorage<>(
                  ConnectUserRecordV24.STORAGE_KEY, ConnectUserRecordV24.class,
                  new ConcreteAndroidDbHelper(c, db));
          SqlStorage<ConnectUserRecord> newStorage = new SqlStorage<>(
                  ConnectUserRecord.STORAGE_KEY, ConnectUserRecord.class,
                  new ConcreteAndroidDbHelper(c, db));

          for (ConnectUserRecordV24 old : oldStorage) {
              ConnectUserRecord newRecord = ConnectUserRecord.fromV24(old);
              newRecord.setID(old.getID());
              newStorage.write(newRecord);
          }
          db.setTransactionSuccessful();
      } catch (Exception e) {
          CrashUtil.reportException(e);
      } finally {
          db.endTransaction();
      }
  }
  ```

  > **Note:** Check how `DbUtil.addColumnToTable(IDatabase, String, String, String)` is called in other migration methods in this file and match the exact signature. Some versions take the db object directly; others call it differently.

- [ ] **Step 2.4: Build to verify compilation**

  Run: `./gradlew assembleCommcareDebug`
  Expected: BUILD SUCCESSFUL

- [ ] **Step 2.5: Commit**

  ```bash
  git add app/src/org/commcare/models/database/connect/DatabaseConnectOpenHelper.java \
          app/src/org/commcare/models/database/connect/ConnectDatabaseUpgrader.java
  git commit -m "[AI] Add DB migration v24→v25 adding email column to ConnectUserRecord"
  ```

---

## Chunk 2: Session Data & API Layer

### Task 3: Add email to PersonalIdSessionData

**Files:**
- Modify: `app/src/org/commcare/android/database/connect/models/PersonalIdSessionData.kt`

- [ ] **Step 3.1: Add email property**

  Open `PersonalIdSessionData.kt` and add to the data class:

  ```kotlin
  var email: String? = null
  var emailVerified: Boolean = false
  var emailSkippedDuringSignup: Boolean = false
  ```

  Add all three alongside the existing fields (e.g., after `userName`).

  > **Why these live in session data:** Neither signup nor recovery has written `ConnectUserRecord` by the time the user acts on the email screen. In signup, `PersonalIdPhotoCaptureFragment` writes the record later. In recovery, `PersonalIdBackupCodeFragment` used to write it immediately on successful backup-code validation, but in the new flow that write is deferred — it now happens in `PersonalIdRecoveryCompleter` after the email step. Session data is the carrier in both cases. (Backup code is also now stored on session data so `PersonalIdRecoveryCompleter` can read it after the email step.)

  > **Why `emailSkippedDuringSignup`:** A user who explicitly skips email during signup has already been presented the offer once. If `emailOfferCount` (in `PersonalIDPreferences`) were left at its default-absent value, `shouldOfferEmail()` would show the dialog again on the very next login, immediately after they just declined during signup. When this flag is set, the PhotoCapture / RecoveryCompleter step writes `emailOfferCount = 1` and `lastEmailOfferDate = now` into `PersonalIDPreferences`, treating the signup screen as the first offer so the dialog only appears 30 days later (second and final offer). Legacy users migrated from v24 have never-set prefs keys (i.e. `emailOfferCount` reads as `null`), which `shouldOfferEmail()` treats as "never offered" — their first dialog appears on the next login as intended.
  >
  > **Recovery-skip behaviour:** The same rule applies when the user skips email during the recovery flow — `PersonalIdRecoveryCompleter` reads `emailSkippedDuringSignup` and writes `emailOfferCount = 1` + `lastEmailOfferDate = now` into `PersonalIDPreferences` after finalizing recovery. Recovery-skip is product-equivalent to signup-skip (the user was shown the offer once and declined), so the legacy prompt should not re-fire for 30 days. Confirm with product that this is the desired behaviour during recovery before shipping; if it is not, remove the `emailSkippedDuringSignup` handling from `PersonalIdRecoveryCompleter.finalizeAccountRecovery()` (Task 6c.2) and update the manual-test expectation in Step 9.3 case 3b accordingly.

- [ ] **Step 3.2: Build to verify compilation**

  Run: `./gradlew assembleCommcareDebug`
  Expected: BUILD SUCCESSFUL

- [ ] **Step 3.3: Commit**

  ```bash
  git add app/src/org/commcare/android/database/connect/models/PersonalIdSessionData.kt
  git commit -m "[AI] Add email field to PersonalIdSessionData"
  ```

---

### Task 3b: Create PersonalIDPreferences (SharedPreferences wrapper for the three email flags)

Only `email` is persisted on `ConnectUserRecord`. The three supporting flags — `emailVerified`, `emailOfferCount`, `lastEmailOfferDate` — live in a dedicated `personalid_prefs` SharedPreferences file accessed through a Kotlin `object` wrapper. Tasks 6c, 6c.2, 8, and 8b all depend on this class, so it must exist before they run.

**Files:**
- Create: `app/src/org/commcare/connect/PersonalIDPreferences.kt`
- Create: `app/unit-tests/src/org/commcare/connect/PersonalIDPreferencesTest.kt`

- [ ] **Step 3b.1: Write the failing unit test**

  Create `app/unit-tests/src/org/commcare/connect/PersonalIDPreferencesTest.kt`:

  ```kotlin
  package org.commcare.connect

  import androidx.test.core.app.ApplicationProvider
  import org.junit.After
  import org.junit.Assert.assertEquals
  import org.junit.Assert.assertNull
  import org.junit.Test
  import org.junit.runner.RunWith
  import org.robolectric.RobolectricTestRunner
  import java.util.Date

  @RunWith(RobolectricTestRunner::class)
  class PersonalIDPreferencesTest {

      private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

      @After
      fun tearDown() {
          PersonalIDPreferences.clear(context)
      }

      @Test
      fun `all getters return null when prefs are empty`() {
          assertNull(PersonalIDPreferences.isEmailVerified(context))
          assertNull(PersonalIDPreferences.getEmailOfferCount(context))
          assertNull(PersonalIDPreferences.getLastEmailOfferDate(context))
      }

      @Test
      fun `round-trips typed values`() {
          val when_ = Date()
          PersonalIDPreferences.setEmailVerified(context, true)
          PersonalIDPreferences.setEmailOfferCount(context, 2)
          PersonalIDPreferences.setLastEmailOfferDate(context, when_)

          assertEquals(true, PersonalIDPreferences.isEmailVerified(context))
          assertEquals(2, PersonalIDPreferences.getEmailOfferCount(context))
          assertEquals(when_.time, PersonalIDPreferences.getLastEmailOfferDate(context)?.time)
      }

      @Test
      fun `setter with null removes the key`() {
          PersonalIDPreferences.setEmailVerified(context, true)
          PersonalIDPreferences.setEmailVerified(context, null)
          assertNull(PersonalIDPreferences.isEmailVerified(context))
      }

      @Test
      fun `clear removes all keys`() {
          PersonalIDPreferences.setEmailVerified(context, true)
          PersonalIDPreferences.setEmailOfferCount(context, 1)
          PersonalIDPreferences.setLastEmailOfferDate(context, Date())

          PersonalIDPreferences.clear(context)

          assertNull(PersonalIDPreferences.isEmailVerified(context))
          assertNull(PersonalIDPreferences.getEmailOfferCount(context))
          assertNull(PersonalIDPreferences.getLastEmailOfferDate(context))
      }

      @Test
      fun `explicitly-stored false is distinguishable from unset`() {
          PersonalIDPreferences.setEmailVerified(context, false)
          // contains() must be true, so getter returns false (not null)
          assertEquals(false, PersonalIDPreferences.isEmailVerified(context))
      }
  }
  ```

  Run: `./gradlew testCommcareDebug --tests "org.commcare.connect.PersonalIDPreferencesTest"`
  Expected: FAIL — `PersonalIDPreferences` does not exist yet.

- [ ] **Step 3b.2: Create PersonalIDPreferences.kt**

  Create `app/src/org/commcare/connect/PersonalIDPreferences.kt`:

  ```kotlin
  package org.commcare.connect

  import android.content.Context
  import android.content.SharedPreferences
  import java.util.Date

  /**
   * SharedPreferences-backed store for the three flags that accompany `ConnectUserRecord.email`:
   *   - emailVerified (Boolean)
   *   - emailOfferCount (Int) — 0 = never offered, 1 = first offer shown, 2 = both offers shown
   *   - lastEmailOfferDate (Date) — when the most recent offer was shown
   *
   * Null-aware: an absent key returns null (not a false/0/0L default). Callers must handle null
   * (typically by treating it as "never set", equivalent to a freshly-migrated v24 user).
   *
   * On PersonalID logout, call [clear] to wipe every key in this prefs file.
   */
  object PersonalIDPreferences {

      private const val PREFS_NAME = "personalid_prefs"
      private const val KEY_EMAIL_VERIFIED = "email_verified"
      private const val KEY_EMAIL_OFFER_COUNT = "email_offer_count"
      private const val KEY_LAST_EMAIL_OFFER_DATE = "last_email_offer_date"

      private fun prefs(context: Context): SharedPreferences =
          context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

      @JvmStatic
      fun isEmailVerified(context: Context): Boolean? {
          val p = prefs(context)
          return if (p.contains(KEY_EMAIL_VERIFIED)) p.getBoolean(KEY_EMAIL_VERIFIED, false) else null
      }

      @JvmStatic
      fun setEmailVerified(context: Context, value: Boolean?) {
          prefs(context).edit().apply {
              if (value == null) remove(KEY_EMAIL_VERIFIED) else putBoolean(KEY_EMAIL_VERIFIED, value)
          }.apply()
      }

      @JvmStatic
      fun getEmailOfferCount(context: Context): Int? {
          val p = prefs(context)
          return if (p.contains(KEY_EMAIL_OFFER_COUNT)) p.getInt(KEY_EMAIL_OFFER_COUNT, 0) else null
      }

      @JvmStatic
      fun setEmailOfferCount(context: Context, value: Int?) {
          prefs(context).edit().apply {
              if (value == null) remove(KEY_EMAIL_OFFER_COUNT) else putInt(KEY_EMAIL_OFFER_COUNT, value)
          }.apply()
      }

      @JvmStatic
      fun getLastEmailOfferDate(context: Context): Date? {
          val p = prefs(context)
          return if (p.contains(KEY_LAST_EMAIL_OFFER_DATE)) Date(p.getLong(KEY_LAST_EMAIL_OFFER_DATE, 0L)) else null
      }

      @JvmStatic
      fun setLastEmailOfferDate(context: Context, value: Date?) {
          prefs(context).edit().apply {
              if (value == null) remove(KEY_LAST_EMAIL_OFFER_DATE) else putLong(KEY_LAST_EMAIL_OFFER_DATE, value.time)
          }.apply()
      }

      /** Remove every PersonalID preference. Call on logout. */
      @JvmStatic
      fun clear(context: Context) {
          prefs(context).edit().clear().apply()
      }
  }
  ```

  > **Why a dedicated prefs file (`personalid_prefs`) instead of `HiddenPreferences` or app prefs:** `clear()` does `edit().clear().apply()`, which only wipes keys in the file we own. Sharing app-level prefs would force enumerating each key individually and risk deleting unrelated state at logout.

- [ ] **Step 3b.3: Run the tests to verify they pass**

  Run: `./gradlew testCommcareDebug --tests "org.commcare.connect.PersonalIDPreferencesTest"`
  Expected: PASS (all 5 tests).

- [ ] **Step 3b.4: Commit**

  ```bash
  git add app/src/org/commcare/connect/PersonalIDPreferences.kt \
          app/unit-tests/src/org/commcare/connect/PersonalIDPreferencesTest.kt
  git commit -m "[AI] Add PersonalIDPreferences wrapper for emailVerified/offerCount/lastOfferDate"
  ```

---

### Task 4: Add email API endpoints

> **Important:** The server-side endpoints `/users/send_email_otp` and `/users/verify_email_otp` are provisional. Confirm actual endpoint paths with the backend team before finalising. The parameter names `email` and `otp` are also provisional.

**Files:**
- Modify: `app/src/org/commcare/connect/network/ApiEndPoints.java`
- Modify: `app/src/org/commcare/connect/network/ApiService.java`
- Modify: `app/src/org/commcare/connect/network/ApiPersonalId.java`
- Modify: `app/src/org/commcare/connect/network/connectId/PersonalIdApiHandler.java`
- Create: `app/src/org/commcare/connect/network/connectId/parser/SendEmailOtpResponseParser.kt`
- Create: `app/src/org/commcare/connect/network/connectId/parser/VerifyEmailOtpResponseParser.kt`

- [ ] **Step 4.1: Write failing unit test for evaluateEmailOffer**

  Create `app/unit-tests/src/org/commcare/connect/PersonalIdEmailOfferTest.kt`:

  ```kotlin
  package org.commcare.connect

  import android.content.Context
  import androidx.test.core.app.ApplicationProvider
  import org.junit.After
  import org.junit.Assert.assertFalse
  import org.junit.Assert.assertTrue
  import org.junit.Test
  import org.junit.runner.RunWith
  import org.robolectric.RobolectricTestRunner
  import java.util.Calendar
  import java.util.Date

  @RunWith(RobolectricTestRunner::class)
  class PersonalIdEmailOfferTest {

      private val context: Context get() = ApplicationProvider.getApplicationContext()

      @After
      fun tearDown() {
          PersonalIDPreferences.clear(context)
      }

      private fun dateMinusDays(days: Int): Date =
          Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -days) }.time

      @Test
      fun `should offer when never shown before (prefs empty)`() {
          // emailVerified=null, offerCount=null, lastOfferDate=null — a legacy v24 user.
          assertTrue(PersonalIdManager.shouldOfferEmail(context))
      }

      @Test
      fun `should not offer when both offers already shown (count=2)`() {
          PersonalIDPreferences.setEmailVerified(context, false)
          PersonalIDPreferences.setEmailOfferCount(context, 2)
          PersonalIDPreferences.setLastEmailOfferDate(context, dateMinusDays(5))
          assertFalse(PersonalIdManager.shouldOfferEmail(context))
      }

      @Test
      fun `should offer second time after 30 days (count=1, date old)`() {
          PersonalIDPreferences.setEmailVerified(context, false)
          PersonalIDPreferences.setEmailOfferCount(context, 1)
          PersonalIDPreferences.setLastEmailOfferDate(context, dateMinusDays(31))
          assertTrue(PersonalIdManager.shouldOfferEmail(context))
      }

      @Test
      fun `should not offer second time before 30 days (count=1, date recent)`() {
          PersonalIDPreferences.setEmailVerified(context, false)
          PersonalIDPreferences.setEmailOfferCount(context, 1)
          PersonalIDPreferences.setLastEmailOfferDate(context, dateMinusDays(15))
          assertFalse(PersonalIdManager.shouldOfferEmail(context))
      }

      @Test
      fun `should not offer when email already verified`() {
          PersonalIDPreferences.setEmailVerified(context, true)
          assertFalse(PersonalIdManager.shouldOfferEmail(context))
      }

      @Test
      fun `should not offer immediately after signup skip (count=1, date just set)`() {
          // Simulates a user who skipped email during signup: count=1, date=now
          PersonalIDPreferences.setEmailVerified(context, false)
          PersonalIDPreferences.setEmailOfferCount(context, 1)
          PersonalIDPreferences.setLastEmailOfferDate(context, Date())
          assertFalse(PersonalIdManager.shouldOfferEmail(context))
      }
  }
  ```

  Run: `./gradlew testCommcareDebug --tests "org.commcare.connect.PersonalIdEmailOfferTest"`
  Expected: FAIL — `PersonalIdManager.shouldOfferEmail(Context)` does not exist yet.

- [ ] **Step 4.2: Add email API endpoints to ApiEndPoints.java**

  ```java
  public static final String sendEmailOtp = "/users/send_email_otp";
  public static final String verifyEmailOtp = "/users/verify_email_otp";
  ```

- [ ] **Step 4.3: Add email service methods to ApiService.java**

  ```java
  @POST(ApiEndPoints.sendEmailOtp)
  Call<ResponseBody> sendEmailOtp(@Header("Authorization") String token,
                                   @Body Map<String, String> emailRequest);

  @POST(ApiEndPoints.verifyEmailOtp)
  Call<ResponseBody> verifyEmailOtp(@Header("Authorization") String token,
                                     @Body Map<String, String> otpRequest);
  ```

- [ ] **Step 4.4: Create SendEmailOtpResponseParser.kt**

  ```kotlin
  package org.commcare.connect.network.connectId.parser

  import org.commcare.android.database.connect.models.PersonalIdSessionData
  import org.json.JSONObject

  class SendEmailOtpResponseParser : PersonalIdApiResponseParser {
      override fun parse(json: JSONObject, sessionData: PersonalIdSessionData) {
          // Server may return additional info; extend as needed when API is finalised.
      }
  }
  ```

- [ ] **Step 4.5: Create VerifyEmailOtpResponseParser.kt**

  ```kotlin
  package org.commcare.connect.network.connectId.parser

  import org.commcare.android.database.connect.models.PersonalIdSessionData
  import org.json.JSONObject

  class VerifyEmailOtpResponseParser : PersonalIdApiResponseParser {
      override fun parse(json: JSONObject, sessionData: PersonalIdSessionData) {
          // Confirmation only; extend when server API is finalised.
      }
  }
  ```

- [ ] **Step 4.6: Add static methods to ApiPersonalId.java**

  ```java
  public static void sendEmailOtp(Context context, String email, String token, IApiCallback callback) {
      AuthInfo authInfo = new AuthInfo.TokenAuth(token);
      String tokenAuth = HttpUtils.getCredential(authInfo);
      Objects.requireNonNull(tokenAuth);

      HashMap<String, String> params = new HashMap<>();
      params.put("email", email);

      ApiService apiService = PersonalIdApiClient.getClientApi();
      Call<ResponseBody> call = apiService.sendEmailOtp(tokenAuth, params);
      BaseApi.Companion.callApi(context, call, callback, ApiEndPoints.sendEmailOtp);
  }

  public static void verifyEmailOtp(Context context, String email, String otp,
                                     String token, IApiCallback callback) {
      AuthInfo authInfo = new AuthInfo.TokenAuth(token);
      String tokenAuth = HttpUtils.getCredential(authInfo);
      Objects.requireNonNull(tokenAuth);

      HashMap<String, String> params = new HashMap<>();
      params.put("email", email);
      params.put("otp", otp);

      ApiService apiService = PersonalIdApiClient.getClientApi();
      Call<ResponseBody> call = apiService.verifyEmailOtp(tokenAuth, params);
      BaseApi.Companion.callApi(context, call, callback, ApiEndPoints.verifyEmailOtp);
  }
  ```

- [ ] **Step 4.7: Add handler methods to PersonalIdApiHandler.java**

  Add imports for new parsers at top of file:
  ```java
  import org.commcare.connect.network.connectId.parser.SendEmailOtpResponseParser;
  import org.commcare.connect.network.connectId.parser.VerifyEmailOtpResponseParser;
  ```

  Add handler methods:
  ```java
  public void sendEmailOtpCall(Activity activity, String email, PersonalIdSessionData sessionData) {
      sessionData.setEmail(email);
      ApiPersonalId.sendEmailOtp(
              activity,
              email,
              sessionData.getToken(),
              createCallback(sessionData, new SendEmailOtpResponseParser())
      );
  }

  public void verifyEmailOtpCall(Activity activity, String otp, PersonalIdSessionData sessionData) {
      ApiPersonalId.verifyEmailOtp(
              activity,
              sessionData.getEmail(),
              otp,
              sessionData.getToken(),
              createCallback(sessionData, new VerifyEmailOtpResponseParser())
      );
  }
  ```

  > **Critical:** `PersonalIdApiHandler` has two distinct `createCallback` overloads. Use the **private** overload defined directly in `PersonalIdApiHandler` — `createCallback(PersonalIdSessionData, PersonalIdApiResponseParser)`. Do NOT use the parent `BaseApiHandler.createCallback`, which takes a different parser interface and does not populate `sessionData`. Both compile without error but the parent overload leaves session data unpopulated.

- [ ] **Step 4.8: Build to verify compilation**

  Run: `./gradlew assembleCommcareDebug`
  Expected: BUILD SUCCESSFUL

- [ ] **Step 4.9: Commit**

  ```bash
  git add app/src/org/commcare/connect/network/ApiEndPoints.java \
          app/src/org/commcare/connect/network/ApiService.java \
          app/src/org/commcare/connect/network/ApiPersonalId.java \
          app/src/org/commcare/connect/network/connectId/PersonalIdApiHandler.java \
          app/src/org/commcare/connect/network/connectId/parser/SendEmailOtpResponseParser.kt \
          app/src/org/commcare/connect/network/connectId/parser/VerifyEmailOtpResponseParser.kt
  git commit -m "[AI] Add email OTP API endpoints, service calls, parsers, and handler methods"
  ```

---

## Chunk 3: Email Entry Fragment

### Task 5: Create email entry layout and fragment

**Files:**
- Create: `app/res/layout/fragment_personalid_email.xml`
- Create: `app/src/org/commcare/fragments/personalId/PersonalIdEmailFragment.kt`

- [ ] **Step 5.1: Create fragment_personalid_email.xml**

  Model closely after `app/res/layout/screen_personalid_name.xml`. The layout must include:
  - `ScrollView` root with ID `personalid_email_scroll_view`
  - `TextInputLayout` + `TextInputEditText` with ID `emailTextValue` (inputType="textEmailAddress")
  - Continue button with ID `personalidEmailContinueButton`
  - "Skip" text button with ID `personalidEmailSkipButton` (styled as secondary action)
  - Error `TextView` with ID `personalidEmailError` (visibility gone by default)

  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
      xmlns:app="http://schemas.android.com/apk/res-auto"
      android:id="@+id/personalid_email_scroll_view"
      android:layout_width="match_parent"
      android:layout_height="match_parent"
      android:fillViewport="true">

      <LinearLayout
          android:layout_width="match_parent"
          android:layout_height="wrap_content"
          android:orientation="vertical"
          android:padding="24dp">

          <TextView
              android:layout_width="wrap_content"
              android:layout_height="wrap_content"
              android:text="@string/personalid_email_title"
              android:textAppearance="?attr/textAppearanceHeadline6"
              android:layout_marginBottom="8dp" />

          <TextView
              android:layout_width="wrap_content"
              android:layout_height="wrap_content"
              android:text="@string/personalid_email_description"
              android:layout_marginBottom="24dp" />

          <com.google.android.material.textfield.TextInputLayout
              android:layout_width="match_parent"
              android:layout_height="wrap_content"
              app:boxBackgroundMode="outline">

              <com.google.android.material.textfield.TextInputEditText
                  android:id="@+id/emailTextValue"
                  android:layout_width="match_parent"
                  android:layout_height="wrap_content"
                  android:hint="@string/personalid_email_hint"
                  android:inputType="textEmailAddress"
                  android:maxLines="1"
                  android:imeOptions="actionDone" />
          </com.google.android.material.textfield.TextInputLayout>

          <TextView
              android:id="@+id/personalidEmailError"
              android:layout_width="wrap_content"
              android:layout_height="wrap_content"
              android:layout_marginTop="4dp"
              android:textColor="@color/design_default_color_error"
              android:visibility="gone" />

          <Button
              android:id="@+id/personalidEmailContinueButton"
              android:layout_width="match_parent"
              android:layout_height="wrap_content"
              android:layout_marginTop="24dp"
              android:enabled="false"
              android:text="@string/personalid_continue" />

          <Button
              android:id="@+id/personalidEmailSkipButton"
              style="?attr/borderlessButtonStyle"
              android:layout_width="match_parent"
              android:layout_height="wrap_content"
              android:layout_marginTop="8dp"
              android:text="@string/personalid_email_skip" />

      </LinearLayout>
  </ScrollView>
  ```

  Add string resources to `app/res/values/strings.xml`:
  ```xml
  <string name="personalid_email_title">Add your email (optional)</string>
  <string name="personalid_email_description">Your email helps you recover your account if you lose access to your phone.</string>
  <string name="personalid_email_hint">Email address</string>
  <string name="personalid_email_skip">Skip for now</string>
  <string name="personalid_email_invalid_format">Please enter a valid email address.</string>
  <string name="personalid_email_verification_title">Verify your email</string>
  <string name="personalid_email_verification_description">Enter the 6-digit code sent to %1$s</string>
  ```

- [ ] **Step 5.2: Create PersonalIdEmailFragment.kt**

  ```kotlin
  package org.commcare.fragments.personalId

  import android.app.Activity
  import android.os.Bundle
  import android.text.Editable
  import android.text.TextWatcher
  import android.util.Patterns
  import android.view.LayoutInflater
  import android.view.View
  import android.view.ViewGroup
  import android.view.WindowManager
  import androidx.lifecycle.ViewModelProvider
  import androidx.navigation.Navigation
  import org.commcare.activities.connect.viewmodel.PersonalIdSessionDataViewModel
  import org.commcare.android.database.connect.models.PersonalIdSessionData
  import org.commcare.connect.PersonalIdRecoveryCompleter
  import org.commcare.connect.network.PersonalIdOrConnectApiErrorHandler
  import org.commcare.connect.network.connectId.PersonalIdApiHandler
  import org.commcare.dalvik.R
  import org.commcare.connect.ConnectConstants
  import org.commcare.dalvik.databinding.FragmentPersonalidEmailBinding
  import org.commcare.google.services.analytics.FirebaseAnalyticsUtil

  class PersonalIdEmailFragment : BasePersonalIdFragment() {

      private lateinit var binding: FragmentPersonalidEmailBinding
      private lateinit var activity: Activity
      private lateinit var personalIdSessionData: PersonalIdSessionData

      /**
       * True when launched for a legacy user who is adding email post-registration.
       * Passed as a nav argument (isLegacyFlow: Boolean = false).
       */
      private var isLegacyFlow: Boolean = false

      /**
       * True when entered from the recovery path (existing user who just validated backup code
       * on a new device). On skip or successful OTP, the fragment must finalize account recovery
       * instead of navigating to Photo Capture.
       */
      private var isRecovery: Boolean = false

      override fun onCreateView(
          inflater: LayoutInflater,
          container: ViewGroup?,
          savedInstanceState: Bundle?
      ): View {
          binding = FragmentPersonalidEmailBinding.inflate(inflater, container, false)
          personalIdSessionData = ViewModelProvider(requireActivity())
              .get(PersonalIdSessionDataViewModel::class.java)
              .personalIdSessionData
          activity = requireActivity()
          activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
          isLegacyFlow = arguments?.getBoolean(ARG_IS_LEGACY_FLOW, false) ?: false
          isRecovery = arguments?.getBoolean(ARG_IS_RECOVERY, false) ?: false

          setupListeners()
          enableContinueButton(false)
          binding.emailTextValue.addTextChangedListener(createEmailWatcher())
          setupKeyboardScrollListener(binding.personalidEmailScrollView)
          return binding.root
      }

      override fun onDestroyView() {
          super.onDestroyView()
          destroyKeyboardScrollListener(binding.personalidEmailScrollView)
      }

      override fun onResume() {
          super.onResume()
          binding.emailTextValue.requestFocus()
      }

      private fun createEmailWatcher() = object : TextWatcher {
          override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
          override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
              enableContinueButton(isValidEmail(s?.toString()))
          }
          override fun afterTextChanged(s: Editable?) {}
      }

      private fun isValidEmail(email: String?): Boolean =
          !email.isNullOrBlank() && Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()

      private fun setupListeners() {
          binding.personalidEmailContinueButton.setOnClickListener { submitEmail() }
          binding.personalidEmailSkipButton.setOnClickListener { skipEmail() }
      }

      private fun enableContinueButton(enabled: Boolean) {
          binding.personalidEmailContinueButton.isEnabled = enabled
      }

      private fun submitEmail() {
          FirebaseAnalyticsUtil.reportPersonalIDContinueClicked(javaClass.simpleName, null)
          clearError()
          enableContinueButton(false)
          val email = binding.emailTextValue.text.toString().trim()

          object : PersonalIdApiHandler<PersonalIdSessionData>() {
              override fun onSuccess(sessionData: PersonalIdSessionData) {
                  navigateToEmailVerification()
              }

              override fun onFailure(
                  failureCode: PersonalIdApiHandler.PersonalIdOrConnectApiErrorCodes,
                  t: Throwable?
              ) {
                  if (handleCommonSignupFailures(failureCode)) return
                  showError(PersonalIdOrConnectApiErrorHandler.handle(requireActivity(), failureCode, t))
                  enableContinueButton(true)
              }
          }.sendEmailOtpCall(requireActivity(), email, personalIdSessionData)
      }

      private fun skipEmail() {
          FirebaseAnalyticsUtil.reportPersonalIDContinueClicked(javaClass.simpleName, "skip")
          // Mark that the email step was explicitly shown and declined during signup.
          // createAndSaveConnectUser() / PersonalIdRecoveryCompleter will read this to initialise
          // emailOfferCount = 1 so the post-login dialog does not fire again immediately.
          personalIdSessionData.emailSkippedDuringSignup = true
          when {
              isLegacyFlow -> requireActivity().finish()
              isRecovery -> finalizeRecoveryAndShowSuccess()
              else -> Navigation.findNavController(binding.root)
                  .navigate(PersonalIdEmailFragmentDirections.actionPersonalidEmailToPersonalidPhotoCapture())
          }
      }

      private fun finalizeRecoveryAndShowSuccess() {
          PersonalIdRecoveryCompleter.finalizeAccountRecovery(requireActivity(), personalIdSessionData)
          // Use the message-display action wired in Task 7 to show the recovery success screen.
          navigateToMessageDisplay(
              getString(R.string.connect_recovery_success_title),
              getString(R.string.connect_recovery_success_message),
              isCancellable = false,
              phase = ConnectConstants.PERSONALID_RECOVERY_SUCCESS,
              buttonText = R.string.ok
          )
      }

      private fun navigateToEmailVerification() {
          val action = PersonalIdEmailFragmentDirections
              .actionPersonalidEmailToPersonalidEmailVerification(isLegacyFlow, isRecovery)
          Navigation.findNavController(binding.root).navigate(action)
      }

      private fun clearError() {
          binding.personalidEmailError.visibility = View.GONE
          binding.personalidEmailError.text = ""
      }

      private fun showError(message: String) {
          binding.personalidEmailError.visibility = View.VISIBLE
          binding.personalidEmailError.text = message
      }

      override fun navigateToMessageDisplay(
          title: String,
          message: String?,
          isCancellable: Boolean,
          phase: Int,
          buttonText: Int
      ) {
          val action = PersonalIdEmailFragmentDirections
              .actionPersonalidEmailToPersonalidMessage(
                  title, message, phase, getString(buttonText), null
              ).setIsCancellable(isCancellable)
          Navigation.findNavController(binding.root).navigate(action)
      }

      companion object {
          const val ARG_IS_LEGACY_FLOW = "isLegacyFlow"
          const val ARG_IS_RECOVERY = "isRecovery"
      }
  }
  ```

- [ ] **Step 5.3: Build to verify compilation**

  Run: `./gradlew assembleCommcareDebug`
  Expected: BUILD SUCCESSFUL (Safe Args for this fragment will be generated after the nav graph is updated in Task 7; if build fails here due to missing directions class, proceed to Task 7 first then return to build verification).

- [ ] **Step 5.4: Commit**

  ```bash
  git add app/res/layout/fragment_personalid_email.xml \
          app/src/org/commcare/fragments/personalId/PersonalIdEmailFragment.kt \
          app/res/values/strings.xml
  git commit -m "[AI] Add PersonalIdEmailFragment and layout for email entry step"
  ```

---

## Chunk 4: Email OTP Verification Fragment

### Task 6: Create email OTP layout and fragment

**Files:**
- Create: `app/res/layout/fragment_personalid_email_verification.xml`
- Create: `app/src/org/commcare/fragments/personalId/PersonalIdEmailVerificationFragment.kt`

- [ ] **Step 6.1: Create fragment_personalid_email_verification.xml**

  Model after `app/res/layout/screen_personalid_phone_verify.xml`. Must include:
  - `ScrollView` root with ID `personalid_email_verification_scroll_view`
  - Description `TextView` with ID `emailVerificationDescription` (shows "Code sent to {email}")
  - `NumericCodeView` with ID `otpCodeView` (codeViewDigitCount=6)
  - Verify button with ID `personalidEmailVerifyButton` (initially disabled)
  - Error `TextView` with ID `personalidEmailVerifyError` (gone by default)
  - Resend button with ID `personalidEmailResendButton`
  - Resend countdown `TextView` with ID `resendCountdownText` (shows remaining seconds)

  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
      xmlns:app="http://schemas.android.com/apk/res-auto"
      android:id="@+id/personalid_email_verification_scroll_view"
      android:layout_width="match_parent"
      android:layout_height="match_parent"
      android:fillViewport="true">

      <LinearLayout
          android:layout_width="match_parent"
          android:layout_height="wrap_content"
          android:orientation="vertical"
          android:padding="24dp">

          <TextView
              android:layout_width="wrap_content"
              android:layout_height="wrap_content"
              android:text="@string/personalid_email_verification_title"
              android:textAppearance="?attr/textAppearanceHeadline6"
              android:layout_marginBottom="8dp" />

          <TextView
              android:id="@+id/emailVerificationDescription"
              android:layout_width="wrap_content"
              android:layout_height="wrap_content"
              android:layout_marginBottom="24dp" />

          <org.commcare.views.connect.NumericCodeView
              android:id="@+id/otpCodeView"
              android:layout_width="match_parent"
              android:layout_height="55dp"
              android:layout_marginStart="16dp"
              android:layout_marginEnd="16dp"
              app:codeViewDigitCount="6"
              app:codeViewBorderColor="@color/black"
              app:codeViewBorderRadius="8dp"
              app:codeViewBorderWidth="2dp"
              app:codeViewTextColor="@color/black"
              app:codeViewTextSize="8sp" />

          <TextView
              android:id="@+id/personalidEmailVerifyError"
              android:layout_width="wrap_content"
              android:layout_height="wrap_content"
              android:layout_marginTop="4dp"
              android:textColor="@color/design_default_color_error"
              android:visibility="gone" />

          <Button
              android:id="@+id/personalidEmailVerifyButton"
              android:layout_width="match_parent"
              android:layout_height="wrap_content"
              android:layout_marginTop="24dp"
              android:enabled="false"
              android:text="@string/personalid_email_verify_button" />

          <LinearLayout
              android:layout_width="match_parent"
              android:layout_height="wrap_content"
              android:orientation="horizontal"
              android:layout_marginTop="12dp"
              android:gravity="center">

              <Button
                  android:id="@+id/personalidEmailResendButton"
                  style="?attr/borderlessButtonStyle"
                  android:layout_width="wrap_content"
                  android:layout_height="wrap_content"
                  android:text="@string/connect_resend_otp"
                  android:enabled="false" />

              <TextView
                  android:id="@+id/resendCountdownText"
                  android:layout_width="wrap_content"
                  android:layout_height="wrap_content"
                  android:visibility="gone" />
          </LinearLayout>

      </LinearLayout>
  </ScrollView>
  ```

  Add string resources to `app/res/values/strings.xml`:
  ```xml
  <string name="personalid_email_verify_button">Verify</string>
  <string name="personalid_email_otp_failed_title">Verification unsuccessful</string>
  <string name="personalid_email_otp_failed_message">You\'ve entered an incorrect code 3 times. Would you like to try again or proceed without adding an email address?</string>
  <string name="personalid_email_otp_failed_retry">Try again</string>
  <string name="personalid_email_otp_failed_skip">Proceed without email</string>
  ```

- [ ] **Step 6.2: Create PersonalIdEmailVerificationFragment.kt**

  ```kotlin
  package org.commcare.fragments.personalId

  import android.app.Activity
  import android.os.Bundle
  import android.os.Handler
  import android.os.Looper
  import android.view.LayoutInflater
  import android.view.View
  import android.view.ViewGroup
  import androidx.lifecycle.ViewModelProvider
  import androidx.navigation.Navigation
  import org.commcare.activities.CommCareActivity
  import org.commcare.activities.connect.viewmodel.PersonalIdSessionDataViewModel
  import org.commcare.android.database.connect.models.ConnectUserRecord
  import org.commcare.android.database.connect.models.PersonalIdSessionData
  import org.commcare.connect.ConnectUserDatabaseUtil
  import org.commcare.connect.ConnectConstants
  import org.commcare.connect.PersonalIDPreferences
  import org.commcare.connect.PersonalIdRecoveryCompleter
  import org.commcare.connect.network.PersonalIdOrConnectApiErrorHandler
  import org.commcare.connect.network.connectId.PersonalIdApiHandler
  import org.commcare.dalvik.R
  import org.commcare.dalvik.databinding.FragmentPersonalidEmailVerificationBinding
  import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
  import org.commcare.views.connect.NumericCodeView
  import org.commcare.views.dialogs.StandardAlertDialog
  import java.util.concurrent.TimeUnit

  class PersonalIdEmailVerificationFragment : BasePersonalIdFragment() {

      private lateinit var binding: FragmentPersonalidEmailVerificationBinding
      private lateinit var activity: Activity
      private lateinit var personalIdSessionData: PersonalIdSessionData
      private var isLegacyFlow: Boolean = false
      private var isRecovery: Boolean = false

      private val resendHandler = Handler(Looper.getMainLooper())
      private var otpRequestTime: Long = 0L
      private val resendCooldownMillis = TimeUnit.MINUTES.toMillis(2)
      private var failedOtpAttempts = 0
      private val maxOtpAttempts = 3

      private val resendTimerRunnable = object : Runnable {
          override fun run() {
              updateResendButtonState()
              resendHandler.postDelayed(this, 1000)
          }
      }

      override fun onCreate(savedInstanceState: Bundle?) {
          super.onCreate(savedInstanceState)
          activity = requireActivity()
          personalIdSessionData = ViewModelProvider(requireActivity())
              .get(PersonalIdSessionDataViewModel::class.java)
              .personalIdSessionData
          isLegacyFlow = arguments?.getBoolean(PersonalIdEmailFragment.ARG_IS_LEGACY_FLOW, false)
              ?: false
          isRecovery = arguments?.getBoolean(PersonalIdEmailFragment.ARG_IS_RECOVERY, false)
              ?: false
      }

      override fun onCreateView(
          inflater: LayoutInflater,
          container: ViewGroup?,
          savedInstanceState: Bundle?
      ): View {
          binding = FragmentPersonalidEmailVerificationBinding.inflate(inflater, container, false)

          binding.emailVerificationDescription.text = getString(
              R.string.personalid_email_verification_description,
              personalIdSessionData.email
          )

          binding.otpCodeView.setOnCodeChangedListener { code ->
              enableVerifyButton(code.length == 6)
          }
          binding.otpCodeView.setCodeCompleteListener { _ -> submitOtp() }
          binding.otpCodeView.setOnEnterKeyPressedListener { submitOtp() }
          binding.personalidEmailVerifyButton.setOnClickListener { submitOtp() }
          binding.personalidEmailResendButton.setOnClickListener { resendOtp() }

          enableVerifyButton(false)
          otpRequestTime = System.currentTimeMillis()
          resendHandler.post(resendTimerRunnable)

          setupKeyboardScrollListener(binding.personalidEmailVerificationScrollView)
          return binding.root
      }

      override fun onDestroyView() {
          super.onDestroyView()
          resendHandler.removeCallbacks(resendTimerRunnable)
          destroyKeyboardScrollListener(binding.personalidEmailVerificationScrollView)
      }

      private fun enableVerifyButton(enabled: Boolean) {
          binding.personalidEmailVerifyButton.isEnabled = enabled
      }

      private fun updateResendButtonState() {
          val elapsed = System.currentTimeMillis() - otpRequestTime
          val remaining = resendCooldownMillis - elapsed
          if (remaining <= 0) {
              binding.personalidEmailResendButton.isEnabled = true
              binding.resendCountdownText.visibility = View.GONE
          } else {
              binding.personalidEmailResendButton.isEnabled = false
              binding.resendCountdownText.visibility = View.VISIBLE
              val seconds = TimeUnit.MILLISECONDS.toSeconds(remaining)
              binding.resendCountdownText.text = getString(R.string.connect_otp_resend_wait, seconds)
          }
      }

      private fun resendOtp() {
          otpRequestTime = System.currentTimeMillis()
          binding.personalidEmailResendButton.isEnabled = false
          binding.otpCodeView.clearCode()
          clearError()

          object : PersonalIdApiHandler<PersonalIdSessionData>() {
              override fun onSuccess(sessionData: PersonalIdSessionData) {
                  // OTP re-sent; timer has already been reset above
              }
              override fun onFailure(
                  failureCode: PersonalIdApiHandler.PersonalIdOrConnectApiErrorCodes,
                  t: Throwable?
              ) {
                  showError(PersonalIdOrConnectApiErrorHandler.handle(requireActivity(), failureCode, t))
              }
          }.sendEmailOtpCall(requireActivity(), personalIdSessionData.email!!, personalIdSessionData)
      }

      private fun submitOtp() {
          val otp = binding.otpCodeView.codeValue
          if (otp.length != 6) return
          FirebaseAnalyticsUtil.reportPersonalIDContinueClicked(javaClass.simpleName, null)
          clearError()
          enableVerifyButton(false)

          object : PersonalIdApiHandler<PersonalIdSessionData>() {
              override fun onSuccess(sessionData: PersonalIdSessionData) {
                  onEmailVerified()
              }
              override fun onFailure(
                  failureCode: PersonalIdApiHandler.PersonalIdOrConnectApiErrorCodes,
                  t: Throwable?
              ) {
                  if (handleCommonSignupFailures(failureCode)) return
                  showError(PersonalIdOrConnectApiErrorHandler.handle(requireActivity(), failureCode, t))
                  failedOtpAttempts++
                  if (failedOtpAttempts >= maxOtpAttempts) {
                      showProceedWithoutEmailDialog()
                  } else if (failureCode.shouldAllowRetry()) {
                      enableVerifyButton(true)
                  }
              }
          }.verifyEmailOtpCall(requireActivity(), otp, personalIdSessionData)
      }

      private fun onEmailVerified() {
          // Always update session data — this is the source of truth for the signup and
          // recovery flows. For signup, `ConnectUserRecord` is written later by
          // PersonalIdPhotoCaptureFragment. For recovery, it is written now by
          // PersonalIdRecoveryCompleter. For the legacy flow the record already exists.
          personalIdSessionData.emailVerified = true

          when {
              isLegacyFlow -> {
                  val user = ConnectUserDatabaseUtil.getUser(requireActivity())
                  if (user != null) {
                      user.setEmail(personalIdSessionData.email)
                      ConnectUserDatabaseUtil.storeUser(requireActivity(), user)
                  }
                  PersonalIDPreferences.setEmailVerified(requireActivity(), true)
                  requireActivity().finish()
              }
              isRecovery -> finalizeRecoveryAndShowSuccess()
              else -> Navigation.findNavController(binding.root)
                  .navigate(PersonalIdEmailVerificationFragmentDirections
                      .actionPersonalidEmailVerificationToPersonalidPhotoCapture())
          }
      }

      private fun finalizeRecoveryAndShowSuccess() {
          PersonalIdRecoveryCompleter.finalizeAccountRecovery(requireActivity(), personalIdSessionData)
          navigateToMessageDisplay(
              getString(R.string.connect_recovery_success_title),
              getString(R.string.connect_recovery_success_message),
              isCancellable = false,
              phase = ConnectConstants.PERSONALID_RECOVERY_SUCCESS,
              buttonText = R.string.ok
          )
      }

      private fun showProceedWithoutEmailDialog() {
          val dialog = StandardAlertDialog(
              getString(R.string.personalid_email_otp_failed_title),
              getString(R.string.personalid_email_otp_failed_message)
          )
          dialog.setPositiveButton(getString(R.string.personalid_email_otp_failed_retry)) { d, _ ->
              (activity as? CommCareActivity<*>)?.dismissAlertDialog()
              failedOtpAttempts = 0
              binding.otpCodeView.clearCode()
              clearError()
              enableVerifyButton(false)
          }
          dialog.setNegativeButton(getString(R.string.personalid_email_otp_failed_skip)) { d, _ ->
              (activity as? CommCareActivity<*>)?.dismissAlertDialog()
              proceedWithoutEmail()
          }
          (activity as? CommCareActivity<*>)?.showAlertDialog(dialog)
      }

      private fun proceedWithoutEmail() {
          personalIdSessionData.email = null
          personalIdSessionData.emailVerified = false
          personalIdSessionData.emailSkippedDuringSignup = true
          when {
              isLegacyFlow -> requireActivity().finish()
              isRecovery -> finalizeRecoveryAndShowSuccess()
              else -> Navigation.findNavController(binding.root)
                  .navigate(PersonalIdEmailVerificationFragmentDirections
                      .actionPersonalidEmailVerificationToPersonalidPhotoCapture())
          }
      }

      private fun clearError() {
          binding.personalidEmailVerifyError.visibility = View.GONE
          binding.personalidEmailVerifyError.text = ""
          binding.otpCodeView.setErrorState(false)
      }

      private fun showError(message: String) {
          binding.personalidEmailVerifyError.visibility = View.VISIBLE
          binding.personalidEmailVerifyError.text = message
          binding.otpCodeView.setErrorState(true)
      }

      override fun navigateToMessageDisplay(
          title: String,
          message: String?,
          isCancellable: Boolean,
          phase: Int,
          buttonText: Int
      ) {
          val action = PersonalIdEmailVerificationFragmentDirections
              .actionPersonalidEmailVerificationToPersonalidMessage(
                  title, message, phase, getString(buttonText), null
              ).setIsCancellable(isCancellable)
          Navigation.findNavController(binding.root).navigate(action)
      }
  }
  ```

  > **Note:** The correct DB write method is `ConnectUserDatabaseUtil.storeUser(Context, ConnectUserRecord)` — there is no `saveUser` method. The `emailVerified` flag lives in `PersonalIDPreferences`, not on `ConnectUserRecord`; do not try to call `user.setEmailVerified(true)` — that setter does not exist.

- [ ] **Step 6.3: Build to verify compilation**

  Run: `./gradlew assembleCommcareDebug`
  Expected: BUILD SUCCESSFUL (may have unresolved Safe Args until nav graph is updated; proceed to Task 7 if so).

- [ ] **Step 6.4: Commit**

  ```bash
  git add app/res/layout/fragment_personalid_email_verification.xml \
          app/src/org/commcare/fragments/personalId/PersonalIdEmailVerificationFragment.kt \
          app/res/values/strings.xml
  git commit -m "[AI] Add PersonalIdEmailVerificationFragment and layout for email OTP step"
  ```

---

### Task 6c: Propagate email/emailVerified into ConnectUserRecord at signup completion and defer recovery finalization

> **Ordering note:** Task 6c.4 below introduces a `PersonalIdBackupCodeFragmentDirections.actionPersonalidBackupcodeToPersonalidEmail(boolean)` call. That Safe Args method only exists once Task 7.1 has added the matching `<action>` element to `nav_graph_personalid.xml`. Two valid orderings:
>
> 1. Do **Task 7.1 first** (nav-graph XML only), then this Task 6c (helper + fragment edits) — cleanest; every build after each step compiles.
> 2. Do Task 6c.1–6c.3 here, then Task 7.1, then come back for Task 6c.4–6c.6 — also fine. Step 6c.5 explicitly expects an unresolved-Safe-Args failure in the interim.
>
> Pick one and stay consistent. The commit messages are independent so ordering does not affect git history.

Both signup and recovery now route through the Email step before writing `ConnectUserRecord`. Two changes are required here:

1. **Signup:** `PersonalIdPhotoCaptureFragment.createAndSaveConnectUser()` must read the new email fields from session data so a user who verified their email during signup has `emailVerified = true` from the moment the record is written.
2. **Recovery:** The current body of `PersonalIdBackupCodeFragment.handleSuccessfulRecovery()` (DB passphrase + `ConnectUserRecord` write + `logRecoveryResult` + `handleSecondDeviceLogin`) is extracted into a new helper `PersonalIdRecoveryCompleter`, so the Email fragments can trigger it after the email step. `PersonalIdBackupCodeFragment` no longer invokes `handleSuccessfulRecovery()` itself — it navigates to Email with `isRecovery=true` after a successful backup-code validation.

> **Critical:** Do NOT add email fields to the `ConnectUserRecord` constructor signature. The 10-parameter constructor is called from several places (including the recovery path); changing its signature would cause compile failures at every callsite. Set email fields via setters after construction, as shown below.

**Files:**
- Modify: `app/src/org/commcare/fragments/personalId/PersonalIdPhotoCaptureFragment.java`
- Create: `app/src/org/commcare/connect/PersonalIdRecoveryCompleter.kt`
- Modify: `app/src/org/commcare/fragments/personalId/PersonalIdBackupCodeFragment.java`
- Modify: `app/src/org/commcare/android/database/connect/models/PersonalIdSessionData.kt` (add `backupCode` getter/setter if missing)

- [ ] **Step 6c.1: Update PersonalIdPhotoCaptureFragment.createAndSaveConnectUser()**

  Write `email` onto the record and push the three flags through `PersonalIDPreferences`:

  ```java
  private void createAndSaveConnectUser(String photoAsBase64) {
      ConnectDatabaseHelper.handleReceivedDbPassphrase(requireActivity(), personalIdSessionData.getDbKey());
      ConnectUserRecord user = new ConnectUserRecord(
              personalIdSessionData.getPhoneNumber(),
              personalIdSessionData.getPersonalId(),
              personalIdSessionData.getOauthPassword(),
              personalIdSessionData.getUserName(),
              String.valueOf(personalIdSessionData.getBackupCode()),
              new Date(), photoAsBase64,
              personalIdSessionData.getDemoUser(),
              personalIdSessionData.getRequiredLock(),
              personalIdSessionData.getInvitedUser());
      // Email (DB column)
      user.setEmail(personalIdSessionData.getEmail());
      ConnectUserDatabaseUtil.storeUser(requireActivity(), user);

      // Email-state flags (SharedPreferences)
      PersonalIDPreferences.setEmailVerified(requireActivity(), personalIdSessionData.getEmailVerified());
      if (personalIdSessionData.getEmailSkippedDuringSignup()) {
          // User was shown the email step and declined. Count it as the first offer so the
          // post-login dialog only fires after the 30-day gap, not on the very next login.
          PersonalIDPreferences.setEmailOfferCount(requireActivity(), 1);
          PersonalIDPreferences.setLastEmailOfferDate(requireActivity(), new Date());
      } else {
          // User either verified email OR hasn't interacted with the email step at all.
          // Leave offerCount/lastOfferDate as null (absent) so the first legacy-prompt fires
          // normally on next login if emailVerified is false.
          PersonalIDPreferences.setEmailOfferCount(requireActivity(), null);
          PersonalIDPreferences.setLastEmailOfferDate(requireActivity(), null);
      }
  }
  ```

  Add the import at the top of the file:
  ```java
  import org.commcare.connect.PersonalIDPreferences;
  ```

  > **Note:** If the user skipped the email step, `personalIdSessionData.getEmail()` is `null` and `getEmailVerified()` is `false` — `setEmail(null)` on the record is fine, and `setEmailVerified(context, false)` stores an explicit `false` that `shouldOfferEmail()` can read unambiguously.

- [ ] **Step 6c.2: Create PersonalIdRecoveryCompleter.kt**

  Extract the body of `PersonalIdBackupCodeFragment.handleSuccessfulRecovery()` (everything except `navigateToSuccess()`, which the caller handles with its own nav controller). Place it in a new Kotlin object so both the email fragments and any future callers can invoke it.

  ```kotlin
  package org.commcare.connect

  import android.app.Activity
  import org.commcare.CommCareNoficationManager
  import org.commcare.android.database.connect.models.ConnectUserRecord
  import org.commcare.android.database.connect.models.PersonalIdSessionData
  import org.commcare.connect.database.ConnectDatabaseHelper
  import org.commcare.connect.database.ConnectUserDatabaseUtil
  import org.commcare.dalvik.R
  import org.commcare.google.services.analytics.AnalyticsParamValue
  import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
  import org.commcare.utils.NotificationUtil
  import org.javarosa.core.model.utils.DateUtils
  import java.util.Date

  /**
   * Finalizes account recovery after the user has validated their backup code AND completed
   * (or skipped) the email step. Mirrors the logic previously inlined in
   * PersonalIdBackupCodeFragment.handleSuccessfulRecovery(), but is callable from fragments
   * that do not have access to the backup code view binding.
   *
   * Requires `sessionData.backupCode` to have been populated by PersonalIdBackupCodeFragment
   * before it navigated to Email.
   */
  object PersonalIdRecoveryCompleter {

      @JvmStatic
      fun finalizeAccountRecovery(activity: Activity, sessionData: PersonalIdSessionData) {
          ConnectDatabaseHelper.handleReceivedDbPassphrase(activity, sessionData.dbKey)

          val user = ConnectUserRecord(
              sessionData.phoneNumber,
              sessionData.personalId,
              sessionData.oauthPassword,
              sessionData.userName,
              sessionData.backupCode,
              Date(),
              sessionData.photoBase64,
              sessionData.demoUser,
              sessionData.requiredLock,
              sessionData.invitedUser
          )
          // Email (DB column)
          user.email = sessionData.email
          ConnectUserDatabaseUtil.storeUser(activity, user)

          // Email-state flags (SharedPreferences)
          PersonalIDPreferences.setEmailVerified(activity, sessionData.emailVerified)
          if (sessionData.emailSkippedDuringSignup) {
              // User was shown the email step during recovery and declined. Count it as the
              // first offer so the post-login dialog only fires after the 30-day gap.
              PersonalIDPreferences.setEmailOfferCount(activity, 1)
              PersonalIDPreferences.setLastEmailOfferDate(activity, Date())
          } else {
              // Either the user verified their email, or they have not interacted with it at
              // all (shouldn't happen in the recovery path, but be defensive): leave
              // offerCount/lastOfferDate absent so shouldOfferEmail() can fire normally.
              PersonalIDPreferences.setEmailOfferCount(activity, null)
              PersonalIDPreferences.setLastEmailOfferDate(activity, null)
          }

          logRecoveryResult(true)
          notifySecondDeviceLoginIfApplicable(activity, sessionData)
      }

      private fun logRecoveryResult(success: Boolean) {
          FirebaseAnalyticsUtil.reportPersonalIdAccountRecovered(
              success, AnalyticsParamValue.CCC_RECOVERY_METHOD_BACKUPCODE
          )
      }

      private fun notifySecondDeviceLoginIfApplicable(
          activity: Activity, sessionData: PersonalIdSessionData
      ) {
          val previousDevice = sessionData.previousDevice ?: return
          val titleId = R.string.personalid_second_device_login_title
          val message = if (sessionData.lastAccessed != null) {
              activity.getString(
                  R.string.personalid_second_device_login_message,
                  previousDevice,
                  DateUtils.getShortStringValue(sessionData.lastAccessed)
              )
          } else {
              activity.getString(
                  R.string.personalid_second_device_login_message_no_date,
                  previousDevice
              )
          }
          NotificationUtil.showNotification(
              activity,
              CommCareNoficationManager.NOTIFICATION_CHANNEL_SERVER_COMMUNICATIONS_ID,
              titleId,
              activity.getString(titleId),
              message,
              null
          )
      }
  }
  ```

  > **Note:** Use whichever Kotlin accessor syntax matches the real `ConnectUserRecord` / `PersonalIdSessionData` — Kotlin sees Java getters/setters as properties, so `user.email` maps to `setEmail`/`getEmail`. If the generated property name collides, use the direct setter call instead. `PersonalIDPreferences` is in the same package (`org.commcare.connect`) so it does not need an explicit import.

- [ ] **Step 6c.3: Verify `PersonalIdSessionData.backupCode` (no code change expected)**

  Sanity check only — as of this plan's snapshot, `var backupCode: String? = null` is already declared at `app/src/org/commcare/android/database/connect/models/PersonalIdSessionData.kt:39` and already written by the existing `handleBackupCodeSubmission()` in `PersonalIdBackupCodeFragment.java:178`. Open both files and confirm the field is still a mutable `String?`. If the type has changed since this plan was written (e.g., to `CharArray`), update the `sessionData.backupCode` access in `PersonalIdRecoveryCompleter.kt` to match. Otherwise no code change is required in this step.

- [ ] **Step 6c.4: Update PersonalIdBackupCodeFragment.java to navigate to Email in both modes**

  The fragment's current behaviour is:
  - Signup: `handleBackupCodeSubmission()` sets `sessionData.backupCode` and calls `navigateToPhoto()` (line ~180).
  - Recovery: `confirmBackupCode()` calls the API; on success calls `handleSuccessfulRecovery()` (line ~193).

  After the change:
  - Signup: set `sessionData.backupCode` and call `navigateToEmail(isRecovery = false)` instead of `navigateToPhoto()`.
  - Recovery: on API success, set `sessionData.backupCode` with `binding.backupCodeView.getCodeValue()` and call `navigateToEmail(isRecovery = true)` instead of `handleSuccessfulRecovery()`.
  - Delete the local `handleSuccessfulRecovery()`, `logRecoveryResult()`, `handleSecondDeviceLogin()`, and `navigateToSuccess()` methods — their behaviour now lives in `PersonalIdRecoveryCompleter` and in the Email fragments. Keep `handleFailedBackupCodeAttempt()` as-is; it still uses `logRecoveryResult(false)` — inline the single-line body (`FirebaseAnalyticsUtil.reportPersonalIdAccountRecovered(false, AnalyticsParamValue.CCC_RECOVERY_METHOD_BACKUPCODE);`).

  The signup continuation becomes:
  ```java
  private void handleBackupCodeSubmission() {
      FirebaseAnalyticsUtil.reportPersonalIDContinueClicked(this.getClass().getSimpleName(), null);
      if (isRecovery) {
          confirmBackupCode();
      } else {
          personalIdSessionData.setBackupCode(binding.backupCodeView.getCodeValue());
          navigateToEmail(false);
      }
  }
  ```

  The recovery callback becomes:
  ```java
  @Override
  public void onSuccess(PersonalIdSessionData sessionData) {
      if (sessionData.getDbKey() != null) {
          // Persist the backup code so PersonalIdRecoveryCompleter can read it after the
          // email step. DO NOT call handleSuccessfulRecovery — recovery is finalized by
          // PersonalIdRecoveryCompleter from within the email fragments.
          personalIdSessionData.setBackupCode(binding.backupCodeView.getCodeValue());
          navigateToEmail(true);
      } else if (sessionData.getAttemptsLeft() != null && sessionData.getAttemptsLeft() > 0) {
          handleFailedBackupCodeAttempt();
      }
  }
  ```

  Replace `navigateToPhoto()` with `navigateToEmail(boolean isRecovery)`:
  ```java
  private void navigateToEmail(boolean isRecovery) {
      Navigation.findNavController(binding.getRoot())
              .navigate(PersonalIdBackupCodeFragmentDirections
                      .actionPersonalidBackupcodeToPersonalidEmail(isRecovery));
  }
  ```

  > **Critical:** Inline `logRecoveryResult(false)` at the single remaining callsite in `handleFailedBackupCodeAttempt()` so the method can be removed cleanly. Leave a single-line analytics call there — don't invent a new helper just for it.

- [ ] **Step 6c.5: Build to verify compilation**

  Run: `./gradlew assembleCommcareDebug`
  Expected: BUILD SUCCESSFUL (Safe Args for the new `actionPersonalidBackupcodeToPersonalidEmail` will be generated once Task 7.1 updates the nav graph; if this step fails with unresolved Safe Args, proceed to Task 7 first and then return.)

- [ ] **Step 6c.6: Commit**

  ```bash
  git add app/src/org/commcare/fragments/personalId/PersonalIdPhotoCaptureFragment.java \
          app/src/org/commcare/connect/PersonalIdRecoveryCompleter.kt \
          app/src/org/commcare/fragments/personalId/PersonalIdBackupCodeFragment.java
  git commit -m "[AI] Extract account-recovery finalization into PersonalIdRecoveryCompleter and route BackupCode through Email step"
  ```

---

## Chunk 4b: Client-Side Email Validation & Keyboard Enter Handling

This chunk is **prerequisite to Chunk 5**. The fragments created in Chunks 3 and 4 must be updated here before wiring navigation, because the keyboard enter logic depends on the validated-submit path being correct.

### Task 6b: Email format validation unit tests and keyboard enter integration

**Files:**
- Create: `app/unit-tests/src/org/commcare/connect/PersonalIdEmailValidationTest.kt`
- Modify: `app/src/org/commcare/fragments/personalId/PersonalIdEmailFragment.kt`
- Modify: `app/src/org/commcare/fragments/personalId/PersonalIdEmailVerificationFragment.kt`

- [ ] **Step 6b.1: Write failing unit test for email validation helper**

  Create `app/unit-tests/src/org/commcare/connect/PersonalIdEmailValidationTest.kt`:

  ```kotlin
  package org.commcare.connect

  import android.util.Patterns
  import org.junit.Assert.assertFalse
  import org.junit.Assert.assertTrue
  import org.junit.Test
  import org.junit.runner.RunWith
  import org.robolectric.RobolectricTestRunner

  @RunWith(RobolectricTestRunner::class)
  class PersonalIdEmailValidationTest {

      private fun isValidEmail(email: String?): Boolean =
          !email.isNullOrBlank() && Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()

      @Test
      fun `valid email passes`() {
          assertTrue(isValidEmail("user@example.com"))
          assertTrue(isValidEmail("user+tag@sub.domain.org"))
          assertTrue(isValidEmail("  user@example.com  ")) // trimmed
      }

      @Test
      fun `blank email fails`() {
          assertFalse(isValidEmail(""))
          assertFalse(isValidEmail("   "))
          assertFalse(isValidEmail(null))
      }

      @Test
      fun `malformed email fails`() {
          assertFalse(isValidEmail("notanemail"))
          assertFalse(isValidEmail("missing@"))
          assertFalse(isValidEmail("@nodomain.com"))
          assertFalse(isValidEmail("spaces in@email.com"))
      }
  }
  ```

  Run: `./gradlew testCommcareDebug --tests "org.commcare.connect.PersonalIdEmailValidationTest"`
  Expected: PASS (logic is self-contained; no new code required — this confirms the validation logic used in the fragment is correct before wiring it into the fragment).

- [ ] **Step 6b.2: Add keyboard enter handling to PersonalIdEmailFragment.kt**

  The base class `BasePersonalIdFragment` provides `setUpEnterKeyAction(EditText)` and the hook `keyboardEnterPressed()`. The phone fragment pattern is:
  - If conditions are met → call the submit action
  - Otherwise → hide the keyboard

  In `PersonalIdEmailFragment.kt`, add to `onCreateView` after setting the text watcher:

  ```kotlin
  setUpEnterKeyAction(binding.emailTextValue)
  ```

  Override `keyboardEnterPressed()`:

  ```kotlin
  override fun keyboardEnterPressed() {
      if (isValidEmail(binding.emailTextValue.text?.toString())) {
          submitEmail()
      } else {
          KeyboardHelper.hideVirtualKeyboard(requireActivity())
      }
  }
  ```

  Add the `KeyboardHelper` import:
  ```kotlin
  import org.commcare.utils.KeyboardHelper
  ```

  > `isValidEmail()` is the private method already defined in the fragment (uses `Patterns.EMAIL_ADDRESS`). No duplication needed.

- [ ] **Step 6b.3: Keyboard enter handling for PersonalIdEmailVerificationFragment.kt**

  No additional wiring needed — the `NumericCodeView` has a built-in `OnEnterKeyPressedListener` which is already set up in `onCreateView` (Step 6.2) via `binding.otpCodeView.setOnEnterKeyPressedListener { submitOtp() }`. The `submitOtp()` method guards against incomplete codes (`if (otp.length != 6) return`), so pressing Enter with fewer than 6 digits is a no-op.

- [ ] **Step 6b.4: Verify showError is called for invalid email before API call in submitEmail()**

  In `PersonalIdEmailFragment.submitEmail()`, add a guard at the start so a malformed email (which should not be reachable via button since it is disabled, but could be reached via keyboard shortcut) never reaches the API:

  ```kotlin
  private fun submitEmail() {
      val email = binding.emailTextValue.text.toString().trim()
      if (!isValidEmail(email)) {
          showError(getString(R.string.personalid_email_invalid_format))
          return
      }
      FirebaseAnalyticsUtil.reportPersonalIDContinueClicked(javaClass.simpleName, null)
      clearError()
      enableContinueButton(false)
      // ... rest of existing submitEmail() code
  }
  ```

- [ ] **Step 6b.5: Build to verify compilation**

  Run: `./gradlew assembleCommcareDebug`
  Expected: BUILD SUCCESSFUL

- [ ] **Step 6b.6: Run validation tests**

  Run: `./gradlew testCommcareDebug --tests "org.commcare.connect.PersonalIdEmailValidationTest"`
  Expected: PASS

- [ ] **Step 6b.7: Commit**

  ```bash
  git add app/unit-tests/src/org/commcare/connect/PersonalIdEmailValidationTest.kt \
          app/src/org/commcare/fragments/personalId/PersonalIdEmailFragment.kt \
          app/src/org/commcare/fragments/personalId/PersonalIdEmailVerificationFragment.kt
  git commit -m "[AI] Add email format validation, keyboard enter handling for email and OTP fragments"
  ```

---

## Chunk 5: Navigation & Activity Updates

### Task 7: Update nav graph and PersonalIdActivity


**Files:**
- Modify: `app/res/navigation/nav_graph_personalid.xml`
- Modify: `app/src/org/commcare/activities/connect/PersonalIdActivity.java`

(Fragment-level changes in `PersonalIdBackupCodeFragment.java` are covered by Task 6c.4; this task only wires the nav graph and the legacy-flow activity entry point.)

- [ ] **Step 7.1: Update nav_graph_personalid.xml**

  The signup flow before this plan is `personalid_name → personalid_backup_code → personalid_photo_capture`. The existing action name on `personalid_backup_code` that targets `@id/personalid_photo_capture` is `action_personalid_backupcode_to_personalid_photo_capture` (note the lower-case `backupcode` — see `PersonalIdBackupCodeFragment.navigateToPhoto()` which calls `actionPersonalidBackupcodeToPersonalidPhotoCapture()`). We keep `name → backup_code` as-is, and insert email + email-OTP destinations between `backup_code` and `photo_capture`.

  **a) Replace the signup-continuation action inside `personalid_backup_code` with a parameterised `backup_code → email` action (carrying `isRecovery`):**

  ```xml
  <!-- Replace: -->
  <action
      android:id="@+id/action_personalid_backupcode_to_personalid_photo_capture"
      app:destination="@id/personalid_photo_capture" />

  <!-- With: -->
  <action
      android:id="@+id/action_personalid_backupcode_to_personalid_email"
      app:destination="@id/personalid_email">
      <argument
          android:name="isRecovery"
          app:argType="boolean"
          android:defaultValue="false" />
  </action>
  ```

  Also inside `personalid_backup_code`, the existing `action_personalid_backupcode_to_personalid_message` action is still needed (used by both the signup failure path and the recovery success message coming from the email fragments — see note below). Leave it untouched.

  **b) Add the email entry destination (before `</navigation>`):**

  ```xml
  <fragment
      android:id="@+id/personalid_email"
      android:name="org.commcare.fragments.personalId.PersonalIdEmailFragment"
      android:label="fragment_personalid_email"
      tools:layout="@layout/fragment_personalid_email">
      <argument
          android:name="isLegacyFlow"
          app:argType="boolean"
          android:defaultValue="false" />
      <argument
          android:name="isRecovery"
          app:argType="boolean"
          android:defaultValue="false" />
      <action
          android:id="@+id/action_personalid_email_to_personalid_email_verification"
          app:destination="@id/personalid_email_verification">
          <argument
              android:name="isLegacyFlow"
              app:argType="boolean"
              android:defaultValue="false" />
          <argument
              android:name="isRecovery"
              app:argType="boolean"
              android:defaultValue="false" />
      </action>
      <action
          android:id="@+id/action_personalid_email_to_personalid_photo_capture"
          app:destination="@id/personalid_photo_capture" />
      <action
          android:id="@+id/action_personalid_email_to_personalid_message"
          app:destination="@id/personalid_message_display" />
  </fragment>
  ```

  **c) Add the email verification destination:**

  ```xml
  <fragment
      android:id="@+id/personalid_email_verification"
      android:name="org.commcare.fragments.personalId.PersonalIdEmailVerificationFragment"
      android:label="fragment_personalid_email_verification"
      tools:layout="@layout/fragment_personalid_email_verification">
      <argument
          android:name="isLegacyFlow"
          app:argType="boolean"
          android:defaultValue="false" />
      <argument
          android:name="isRecovery"
          app:argType="boolean"
          android:defaultValue="false" />
      <action
          android:id="@+id/action_personalid_email_verification_to_personalid_photo_capture"
          app:destination="@id/personalid_photo_capture" />
      <action
          android:id="@+id/action_personalid_email_verification_to_personalid_message"
          app:destination="@id/personalid_message_display" />
  </fragment>
  ```

  > **Why a message-display action on each email fragment:** the recovery-success screen is the existing shared `personalid_message_display` destination (phase `PERSONALID_RECOVERY_SUCCESS`). Both email fragments reach it via their `navigateToMessageDisplay` override — which in turn uses `action_personalid_email_to_personalid_message` / `action_personalid_email_verification_to_personalid_message` declared above.

- [ ] **Step 7.2: Add legacy email flow support to PersonalIdActivity.java**

  Add constant and override `onCreate`:

  ```java
  public static final String EXTRA_LEGACY_EMAIL_FLOW = "extra_legacy_email_flow";
  ```

  In `onCreate`, after `super.onCreate(savedInstanceState)`:

  ```java
  if (savedInstanceState == null && getIntent().getBooleanExtra(EXTRA_LEGACY_EMAIL_FLOW, false)) {
      // Navigate to email entry, clearing the phone fragment from back stack
      NavHostFragment navHostFragment =
              (NavHostFragment) getSupportFragmentManager()
                      .findFragmentById(R.id.nav_host_fragment_connectid);
      if (navHostFragment != null) {
          Bundle args = new Bundle();
          args.putBoolean(PersonalIdEmailFragment.ARG_IS_LEGACY_FLOW, true);
          navHostFragment.getNavController().navigate(R.id.personalid_email, args,
                  new androidx.navigation.NavOptions.Builder()
                          .setPopUpTo(R.id.personalid_phone_fragment, true)
                          .build());
      }
  }
  ```

  Add import at top:
  ```java
  import org.commcare.fragments.personalId.PersonalIdEmailFragment;
  ```

- [ ] **Step 7.3: Build to verify compilation**

  Run: `./gradlew assembleCommcareDebug`
  Expected: BUILD SUCCESSFUL (Safe Args now regenerates Directions classes for both `personalid_backup_code` — matching the fragment edits from Task 6c.4 — and the new email destinations.)

- [ ] **Step 7.4: Commit**

  ```bash
  git add app/res/navigation/nav_graph_personalid.xml \
          app/src/org/commcare/activities/connect/PersonalIdActivity.java
  git commit -m "[AI] Wire email entry step into PersonalID nav graph and legacy activity entry"
  ```

---

## Chunk 6: Legacy User Email Prompt

### Task 8: Add email offer logic to PersonalIdManager and LoginActivity


**Files:**
- Modify: `app/src/org/commcare/connect/PersonalIdManager.java`
- Modify: `app/src/org/commcare/activities/LoginActivity.java` (find where `handleFinishedActivity` is called and add the email check after it)

- [ ] **Step 8.1: Implement shouldOfferEmail() in PersonalIdManager.java (makes test from Step 4.1 pass)**

  Add the following to `PersonalIdManager.java`. The offer policy now reads from `PersonalIDPreferences`, not `ConnectUserRecord` — so `shouldOfferEmail` takes a `Context` rather than a record:

  ```java
  // Expose as package-private static for testability
  static boolean shouldOfferEmail(Context context) {
      Boolean verified = PersonalIDPreferences.isEmailVerified(context);
      if (verified != null && verified) {
          return false;
      }

      Integer count = PersonalIDPreferences.getEmailOfferCount(context);
      if (count == null || count == 0) {
          return true; // Never shown — show first offer
      }
      if (count >= 2) {
          return false; // Both offers already shown — stop
      }

      // count == 1: show second offer only after 30-day gap
      Date lastOffer = PersonalIDPreferences.getLastEmailOfferDate(context);
      if (lastOffer == null) {
          return true; // count was 1 but no date recorded — be permissive
      }
      long millis = new Date().getTime() - lastOffer.getTime();
      long days = TimeUnit.DAYS.convert(millis, TimeUnit.MILLISECONDS);
      return days >= DAYS_TO_SECOND_OFFER;
  }

  public void checkEmailCollection(CommCareActivity<?> activity) {
      if (!shouldOfferEmail(activity)) {
          return;
      }

      // Increment count and record date BEFORE showing dialog (so the offer is recorded
      // even if the user dismisses the dialog by swiping away or backing out).
      Integer current = PersonalIDPreferences.getEmailOfferCount(activity);
      PersonalIDPreferences.setEmailOfferCount(activity, (current == null ? 0 : current) + 1);
      PersonalIDPreferences.setLastEmailOfferDate(activity, new Date());

      showEmailOfferDialog(activity);
  }

  private void showEmailOfferDialog(CommCareActivity<?> activity) {
      StandardAlertDialog dialog = new StandardAlertDialog(
              activity.getString(R.string.personalid_email_offer_title),
              activity.getString(R.string.personalid_email_offer_message));

      dialog.setPositiveButton(activity.getString(R.string.personalid_email_offer_yes), (d, w) -> {
          activity.dismissAlertDialog();
          launchPersonalIdForEmailCollection(activity);
      });

      dialog.setNegativeButton(activity.getString(R.string.personalid_email_offer_no), (d, w) -> {
          activity.dismissAlertDialog();
      });

      activity.showAlertDialog(dialog);
  }

  private void launchPersonalIdForEmailCollection(CommCareActivity<?> activity) {
      Intent intent = new Intent(activity, PersonalIdActivity.class);
      intent.putExtra(PersonalIdActivity.EXTRA_LEGACY_EMAIL_FLOW, true);
      activity.startActivity(intent);
  }
  ```

  Add string resources:
  ```xml
  <string name="personalid_email_offer_title">Add your email address</string>
  <string name="personalid_email_offer_message">Add an email to help recover your account if you lose access to your phone.</string>
  <string name="personalid_email_offer_yes">Add email</string>
  <string name="personalid_email_offer_no">Not now</string>
  ```

- [ ] **Step 8.2: Run the unit test to verify it passes**

  Run: `./gradlew testCommcareDebug --tests "org.commcare.connect.PersonalIdEmailOfferTest"`
  Expected: PASS

- [ ] **Step 8.3: Call checkEmailCollection in LoginActivity**

  Find the location in `LoginActivity` where `PersonalIdManager.getInstance().handleFinishedActivity(...)` is called. Immediately after that call, add:

  ```java
  PersonalIdManager.getInstance().checkEmailCollection(this);
  ```

  > **Note:** This hook fires only when the user has just completed a PersonalID signup/login flow (i.e., after `PersonalIdActivity` returns `RESULT_OK`). For legacy users — who have long completed their one-time PersonalID signup — this code path is never re-entered, so this hook alone is insufficient. Step 8.4 adds a second hook for app-open events.

- [ ] **Step 8.4: Call checkEmailCollection in StandardHomeActivity**

  A legacy user (upgrading from a pre-email build) typically won't go through the PersonalID signup flow again, so the `LoginActivity.onActivityResult` hook from Step 8.3 never fires for them. Add a second hook on the CommCare app home screen so the prompt has a chance to appear on every app open.

  In `app/src/org/commcare/activities/StandardHomeActivity.java`, add to `onCreate(Bundle)` after `super.onCreate(...)`:

  ```java
  PersonalIdManager.getInstance().checkEmailCollection(this);
  ```

  Add the import if missing:
  ```java
  import org.commcare.connect.PersonalIdManager;
  ```

  > **Why `onCreate` and not `onResume`:** `onResume` fires every time the user returns to home (including after completing a form or backing out of a module), which would make the dialog feel intrusive. `onCreate` runs once per activity instance — typically once per app-open — so the prompt appears at most once per app-launch. `shouldOfferEmail()` independently enforces the 30-day cadence, so even if the activity is recreated mid-session (e.g., configuration change), the dialog will not spam the user.

  > **Known gap:** If a PersonalID-primary user never opens a CommCare app (i.e., stays within the PersonalID / Connect drawer UI), this hook does not fire. Revisit if product flags it — options include adding a hook to the Connect landing screen or centralising via `CommCareSessionService`.

- [ ] **Step 8.5: Build to verify compilation**

  Run: `./gradlew assembleCommcareDebug`
  Expected: BUILD SUCCESSFUL

- [ ] **Step 8.6: Commit**

  ```bash
  git add app/src/org/commcare/connect/PersonalIdManager.java \
          app/src/org/commcare/activities/LoginActivity.java \
          app/src/org/commcare/activities/StandardHomeActivity.java \
          app/res/values/strings.xml \
          app/unit-tests/src/org/commcare/connect/PersonalIdEmailOfferTest.kt
  git commit -m "[AI] Add legacy user email collection prompt (PersonalIdManager, LoginActivity, StandardHomeActivity)"
  ```

---

### Task 8b: Clear PersonalIDPreferences on logout

The three email flags (`emailVerified`, `emailOfferCount`, `lastEmailOfferDate`) must be wiped when the user logs out of PersonalID so the next signed-in account starts with a clean slate. `ConnectUserRecord.email` is already removed from the DB by the existing logout path (the `connect` database is cleared); the prefs are not on that path today.

**Files:**
- Modify: `app/src/org/commcare/connect/PersonalIdManager.java` (or whichever class owns the PersonalID logout path — see Step 8b.1)

- [ ] **Step 8b.1: Locate the PersonalID logout path**

  The PersonalID logout is typically reached via `PersonalIdManager`. Search for the method that runs on user-initiated logout. Candidates to grep for:

  ```bash
  grep -rn "logout\|signOut\|forgetUser\|clearUserData\|ConnectDatabaseUtil.*delete" \
      app/src/org/commcare/connect/ app/src/org/commcare/activities/connect/
  ```

  Identify the single entry point that runs on PersonalID logout (there may be more than one call-site, but a single shared method is likely). That method is where the prefs wipe belongs. If there is no shared method, create a new `PersonalIdManager.onPersonalIdLogout(Context)` that the existing call-sites call into.

- [ ] **Step 8b.2: Add the prefs wipe**

  Inside the logout method, call:

  ```java
  PersonalIDPreferences.clear(context);
  ```

  Add the import:
  ```java
  import org.commcare.connect.PersonalIDPreferences;
  ```

  > **Ordering:** Clear the prefs AFTER the DB-side user deletion, so that an exception raised by the DB code does not leave the prefs in an inconsistent "user gone but email flags still present" state. If the DB delete throws, abort logout and surface the error as today.

- [ ] **Step 8b.3: Write a Robolectric unit test for the logout wipe**

  Add to `app/unit-tests/src/org/commcare/connect/PersonalIdEmailOfferTest.kt` (or a dedicated `PersonalIdManagerLogoutTest.kt` if the class grows):

  ```kotlin
  @Test
  fun `logout clears email preferences`() {
      PersonalIDPreferences.setEmailVerified(context, true)
      PersonalIDPreferences.setEmailOfferCount(context, 2)
      PersonalIDPreferences.setLastEmailOfferDate(context, Date())

      // Invoke whichever method runs the PersonalID logout path (replace with real name)
      PersonalIdManager.getInstance().onPersonalIdLogout(context)

      assertNull(PersonalIDPreferences.isEmailVerified(context))
      assertNull(PersonalIDPreferences.getEmailOfferCount(context))
      assertNull(PersonalIDPreferences.getLastEmailOfferDate(context))
  }
  ```

  Run: `./gradlew testCommcareDebug --tests "org.commcare.connect.PersonalIdEmailOfferTest"`
  Expected: PASS.

- [ ] **Step 8b.4: Commit**

  ```bash
  git add app/src/org/commcare/connect/PersonalIdManager.java \
          app/unit-tests/src/org/commcare/connect/PersonalIdEmailOfferTest.kt
  git commit -m "[AI] Clear PersonalIDPreferences on PersonalID logout"
  ```

---

## Chunk 7: Final Verification

### Task 9: Full build and test run


- [ ] **Step 9.1: Run all unit tests**

  Run: `./gradlew testCommcareDebug`
  Expected: All tests pass. Fix any failures before proceeding.

- [ ] **Step 9.2: Lint / code quality**

  Run ktlint on all new/modified Kotlin files:
  ```bash
  ktlint --format \
    app/src/org/commcare/fragments/personalId/PersonalIdEmailFragment.kt \
    app/src/org/commcare/fragments/personalId/PersonalIdEmailVerificationFragment.kt \
    app/src/org/commcare/android/database/connect/models/PersonalIdSessionData.kt \
    app/src/org/commcare/connect/network/connectId/parser/SendEmailOtpResponseParser.kt \
    app/src/org/commcare/connect/network/connectId/parser/VerifyEmailOtpResponseParser.kt \
    app/unit-tests/src/org/commcare/connect/PersonalIdEmailOfferTest.kt \
    app/unit-tests/src/org/commcare/connect/ConnectUserRecordMigrationV24Test.kt
  ```

  Verify Java files compile cleanly (checkstyle is run in CI).

- [ ] **Step 9.3: Verify end-to-end new user flow manually (if emulator available)**

  Steps:
  1. Start fresh signup on PersonalID
  2. Complete Phone → Biometrics → OTP → Name → Backup Code steps
  3. Confirm the Email entry screen appears
  4. Enter a valid email address → Continue
  5. Confirm Email OTP screen appears
  6. Enter OTP code → Verify
  7. Confirm navigation proceeds to Photo Capture screen
  8. Complete signup to end

  Also verify skip path:
  1. On Email entry screen, tap "Skip for now"
  2. Confirm navigation goes directly to Photo Capture

  Also verify 3-failed-OTP path:
  1. Enter email → Continue → Email OTP screen appears
  2. Enter wrong code 3 times
  3. Confirm dialog appears: "Verification unsuccessful" with "Try again" and "Proceed without email" buttons
  4. Tap "Proceed without email" → navigation goes to Photo Capture
  5. Repeat steps 1–3, but tap "Try again" → dialog dismisses, code input is cleared, counter resets, user can attempt again

  Also verify recovery path:
  1. Start recovery on PersonalID (existing user on a new device)
  2. Complete recovery steps through Backup Code — after a successful backup-code validation, confirm the Email entry screen appears (NOT the recovery-success screen).
  3a. Enter a valid email → Continue → Verify OTP → confirm the recovery-success screen appears and that `ConnectUserRecord` now has `email` populated + `emailVerified = true`.
  3b. Alternative: tap "Skip for now" on the Email entry screen → confirm the recovery-success screen appears immediately; `ConnectUserRecord` has `email = null`, `emailVerified = false`, `emailOfferCount = 1`, `lastEmailOfferDate = now` (so the legacy prompt only fires after 30 days).
  4. Also exercise the 3-failed-OTP → "Proceed without email" branch during recovery: confirm the recovery-success screen still appears and the record is written with skip-state.

- [ ] **Step 9.4: Verify legacy user prompt manually (if emulator available)**

  Prerequisites: install the upgrade on a device that has an existing PersonalID account (no `email` on record, and `PersonalIDPreferences` is empty because none of the three keys were ever written). This mirrors real v24 → v25 upgrades.

  Steps (StandardHomeActivity-triggered path — the dominant legacy case):
  1. Open the CommCare app — routes through `DispatchActivity` → CommCare login → `StandardHomeActivity`.
  2. Confirm the email offer dialog appears as `StandardHomeActivity.onCreate` fires.
  3. Tap "Add email" → `PersonalIdActivity` launches at email-entry screen (`EXTRA_LEGACY_EMAIL_FLOW = true`).
  4. Complete email + OTP flow → activity finishes; `PersonalIDPreferences.isEmailVerified` should now be `true`.
  5. Reopen the CommCare app → confirm dialog does NOT appear (verified = true short-circuits).

  Also verify dismissal + 30-day gap:
  1. Open app → dialog appears → tap "Not now".
  2. Inspect `personalid_prefs` file (`adb shell run-as org.commcare.dalvik cat shared_prefs/personalid_prefs.xml`): `email_offer_count = 1`, `last_email_offer_date` ≈ now.
  3. Reopen the app within 30 days → confirm dialog does NOT appear (count=1, date recent).
  4. Advance the device clock (or overwrite `last_email_offer_date` to >30 days ago) → reopen the app → confirm second dialog appears.
  5. Dismiss the second dialog → count becomes 2 → further app-opens never show the dialog again.

  Also verify the LoginActivity-triggered path (for users who are completing a fresh PersonalID signup/login, not the StandardHomeActivity path):
  1. Complete a full PersonalID signup and skip the email step.
  2. Confirm that on the FIRST app-open after signup the dialog does NOT appear (count=1, date recent — set by PhotoCapture per Step 6c.1).
  3. Advance the clock >30 days, reopen → dialog appears from `StandardHomeActivity.onCreate` as the second offer.

- [ ] **Step 9.5: Final cleanup commit (if any)**

  Remove unused imports and dead code found during testing. Run ktlint once more. Commit as a separate cleanup commit.

---

## Implementation Notes

### Backend API Confirmation Required
Before implementing Steps 4.2–4.7, confirm with the backend team:
- Exact endpoint paths (provisional: `/users/send_email_otp`, `/users/verify_email_otp`)
- Request body parameter names (provisional: `email`, `otp`)
- Response body structure (to update the parsers accordingly)
- Error codes for invalid email, invalid OTP, expired OTP, and rate limiting

### Error Code Additions
The backend may return new error codes for email OTP (e.g., `INVALID_EMAIL`, `EXPIRED_OTP`). When those are confirmed, add handling in `PersonalIdApiHandler.handleErrorCodeIfPresent()` and `PersonalIdOrConnectApiErrorHandler`.

### ConnectUserDatabaseUtil.storeUser()
The correct write method is `ConnectUserDatabaseUtil.storeUser(Context context, ConnectUserRecord user)`. There is no `saveUser` method — any reference to `saveUser` in this plan is an error. All usages have been corrected to `storeUser`.

### Release Toggle
If the product requires this feature to be guarded by a release toggle (`ConnectReleaseToggleRecord`), add a toggle check inside `PersonalIdBackupCodeFragment.navigateToEmail(...)` (introduced in Task 6c.4). Note that Task 6c.4 and Task 7.1 **delete** `navigateToSuccess()` / `handleSuccessfulRecovery()` / `actionPersonalidBackupcodeToPersonalidPhotoCapture()` — the fallbacks below are written to NOT depend on those symbols.

- When the toggle is **off and `isRecovery` is false** (signup), navigate to Photo Capture by destination id:
  ```java
  Navigation.findNavController(binding.getRoot()).navigate(R.id.personalid_photo_capture);
  ```
- When the toggle is **off and `isRecovery` is true** (recovery), run the finalizer helper directly and navigate to the shared recovery-success message destination using the existing `navigateWithMessage(...)` helper (which is NOT deleted):
  ```java
  personalIdSessionData.setBackupCode(binding.backupCodeView.getCodeValue());
  PersonalIdRecoveryCompleter.finalizeAccountRecovery(requireActivity(), personalIdSessionData);
  navigateWithMessage(
          getString(R.string.connect_recovery_success_title),
          getString(R.string.connect_recovery_success_message),
          ConnectConstants.PERSONALID_RECOVERY_SUCCESS);
  ```

Confirm with the team whether a toggle is needed before wiring either fallback.
---

# Plan: Create Jira Sub-task Tickets for CCCT-2204

## Context
CCCT-2204 ("Tech Spec - Adding email to PersonalID signup / recovery flow") requires implementation tickets to be created as an acceptance criterion. The implementation plan above defines 7 logical chunks of Android work plus the backend endpoints it depends on. This section defines 9 Jira Sub-tasks to be created under parent **CCCT-2204** (project id: 10229). Tickets 1–8 are Mobile; Ticket 9 is Web/Backend (CommCare Connect server).

**Jira metadata:**
- API base: `https://dimagi.atlassian.net/rest/api/3`
- Project id: `10229` (CCCT)
- Sub-task issue type id: `10007`
- Parent: `CCCT-2204`
- Component: `Mobile` for Tickets 1–8, `Web` for Ticket 9

---

## Sub-tasks to Create

### Ticket 1 — Persistence Layer (DB column + SharedPreferences)
**Summary:** `[Android] Add email column to ConnectUserRecord (DB v25) and PersonalIDPreferences for email flags`
**Description:**
Email state is persisted in two places: `email` lives on `ConnectUserRecord` (one new DB column), and the three supporting flags — `emailVerified`, `emailOfferCount`, `lastEmailOfferDate` — live in a dedicated `personalid_prefs` SharedPreferences file accessed through a new Kotlin `object` wrapper. Both are delivered together because they are read/written as a set by the signup, recovery, legacy-prompt, and logout paths.

**Database (new `email` column):**
- Create `ConnectUserRecordV24.java` (snapshot of current record for migration source).
- Add a single field to `ConnectUserRecord.java`: `email` (String nullable) with `META_EMAIL` constant and getter/setter. Do NOT add fields for the other three flags — they live in `PersonalIDPreferences`.
- Add `fromV24()` factory method to `ConnectUserRecord` (copies all v24 fields, defaults `email` to null).
- Add `upgradeTwentyFourTwentyFive()` to `ConnectDatabaseUpgrader.java` — adds one TEXT column and migrates every existing record.
- Bump `CONNECT_DB_VERSION` from 24 → 25 in `DatabaseConnectOpenHelper.java`.
- Unit test: `ConnectUserRecordMigrationV24Test.kt` — asserts `email` defaults to null after `fromV24()`.

**SharedPreferences (`PersonalIDPreferences` wrapper):**
- Create `PersonalIDPreferences.kt` as a Kotlin `object` at `app/src/org/commcare/connect/`. Backing file: `personalid_prefs`. Keys: `email_verified` (Boolean), `email_offer_count` (Int), `last_email_offer_date` (Long millis → `Date?`).
- Null-aware: `@JvmStatic` getters return `Boolean?` / `Int?` / `Date?` — an absent key returns null (distinguishable from false/0/epoch). Setters accept null to remove the key. `clear(context)` wipes the entire file (called on PersonalID logout — see Ticket 8).
- Unit test: `PersonalIDPreferencesTest.kt` (Robolectric) covers null-on-empty, round-trip, null-removes-key, clear-wipes-all, and the explicit-false-vs-null distinction.

**Files:** `ConnectUserRecord.java`, `ConnectUserRecordV24.java`, `ConnectDatabaseUpgrader.java`, `DatabaseConnectOpenHelper.java`, `ConnectUserRecordMigrationV24Test.kt`, `PersonalIDPreferences.kt`, `PersonalIDPreferencesTest.kt`

---

### Ticket 2 — Session Data & API Layer
**Summary:** `[Android] Add email OTP API endpoints and update PersonalIdSessionData`
**Description:**
- Add `email`, `emailVerified`, `emailSkippedDuringSignup` fields to `PersonalIdSessionData.kt`
- Add `/users/send_email_otp` and `/users/verify_email_otp` to `ApiEndPoints.java`
- Add `sendEmailOtp()` / `verifyEmailOtp()` Retrofit methods to `ApiService.java`
- Add static methods to `ApiPersonalId.java`
- Create `SendEmailOtpResponseParser.kt` and `VerifyEmailOtpResponseParser.kt`
- Add `sendEmailOtpCall()` / `verifyEmailOtpCall()` to `PersonalIdApiHandler.java`

⚠️ **Dependency:** Confirm actual server endpoint paths with backend before implementing.

**Files:** `PersonalIdSessionData.kt`, `ApiEndPoints.java`, `ApiService.java`, `ApiPersonalId.java`, `PersonalIdApiHandler.java`, two new parser classes

---

### Ticket 3 — Email Entry Fragment
**Summary:** `[Android] Create PersonalIdEmailFragment for email entry step (signup + recovery)`
**Description:**
- Create `fragment_personalid_email.xml` with email TextInputEditText, Continue button, Skip button, error TextView, ScrollView
- Create `PersonalIdEmailFragment.kt` extending `BasePersonalIdFragment`:
  - Accepts two nav args: `isLegacyFlow` and `isRecovery`
  - Validates email format using `Patterns.EMAIL_ADDRESS` before enabling Continue
  - Continue: calls `sendEmailOtpCall()` → navigates to email OTP screen (passing both flags)
  - Skip branches: legacy → `activity.finish()`; recovery → `PersonalIdRecoveryCompleter.finalizeAccountRecovery(...)` + navigate to recovery-success message; signup → navigate to photo capture (records the skip via `emailSkippedDuringSignup = true`)
- Add string resources for title, description, hint, skip, error

**Files:** `PersonalIdEmailFragment.kt`, `fragment_personalid_email.xml`, `strings.xml`

---

### Ticket 4 — Email Format Validation & Keyboard Enter Handling
**Summary:** `[Android] Add client-side email validation and keyboard enter handling`
**Description:**
- Unit tests: `PersonalIdEmailValidationTest.kt` covering valid, blank, and malformed email formats
- Wire `setUpEnterKeyAction(binding.emailTextValue)` in `PersonalIdEmailFragment`:
  - Override `keyboardEnterPressed()`: call `submitEmail()` if valid, else hide keyboard
- `PersonalIdEmailVerificationFragment` uses `NumericCodeView.setOnEnterKeyPressedListener` (set up in Step 6.2), so no additional keyboard wiring is needed
- Guard at top of `submitEmail()`: show inline error and return if format is invalid (defensive, since button is already gated)

**Files:** `PersonalIdEmailFragment.kt`, `PersonalIdEmailVerificationFragment.kt`, `PersonalIdEmailValidationTest.kt`
**Depends on:** Ticket 3, Ticket 5

---

### Ticket 5 — Email OTP Verification Fragment
**Summary:** `[Android] Create PersonalIdEmailVerificationFragment for email OTP step`
**Description:**
- Create `fragment_personalid_email_verification.xml` with `NumericCodeView` (6-digit OTP input), Verify button, Resend button, countdown TextView, description TextView, error TextView
- Create `PersonalIdEmailVerificationFragment.kt` extending `BasePersonalIdFragment`:
  - Accepts two nav args: `isLegacyFlow` and `isRecovery`
  - Resend button with 2-minute cooldown timer
  - On OTP success: set `personalIdSessionData.emailVerified = true`, then branch on legacy/recovery/signup
  - After 3 failed OTP attempts: show `StandardAlertDialog` offering to proceed without email or retry. "Proceed without email" sets `emailSkippedDuringSignup = true` and branches the same way. "Try again" resets the counter and clears the code input.
  - Legacy flow: writes email + emailVerified directly to the existing `ConnectUserRecord`, then `activity.finish()`
  - Recovery flow: calls `PersonalIdRecoveryCompleter.finalizeAccountRecovery(...)` and navigates to the recovery-success message destination
  - New signup flow: navigates to photo capture (ConnectUserRecord written later by `PersonalIdPhotoCaptureFragment`)
- Add string resources

**Files:** `PersonalIdEmailVerificationFragment.kt`, `fragment_personalid_email_verification.xml`, `strings.xml`

---

### Ticket 6 — Signup & Recovery Completion Integration
**Summary:** `[Android] Propagate email into ConnectUserRecord + flags into PersonalIDPreferences, extract recovery finalization`
**Description:**
Both signup and recovery now route through the Email step before persisting. `email` is written to `ConnectUserRecord`; `emailVerified`, `emailOfferCount`, `lastEmailOfferDate` are written to `PersonalIDPreferences`. This ticket:

- `PersonalIdPhotoCaptureFragment.createAndSaveConnectUser()` (signup): after `storeUser()`, call `PersonalIDPreferences.setEmailVerified(context, sessionData.emailVerified)`; if `emailSkippedDuringSignup`, also `setEmailOfferCount(context, 1)` + `setLastEmailOfferDate(context, new Date())`, otherwise set both to null.
- Create `PersonalIdRecoveryCompleter.kt` (recovery): extracts the body of the existing `PersonalIdBackupCodeFragment.handleSuccessfulRecovery()` (DB passphrase, `ConnectUserRecord.setEmail(...)` + storeUser, `PersonalIDPreferences` writes, `logRecoveryResult(true)`, second-device-login notification). Does **not** navigate — the caller handles navigation. Reads `backupCode` from session data.
- `PersonalIdBackupCodeFragment.java`: store `backupCode` on session data in both paths; replace `navigateToPhoto()` with `navigateToEmail(false)` for signup and replace the `handleSuccessfulRecovery()` call with `navigateToEmail(true)` for recovery. Delete the now-unused `handleSuccessfulRecovery()`, `logRecoveryResult()`, `handleSecondDeviceLogin()`, and `navigateToSuccess()` methods (inline the single `logRecoveryResult(false)` call from `handleFailedBackupCodeAttempt()`).

**Files:** `PersonalIdPhotoCaptureFragment.java`, `PersonalIdRecoveryCompleter.kt`, `PersonalIdBackupCodeFragment.java`
**Depends on:** Ticket 1, Ticket 2

---

### Ticket 7 — Navigation & Activity Updates
**Summary:** `[Android] Wire email step into PersonalID nav graph and activity`
**Description:**
- `nav_graph_personalid.xml`:
  - Add `personalid_email` fragment destination with `isLegacyFlow` + `isRecovery` boolean args (both default false)
  - Add `personalid_email_verification` fragment destination with the same args
  - Add actions: email → email-verification (carrying both flags), email → photo capture (signup skip), email-verification → photo capture (signup success), email → message-display and email-verification → message-display (used for the recovery-success screen)
  - Replace the existing `action_personalid_backupcode_to_personalid_photo_capture` on `personalid_backup_code` with `action_personalid_backupcode_to_personalid_email` (carrying `isRecovery` arg)
- `PersonalIdActivity.java`: add `EXTRA_LEGACY_EMAIL_FLOW` intent extra; on receipt, navigate NavController to `personalid_email` with `isLegacyFlow = true`, popping phone fragment from back stack

(Fragment edits for `PersonalIdBackupCodeFragment` are covered by Ticket 6.)

**Files:** `nav_graph_personalid.xml`, `PersonalIdActivity.java`
**Depends on:** Ticket 3, Ticket 5, Ticket 6

---

### Ticket 8 — Legacy User Email Prompt + Logout Wipe
**Summary:** `[Android] Add post-login email collection prompt and clear prefs on logout`
**Description:**
Two-offer-with-30-day-gap dialog shown to users whose `PersonalIDPreferences.isEmailVerified(context)` is null or false after PersonalID login. Plus a logout hook that wipes the prefs file.

- `PersonalIdManager.java`:
  - Add `static boolean shouldOfferEmail(Context context)`. All state reads come from `PersonalIDPreferences`:
    - `isEmailVerified == true` → false (never offer)
    - `emailOfferCount` null or 0 → true (first offer; legacy v24 users land here)
    - `emailOfferCount >= 2` → false (exhausted)
    - `emailOfferCount == 1` → true only if `lastEmailOfferDate` is null or > 30 days ago
  - Add `checkEmailCollection(CommCareActivity)`: checks `shouldOfferEmail`, increments `emailOfferCount` via `PersonalIDPreferences.setEmailOfferCount(...)`, updates `lastEmailOfferDate`, shows `StandardAlertDialog`.
  - On dialog accept: launch `PersonalIdActivity` with `EXTRA_LEGACY_EMAIL_FLOW = true`.
  - **Logout wipe:** call `PersonalIDPreferences.clear(context)` in the PersonalID logout path (locate the single entry point — `PersonalIdManager.onPersonalIdLogout(Context)` or similar — and wire the call in AFTER the existing DB-side user deletion). Add the method if no shared entry point exists.
- `LoginActivity.java`: call `checkEmailCollection()` after a successful PersonalID signup/login (in `onActivityResult` success branch, after `handleFinishedActivity()`). This hook only fires for users who have just completed PersonalID signup/login.
- `StandardHomeActivity.java`: call `checkEmailCollection()` in `onCreate(Bundle)`. This catches legacy users who completed PersonalID signup long ago — they hit this every time they open a CommCare app. Do NOT use `onResume` (would spam on every return-to-home). `shouldOfferEmail` independently enforces the 30-day cadence. **Known gap:** a PersonalID-primary user who never opens a CommCare app won't hit this hook; revisit if product flags it.
- Unit tests: `PersonalIdEmailOfferTest.kt` (Robolectric) covers all offer-count and date combinations AND the logout-clears-prefs case.

**Files:** `PersonalIdManager.java`, `LoginActivity.java`, `StandardHomeActivity.java`, `PersonalIdEmailOfferTest.kt`
**Depends on:** Ticket 1, Ticket 7

---

### Ticket 9 — Web/Backend Email OTP API
**Summary:** `[Web] Add /users/send_email_otp and /users/verify_email_otp endpoints for PersonalID email verification`
**Component:** Web (CommCare Connect server)

**Description:**
Expose two new authenticated HTTP endpoints on the CommCare Connect server that the Android PersonalID client will call during signup and legacy-user email collection. The endpoints issue and verify a time-limited OTP sent to a user-supplied email address.

**Authentication:**
- Both endpoints require the standard PersonalID token (same Bearer token scheme used by the existing `/users/*` endpoints such as `validate_secondary_phone` and `confirm_secondary_phone`).
- Header: `Authorization: Bearer <personal_id_token>`

**Endpoint 1 — POST `/users/send_email_otp`**

Sends a one-time verification code to the given email address.

- Request body (JSON):
  ```json
  {
    "email": "user@example.com"
  }
  ```
- Field rules:
  - `email` (string, required): must pass server-side email format validation; must not already be verified against a different PersonalID account (return `409 CONFLICT` if so).
- Response: `200 OK` on success; body is optional. Suggested payload for forward-compatibility:
  ```json
  {
    "otp_expires_in_seconds": 300,
    "resend_available_in_seconds": 120
  }
  ```
- Error responses:
  - `400 BAD_REQUEST` — malformed email
  - `401 UNAUTHORIZED` — missing/invalid token
  - `409 CONFLICT` — email already verified on another account
  - `429 TOO_MANY_REQUESTS` — resend requested before cooldown elapsed (client cooldown is 2 minutes — server should enforce independently)

**Endpoint 2 — POST `/users/verify_email_otp`**

Verifies the OTP entered by the user and marks the email as verified on the PersonalID account.

- Request body (JSON):
  ```json
  {
    "email": "user@example.com",
    "otp": "123456"
  }
  ```
- Field rules:
  - `email` (string, required): must match the email the OTP was issued to.
  - `otp` (string, required): 6-digit numeric code.
- Response: `200 OK` on successful verification. Server persists `email` and `email_verified = true` on the user record. Suggested payload:
  ```json
  {
    "email_verified": true
  }
  ```
- Error responses:
  - `400 BAD_REQUEST` — malformed email or OTP
  - `401 UNAUTHORIZED` — missing/invalid token
  - `403 FORBIDDEN` — OTP incorrect (client tolerates up to 3 attempts before offering to skip)
  - `410 GONE` — OTP expired
  - `429 TOO_MANY_REQUESTS` — too many verification attempts

**Server-side behaviour:**
- Generate a 6-digit numeric OTP, valid for ~5 minutes.
- Store OTP hashed (not plaintext), associated with `(personal_id_user, email)`.
- Invalidate prior OTPs for the same user when a new one is issued.
- Rate-limit resend per user and per email (suggested: 2-minute minimum between sends).
- On successful verification, set `email` and `email_verified = true` on the PersonalID user record.
- Send the OTP via the existing transactional email provider; subject and body copy to be coordinated with product.

**Coordination notes:**
- Android Ticket 2 depends on these endpoints and their payload shape. Confirm final endpoint paths and field names before Android implementation begins — the Android plan currently treats them as provisional.
- Endpoints must be deployed (at minimum to staging) before Android Ticket 2 integration testing.

**Depends on:** none (upstream of Android Ticket 2)

---

## Dependency Order

```
Ticket 9 (Web API) ──► Ticket 2 (Android API/Session)

Ticket 1 (Persistence: DB column + PersonalIDPreferences)
    ├── Ticket 2 (API/Session)
    │       ├── Ticket 3 (Email Entry Fragment)
    │       │       ├── Ticket 4 (Validation + Keyboard)
    │       │       └── Ticket 7 (Navigation)
    │       ├── Ticket 5 (OTP Fragment)
    │       │       ├── Ticket 4 (Validation + Keyboard)
    │       │       └── Ticket 7 (Navigation)
    │       └── Ticket 6 (Signup + Recovery Completion)
    └── Ticket 8 (Legacy Prompt + Logout Wipe)  ◄── also depends on Ticket 7
```

Safe parallel work:
- Ticket 9 (Web) can be built in parallel with Android Ticket 1 — they are fully independent.
- Tickets 3 and 5 can be built concurrently after Ticket 2.
