# PersonalID Backup Code Management — Design

**Jira:** CCCT-2677
**Related:** https://dimagi.atlassian.net/browse/CCCT-2555

---
**Design Doc:**  https://docs.google.com/document/d/1EC8d9TnQMRAJbAAIGcVaaHE9VLc-stoYZjirMyuaGjw/

## Navigation Overview

New screens and flows added across both nav graphs.

**Profile graph (`nav_graph_personalid_profile.xml`):**

```
Manage Profile
 ├─[Change backup code]──────────────────────────────────────────────────┐
 │                                                                       ▼
 │                                                   Confirm current backup code
 │                                                   │                        │
 │                                          [Forgot?]│               [correct]│
 │                                                   ▼                        ▼
 │                                            [email?]             Set new backup code
 │                                         no │    │ yes                      │
 │                                             ▼    ▼                         ▼
 │                                   ◄ Profile   Send email OTP screen  Biometric / PIN unlock
 │                                   (toast)          │                       │
 │                                                    ▼                       ▼
 │                                           Email OTP entry          ◄ Profile (success)
 │                                                    │
 │                                                    ▼
 │                                          Set new backup code
 │                                                    │ Save
 │                                                    ▼
 │                                          Biometric / PIN unlock
 │                                                    │
 │                                                    ▼
 │                                           ◄ Profile (success)
 │
 └─[Save — email change]─────────────────────────────────────────────────┐
                                                                         ▼
                                              [has email]           [no email]
                                                   │                     │
                                                   ▼                     ▼
                                        Confirm backup code    Send Phone OTP screen
                                         │           │                   │
                                [Forgot?]│  [correct]│                   ▼
                                         ▼           │           Phone OTP entry
                              (→ same as Change      │                   │
                               backup code           └─────────┬─────────┘
                               forgot flow)                    │
                                                               ▼
                                                   Send email OTP screen
                                                               │
                                                               ▼
                                                       Email OTP entry
                                                               │
                                                               ▼
                                                   Biometric / PIN unlock
                                                               │
                                                               ▼
                                                    ◄ Profile (success)
```

**Account Configuration Flow (`nav_graph_personalid.xml`) — additions only:**

```
Enter backup code  (existing, gains [Forgot?] link)
 └─[Forgot?]──► Send email OTP screen ──► Email OTP entry ──► Set new backup code
```

---

## Architecture

We need to support 2 versions of the backup code and phone verification fragments:

1. One for the existing PersonalID flow (signup/recovery) and
2. Another for the new flows (backup code change and recovery, email modification).

###  Backup Fragment Refactor — Base + Two Implementations

**Backup code fragment hierarchy:**

```
BasePersonalIdBackupCodeFragment
├── PersonalIdBackupCodeFragment 
│   workflows : REGISTRATION, CONFIRM_RECOVERY
│   auth      : PersonalID session token
│   nav graph : nav_graph_personalid
└── PersonalIdProfileBackupCodeFragment 
    workflows : CONFIRM_BACKUP_CODE, SET_NEW_CODE
    auth      : ProvidedAuth(userId, password)
    nav graph : nav_graph_personalid_profile
```

#### `BasePersonalIdBackupCodeFragment` (new abstract Kotlin class)

Owns all shared UI: layout binding, `NumericCodeView` wiring (code-changed, code-complete,
enter-key), visibility toggles, validation (length check, confirm match), error display,
continue-button enable/disable.

Abstract hooks:

```kotlin
abstract fun getWorkflow(): BackupCodeWorkflow
abstract fun onValidCodeSubmitted(code: String, confirmCode: String?)
abstract fun navigateOnForgotBackupCode()  // base shows "Forgot?" link only when workflow has it
abstract fun setupHeader()                // implementations own full header UI setup
```

#### `BackupCodeWorkflow` enum

| Value | API call | API Auth | Biometric/Pin Unlock on Save |
|---|---|---|------------------------------|
| `REGISTRATION` | none (stored in session) | — | no                           |
| `CONFIRM_RECOVERY` | `users/recover/confirm_backup_code` | session token | no                           |
| `CONFIRM_BACKUP_CODE` | none (local validation against `ConnectUserRecord.password`) | — | no                           |
| `SET_NEW_CODE` | `/users/set_backup_code` | user credentials or session token | yes                          |

### API Changes

#### New: `POST /users/set_backup_code`

We currently set the backup code only during registration using the `complete_profile` endpoint.
This new endpoint allows users to change their backup code after registration or during account recovery.

| Field | Value                                                                                       |
|---|---------------------------------------------------------------------------------------------|
| Auth | `ProvidedAuth(userId, password)` (change flow) or `TokenAuth(sessionToken)` (recovery flow) |
| Request | `{ "recovery_pin": "<new_code>" }`                                                          |
| Success | `HTTP 200` — no response body                                                               |

### Phone Verification Refactor — Base + Two Implementations

**Phone verification fragment hierarchy:**

```
BasePersonalIdPhoneVerificationFragment
├── PersonalIdPhoneVerificationFragment (existing fragment in signup/recovery flow)
│   data source : PersonalIdSessionData.phone
│   on success  : → name entry
│   nav graph   : nav_graph_personalid
└── PersonalIdProfilePhoneVerificationFragment (new fragment in profile graph)
    data source : ConnectUserRecord.primaryPhone
    on success  : → email verification 
    nav graph   : nav_graph_personalid_profile
```

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

### `PersonalIdSendEmailOtpFragment`

Reusable screen that shows the email address an OTP will be sent to, with a "Send code" button.
Nav args: `email: String`, `masked: Boolean`, `workflow: EmailWorkFlow`.

- `masked = true`: displays `email` as `x***y@gmail.com` (existing account email)
- `masked = false`: displays `email` as-is (newly entered email)

On "Send code" → calls `send_email_otp` → navigates to `PersonalIdEmailVerificationFragment`.

### `PersonalIdProfileActivityViewModel`

Single activity-scoped ViewModel on `PersonalIdProfileActivity`. Consolidates profile display and cross-fragment workflow state:
- `profileDisplayModel: LiveData<PersonalIdProfileDisplayModel>` — replaces the existing fragment-scoped `PersonalIdProfileViewModel`
- `pendingEmail: String?` — set before navigation begins on the email-change path

---
# Implementation Details

The following sections describe the implementation in detail and is intended to be used as an implementation reference for Claude.
These changes are better reviewed as part of the code review on the implementation PRs on this work and can therefore be skipped
to save time during spec review.

## Area 1 — Change Backup Code

**Entry point:** New "Change backup code" row on `PersonalIdProfileFragment`.

**Flow:**

1. Tap "Change backup code" → `personalid_confirm_backup_code`
   (`PersonalIdProfileBackupCodeFragment(CONFIRM_BACKUP_CODE)`)
   - One field, no API call — local validation only (compare against `ConnectUserRecord.password`)
   - "Forgot backup code?" link visible only if `user.email != null`

2. On submit → `PersonalIdProfileBackupCodeFragment(SET_NEW_CODE)`
   - Two fields (new + confirm)
   - "Save" → `PersonalIdUnlocker.unlock(ALWAYS)` → `POST /users/set_backup_code` with
     `ProvidedAuth(userId, password)`

3. **Success:** update `ConnectUserRecord.password = newCode`, persist, pop back to profile
   with success toast

4. **`INCORRECT_BACKUP_CODE`:** navigate back to `personalid_confirm_backup_code`,
   clear fields, show error

5. **`ACCOUNT_LOCKED`** (3 failed attempts, 24h): navigate to message screen with a CTA to pop back to profile. 

**Analytics:**
- Workflow initiated — with source (Manage Profile / Email Recovery Flow / Reminder prompt)
- Outcome: success / failure / cancelled
- Failure reason (e.g. server error, network issue, incorrect current backup code)
- Number of attempts to confirm current backup code

---

## Area 2 — Backup Code Recovery

**Triggered from** "Forgot backup code?" link on:
- `PersonalIdBackupCodeFragment` in signup/recovery graph (session token available)
- `PersonalIdProfileBackupCodeFragment` in profile graph (user signed in, no session token)

**Visibility rule:**
- Recovery graph: link hidden if `user.email == null`
- Profile graph: link always visible; if `user.email == null`, redirect to Manage Profile with
  toast "Please add email to recover your backup code"

### `EmailWorkFlow.BACKUP_CODE_RECOVERY`

New value added to `EmailWorkFlow` enum. Changes to `PersonalIdEmailVerificationFragment`:
- `onEmailVerified()` — new branch navigates to `PersonalIdProfileBackupCodeFragment(SET_NEW_CODE)`
- 3-failure path: navigate to message screen with CTA to pop back to profile
- no "skip" option

### Flow

1. "Forgot backup code?" → `PersonalIdSendEmailOtpFragment(email=user.email, BACKUP_CODE_RECOVERY)`
   → tap "Send code" → `send_email_otp`
   → navigate to `PersonalIdEmailVerificationFragment(BACKUP_CODE_RECOVERY)`

2. OTP verified → navigate to `PersonalIdProfileBackupCodeFragment(SET_NEW_CODE)`
   - Two fields, biometric gate on Save
   - `POST /users/set_backup_code`
   - Auth: `TokenAuth(sessionToken)` in recovery graph;
     `ProvidedAuth(userId, ConnectUserRecord.password)` in profile graph

3. **Success:** update `ConnectUserRecord.password`
   - Navigate to next screen in recovery graph with success toast
   - Success Message screen with CTA to pop back to profile

**Analytics:**
- Recovery workflow initiated
- OTP requested
- OTP verification attempt
- New backup code set (success / failure)
- User blocked on reminder prompt and redirected to set new backup code (success / failure)

---

## Area 3 — Periodic Backup Code Reminders

### State

One new key in `PersonalIdUserPreferences`:
- `KEY_BACKUP_CODE_REMINDER_NEXT_DUE` (Long) — initialized to `now + 1h` at registration

### Scheduling

Reminder cadence:
- First: 1h after registration
- Second: ~3d after registration (scheduled from current time when first fires)
- Subsequent: every 7d rolling

### Hook

All CommCare launching activities (e.g. `ConnectActivity`, `LoginActivity`, `StandardHomeActivity`) check for due reminders on resume. 
If due, show the reminder dialog.

`onResume()` calls `PersonalIdReminderHelper.isDue()`. If due, show
`BackupCodeReminderDialogFragment`.

### `BackupCodeReminderDialogFragment`

- Single `NumericCodeView` + "Skip" and "Confirm" buttons
- Local validation only: compare entered code against `ConnectUserRecord.password` (no API call)
- **Correct or skip:** schedule next reminder time and dismiss
- **Wrong code (up to 3):** show inline error, allow retry
- **3 wrong attempts:** dismiss and initiate the backup code recovery flow (Area 2). If there is no email, show a toast "Please add email to recover your backup code" and pop to Manage Profile.
- **Forgot backup code:** Initiates the backup code recovery flow (Area 2)

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

## Area 4 — Email Modification Flow Changes

### 1. Changing email (user has existing email)

`PersonalIdProfileEditFragment.onSaveClicked()` when `isEmailModified()` and `user.email != null`:
store new email in `PersonalIdProfileActivityViewModel.pendingEmail`, then navigate to
`personalid_confirm_backup_code`.

On backup code confirmed → `PersonalIdSendEmailOtpFragment(email=pendingEmail, EXISTING_USER)`
→ `PersonalIdEmailVerificationFragment(EXISTING_USER)` → `PersonalIdUnlocker.unlock(ALWAYS)`
→ persist new email locally, pop to profile with success toast.

The `PersonalIdProfileBackupCodeFragment` is reused from Area 1 with a separate
nav action for the email-change exit path.

### 2. Adding email (no existing email, already signed in)

`PersonalIdProfileEditFragment.onSaveClicked()` when `isEmailModified()` and `user.email == null`:
store new email in `PersonalIdProfileActivityViewModel.pendingEmail`, then navigate to
the Phone OTP verification screen(`PersonalIdProfilePhoneVerificationFragment`) (sends otp to `ConnectUserRecord.primaryPhone`)

On phone OTP verified → `PersonalIdSendEmailOtpFragment(email=pendingEmail, EXISTING_USER)`
→ `PersonalIdEmailVerificationFragment(EXISTING_USER)` → `PersonalIdUnlocker.unlock(ALWAYS)`
→ persist new email locally, pop to profile with success toast.

Note: On manage profile screen under "Email" row, if `user.email == null`, the row should show a hint message -
"A verification code will be sent to your registered phone number to add an email address." 

### 3. Skip email dialog during signup

`PersonalIdEmailFragment.confirmSkipEmail()`: same dialog logic, string resources updated to
"Protect your account" framing:

> "Adding an email strengthens your account security. It helps verify important account changes,
> prevents unauthorized recovery attempts, and gives you a secure way to regain access if you
> lose your device."

Buttons: "Skip" / "Add Email".


### 4. Forgot backup code with no email (profile graph only)

When `user.email == null` and "Forgot?" is tapped in the  profile graph, the user is redirected
immediately to Manage Profile with a toast:

> "Please add email to recover your backup code"

No intermediate screen is shown. The user must add an email via change #2 before recovery is possible.

**Analytics:**
- Email change initiated (with backup code gate)
- Email change outcome: success / failure / cancelled/r
- Add email (no existing email) initiated
- Add email outcome: success / failure / cancelled
- Skip email prompt shown
- Skip email prompt outcome: skipped / added email
