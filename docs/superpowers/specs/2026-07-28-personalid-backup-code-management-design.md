# PersonalID Backup Code Management — Design

**Date:** 2026-07-28
**Author:** Shubham Goyal
**Jira:** CCCT-2677
**Related:** https://dimagi.atlassian.net/browse/CCCT-2555

---

## Problem

Front Line Workers using Personal ID authenticate with a backup code set once at account
creation. There is no mechanism to update it, no reminder system, and no recovery path when it
is forgotten. This creates hard lock-out risk and a security risk when codes are compromised.

---

## Four Work Areas

1. **Change Backup Code** — proactively update from Manage Profile
2. **Backup Code Recovery** — self-service recovery via email OTP when code is forgotten
3. **Periodic Reminders** — periodic prompts to rehearse the code
4. **Email Modification Flow Changes** — gate email changes behind backup code; add email without
   backup code via phone OTP

---

## Section 1: Architecture

### Fragment Refactor — Base + Two Implementations

#### `BasePersonalIdBackupCodeFragment` (new abstract Kotlin class)

Owns all shared UI: layout binding, `NumericCodeView` wiring (code-changed, code-complete,
enter-key), visibility toggles, validation (length check, confirm match), error display,
continue-button enable/disable.

Abstract hooks:

```kotlin
abstract fun getWorkflow(): BackupCodeWorkflow
abstract fun onValidCodeSubmitted(code: String, confirmCode: String?)
abstract fun navigateOnForgotBackupCode()           // base shows "Forgot?" link only when workflow has it
abstract fun getUserDisplayData(): UserDisplayData? // for welcome-back name/photo UI
```

#### `PersonalIdBackupCodeFragment` (existing Java, refactored to extend base)

Handles `REGISTRATION` and `CONFIRM_RECOVERY`. Auth: session token from `PersonalIdSessionData`.
Navigation: existing SafeArgs actions in `nav_graph_personalid.xml`. No behaviour change.

#### `PersonalIdProfileBackupCodeFragment` (new Kotlin)

Handles `CONFIRM_CHANGE` and `SET_AFTER_RECOVERY`, passed as a nav arg. Auth:
`ProvidedAuth(user.userId, user.password)` from `ConnectUserRecord` — same dual-auth pattern as
`sendEmailOtp`. Navigation: SafeArgs actions in `nav_graph_personalid_profile.xml`.

#### `BackupCodeWorkflow` enum

| Value | Fields | API call | Auth |
|---|---|---|---|
| `REGISTRATION` | two | none (stored in session) | — |
| `CONFIRM_RECOVERY` | one | `confirmBackupCode` | session token |
| `CONFIRM_CHANGE` | one | none (code held in ViewModel) | — |
| `SET_AFTER_RECOVERY` | two | `POST /users/set_backup_code` | user credentials or session token |

`confirmBackupCode` API gains dual-auth support:
`token != null ? TokenAuth(token) : ProvidedAuth(user.userId, user.password)` — same pattern as
`sendEmailOtp`.

### Phone Verification Refactor — Base + Two Implementations

Same pattern applied to `PersonalIdPhoneVerificationFragment`.

#### `BasePersonalIdPhoneVerificationFragment` (new abstract Kotlin class)

Owns: layout/binding, resend timer, SMS user consent + `SMSBroadcastReceiver`, OTP field
wiring, error display.

Abstract hooks:

```kotlin
abstract fun getPhoneNumber(): String
abstract fun createOtpManager(): OtpManager
abstract fun onOtpVerified()
abstract fun recordFailedAttempt()
abstract fun getOtpAttemptCount(): Int
```

#### `PersonalIdPhoneVerificationFragment` (existing Java, refactored to extend base)

Implements hooks using `PersonalIdSessionData`. Navigates to name entry on success.

#### `PersonalIdProfilePhoneVerificationFragment` (new Kotlin)

Implements hooks using `ConnectUserRecord.primaryPhone` and a simplified `OtpManager` config.
Navigates to email entry within the profile graph on success.

### New API Endpoint

**`POST /users/set_backup_code`**

- Body: `{ recovery_pin: newCode }`
- Auth: `ProvidedAuth(userId, currentCode)` for change flow; `TokenAuth(sessionToken)` for
  recovery flow — same dual-auth pattern as `sendEmailOtp`
- Error codes: `INCORRECT_BACKUP_CODE`, `ACCOUNT_LOCKED`

All other operations reuse existing endpoints:
- Send recovery OTP → `send_email_otp` (existing)
- Verify recovery OTP → `verify_email_otp` (existing)

### Nav Graph Changes

`nav_graph_personalid_profile.xml` gains:

| Destination ID | Fragment | Workflow arg |
|---|---|---|
| `personalid_confirm_change_backup_code` | `PersonalIdProfileBackupCodeFragment` | `CONFIRM_CHANGE` |
| `personalid_set_new_backup_code` | `PersonalIdProfileBackupCodeFragment` | `SET_AFTER_RECOVERY` |
| `personalid_forgot_backup_code_email` | `PersonalIdForgotBackupCodeEmailFragment` | — |
| `personalid_email_verification_fragment` | `PersonalIdEmailVerificationFragment` | `BACKUP_CODE_RECOVERY` |
| `personalid_profile_phone_verification` | `PersonalIdProfilePhoneVerificationFragment` | — |

`nav_graph_personalid.xml` gains a "Forgot?" action on `personalid_backup_code` pointing to:

| Destination ID | Fragment |
|---|---|
| `personalid_forgot_backup_code_email` | `PersonalIdForgotBackupCodeEmailFragment` |
| `personalid_email_verification` | `PersonalIdEmailVerificationFragment` (workflow=`BACKUP_CODE_RECOVERY`) |

### `PersonalIdChangeBackupCodeViewModel`

Activity-scoped ViewModel on `PersonalIdProfileActivity`. Holds:
- `currentCode: String?` — entered at `CONFIRM_CHANGE` step, used as auth for the final API call
- `pendingEmail: String?` — set when email change is gated behind backup code confirmation

Cleared on success or cancellation.

---

## Section 2: Area 1 — Change Backup Code

**Entry point:** New "Change backup code" row on `PersonalIdProfileFragment`.

**Flow:**

1. Tap "Change backup code" → `personalid_confirm_change_backup_code`
   (`PersonalIdProfileBackupCodeFragment(CONFIRM_CHANGE)`)
   - One field, no API call — submitted code stored in `PersonalIdChangeBackupCodeViewModel.currentCode`
   - "Forgot backup code?" link visible only if `user.email != null`

2. On submit → `personalid_set_new_backup_code`
   (`PersonalIdProfileBackupCodeFragment(SET_AFTER_RECOVERY)`)
   - Two fields (new + confirm)
   - "Save" → `PersonalIdUnlocker.unlock(ALWAYS)` → `POST /users/set_backup_code` with
     `ProvidedAuth(userId, currentCode)`

3. **Success:** update `ConnectUserRecord.password = newCode`, persist, pop back to profile
   with success toast

4. **`INCORRECT_BACKUP_CODE`:** navigate back to `personalid_confirm_change_backup_code`,
   clear fields, show error

5. **`ACCOUNT_LOCKED`** (3 failed attempts, 24h): navigate to message screen via existing
   `handleCommonSignupFailures` pattern

**Analytics:**
- Workflow initiated (source: profile)
- Outcome: success / failure / cancelled
- Failure reason
- Number of attempts

---

## Section 3: Area 2 — Backup Code Recovery

**Triggered from** "Forgot backup code?" link on:
- `PersonalIdBackupCodeFragment` in signup/recovery graph (session token available)
- `personalid_confirm_change_backup_code` in profile graph (user signed in, no session token)

**Visibility rule:**
- Recovery graph: link hidden if `user.email == null`
- Profile graph: link always visible; if `user.email == null`, `PersonalIdForgotBackupCodeEmailFragment`
  shows "add email" prompt instead of masked email (see Area 4)

### New Fragment

**`PersonalIdForgotBackupCodeEmailFragment`** — shows masked `user.email` (`x***y@gmail.com`),
"Send code" button. When `user.email == null` (profile graph only): shows "You need to add an
email to recover your account" with "Cancel Recovery" and "Add Email" buttons.

### `EmailWorkFlow.BACKUP_CODE_RECOVERY`

New value added to `EmailWorkFlow` enum. Changes to `PersonalIdEmailVerificationFragment`:
- `onEmailVerified()` — new branch navigates to `personalid_set_new_backup_code`
- 3-failure path: no "skip" option; navigate to lockout message screen (server returns
  `ACCOUNT_LOCKED` after 3 OTP failures; `handleCommonSignupFailures` handles it)

### Flow

1. "Forgot backup code?" → `PersonalIdForgotBackupCodeEmailFragment`
   → tap "Send code" → `send_email_otp`
   → navigate to `PersonalIdEmailVerificationFragment(BACKUP_CODE_RECOVERY)`

2. OTP verified → navigate to `personalid_set_new_backup_code`
   (`PersonalIdProfileBackupCodeFragment(SET_AFTER_RECOVERY)`)
   - Two fields, biometric gate on Save
   - `POST /users/set_backup_code`
   - Auth: `TokenAuth(sessionToken)` in recovery graph;
     `ProvidedAuth(userId, ConnectUserRecord.password)` in profile graph
     (stored value used as auth even though user can't recall it from memory)

3. **Success:** update `ConnectUserRecord.password`, persist, navigate to success message

**Analytics:**
- Recovery workflow initiated
- OTP requested
- OTP verification attempt
- New backup code set (success / failure)

---

## Section 4: Area 3 — Periodic Backup Code Reminders

### State

One new key in `PersonalIdUserPreferences`:
- `KEY_BACKUP_CODE_REMINDER_NEXT_DUE` (Long) — initialized to `now + 1h` at registration

### Scheduling

```kotlin
fun scheduleNext() {
    val now = System.currentTimeMillis()
    val nextDue = if (now - getNextDue() < 2.days) now + 3.days else now + 7.days
    prefs.put(KEY_BACKUP_CODE_REMINDER_NEXT_DUE, nextDue)
}
```

Reminder cadence:
- First: 1h after registration
- Second: ~3d after registration (scheduled from current time when first fires)
- Subsequent: every 7d rolling

### Hook

`ConnectActivity.onResume()` calls `PersonalIdReminderHelper.isDue()`. If due, show
`BackupCodeReminderDialogFragment`.

### `BackupCodeReminderDialogFragment`

- Single `NumericCodeView` + "Skip" button
- Local validation only: compare entered code against `ConnectUserRecord.password` (no API call)
- **Correct or skip:** `scheduleNext()`, dismiss
- **Wrong code (up to 3):** show inline error, allow retry
- **3 wrong attempts:** dismiss, start `PersonalIdProfileActivity` and navigate to
  `personalid_forgot_backup_code_email` (profile graph — user is signed in at this point)

### `PersonalIdReminderHelper`

```kotlin
object PersonalIdReminderHelper {
    fun isDue(): Boolean
    fun scheduleNext()
    fun clear() // called on PersonalID logout
}
```

**Analytics:**
- Prompt shown
- Outcome: success / skip / failed (redirected to recovery)
- Each individual attempt (success / failure)

---

## Section 5: Area 4 — Email Modification Flow Changes

### 1. Changing email (user has existing email)

`PersonalIdProfileEditFragment.onSaveClicked()` when `isEmailModified()` and
`user.email != null`: navigate to `personalid_confirm_change_backup_code` before initiating
email OTP. Pending email stored in `PersonalIdChangeBackupCodeViewModel.pendingEmail`.

On backup code confirmed → new nav action leads to
`PersonalIdEmailVerificationFragment(EXISTING_USER)`.

The `personalid_confirm_change_backup_code` destination is reused from Area 1 with a separate
nav action for the email-change exit path.

### 2. Adding email (no existing email, already signed in)

When `user.email == null` and email is modified:
- `PersonalIdUnlocker.unlock(ALWAYS)`
- On success → navigate to `personalid_profile_phone_verification`
  (`PersonalIdProfilePhoneVerificationFragment`)
- On phone OTP verified → existing email entry + email OTP flow

`PersonalIdProfilePhoneVerificationFragment` extends `BasePersonalIdPhoneVerificationFragment`,
using `ConnectUserRecord.primaryPhone` and simplified `OtpManager` config.

### 3. Skip email dialog during signup

`PersonalIdEmailFragment.confirmSkipEmail()`: same dialog logic, string resources updated to
"Protect your account" framing:

> "Adding an email strengthens your account security. It helps verify important account changes,
> prevents unauthorized recovery attempts, and gives you a secure way to regain access if you
> lose your device."

Buttons: "Skip" / "Add Email".

### 4. Forgot backup code with no email (profile graph only)

`PersonalIdForgotBackupCodeEmailFragment` when `user.email == null` (reachable from profile
graph only — recovery graph hides the "Forgot?" link when no email):

> "You need to add an email to be able to recover your account"

Buttons: "Cancel Recovery" / "Add Email".

"Add Email" → navigates to `personalid_profile_phone_verification` flow from change #2.

---

## Rollout Order (per spec)

1. Area 1 — Change backup code (without "Forgot?" link)
2. Area 2 — Email recovery + wire "Forgot?" into Area 1 and account recovery
3. Area 3 — Periodic reminders
4. Area 4 — Email modification flow changes

Each area can be shipped independently by hiding intersection points (e.g., omitting "Forgot?"
link until Area 2 is ready).