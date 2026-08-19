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
 └─[Save — email change]────────────────────────────────────────────────────┐
                                                                            ▼
                                                            Confirm backup code
                                                           │                │
                                                  [Forgot?]│       [correct]│
                                                           │                │
                                               [has email] │  [no email]    │
                                                      │         │           │
                                                      ▼         ▼           │
                                          (→ same as    Send Phone OTP      │
                                           Change        screen             │
                                           backup code        │             │
                                           forgot flow)       ▼             │
                                                        Phone OTP entry     │
                                                                   │        │
                                                                   └────────┘
                                                                        │
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
 └─[Forgot?]──► Send email OTP screen ──► Email OTP entry ──► Complete recovery (sign in) ──► Set new backup code
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
    workflows : CONFIRM_BACKUP_CODE_CHANGE_CODE, CONFIRM_BACKUP_CODE_CHANGE_EMAIL,
                CONFIRM_BACKUP_CODE_ADD_EMAIL, SET_NEW_CODE
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

| Value | API call | API Auth | Biometric/Pin Unlock on Save | Forgot? routing |
|---|---|---|---|---|
| `REGISTRATION` | none (stored in session) | — | no | n/a |
| `CONFIRM_RECOVERY` | `users/recover/confirm_backup_code` | session token | no | n/a |
| `CONFIRM_BACKUP_CODE_CHANGE_CODE` | none (local validation against `ConnectUserRecord.password`) | — | no | no email → Manage Profile toast; has email → email OTP recovery |
| `CONFIRM_BACKUP_CODE_CHANGE_EMAIL` | none (local validation against `ConnectUserRecord.password`) | — | no | → email OTP recovery (has email guaranteed) |
| `CONFIRM_BACKUP_CODE_ADD_EMAIL` | none (local validation against `ConnectUserRecord.password`) | — | no | → Send Phone OTP screen |
| `SET_NEW_CODE` | `/users/set_recovery_pin` | `ProvidedAuth(userId, password)` | yes | n/a |


#### Server Team Request — Account Recovery Without Backup Code

Two open questions for the server team to decide:

**1. Completing account recovery via email OTP (without backup code)**

When a user forgets their backup code during the account configuration flow, they verify via
email OTP (`verify_email_otp`). After OTP verification, the app needs to sign the user in —
without requiring a backup code. The response must return the same sign-in fields as
`confirm_backup_code` (`username`, `db_key`, `password`, `invited_user`, `previous_device`,
`last_accessed`, `email`).

Options for server team to decide between:
- **(a) New endpoint** — e.g., `POST /users/recover/confirm_email_otp`: accepts the OTP
  verification result and returns the sign-in payload.
- **(b) Extend `confirm_backup_code`** — accept an OTP token in place of the backup code
  and return the same payload.

**2. Email availability in the account configuration flow**

To offer email-based recovery during account configuration, the app must know the user's email
address before the "Forgot?" link is shown. Email is not currently returned early enough in
the flow.

Options for server team to decide between:
- **(a) `start_configuration` returns `email`** — preferred by client; email is available
  from the very start of the configuration flow and supports any future email-aware logic.
- **(b) `check_name` returns `email`** — makes email available at name-lookup time.

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
   (`PersonalIdProfileBackupCodeFragment(CONFIRM_BACKUP_CODE_CHANGE_CODE)`)
   - One field, no API call — local validation only (compare against `ConnectUserRecord.password`)
   - "Forgot backup code?" link always visible. If `user.email == null`, tapping it redirects
     immediately to Manage Profile with toast "Please add email to recover your backup code".
     If `user.email != null`, navigates to email OTP recovery.

2. On submit → `PersonalIdProfileBackupCodeFragment(SET_NEW_CODE)`
   - Two fields (new + confirm)
   - "Save" → `PersonalIdUnlocker.unlock(ALWAYS)` → `POST /users/set_recovery_pin` with
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
- `PersonalIdBackupCodeFragment` in account configuration graph (session token available)
- `PersonalIdProfileBackupCodeFragment` in profile graph (user signed in, no session token)

**Visibility rule:**
- Account configuration graph: link hidden if email is not available. 
- Profile graph: link always visible; if `user.email == null`, redirect to Manage Profile with
  toast "Please add email to recover your backup code"

### `EmailWorkFlow` enum values

Two distinct values handle the two recovery contexts:

| Enum value | Graph | OTP verified → | Outcome |
|---|---|---|---|
| `BACKUP_CODE_RECOVERY_SIGN_IN` | Account configuration | Complete recovery via server → `SET_NEW_CODE` | User signed in, new backup code set |
| `BACKUP_CODE_RECOVERY_SET_CODE` | Profile | `PersonalIdProfileBackupCodeFragment(SET_NEW_CODE)` | New backup code set |

Changes to `PersonalIdEmailVerificationFragment`:
- `onEmailVerified()` branches on workflow value (see table above)
- 3-failure path: navigate to message screen with CTA
- no "skip" option

### Flow — Account configuration graph (`BACKUP_CODE_RECOVERY_SIGN_IN`)

1. "Forgot backup code?" → `PersonalIdSendEmailOtpFragment(email=user.email, BACKUP_CODE_RECOVERY_SIGN_IN)`
   → tap "Send code" → `send_email_otp`
   → navigate to `PersonalIdEmailVerificationFragment(BACKUP_CODE_RECOVERY_SIGN_IN)`

2. OTP verified → call server endpoint to complete recovery without backup code
   (endpoint TBD — see server team request above)
   - Response: same sign-in fields as `confirm_backup_code` (`username`, `db_key`, `password`,
     `invited_user`, `previous_device`, `last_accessed`, `email`)

3. **Sign-in success:** navigate to `SET_NEW_CODE` screen — user must set a new backup code
   before continuing. Auth: `ProvidedAuth(userId, password)` using credentials from sign-in
   response.

4. **Backup code saved:** continue to next screen in account configuration flow with success toast

### Flow — Profile graph (`BACKUP_CODE_RECOVERY_SET_CODE`)

1. "Forgot backup code?" → `PersonalIdSendEmailOtpFragment(email=user.email, BACKUP_CODE_RECOVERY_SET_CODE)`
   → tap "Send code" → `send_email_otp`
   → navigate to `PersonalIdEmailVerificationFragment(BACKUP_CODE_RECOVERY_SET_CODE)`

2. OTP verified → navigate to `PersonalIdProfileBackupCodeFragment(SET_NEW_CODE)`
   - Two fields, biometric gate on Save
   - `POST /users/set_recovery_pin` with `ProvidedAuth(userId, ConnectUserRecord.password)`

3. **Success:** update `ConnectUserRecord.password`, success message screen with CTA to pop back
   to profile

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

`PersonalIdSendEmailOtpFragment` replaces the existing `showEmailOtpConfirmationDialog` in this flow.

`PersonalIdProfileEditFragment.onSaveClicked()` when `isEmailModified()` and `user.email != null`:
1. Store new email in `PersonalIdProfileActivityViewModel.pendingEmail`.
2. If name is also modified, call `saveProfileDetails` first (name save must complete before navigating).
3. Navigate to `personalid_confirm_backup_code` with `CONFIRM_BACKUP_CODE_CHANGE_EMAIL`.

On backup code confirmed → `PersonalIdSendEmailOtpFragment(email=pendingEmail, masked=true, EXISTING_USER)`
→ `PersonalIdEmailVerificationFragment(EXISTING_USER)` → `PersonalIdUnlocker.unlock(ALWAYS)`
→ persist new email locally, pop to profile with success toast.

On Forgot? → email OTP recovery (email guaranteed; same forgot flow as `CONFIRM_BACKUP_CODE_CHANGE_CODE` with email).

### 2. Adding email (no existing email, already signed in)

`PersonalIdProfileEditFragment.onSaveClicked()` when `isEmailModified()` and `user.email == null`:
1. Store new email in `PersonalIdProfileActivityViewModel.pendingEmail`.
2. Navigate to `personalid_confirm_backup_code` with `CONFIRM_BACKUP_CODE_ADD_EMAIL`.

On backup code confirmed → `PersonalIdSendEmailOtpFragment(email=pendingEmail, masked=false, EXISTING_USER)`
→ `PersonalIdEmailVerificationFragment(EXISTING_USER)` → `PersonalIdUnlocker.unlock(ALWAYS)`
→ persist new email locally, pop to profile with success toast.

On Forgot? → `PersonalIdProfilePhoneVerificationFragment` (sends OTP to `ConnectUserRecord.primaryPhone`)
→ on phone OTP verified → `PersonalIdSendEmailOtpFragment(email=pendingEmail, masked=false, EXISTING_USER)`
→ (same path as above).

### 3. Skip email dialog during signup

`PersonalIdEmailFragment.confirmSkipEmail()`: same dialog logic, string resources updated to
"Protect your account" framing:

> "Adding an email strengthens your account security. It helps verify important account changes,
> prevents unauthorized recovery attempts, and gives you a secure way to regain access if you
> lose your device."

Buttons: "Skip" / "Add Email".


**Analytics:**
- Email change initiated (with backup code gate)
- Email change outcome: success / failure / cancelled
- Add email (no existing email) initiated
- Add email outcome: success / failure / cancelled
- Skip email prompt shown
- Skip email prompt outcome: skipped / added email
