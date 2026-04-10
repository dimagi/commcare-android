# Add Email to PersonalID Signup / Recovery Flow — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Insert an optional email-entry + OTP-verification step into the PersonalID signup flow (between Name and Backup Code), and offer a non-blocking email-collection prompt to existing users who completed signup without an email.

**Architecture:** New Kotlin fragments (`PersonalIdEmailFragment`, `PersonalIdEmailVerificationFragment`) extend the existing `BasePersonalIdFragment` pattern. Email state is stored in `PersonalIdSessionData` during signup and persisted to `ConnectUserRecord` (new DB columns) upon verification. The legacy prompt uses a two-offer-with-30-day-gap policy tracked via `emailOfferCount` (int) and `lastEmailOfferDate` (Date) on `ConnectUserRecord`.

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
| `app/unit-tests/src/org/commcare/connect/PersonalIdEmailOfferTest.kt` | Unit tests for evaluateEmailOffer logic |
| `app/unit-tests/src/org/commcare/connect/PersonalIdEmailValidationTest.kt` | Unit tests for client-side email format validation |

### Files to Modify
| File | Change |
|------|--------|
| `app/src/org/commcare/android/database/connect/models/ConnectUserRecord.java` | Add email, emailVerified, emailOfferCount, lastEmailOfferDate fields |
| `app/src/org/commcare/android/database/connect/models/PersonalIdSessionData.kt` | Add email and emailVerified fields |
| `app/src/org/commcare/fragments/personalId/PersonalIdPhotoCaptureFragment.java` | Read email/emailVerified from session data when constructing ConnectUserRecord |
| `app/src/org/commcare/fragments/personalId/PersonalIdBackupCodeFragment.java` | Read email/emailVerified from session data in handleSuccessfulRecovery() |
| `app/src/org/commcare/models/database/connect/DatabaseConnectOpenHelper.java` | Bump CONNECT_DB_VERSION to 25 |
| `app/src/org/commcare/models/database/connect/ConnectDatabaseUpgrader.java` | Add upgradeTwentyFourTwentyFive() |
| `app/src/org/commcare/connect/network/ApiEndPoints.java` | Add sendEmailOtp and verifyEmailOtp endpoints |
| `app/src/org/commcare/connect/network/ApiService.java` | Add sendEmailOtp and verifyEmailOtp service methods |
| `app/src/org/commcare/connect/network/ApiPersonalId.java` | Add sendEmailOtp() and verifyEmailOtp() static methods |
| `app/src/org/commcare/connect/network/connectId/PersonalIdApiHandler.java` | Add sendEmailOtpCall() and verifyEmailOtpCall() |
| `app/res/navigation/nav_graph_personalid.xml` | Add email and email-OTP destinations; reroute Name → Email |
| `app/src/org/commcare/fragments/personalId/PersonalIdNameFragment.java` | Update navigate action to go to email instead of backup code |
| `app/src/org/commcare/activities/connect/PersonalIdActivity.java` | Handle EXTRA_LEGACY_EMAIL_FLOW intent extra |
| `app/src/org/commcare/connect/PersonalIdManager.java` | Add checkEmailCollection() and evaluateEmailOffer() |
| `app/src/org/commcare/activities/LoginActivity.java` | Call checkEmailCollection() after PersonalID login |

---

## Chunk 1: Database Layer

### Task 1: Create ConnectUserRecordV24 (migration snapshot)

**Files:**
- Create: `app/src/org/commcare/android/database/connect/models/ConnectUserRecordV24.java`

- [ ] **Step 1.1: Write the failing unit test**

  Create `app/unit-tests/src/org/commcare/connect/ConnectUserRecordMigrationV24Test.kt`:

  ```kotlin
  package org.commcare.connect

  import org.junit.Assert.assertEquals
  import org.junit.Assert.assertNull
  import org.junit.Test

  class ConnectUserRecordMigrationV24Test {

      @Test
      fun `fromV24 copies all fields and sets email defaults`() {
          val old = ConnectUserRecordV24().apply {
              // Verify V24 compiles with fields 1-16 only (no email fields)
          }
          val new = ConnectUserRecord.fromV24(old)
          assertNull(new.email)
          assertEquals(false, new.emailVerified)
          assertNull(new.emailOfferDate1)
          assertNull(new.emailOfferDate2)
      }
  }
  ```

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

- [ ] **Step 1.3: Add email fields and fromV24() to ConnectUserRecord.java**

  In `ConnectUserRecord.java`, after field `@Persisting(value = 16)`:

  ```java
  public static final String META_EMAIL = "email";
  public static final String META_EMAIL_VERIFIED = "email_verified";
  public static final String META_EMAIL_OFFER_COUNT = "email_offer_count";
  public static final String META_LAST_EMAIL_OFFER_DATE = "last_email_offer_date";

  @Persisting(value = 17, nullable = true)
  @MetaField(META_EMAIL)
  private String email;

  @Persisting(value = 18)
  @MetaField(META_EMAIL_VERIFIED)
  private boolean emailVerified;

  @Persisting(value = 19)
  @MetaField(META_EMAIL_OFFER_COUNT)
  private int emailOfferCount;  // 0 = never offered, 1 = first offer shown, 2 = both offers shown

  @Persisting(value = 20, nullable = true)
  @MetaField(META_LAST_EMAIL_OFFER_DATE)
  private Date lastEmailOfferDate;  // when the most recent offer was shown
  ```

  Add getters and setters:

  ```java
  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }

  public boolean isEmailVerified() { return emailVerified; }
  public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }

  public int getEmailOfferCount() { return emailOfferCount; }
  public void setEmailOfferCount(int count) { this.emailOfferCount = count; }

  public Date getLastEmailOfferDate() { return lastEmailOfferDate; }
  public void setLastEmailOfferDate(Date date) { this.lastEmailOfferDate = date; }
  ```

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
      // email defaults to null, emailVerified to false, emailOfferCount to 0, lastEmailOfferDate to null
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
  git commit -m "[AI] Add email/emailVerified fields to ConnectUserRecord with V24 snapshot for migration"
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
  // V.25 - Added email, emailVerified, emailOfferCount, lastEmailOfferDate to ConnectUserRecord
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
          DbUtil.addColumnToTable(db, ConnectUserRecord.STORAGE_KEY,
                  ConnectUserRecord.META_EMAIL_VERIFIED, "INTEGER DEFAULT 0");
          DbUtil.addColumnToTable(db, ConnectUserRecord.STORAGE_KEY,
                  ConnectUserRecord.META_EMAIL_OFFER_COUNT, "INTEGER DEFAULT 0");
          DbUtil.addColumnToTable(db, ConnectUserRecord.STORAGE_KEY,
                  ConnectUserRecord.META_LAST_EMAIL_OFFER_DATE, "TEXT");

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
  git commit -m "[AI] Add DB migration v24→v25 for email fields on ConnectUserRecord"
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

  > **Why these live in session data:** `ConnectUserRecord` is not created until `PersonalIdPhotoCaptureFragment` (new signup) or `PersonalIdBackupCodeFragment` (recovery). When the user acts on the email screen — which comes *before* those steps — the record does not exist yet. Session data is the correct carrier until the record is written at signup completion.

  > **Why `emailSkippedDuringSignup`:** A user who explicitly skips email during signup has already been presented the offer once. If `emailOfferCount` were left at 0, `shouldOfferEmail()` would show the dialog again on the very next login, immediately after they just declined during signup. Setting `emailOfferCount = 1` and `lastEmailOfferDate = now` when recording the skip treats the signup screen as the first offer, so the dialog only appears 30 days later (second and final offer). Legacy users migrated from v24 have `emailOfferCount = 0` and do not have this flag set, so their first dialog appears on the next login as intended.

- [ ] **Step 3.2: Build to verify compilation**

  Run: `./gradlew assembleCommcareDebug`
  Expected: BUILD SUCCESSFUL

- [ ] **Step 3.3: Commit**

  ```bash
  git add app/src/org/commcare/android/database/connect/models/PersonalIdSessionData.kt
  git commit -m "[AI] Add email field to PersonalIdSessionData"
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

  import org.commcare.android.database.connect.models.ConnectUserRecord
  import org.junit.Assert.assertFalse
  import org.junit.Assert.assertTrue
  import org.junit.Test
  import java.util.Calendar
  import java.util.Date

  class PersonalIdEmailOfferTest {

      private fun dateMinusDays(days: Int): Date =
          Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -days) }.time

      @Test
      fun `should offer when never shown before (count=0)`() {
          val user = ConnectUserRecord().apply { emailVerified = false }
          assertTrue(PersonalIdManager.shouldOfferEmail(user))
      }

      @Test
      fun `should not offer when both offers already shown (count=2)`() {
          val user = ConnectUserRecord().apply {
              emailVerified = false
              emailOfferCount = 2
              lastEmailOfferDate = dateMinusDays(5)
          }
          assertFalse(PersonalIdManager.shouldOfferEmail(user))
      }

      @Test
      fun `should offer second time after 30 days (count=1, date old)`() {
          val user = ConnectUserRecord().apply {
              emailVerified = false
              emailOfferCount = 1
              lastEmailOfferDate = dateMinusDays(31)
          }
          assertTrue(PersonalIdManager.shouldOfferEmail(user))
      }

      @Test
      fun `should not offer second time before 30 days (count=1, date recent)`() {
          val user = ConnectUserRecord().apply {
              emailVerified = false
              emailOfferCount = 1
              lastEmailOfferDate = dateMinusDays(15)
          }
          assertFalse(PersonalIdManager.shouldOfferEmail(user))
      }

      @Test
      fun `should not offer when email already verified`() {
          val user = ConnectUserRecord().apply { emailVerified = true }
          assertFalse(PersonalIdManager.shouldOfferEmail(user))
      }

      @Test
      fun `should not offer immediately after signup skip (count=1, date just set)`() {
          // Simulates a user who skipped email during signup: count=1, date=now
          val user = ConnectUserRecord().apply {
              emailVerified = false
              emailOfferCount = 1
              lastEmailOfferDate = Date()
          }
          assertFalse(PersonalIdManager.shouldOfferEmail(user))
      }
  }
  ```

  Run: `./gradlew testCommcareDebug --tests "org.commcare.connect.PersonalIdEmailOfferTest"`
  Expected: FAIL — `PersonalIdManager.shouldOfferEmail` does not exist yet.

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
  import org.commcare.connect.network.PersonalIdOrConnectApiErrorHandler
  import org.commcare.connect.network.connectId.PersonalIdApiHandler
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
          // createAndSaveConnectUser() will read this to initialise emailOfferCount = 1
          // so the post-login dialog does not fire again immediately.
          personalIdSessionData.emailSkippedDuringSignup = true
          if (isLegacyFlow) {
              requireActivity().finish()
          } else {
              Navigation.findNavController(binding.root)
                  .navigate(PersonalIdEmailFragmentDirections.actionPersonalidEmailToPersonalidBackupCode())
          }
      }

      private fun navigateToEmailVerification() {
          val action = PersonalIdEmailFragmentDirections
              .actionPersonalidEmailToPersonalidEmailVerification(isLegacyFlow)
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
          // Always update session data — this is the source of truth for the signup flow.
          // ConnectUserRecord does not exist yet during new signup (it is created later in
          // PersonalIdPhotoCaptureFragment / PersonalIdBackupCodeFragment which will read these
          // session fields). For the legacy flow the record already exists so write it now.
          personalIdSessionData.emailVerified = true

          if (isLegacyFlow) {
              val user = ConnectUserDatabaseUtil.getUser(requireActivity())
              if (user != null) {
                  user.setEmail(personalIdSessionData.email)
                  user.setEmailVerified(true)
                  ConnectUserDatabaseUtil.storeUser(requireActivity(), user)
              }
              requireActivity().finish()
          } else {
              Navigation.findNavController(binding.root)
                  .navigate(PersonalIdEmailVerificationFragmentDirections
                      .actionPersonalidEmailVerificationToPersonalidBackupCode())
          }
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
          if (isLegacyFlow) {
              requireActivity().finish()
          } else {
              Navigation.findNavController(binding.root)
                  .navigate(PersonalIdEmailVerificationFragmentDirections
                      .actionPersonalidEmailVerificationToPersonalidBackupCode())
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

  > **Note:** The correct method is `ConnectUserDatabaseUtil.storeUser(Context, ConnectUserRecord)` — there is no `saveUser` method. Use `user.setEmailVerified(true)` based on the Java setter added in Task 1.

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

### Task 6c: Propagate email/emailVerified into ConnectUserRecord at signup completion

`ConnectUserRecord` is constructed from `PersonalIdSessionData` at two points. Both must read the new email fields so that a user who verified their email during signup has `emailVerified = true` from the moment the record is written, making `shouldOfferEmail()` return `false` on all future logins.

> **Critical:** Do NOT add email fields to the `ConnectUserRecord` constructor signature. The 10-parameter constructor is called in both fragments below; changing its signature would cause compile failures at both callsites and any other callers. Set email fields via setters after construction, as shown in the code below.

**Files:**
- Modify: `app/src/org/commcare/fragments/personalId/PersonalIdPhotoCaptureFragment.java`
- Modify: `app/src/org/commcare/fragments/personalId/PersonalIdBackupCodeFragment.java`

- [ ] **Step 6c.1: Update PersonalIdPhotoCaptureFragment.createAndSaveConnectUser()**

  After the `ConnectUserDatabaseUtil.storeUser(...)` call, add:

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
      // Carry over email verification state from the signup flow
      user.setEmail(personalIdSessionData.getEmail());
      user.setEmailVerified(personalIdSessionData.getEmailVerified());
      if (personalIdSessionData.getEmailSkippedDuringSignup()) {
          // User was shown the email step and declined. Count it as the first offer so the
          // post-login dialog only fires after the 30-day gap, not on the very next login.
          user.setEmailOfferCount(1);
          user.setLastEmailOfferDate(new Date());
      }
      ConnectUserDatabaseUtil.storeUser(requireActivity(), user);
  }
  ```

- [ ] **Step 6c.2: Update PersonalIdBackupCodeFragment.handleSuccessfulRecovery()**

  Same change in the recovery path:

  ```java
  private void handleSuccessfulRecovery() {
      ConnectDatabaseHelper.handleReceivedDbPassphrase(activity, personalIdSessionData.getDbKey());
      ConnectUserRecord user = new ConnectUserRecord(
              personalIdSessionData.getPhoneNumber(),
              personalIdSessionData.getPersonalId(),
              personalIdSessionData.getOauthPassword(),
              personalIdSessionData.getUserName(),
              String.valueOf(binding.connectBackupCodeInput.getText()),
              new Date(),
              personalIdSessionData.getPhotoBase64(),
              personalIdSessionData.getDemoUser(),
              personalIdSessionData.getRequiredLock(),
              personalIdSessionData.getInvitedUser());
      // Carry over email verification state from the recovery flow
      user.setEmail(personalIdSessionData.getEmail());
      user.setEmailVerified(personalIdSessionData.getEmailVerified());
      if (personalIdSessionData.getEmailSkippedDuringSignup()) {
          user.setEmailOfferCount(1);
          user.setLastEmailOfferDate(new Date());
      }
      ConnectUserDatabaseUtil.storeUser(requireActivity(), user);
      logRecoveryResult(true);
      handleSecondDeviceLogin();
      navigateToSuccess();
  }
  ```

  > **Note:** If the user skipped the email step, `personalIdSessionData.getEmail()` is `null` and `getEmailVerified()` is `false` — those are already the defaults on `ConnectUserRecord`, so no null-guard is needed.

- [ ] **Step 6c.3: Build to verify compilation**

  Run: `./gradlew assembleCommcareDebug`
  Expected: BUILD SUCCESSFUL

- [ ] **Step 6c.4: Commit**

  ```bash
  git add app/src/org/commcare/fragments/personalId/PersonalIdPhotoCaptureFragment.java \
          app/src/org/commcare/fragments/personalId/PersonalIdBackupCodeFragment.java
  git commit -m "[AI] Propagate email/emailVerified from session data into ConnectUserRecord at signup/recovery completion"
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

### Task 7: Update nav graph and PersonalIdNameFragment


**Files:**
- Modify: `app/res/navigation/nav_graph_personalid.xml`
- Modify: `app/src/org/commcare/fragments/personalId/PersonalIdNameFragment.java`
- Modify: `app/src/org/commcare/activities/connect/PersonalIdActivity.java`

- [ ] **Step 7.1: Update nav_graph_personalid.xml**

  Make the following changes:

  **a) Change the action in `personalid_name` from backup code to email:**

  ```xml
  <!-- Replace: -->
  <action
      android:id="@+id/action_personalid_name_to_personalid_backup_code"
      app:destination="@id/personalid_backup_code" />

  <!-- With: -->
  <action
      android:id="@+id/action_personalid_name_to_personalid_email"
      app:destination="@id/personalid_email" />
  ```

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
      <action
          android:id="@+id/action_personalid_email_to_personalid_email_verification"
          app:destination="@id/personalid_email_verification">
          <argument
              android:name="isLegacyFlow"
              app:argType="boolean"
              android:defaultValue="false" />
      </action>
      <action
          android:id="@+id/action_personalid_email_to_personalid_backup_code"
          app:destination="@id/personalid_backup_code" />
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
      <action
          android:id="@+id/action_personalid_email_verification_to_personalid_backup_code"
          app:destination="@id/personalid_backup_code" />
      <action
          android:id="@+id/action_personalid_email_verification_to_personalid_message"
          app:destination="@id/personalid_message_display" />
  </fragment>
  ```

- [ ] **Step 7.2: Update PersonalIdNameFragment.java to navigate to email**

  > **Critical:** Step 7.1 replaces the `action_personalid_name_to_personalid_backup_code` action with `action_personalid_name_to_personalid_email` in the nav graph. Safe Args will regenerate `PersonalIdNameFragmentDirections` — the old method `actionPersonalidNameToPersonalidBackupCode()` will no longer exist. This step MUST be completed in the same build as Step 7.1 or the build will fail. Do not attempt `assembleCommcareDebug` after Step 7.1 without also applying Step 7.2.

  Change `navigateToBackupCodePage()`:

  ```java
  // Before:
  private NavDirections navigateToBackupCodePage() {
      return PersonalIdNameFragmentDirections.actionPersonalidNameToPersonalidBackupCode();
  }

  // After:
  private NavDirections navigateToEmailPage() {
      return PersonalIdNameFragmentDirections.actionPersonalidNameToPersonalidEmail();
  }
  ```

  Update the call site in `verifyOrAddName()`:

  ```java
  // Before:
  Navigation.findNavController(binding.getRoot()).navigate(navigateToBackupCodePage());

  // After:
  Navigation.findNavController(binding.getRoot()).navigate(navigateToEmailPage());
  ```

- [ ] **Step 7.3: Add legacy email flow support to PersonalIdActivity.java**

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

- [ ] **Step 7.4: Build to verify compilation**

  Run: `./gradlew assembleCommcareDebug`
  Expected: BUILD SUCCESSFUL (Safe Args will now generate all Directions classes).

- [ ] **Step 7.5: Commit**

  ```bash
  git add app/res/navigation/nav_graph_personalid.xml \
          app/src/org/commcare/fragments/personalId/PersonalIdNameFragment.java \
          app/src/org/commcare/activities/connect/PersonalIdActivity.java
  git commit -m "[AI] Update nav graph and wire email entry step between Name and Backup Code"
  ```

---

## Chunk 6: Legacy User Email Prompt

### Task 8: Add email offer logic to PersonalIdManager and LoginActivity


**Files:**
- Modify: `app/src/org/commcare/connect/PersonalIdManager.java`
- Modify: `app/src/org/commcare/activities/LoginActivity.java` (find where `handleFinishedActivity` is called and add the email check after it)

- [ ] **Step 8.1: Implement shouldOfferEmail() in PersonalIdManager.java (makes test from Step 4.1 pass)**

  Add the following to `PersonalIdManager.java`:

  ```java
  // Expose as package-private static for testability
  static boolean shouldOfferEmail(ConnectUserRecord user) {
      if (user == null || user.isEmailVerified()) {
          return false;
      }

      int count = user.getEmailOfferCount();
      if (count == 0) {
          return true; // Never shown — show first offer
      }
      if (count >= 2) {
          return false; // Both offers already shown — stop
      }

      // count == 1: show second offer only after 30-day gap
      Date lastOffer = user.getLastEmailOfferDate();
      if (lastOffer == null) {
          return true;
      }
      long millis = new Date().getTime() - lastOffer.getTime();
      long days = TimeUnit.DAYS.convert(millis, TimeUnit.MILLISECONDS);
      return days >= DAYS_TO_SECOND_OFFER;
  }

  public void checkEmailCollection(CommCareActivity<?> activity) {
      ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(activity);
      if (!shouldOfferEmail(user)) {
          return;
      }

      // Increment count and record date before showing dialog
      user.setEmailOfferCount(user.getEmailOfferCount() + 1);
      user.setLastEmailOfferDate(new Date());
      ConnectUserDatabaseUtil.storeUser(activity, user);

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

  > **Note:** Open `LoginActivity.java` and search for `handleFinishedActivity` to find the exact location. The check should only run when the result is successful (`resultCode == RESULT_OK`), which is already guarded inside `handleFinishedActivity` — but add the email check only in the same successful branch in `LoginActivity.onActivityResult`.

- [ ] **Step 8.4: Build to verify compilation**

  Run: `./gradlew assembleCommcareDebug`
  Expected: BUILD SUCCESSFUL

- [ ] **Step 8.5: Commit**

  ```bash
  git add app/src/org/commcare/connect/PersonalIdManager.java \
          app/src/org/commcare/activities/LoginActivity.java \
          app/res/values/strings.xml \
          app/unit-tests/src/org/commcare/connect/PersonalIdEmailOfferTest.kt
  git commit -m "[AI] Add legacy user email collection prompt in PersonalIdManager and LoginActivity"
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
  2. Complete Phone → Biometrics → OTP → Name steps
  3. Confirm the Email entry screen appears
  4. Enter a valid email address → Continue
  5. Confirm Email OTP screen appears
  6. Enter OTP code → Verify
  7. Confirm navigation proceeds to Backup Code screen
  8. Complete signup to end

  Also verify skip path:
  1. On Email entry screen, tap "Skip for now"
  2. Confirm navigation goes directly to Backup Code

  Also verify 3-failed-OTP path:
  1. Enter email → Continue → Email OTP screen appears
  2. Enter wrong code 3 times
  3. Confirm dialog appears: "Verification unsuccessful" with "Try again" and "Proceed without email" buttons
  4. Tap "Proceed without email" → navigation goes to Backup Code
  5. Repeat steps 1–3, but tap "Try again" → dialog dismisses, code input is cleared, counter resets, user can attempt again

- [ ] **Step 9.4: Verify legacy user prompt manually (if emulator available)**

  Prerequisites: install a build on a device that has an existing PersonalID account (emailVerified = false in DB, which will be the case for all existing accounts after the migration).

  Steps:
  1. Log in with existing PersonalID account
  2. Confirm email offer dialog appears
  3. Tap "Add email" → PersonalIdActivity launches at email entry screen
  4. Complete email + OTP flow → activity finishes
  5. Log in again → confirm dialog does not appear (emailVerified = true)

  Also verify dismissal:
  1. Log in → dialog appears → tap "Not now"
  2. Confirm emailOfferDate1 is set (check DB or logs)
  3. Log in again within 30 days → confirm dialog does not appear
  4. (Advance clock / change emailOfferDate1 to >30 days ago) → log in again → dialog appears for second offer

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
If the product requires this feature to be guarded by a release toggle (`ConnectReleaseToggleRecord`), add a toggle check in `PersonalIdNameFragment.navigateToEmailPage()` — if the toggle is off, call `navigateToBackupCodePage()` instead. Confirm with the team whether a toggle is needed.
---

# Plan: Create Jira Sub-task Tickets for CCCT-2204

## Context
CCCT-2204 ("Tech Spec - Adding email to PersonalID signup / recovery flow") requires implementation tickets to be created as an acceptance criterion. The implementation plan above defines 7 logical chunks of work. This section defines 8 Jira Sub-tasks to be created under parent **CCCT-2204** (project id: 10229, component: Mobile).

**Jira metadata:**
- API base: `https://dimagi.atlassian.net/rest/api/3`
- Project id: `10229` (CCCT)
- Sub-task issue type id: `10007`
- Parent: `CCCT-2204`
- Component: `Mobile`

---

## Sub-tasks to Create

### Ticket 1 — Database Layer
**Summary:** `[Android] Add email fields to ConnectUserRecord and DB migration v24→v25`
**Description:**
- Create `ConnectUserRecordV24.java` (snapshot of current record for migration source)
- Add fields to `ConnectUserRecord.java`: `email` (String nullable), `emailVerified` (boolean), `emailOfferCount` (int), `lastEmailOfferDate` (Date nullable)
- Add `fromV24()` factory method to `ConnectUserRecord`
- Add `upgradeTwentyFourTwentyFive()` to `ConnectDatabaseUpgrader.java`
- Bump `CONNECT_DB_VERSION` from 24 → 25 in `DatabaseConnectOpenHelper.java`
- Unit test: `ConnectUserRecordMigrationV24Test.kt`

**Files:** `ConnectUserRecord.java`, `ConnectUserRecordV24.java`, `ConnectDatabaseUpgrader.java`, `DatabaseConnectOpenHelper.java`

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
**Summary:** `[Android] Create PersonalIdEmailFragment for email entry step in signup`
**Description:**
- Create `fragment_personalid_email.xml` with email TextInputEditText, Continue button, Skip button, error TextView, ScrollView
- Create `PersonalIdEmailFragment.kt` extending `BasePersonalIdFragment`:
  - Validates email format using `Patterns.EMAIL_ADDRESS` before enabling Continue
  - Continue: calls `sendEmailOtpCall()` → navigates to email OTP screen
  - Skip: sets `personalIdSessionData.emailSkippedDuringSignup = true` → navigates to backup code
  - Legacy flow mode: on skip/complete, finishes the activity instead of navigating
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
  - Resend button with 2-minute cooldown timer
  - On OTP success: set `personalIdSessionData.emailVerified = true`
  - After 3 failed OTP attempts: show `StandardAlertDialog` offering to proceed without email or retry. "Proceed without email" sets `emailSkippedDuringSignup = true` and navigates to backup code (signup) or finishes activity (legacy). "Try again" resets the counter and clears the code input.
  - Legacy flow: also writes email + emailVerified directly to `ConnectUserRecord`, then finishes activity
  - New signup flow: navigates to backup code (ConnectUserRecord written later by PhotoCapture/BackupCode fragments)
- Add string resources

**Files:** `PersonalIdEmailVerificationFragment.kt`, `fragment_personalid_email_verification.xml`, `strings.xml`

---

### Ticket 6 — Signup Completion Integration
**Summary:** `[Android] Propagate email/emailVerified from session data into ConnectUserRecord at signup completion`
**Description:**
Two fragments create `ConnectUserRecord` for the first time. Both must read email state from session data so a user who verified (or skipped) email during signup has the correct state persisted from the moment the record is written.

- `PersonalIdPhotoCaptureFragment.createAndSaveConnectUser()`: after constructing record, call `user.setEmail(sessionData.email)`, `user.setEmailVerified(sessionData.emailVerified)`, and if `emailSkippedDuringSignup`, set `emailOfferCount = 1` + `lastEmailOfferDate = now`
- `PersonalIdBackupCodeFragment.handleSuccessfulRecovery()`: same additions

**Files:** `PersonalIdPhotoCaptureFragment.java`, `PersonalIdBackupCodeFragment.java`
**Depends on:** Ticket 1, Ticket 2

---

### Ticket 7 — Navigation & Activity Updates
**Summary:** `[Android] Wire email step into PersonalID signup nav graph and activity`
**Description:**
- `nav_graph_personalid.xml`:
  - Add `personalid_email` fragment destination (with `isLegacyFlow` boolean arg, default false)
  - Add `personalid_email_verification` fragment destination (with `isLegacyFlow` arg)
  - Add actions: email → email-verification, email → backup code (skip), email-verification → backup code
  - Change `personalid_name` action from backup code → email
- `PersonalIdNameFragment.java`: change `navigateToBackupCodePage()` → `navigateToEmailPage()`
- `PersonalIdActivity.java`: add `EXTRA_LEGACY_EMAIL_FLOW` intent extra; on receipt, navigate NavController to `personalid_email` with `isLegacyFlow = true`, popping phone fragment from back stack

**Files:** `nav_graph_personalid.xml`, `PersonalIdNameFragment.java`, `PersonalIdActivity.java`
**Depends on:** Ticket 3, Ticket 5

---

### Ticket 8 — Legacy User Email Prompt
**Summary:** `[Android] Add post-login email collection prompt for existing users without email`
**Description:**
Two-offer-with-30-day-gap dialog shown to users with `emailVerified = false` after PersonalID login.

- `PersonalIdManager.java`:
  - Add `static boolean shouldOfferEmail(ConnectUserRecord user)`:
    - `emailVerified = true` → false (never offer)
    - `emailOfferCount == 0` → true (first offer)
    - `emailOfferCount >= 2` → false (exhausted)
    - `emailOfferCount == 1` → true only if `lastEmailOfferDate` > 30 days ago
  - Add `checkEmailCollection(CommCareActivity)`: reads user record, checks `shouldOfferEmail`, increments `emailOfferCount`, updates `lastEmailOfferDate`, shows `StandardAlertDialog`
  - On dialog accept: launch `PersonalIdActivity` with `EXTRA_LEGACY_EMAIL_FLOW = true`
- `LoginActivity.java`: call `checkEmailCollection()` after successful PersonalID login (in `onActivityResult` success branch, after `handleFinishedActivity()`)
- Unit tests: `PersonalIdEmailOfferTest.kt` covering all offer-count and date combinations

**Files:** `PersonalIdManager.java`, `LoginActivity.java`, `PersonalIdEmailOfferTest.kt`
**Depends on:** Ticket 1, Ticket 7

---

## Dependency Order

```
Ticket 1 (DB)
    └── Ticket 2 (API/Session)
            ├── Ticket 3 (Email Entry Fragment)
            │       ├── Ticket 4 (Validation + Keyboard)
            │       └── Ticket 7 (Navigation)
            ├── Ticket 5 (OTP Fragment)
            │       ├── Ticket 4 (Validation + Keyboard)
            │       └── Ticket 7 (Navigation)
            └── Ticket 6 (Signup Completion)
Ticket 7 (Navigation) ──► Ticket 8 (Legacy Prompt)
```

Safe parallel work: Tickets 3 and 5 can be built concurrently after Ticket 2.
