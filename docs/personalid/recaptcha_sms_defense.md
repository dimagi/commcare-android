# reCAPTCHA SMS Defense for Firebase Phone OTP

## Overview

PersonalID phone verification sends its OTP through Firebase Auth phone sign-in
(`PersonalIdPhoneVerificationFragment` → `OtpManager` → `FirebaseAuthService`). Firebase applies an
automated carrier-exclusion guardrail to carriers with low SMS success rates, which surfaces as
**error 39**: the attempt is blocked before an SMS is sent. Users then retry, and because Identity
Toolkit still logs each blocked attempt, they hit the IP rate limit and see `TOO_MANY_ATTEMPTS`.

Enabling **reCAPTCHA SMS Defense** (formerly toll fraud protection) exempts the project from those
automatic guardrails: error 39 stops and rate limits relax, in exchange for us owning the risk of
billing anomalies and poor delivery from the affected carriers.

Everything below is project-level configuration on Firebase project `<firebase_project_id>`
(number `<firebase_project_number>`). There is no client-side code change — see
[Client requirements](#client-requirements).

## Local setup

The tooling fetches an access token at runtime rather than storing credentials, so `gcloud` must be
installed and logged in:

```bash
brew install --cask google-cloud-sdk
gcloud components install beta   # `services identity` exists only in the beta track
gcloud auth login
gcloud config set project <firebase_project_id>
```

Tokens expire; re-run `gcloud auth login` when commands start failing with
`Reauthentication failed`.

## Project prerequisites

All of these are **already in place** on `<firebase_project_id>`, verified 2026-08-21. They are recorded
here for understanding and for reproducing the setup on another project.

**Required APIs**

```bash
gcloud services enable recaptchaenterprise.googleapis.com --project=<firebase_project_id>
gcloud services list --enabled --project=<firebase_project_id>   # verify
```

Both `recaptchaenterprise.googleapis.com` and `identitytoolkit.googleapis.com` are enabled.

**Identity Toolkit service agent** — Identity Platform calls reCAPTCHA Enterprise server-side during
`sendVerificationCode`, authenticating as its own service agent.
`roles/identitytoolkit.serviceAgent` is what permits that call.

Google normally provisions this agent when Identity Toolkit is enabled, so **check before changing
anything** — on an already-working project there is nothing to run here.

### Check: does the agent hold the role?

```bash
gcloud projects get-iam-policy <firebase_project_id> \
  --flatten="bindings[].members" \
  --filter="bindings.members:gcp-sa-identitytoolkit" \
  --format="value(bindings.members,bindings.role)"
```

Expected output — one tab-separated row:

```
serviceAccount:service-<firebase_project_number>@gcp-sa-identitytoolkit.iam.gserviceaccount.com	roles/identitytoolkit.serviceAgent
```

**If that row appears, this section is done — skip the rest of it.** The agent exists and is bound,
which is the only state reCAPTCHA needs. On `<firebase_project_id>` the row is present.

**If the output is empty**, the agent is missing or unbound, and the two commands below fix it.

### Repair: only if the check came back empty

```bash
# force-creates the agent if it was never provisioned; otherwise just prints its address
gcloud beta services identity create \
  --service=identitytoolkit.googleapis.com --project=<firebase_project_id>

gcloud projects add-iam-policy-binding <firebase_project_id> \
  --member=serviceAccount:service-<firebase_project_number>@gcp-sa-identitytoolkit.iam.gserviceaccount.com \
  --role=roles/identitytoolkit.serviceAgent
```

Both are idempotent, but the binding needs `resourcemanager.projects.setIamPolicy`, which
`roles/editor` does **not** grant — see [Permissions you need to run this](#permissions-you-need-to-run-this). Expect to
raise an infra request rather than running it yourself. Re-run the check afterwards.

### Reference: what the role permits

Not a check — this is the global role definition and returns the same thing on any project. Useful
only to see why the role is the one that matters:

```bash
gcloud iam roles describe roles/identitytoolkit.serviceAgent
```

Expected output:

```yaml
description: Gives Identity Platform service account access to customer project resources.
etag: AA==
includedPermissions:
- cloudfunctions.functions.invoke
- recaptchaenterprise.assessments.create
- recaptchaenterprise.keys.create
- recaptchaenterprise.keys.delete
- recaptchaenterprise.keys.get
name: roles/identitytoolkit.serviceAgent
stage: GA
title: Identity Platform Service Agent
```

Four of the five permissions are `recaptchaenterprise.*`. `assessments.create` is the one that
matters here — it is what scores each `sendVerificationCode`. `cloudfunctions.functions.invoke`
belongs to Auth blocking functions and is unrelated. Predefined roles can change over time, so treat
this as what to expect rather than a contract.

## Permissions you need to run this

Reading and changing the config needs `firebaseauth.configs.get` and
`firebaseauth.configs.update`. `roles/firebaseauth.admin` grants both, but `roles/editor` is
sufficient and is what we hold in practice.

Query effective permissions rather than inferring them from role names:

```bash
curl -s -X POST -H "Authorization: Bearer $(gcloud auth print-access-token)" \
  -H "Content-Type: application/json" \
  -d '{"permissions":["firebaseauth.configs.update","resourcemanager.projects.setIamPolicy"]}' \
  "https://cloudresourcemanager.googleapis.com/v1/projects/<firebase_project_id>:testIamPermissions"
```

`firebaseauth.configs.update` is granted via `roles/editor` (verified 2026-08-26), so IAM is not a
blocker for enabling SMS Defense — though the write itself remains unexercised.
`resourcemanager.projects.setIamPolicy` is **not** granted, so the service-agent binding above would
need an infra request (Owner or Project IAM Admin).

## Client requirements

Already satisfied, no dependency or code change needed:

| Requirement | Ours |
| --- | --- |
| `firebase-auth` ≥ 23.1.0 | 23.2.1 (via `firebase-bom:33.16.0`) |
| `com.google.android.recaptcha` ≥ 18.5.1 | 18.6.1 (transitive) |
| `minSdkVersion` ≥ 23 | 23 |

`FirebaseAuthService` uses plain `PhoneAuthOptions` / `verifyPhoneNumber` with no app-verification
overrides, so the SDK picks up the project's reCAPTCHA configuration on its own.

## reCAPTCHA keys

**"reCAPTCHA key" and "site key" are the same thing.** Per the
[keys overview](https://docs.cloud.google.com/recaptcha/docs/keys):

> In some parts of the API reference documentation, reCAPTCHA keys are also referred to as
> *site keys*.

So the console's "Configured platform site keys", the `recaptchaKeys` field in the Identity Platform
config, and the output of `gcloud recaptcha keys list` all refer to the same objects. Two distinct
things are easy to conflate, though:

* **The project's key inventory** — every key that exists, listed by `gcloud recaptcha keys list`.
* **`recaptchaConfig.recaptchaKeys`** — the subset *registered with Identity Platform*, each tagged
  with a platform `type` (`WEB` / `IOS` / `ANDROID`). These are references
  (`projects/{project}/keys/{key}`) to keys that already exist in the inventory.

Mobile keys are always score-based, so they are invisible to users. Our existing web key is
CHECKBOX type and cannot be reused for Android.

**Prefer the console for creation.** `gcloud` has no flag for SMS Toll Fraud Defense, so a
gcloud-created key always needs a follow-up console visit — see
[Enable SMS defense on the key](#enable-sms-defense-on-the-key).

### Create an Android key with gcloud

One key can cover several packages
([docs](https://docs.cloud.google.com/recaptcha/docs/create-key-mobile#create-recaptcha-key-gcloud)):

```bash
gcloud recaptcha keys create \
  --display-name="CommCare Android" \
  --android \
  --package-names=org.commcare.dalvik \
  --project=<firebase_project_id>
```

Verify, and note the returned key id:

```bash
gcloud recaptcha keys list --project=<firebase_project_id>
gcloud recaptcha keys describe KEY_ID --project=<firebase_project_id>
```

### Create an Android key from the console

Same result via the UI
([docs](https://docs.cloud.google.com/recaptcha/docs/create-key-mobile#create-recaptcha-key-console)):

1. Open **Google Cloud Fraud Defense** in the Cloud console for `<firebase_project_id>`.
2. Select the **Keys** tab and click **Create key**.
3. Enter a display name and select **Android** as the application type.
4. Click **Add Android package** and enter a package name; repeat for each package.
5. Click **Create key**.

Leave **Disable package name verification** *off* — that is what restricts the key to our packages.
The docs also suggest separate keys for Play Store versus other distribution channels, which would
matter if LTS ships outside Play.

Whichever method is used, do not edit or delete `6Ldb8QgeXXXX8` (the
`commcarehq.org` web key), and do not hand-edit any key named *"Key for Identity Platform
integration with reCAPTCHA"* — Identity Platform manages that one itself.

### Creating an Android key never asks for a SHA-256 fingerprint

Neither method takes SHA-256 as an input. Both ask only for a display name and package names, so an
Android key is bound to the package name alone.

> [!NOTE]
> ### Resolved: support's SHA-256 instruction
>
> The *"Email Response from Firebase"* comment instructed us to enter a package name and
> SHA-256 during key creation, matching neither flow above. Resolved 2026-08-27: SHA-256 belongs to
> the Firebase Android app (Project Settings → Your apps) and was already registered. The step
> actually missing was [Enable SMS defense on the key](#enable-sms-defense-on-the-key).

## Register the key with Identity Platform

Creating the reCAPTCHA key — in the reCAPTCHA Enterprise console or with `gcloud` — only generates
the site key; it does not automatically link it to Firebase Authentication.

You must map the generated key to the project's Identity Toolkit configuration using the API call
below. Firebase Authentication needs this mapping to know which reCAPTCHA Enterprise key to invoke
when performing risk analysis and verifying phone numbers for the Android app.

The two live in separate resources owned by separate APIs — the key inventory under
`recaptchaenterprise`, the mapping in `recaptchaConfig.recaptchaKeys` under `identitytoolkit` — so
neither creation flow can write the mapping. Until it exists the key is invisible in the Firebase
console and Identity Platform has nothing to produce assessments with.

There is no `gcloud` equivalent for this write: `gcloud` exposes no `identity-platform` command group
and `gcloud firebase` covers only Test Lab. It is REST, or the Firebase Admin SDK.

`scripts/recaptcha_sms_defense.py` does **not** do this step — it only warns
(`no recaptchaKeys configured on this project`). There is no subcommand for it; register with the
call below before using `on`.

### Register

`updateMask` scopes the write to this one field:

```bash
curl -X PATCH -H "Authorization: Bearer $(gcloud auth print-access-token)" \
  -H "x-goog-user-project: <firebase_project_id>" \
  -H "Content-Type: application/json" \
  "https://identitytoolkit.googleapis.com/admin/v2/projects/<firebase_project_id>/config?updateMask=recaptchaConfig.recaptchaKeys" \
  -d '{"recaptchaConfig":{"recaptchaKeys":[{"key":"projects/<firebase_project_id>/keys/<android_key_id>","type":"ANDROID"}]}}'
```

The response is the full config object with `recaptchaKeys` populated.

> [!IMPORTANT]
> `x-goog-user-project` is required. Without it, writes fail with **403** —
> *"The identitytoolkit.googleapis.com API requires a quota project, which is not set by default."*
> A user access token carries no quota project of its own, so the billing/quota target has to be
> named explicitly. Using the header needs `serviceusage.services.use` on that project, which
> `roles/editor` grants. `scripts/recaptcha_sms_defense.py` sets this header itself, which is why the
> script never hits this error. Reads may succeed without it — do not infer from a working `GET` that
> a `PATCH` will work.

### Verify

```bash
curl -s -H "Authorization: Bearer $(gcloud auth print-access-token)" \
  -H "x-goog-user-project: <firebase_project_id>" \
  "https://identitytoolkit.googleapis.com/admin/v2/projects/<firebase_project_id>/config" \
  | python3 -c 'import json,sys; print(json.dumps(json.load(sys.stdin).get("recaptchaConfig",{}),indent=2))'
```

Expected output *before* registering — the whole subobject is absent, which is what an unregistered
project looks like:

```json
{}
```

Expected output *after* registering:

```json
{
  "recaptchaKeys": [
    {
      "key": "projects/<firebase_project_id>/keys/<android_key_id>",
      "type": "ANDROID"
    }
  ]
}
```

### Two things to get right

* **`recaptchaKeys` is an array, and a masked PATCH replaces it wholesale.** Always resend every
  entry that should survive — registering an `IOS` or `WEB` key later without repeating the `ANDROID`
  entry silently drops it.
* **Register before enabling.** Registration is independent of `phoneEnforcementState`, so it can be
  done while enforcement is still off — the safe order, since raising enforcement with no key
  registered leaves Identity Platform nothing to produce assessments with.

## Enable SMS defense on the key

**Console only**, and required. Neither `gcloud` nor the v1 Key resource exposes this setting, and
`useSmsTollFraudProtection: true` does not imply it. Two layers must both be on:

| Layer | Setting | Visibility |
| --- | --- | --- |
| Identity Platform | `useSmsTollFraudProtection` | API, set by [Toggle script](#toggle-script) |
| reCAPTCHA Enterprise | SMS defense enablement | console only, manual |

Google Cloud console → **reCAPTCHA** (or Fraud Defense) → select the key → configuration panel →
enable **SMS Toll Fraud Defense**. It mirrors to **Settings → SMS defense → Configure**, so either
surface confirms it.

With the Identity Platform flag on and this off, assessments still run and return a bot score but no
toll-fraud verdict: SMS Defense is inert while `status` reports it enabled. On
`<firebase_project_id>` that state persisted for a day, producing `INVALID_APP_CREDENTIAL` on
`SendVerificationCode`, a fallback to legacy reCAPTCHA v2, and continued error 39.

### Verify

No API exposes the setting, so check functionally: `sms_tf_risk_scores` records a point per assessed
request once it is on, and nothing while it is off — see
[Raw metric queries](#raw-metric-queries). On `<firebase_project_id>` the first score point landed
the same second the toggle was enabled, so there is no propagation delay to wait out.

## Toggle script

`scripts/recaptcha_sms_defense.py` turns reCAPTCHA SMS Defense on and off, and reports the current
setting. Before changing anything it shows what will change and asks for confirmation, and it saves
a backup of the old config so the change can be undone.

> [!IMPORTANT]
> `status` reads the Identity Platform layer only. It cannot see the console-only reCAPTCHA-side
> enablement, so it will report `useSmsTollFraudProtection: true` even when no toll-fraud assessment
> is happening. Treat it as necessary but not sufficient, and confirm with `sms_tf_risk_scores` —
> see [Enable SMS defense on the key](#enable-sms-defense-on-the-key).

### reCAPTCHA ON and OFF with different modes

```bash
  ./scripts/recaptcha_sms_defense.py status --project <firebase_project_id>
  ./scripts/recaptcha_sms_defense.py on --mode audit --project <firebase_project_id>
  ./scripts/recaptcha_sms_defense.py on --mode enforce --project <firebase_project_id>
  ./scripts/recaptcha_sms_defense.py off --project <firebase_project_id>
```

`--dry-run` builds and prints the real payload without sending it. `--threshold` defaults to `0.8`.

### Current state

Read on 2026-08-21 (re-verify before acting, it may have moved):

| Field | Value | Meaning | Affects phone auth |
| --- | --- | --- | --- |
| `phoneEnforcementState` | `OFF` | Master switch for reCAPTCHA on phone sign-in: `OFF`, `AUDIT` or `ENFORCE`. Gates the two flags below — the API rejects setting either true unless this is `AUDIT` or `ENFORCE`. `AUDIT` scores without blocking, `ENFORCE` blocks. While `OFF`, Firebase's carrier guardrails apply, which is what produces error 39. | Yes — it is the switch |
| `useSmsTollFraudProtection` | `false` | Turns on SMS Defense itself, the toll-fraud assessment. The field this ticket exists to flip; takes its threshold from `tollFraudManagedRules`. | Yes |
| `useSmsBotScore` | `false` | Bot protection — a separate feature sharing `phoneEnforcementState`, asking whether the caller is a bot rather than whether the request is toll fraud, configured via `managedRules` / `endScore`. Raising enforcement is what activates it, so leaving it false keeps audit results attributable to SMS Defense alone. | Yes, if enabled — we are leaving it off |
| `emailPasswordEnforcementState` | `UNSPECIFIED` | The same enforcement switch for email/password sign-in. `UNSPECIFIED` means never configured, which is not an explicit `OFF`. Google's documented `updateConfig` snippet hardcodes `"OFF"` here. | No — email/password only |
| `tollFraudManagedRules` | absent | An array, not a scalar. Each entry is `{"action": "BLOCK", "startScore": N}`, and `startScore` is the only place a threshold exists. Absent means no threshold at all, which is not the same as zero. | Yes — sets the blocking threshold |
| `recaptchaKeys` | empty | Per-platform key registrations, each `{"key": "projects/{project}/keys/{KEY_ID}", "type": "ANDROID" \| "WEB" \| "IOS"}`, referencing a key already in the project's inventory. Distinct from what `gcloud recaptcha keys list` returns: the inventory holds the web key below, this registration list is empty. Empty means Identity Platform has no key to produce assessments with. | Yes — the `ANDROID` entry is required |

The inventory holds two keys. `6Ldb8QgeXXXX8` is a **CHECKBOX web** key for `commcarehq.org` created
in 2022; it is unrelated to mobile auth and must not be edited or deleted. *"Android - CommCare
reCAPTCHA Key"* was created 2026-08-26 for `org.commcare.dalvik` with package-name enforcement on
(`allowAllPackageNames: false`); it is **not yet registered** with Identity Platform — see
[Register the key with Identity Platform](#register-the-key-with-identity-platform).

### Threshold semantics

There is no standalone threshold field. It lives as `startScore` inside a `tollFraudManagedRules`
entry and means *start blocking at this fraud-likelihood score* — so **higher is more permissive**. Valid range is 0.0–0.9. A stored `0` would block
everything, which is why `off` removes the rule rather than zeroing it.

Bot protection is a separate feature sharing the same `phoneEnforcementState`, keyed on
`managedRules` / `endScore`, which runs the other way. The script never touches it, but note that
flipping enforcement is what makes it live — keep `useSmsBotScore` false so audit results aren't
confounded.

## Monitoring

### Metrics Explorer

This is the place to watch a rollout. Open
[Metrics Explorer](https://console.cloud.google.com/monitoring/metrics-explorer), pick the project,
and in **Select a metric** search for the resource type **`Identity Toolkit Tenant`** — not the
metric path, which the picker does not match. The reCAPTCHA metrics are listed under that resource.
It is the "Tenant" resource even though we use no tenants; parent-project data appears with
`tenant_name` empty.

**Nothing is visible until reCAPTCHA is enabled.** A verdict is only recorded when a token is
present, so with `phoneEnforcementState: OFF` no time series exists, and Metrics Explorer hides
metric types that have no data. Expect an empty search until an audit window is running; turning off
the picker's **Active** filter reveals the descriptors with empty charts.

The four metrics, all under `identitytoolkit.googleapis.com`:

* `recaptcha/verdict_count` — the outcome of each assessment.
* `recaptcha/sms_tf_risk_scores` — SMS defense risk score distribution, used to choose the threshold.
* `recaptcha/token_count` — token status: `VALID`, `EXPIRED`, `DUPLICATE`, `INVALID`, `MISSING`,
  `UNCHECKED`.
* `recaptcha/risk_scores` — bot protection scores, a separate feature we are not enabling.

`verdict_count` is the one to read first, and its values matter:

* `PASSED` — would be allowed under enforcement.
* `FAILED_AUDIT` — denied in audit mode, i.e. enforcement *would* have blocked it.
* `FAILED_ENFORCE` — denied under enforcement.
* `CLIENT_TYPE_MISSING` — the request carried no client type, typically an SDK too old to support
  reCAPTCHA. Our floors are met, so this should stay at zero.
* `KEYS_MISSING` — Identity Platform could not retrieve valid reCAPTCHA keys. **This is the direct
  signal for the site-key question**: any of these during an audit window means the key is not
  usable and the scores mean nothing.

In audit mode, judge readiness from the ratio of `PASSED` to `FAILED_AUDIT`: a `FAILED_AUDIT` is a
request that *would* have been blocked under enforcement, so a high proportion means enforcing would
reject real users at the chosen threshold.

### Raw metric queries

Metrics Explorer is the place to watch a rollout, but for a quick check — or to distinguish "no data"
from "misconfigured" — query Cloud Monitoring directly. The method is `projects.timeSeries.list` on
the Monitoring API v3:

```bash
curl -s -G \
  -H "Authorization: Bearer $(gcloud auth print-access-token)" \
  -H "x-goog-user-project: <firebase_project_id>" \
  --data-urlencode 'filter=metric.type="identitytoolkit.googleapis.com/recaptcha/token_count"' \
  --data-urlencode 'interval.startTime=2026-08-27T00:00:01Z' \
  --data-urlencode 'interval.endTime=2026-08-27T23:59:59Z' \
  "https://monitoring.googleapis.com/v3/projects/<firebase_project_id>/timeSeries"
```

Swap the metric in the filter for `recaptcha/verdict_count` or `recaptcha/sms_tf_risk_scores`. Needs
`monitoring.timeSeries.list`, which `roles/editor` grants.

`-G` with `--data-urlencode` is required, not stylistic: the filter contains quotes, `=`, `/` and
spaces, and the request fails unless they are encoded onto the query string.

One time series comes back per label combination, so `token_state` and `verdict_state` arrive as
*separate series* rather than fields on one. Counts live in `points[].value.int64Value`, one point
per sample interval. To summarise:

```bash
... | python3 -c '
import json,sys
for ts in json.load(sys.stdin).get("timeSeries",[]):
    l=ts["metric"]["labels"]
    tot=sum(int(p["value"].get("int64Value",0)) for p in ts.get("points",[]))
    print(l, "total=%d" % tot)'
```

`sms_tf_risk_scores` is a **distribution**, not a counter — it has no `int64Value`, so the snippet
above sums to `0` and looks like "no data". Read `distributionValue.count` instead:

```bash
... | python3 -c '
import json,sys
for ts in json.load(sys.stdin).get("timeSeries",[]):
    for pt in ts.get("points",[]):
        dv=pt["value"].get("distributionValue") or {}
        if int(dv.get("count",0) or 0):
            print(pt["interval"]["endTime"], "count=%s" % dv["count"])'
```

Print every point, not a slice — truncating the list is an easy way to misread when scoring started.
For the threshold decision, decode `distributionValue.bucketCounts`; `mean` is often absent.

> [!IMPORTANT]
> **Label values are lowercase in the API**, unlike the uppercase forms listed above. Observed on this
> project: `token_state` of `invalid` and `missing`, and `verdict_state` of `failed_in_audit` — note
> that last one is not `FAILED_AUDIT`. A filter written with the uppercase spelling matches nothing.
> The remaining values are documented as listed above but have not been observed here, so confirm the
> exact spelling against a real series before filtering on one.

**Zero series is not the same as misconfigured.** `sms_tf_risk_scores` stays empty until a token
validates, since a score cannot be computed without one — so an audit window that is refusing every
token yields no scores while `token_count` and `verdict_count` are both populated.

### Per-key charts

Google Cloud console → **reCAPTCHA** → **Keys** → click the key → **Bots** tab gives a Score
overview chart (set a threshold under *Risky definition*) and a Challenges chart. The docs do not
describe SMS-toll-fraud-specific charts here, so treat this as supporting detail and rely on
`sms_tf_risk_scores` above.

### Errors

`SendVerificationCode` outcomes — `INVALID_APP_CREDENTIAL`, `Error code: 39`,
`TOO_MANY_ATTEMPTS_TRY_LATER`, `MISSING_RECAPTCHA_TOKEN` — come from the Identity Toolkit request
log, bucketed by hour:

```bash
gcloud logging read \
  'logName="projects/<firebase_project_id>/logs/identitytoolkit.googleapis.com%2Frequests"
   AND timestamp>="2026-08-27T06:00:00Z"' \
  --project=<firebase_project_id> --limit=300 --format=json \
| python3 -c '
import json,sys,collections
buck=collections.defaultdict(collections.Counter)
for e in json.load(sys.stdin):
    p=e.get("jsonPayload",{})
    if (p.get("methodName") or "").split(".")[-1]!="SendVerificationCode": continue
    buck[e["timestamp"][11:13]+":00"][(p.get("status") or {}).get("message","SUCCESS")[:30]]+=1
for h in sorted(buck): print(h, dict(buck[h]))'
```

This is the fastest read on whether a rollout is healthy: a rising `INVALID_APP_CREDENTIAL` count
means app verification is failing and every send is taking the legacy fallback path, which burns the
rate limit and keeps error 39 alive. Drop the `methodName` filter to see `GetRecaptchaParam` (the
legacy v2 fallback) and `GetRecaptchaConfig` interleaved, which shows the retry sequence per request.
Note the log carries **no package identifier**, so failures cannot be attributed to a specific app.

Firebase Auth errors, including error 39, are in
[Logs Explorer](https://console.cloud.google.com/logs/query?project=<firebase_project_id>) for the
project — this is where Firebase support read our error 39 and `TOO_MANY_ATTEMPTS` volumes from.

The exact filter is not documented in the public docs. Identity Toolkit activity arrives as audit
logs, so start from the service and narrow by the error text:

```
protoPayload.serviceName="identitytoolkit.googleapis.com"
```

Confirm the shape against real entries before relying on it, for example:

```bash
gcloud logging read 'protoPayload.serviceName="identitytoolkit.googleapis.com"' \
  --project=<firebase_project_id> --limit=5
```

Monitor hourly through any audit or enforcement window and roll back if the error rate climbs.

### Where to view in console

Two panes show parts of the config:

**Google Cloud console** — Security → reCAPTCHA → Settings → SMS defense → Configure

**Firebase console** — select project → left navigation menu → Authentication → Settings → Safety
(or reCAPTCHA Enterprise)

Both surface the Enable toggle, the enforcement mode (`AUDIT` or `ENFORCE`) and the threshold, the
threshold as a slider. The script's `status` reads the same fields from the API, which is the quicker
check and the one to trust if the two ever disagree.

## Cost

Three separate cost surfaces, and only the first has firm published figures.

### reCAPTCHA assessments

Per [Fraud Defense billing information](https://docs.cloud.google.com/recaptcha/docs/billing-information)
and the [tier comparison](https://docs.cloud.google.com/recaptcha/docs/compare-tiers):

* 0–10,000 assessments per calendar month are **free**, and that allowance is **per organization**,
  aggregated across every project and key — not per project. The existing `commcarehq.org` web key
  already draws on the same pool.
* With billing enabled (Premium): a **$8 flat fee** for assessments 10,001–100,000, then
  **$0.001 per assessment** ($1.00 per 1,000) above 100,000.
* Without billing enabled (Essentials): requests past 10,000 return a `429` quota error rather than
  being charged — which would break phone sign-in, so the project must stay on a billing account.

The billing page does not say whether an SMS Defense assessment is priced as an ordinary assessment
or separately. Treat that as unconfirmed.

### SMS delivery

Phone sign-in SMS is billed by Firebase / Identity Platform, not by reCAPTCHA — see
[Identity Platform pricing](https://cloud.google.com/identity-platform/pricing) and
[Firebase pricing](https://firebase.google.com/pricing). Rates vary by destination country.

Note that [Firebase Phone Number Verification pricing](https://firebase.google.com/docs/phone-number-verification/pricing)
is a **different product** from the phone sign-in this app uses, so its published per-verification
rates do not apply here. Our own per-message figure still needs confirming against a billing report.

**Expect SMS spend to rise as a direct result of this change.** Error 39 currently blocks sends
before an SMS goes out, and a blocked send costs nothing. Once the carrier exemption is in place
those attempts succeed and become billable. That increase is the fix working, not an anomaly.

### Toll-fraud exposure

This is the cost the ticket actually warns about. Enabling SMS Defense exempts the project from
Google's automatic carrier guardrails, and support was explicit that we then *"assume responsibility
for potential billing anomalies or low delivery rates from these carriers"*. See
[Detect and prevent SMS fraud](https://docs.cloud.google.com/recaptcha/docs/sms-fraud-detection) for
what the feature is protecting against.

In `AUDIT` mode nothing is blocked, so the exemption is in force with no mitigation — that is the
mode with the most fraud exposure. `ENFORCE` is what actually blocks suspected toll fraud, which is
the substance of the [open question in step 3](#3-enforce).

## Test plan

The staged rollout. Every window runs during late EST / early IST, when Connect traffic is lowest.

### 1. Prep up

| Ticket item | Status |
| --- | --- |
| Review the reCAPTCHA docs, change what is needed without enabling it, flag blockers | Done. Main blocker is the site key: `recaptchaKeys` is empty, and support's instruction to supply a SHA-256 during key creation matches neither creation flow — see [Creating an Android key never asks for a SHA-256 fingerprint](#creating-an-android-key-never-asks-for-a-sha-256-fingerprint) |
| Recommend a target reCAPTCHA score threshold | **Open** — `0.8` is Firebase's suggestion, not yet a team decision |
| Prepare API commands to turn reCAPTCHA on **and** off | Done — [Toggle script](#toggle-script). The `off` path has never been executed |
| Know where and how to monitor Firebase errors | Done — [Monitoring](#monitoring). The Logs Explorer filter is still unverified |
| Confirm no user-visible impact | Audit mode does not block, so no impact is expected there; enforcement can block real users, which is what the threshold decision governs |
| Document minimum Android version and compatibility | Done — [Client requirements](#client-requirements), all floors already met |

Do not start step 2 until the threshold is agreed and the site-key question is answered.

### 2. Audit mode

Audit collects scores without blocking anyone.

```bash
./scripts/recaptcha_sms_defense.py on --mode audit --project <firebase_project_id>
```

Then, using a release build installed from the Play Store:

1. Request an OTP and confirm the SMS arrives.
2. Repeat every 15 minutes for an hour, and across 2–3 different numbers and carriers.
3. Watch `verdict_count` in Metrics Explorer. `PASSED` and `FAILED_AUDIT` mean reCAPTCHA is
   working; any `KEYS_MISSING` means the key is not usable and the scores are meaningless — stop and
   fix that first.
4. Confirm `sms_tf_risk_scores` is receiving data, which is the proof that scoring runs at all.
5. Keep checking Logs Explorer hourly for the rest of the day.

Roll back immediately if the Firebase Auth error rate climbs:

```bash
./scripts/recaptcha_sms_defense.py off --project <firebase_project_id>
```

Note the metrics do not exist until this step begins, so an empty chart in the first minutes is
expected rather than a fault.

### 3. Enforce

> [!IMPORTANT]
> ### Open question: is enforce mode needed at all?
>
> The ticket says to *"test the change for CommCare by turning on ReCaptcha in same fashion as step
> 2"* without saying whether that means `AUDIT` or `ENFORCE`, and it never states that `ENFORCE` is
> the end state.
>
> This matters because the two modes buy different things. Support's own account is that `AUDIT`
> *"allows legitimate carrier traffic through that was previously blocked (resolving Error 39)"* — so
> if that holds, audit alone fixes the problem the ticket exists to solve. `ENFORCE` adds the
> blocking of high-risk requests, which protects us from toll-fraud billing rather than from error 39.
>
> So the trade is: stopping at `AUDIT` fixes OTP delivery but leaves us paying for any fraudulent
> traffic the exemption lets through, while going to `ENFORCE` adds that protection at the risk of
> blocking real users at whatever threshold we pick. Needs a decision from the team, and confirmation
> from support that audit really does clear error 39.
>
> Note also that audit is not entirely passive for phone auth: on a failed assessment it falls back
> to a silent push notification, and to a reCAPTCHA v2 challenge if that push does not arrive. That
> challenge is user-visible, so it bears on the "no user-visible impact" item in step 1.

Same window and the same checks, with enforcement active so failures actually block:

```bash
./scripts/recaptcha_sms_defense.py on --mode enforce --project <firebase_project_id>
```

Judge the threshold from the audit window's `PASSED` to `FAILED_AUDIT` ratio before starting: a high
proportion of `FAILED_AUDIT` means enforcing would reject real users. Watch `FAILED_ENFORCE` once
live — those are blocked requests. Roll back on any error spike and attach the full error logs to
the ticket.

### 4. CommCare LTS

Per the ticket requirements, we need to wait one week after enabling Audit Mode on CommCare. If no issues are reported and no errors appear in the logs during this period, we can proceed with enabling reCAPTCHA Audit Mode on CommCare LTS.