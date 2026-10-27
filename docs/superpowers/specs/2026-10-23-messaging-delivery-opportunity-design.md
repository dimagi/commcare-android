# PersonalID messaging delivery opportunities

## Intent

Connect is adding a new kind of opportunity in which delivery happens through PersonalID messaging
instead of a CommCare deliver app. These opportunities never have a deliver app. They may or may not
have a learn app:

- **With a learn app:** learning works as today. After claiming, the worker goes to Delivery Home,
  whose CTA opens messaging instead of a deliver app.
- **Without a learn app:** the worker reads Intro, claims from Intro, and goes straight to Delivery
  Home with the messaging CTA.

Today the mobile app assumes every opportunity has both apps. Parsing, storage, status derivation,
and most Connect screens dereference `learnAppInfo` / `deliveryAppInfo` unconditionally, so an
opportunity without them either fails to parse or crashes.

## Goals

- Opportunities with `delivery_type = "personalid_messaging"` parse, store, and display without
  crashing, with or without a learn app.
- Without a learn app: job list → Intro → **Claim** on Intro → DELIVERING → Delivery Home with the
  messaging CTA.
- With a learn app: job list → Intro → start learning → learn app → learn progress → claim →
  DELIVERING → Delivery Home with the messaging CTA.
- The status stays DELIVERING across syncs after claim.
- Existing learn/deliver opportunities behave exactly as today, except job list ordering (see
  **Job list ordering**), which changes for all opportunities.


## Server contract

On the opportunity list API, for a PersonalID messaging opportunity:

- `delivery_type`: new string field. `"personalid_messaging"` marks this type. Absent or any other
  value means the existing learn/deliver type (older servers, existing cached jobs).
- `deliver_app`: always `null` when delivery_type is "personalid_messaging".
- `learn_app`: an app object, or `null` when the opportunity has no learn app.
- `learn_progress`: still present; parsed as today but API is only called when learn app is present. 
- `launch.slug`: new nested field holding the messaging channel id the Delivery Home CTA opens.
  `launch` or `slug` would be absent or empty for non PersonalID messaging opportunities.

Other APIs:

- Learn progress API: the client must never call it for a job whose `learnAppInfo` is null.
- Claim API: called from Intro when there is no learn app, and from learn progress (as today) when
  there is one. Without a learn app, the server accepts the claim with no learning completed.
- Delivery progress, deliveries, and payments APIs: unchanged.

## Design

### Data model and storage

- Add a persisted `deliveryType` field to `ConnectJobRecord` (`delivery_type`). Branches use two
  checks on the record:
  - **PersonalID messaging delivery** (`deliveryType == "personalid_messaging"`): governs everything
    on the delivery side (deliver app, Delivery Home CTA, tasks).
  - **Has learn app** (`learnAppInfo != null`): governs everything on the learn side (Intro learn
    cards, start learning vs. claim, learn progress API, revisit learning).
- Add a persisted `launchSlug` field (`launch_slug`), parsed from `launch.slug` (empty when absent).
- Bump `DB_VERSION_CONNECT` (currently 30) once for both new fields, add the `ConnectJobRecordV<N>`
  snapshot, and add an upgrade step in `ConnectDatabaseUpgrader` that backfills existing rows with
  the default type and an empty channel id.
  Prior migrations that targeted `ConnectJobRecord.class` must be retargeted to the new snapshot,
  following the existing V2…V21 pattern.
- `ConnectJobRecord.fromJson`:
  - PersonalID messaging job: `learn_app` is nullable. `deliver_app`
    record is always null, validate and reject as malformed if not. 
  - Any other job: a null or missing `learn_app` or `deliver_app` is rejected as malformed (throws,
    so `ConnectOpportunitiesParser` skips the job as it does other bad entries). This keeps
    learn/deliver paths free of null guards. *(Our call.)*
- `JobStoreManager`. Stored app records and learn modules mirror the server, and no step reads
  fields of an app record that is null:
  - `storeAppInfo`: an app that is present is written as today. When `learnAppInfo` or
    `deliveryAppInfo` is null, any stored record of that kind for the job is deleted and nothing is
    written. Without the delete, `ConnectJobUtils.populateJobs`, which attaches every stored app
    record matching the job UUID, would bring back a learn app the server has removed.
  - `storeModules`: a null `learnAppInfo` counts as an empty incoming module list. The existing
    prune step deletes the job's stored modules, and nothing is written. Modules only arrive inside
    `learn_app.learn_modules`.
  - `pruneOldJobs`: when deleting a job the server no longer sends, it collects IDs only from app
    records that are present. Module IDs come from `learnAppInfo` when it is present. Without a
    learn app the job has no stored modules, because `storeModules` keeps them empty.

### Opportunity Status

Opportunity Status is set locally in four places:

| Where | What it does | PersonalID messaging change |
|---|---|---|
| `ConnectJobRecord.fromJson` | Derives AVAILABLE / LEARNING / DELIVERING from learn progress and `claim`. | New branch, below. |
| `JobStoreManager.storeOrUpdateJob` | Keeps the stored status if it is further along than the parsed one; marks newly seen AVAILABLE jobs AVAILABLE_NEW. | None. |
| `ConnectJobIntroFragment` start-learning success | Sets LEARNING. | Unchanged with a learn app; not reached without one. |
| `ConnectRepository.claimJob` success | Sets DELIVERING. | None; both claim entry points reuse it. |

`fromJson` branch for PersonalID messaging jobs:

1. `claim` present → DELIVERING.
2. Otherwise, has a learn app and learn progress > 0 → LEARNING.
3. Otherwise → AVAILABLE.

Without a learn app the job never enters LEARNING. The claim rule is needed because the
no-regression rule in `storeOrUpdateJob` only protects a device that recorded the claim locally. A
fresh install, a second device, or a claim made outside this device would otherwise derive
AVAILABLE and show Intro for a claimed job.

`passedAssessment()` reads `learnAppInfo.passingScore`, so it must not run for jobs without a learn
app.

### Screens

| Screen | With learn app                                                                                                                                                                                                 | Without learn app |
|---|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---|
| Job list (`ConnectOpportunityListFragment`) | For all jobs: remove the unused `isAppInstalled` checks and the unused `ConnectLoginJobListModel.isAppInstalled` field (nothing reads it), and sort as in **Job list ordering**. No app record lookups remain. | Same. |
| Intro (`ConnectJobIntroFragment`) | As today: learn cards shown, CTA starts learning.                                                                                                                                                              | Learn cards dropped entirely. CTA claims the job, then navigates to Delivery Home. |
| Learn progress (`ConnectLearningProgressFragment`, `ConnectLearnCompleteView`) | For Messaging opportunities, Claim success always navigates to Delivery Home; never launches a deliver app. The learn-complete CTA uses the "go to delivery" label, never the download-app label.              | Not reached. |
| Delivery Home (`ConnectDeliveryHomeFragment`) | CTA opens messaging (see **Messaging CTA**). No deliver-app install checks. `loadLearningProgress` runs as today.                                                                                              | Same, but `loadLearningProgress` is skipped. |
| Delivery dashboard / more | Task open handled as in **Tasks**. "Revisit learning" shown as today.                                                                                                                                          | Task open handled as in **Tasks**. "Revisit learning" hidden. |
| `ConnectActivity.handleInfoRedirect` | As today.                                                                                                                                                                                                      | A non-delivering job routes to Intro, not the learn progress screen. |

For jobs without a learn app, Intro needs claim handling (loading, error, success). It currently
lives only in `ConnectLearningProgressViewModel`, so Intro's ViewModel needs the same handling.

At places where job list cards would read description and organization from an app record, 
we would read them from the job record when app record is absent.

### Job list ordering

Applies to all opportunities, not only PersonalID messaging ones.

- Each job list section sorts by `ConnectJobRecord.lastWorkedDate`, most recent first. This replaces
  the sort by the linked app's `lastAccessed` (`processJobRecords`), which was ascending
  (least recent first) and only updated on CommCare app login.
- `lastWorkedDate` is set to now whenever the user taps a learn or delivery CTA for the job:
  - Intro CTA (start learning, or claim when there is no learn app)
  - Learn progress continue CTA
  - Learn-complete claim CTA
  - Delivery Home CTA (deliver app or messaging)
  - "Revisit learning"
  - Task taps on the dashboard and More tabs
- `last_worked` from the server stays unparsed. `JobStoreManager.storeOrUpdateJob` copies the
  stored `lastWorkedDate` onto the incoming record before writing, the same way it keeps status
  from going backwards. A newly seen job keeps the constructor default (first sync time).
- Logging into a CommCare app directly (not through a Connect CTA) no longer affects ordering.
- `lastWorkedDate` is already persisted; no migration needed for it.

### Messaging CTA

- Opens messaging through `ConnectNavHelper.unlockAndGoToMessaging` with the job's
  `launchSlug`, matching how conversation tasks open their channel.
- Empty channel id → opens the messaging channel list.
- Channel id not yet in the local messaging DB (for example, right after claim, before messages
  sync) → stays on the channel list instead of opening the channel. Today
  `ConnectMessageChannelListFragment.onViewCreated` passes the lookup result straight to
  `selectChannel`, which crashes on null. The fix belongs there, so it also covers conversation
  tasks.
- Consent is handled by the existing channel screen (`ConnectMessageFragment`); no change.

### Tasks

PersonalID messaging jobs can have pending tasks. Opening one goes through
`ConnectTaskPresenter.open` from the dashboard and More tabs:

- **Conversation task** : unchanged. Opens messaging on the task's channel.
- **Any other task**: shows an error toast when there is no app present -  "No action found for the task".

### Delivery Home CTA 

Today the CTA is disabled only when the deliver
app is installed **and** at least one task is pending. Otherwise it is enabled.

For PersonalID messaging jobs, the messaging CTA counts as an installed deliver app: **the CTA is
disabled while any task is pending, and enabled otherwise.** Accepted risk: a pending
non-conversation task blocks the CTA, and tapping it only shows the toast, so the worker stays
blocked until the server clears the task.

### Learn progress API guard

Guard inside `ConnectRepository.getLearningProgress`: if `learnAppInfo` is null, don't make the call.
This covers Delivery Home's `loadLearningProgress` and any future caller.

### Notifications

`ConnectJobUtils.getAppIdForOpportunity` (used by session-endpoint notifications in
`FirebaseMessagingUtil`) returns null when the requested app is absent. `handleSessionEndpointNotification` treats null
like its existing unresolved-app case and returns null, so no notification is shown.

## Other

- Robolectric UI tests for Intro claim → Delivery Home, learn-complete claim → Delivery
  Home, and the messaging CTA.
- Update `docs/connect/` with a short note on the PersonalID messaging delivery type.
