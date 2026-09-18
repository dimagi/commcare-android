# reCAPTCHA SMS Defense: audit results

## Android App Verification Order

```
                  1. App Verification Stage
+-------------------------------------------------------------+
|               Try Play Integrity API (Silent)               |
+------------------------------+------------------------------+
                               |
            +------------------+------------------+
            |                                     |
    [Play Integrity PASSES]              [Play Integrity FAILS]
            |                                     |
            v                                     v
 2. Risk Evaluation Stage                *Trigger Visible reCAPTCHA*
+---------------------------+            User solves visual puzzle on-screen.
| Evaluate reCAPTCHA Risk   |                     |
| Score against threshold   |            +--------+--------+
+-------------+-------------+            |                 |
              |                      [Solved]           [Failed / Cancelled]
      +-------+-------+                  |                 |
      |               |                  v                 v
  [< 0.8]          [> 0.8]      Proceed to Risk     Fire onVerificationFailed
 (Low Risk)      (High Risk)    Evaluation Stage     (Flow stops immediately)
      |               |
      v               v
  Send SMS       Block Request
```

Observed results from the audit window on `<firebase_project_id>`. For the configuration itself and
how to change it, see [reCAPTCHA SMS Defense for Firebase Phone OTP](recaptcha_sms_defense.md).

All timestamps are **UTC**, and every table covers `2026-08-31T00:00:00Z` onwards, so 2026-09-18 is a
partial day. Every section was read at `2026-09-18T12:12:19Z`, so the counts are directly comparable
across them.

## Token and verdict counts

```bash
curl -s -G \
  -H "Authorization: Bearer $(gcloud auth print-access-token)" \
  -H "x-goog-user-project: <firebase_project_id>" \
  --data-urlencode 'filter=metric.type="identitytoolkit.googleapis.com/recaptcha/token_count"' \
  --data-urlencode 'interval.startTime=2026-08-31T00:00:00Z' \
  --data-urlencode 'interval.endTime=2026-09-18T12:12:19Z' \
  "https://monitoring.googleapis.com/v3/projects/<firebase_project_id>/timeSeries"
```

Swap `token_count` for `verdict_count` to get the second table. Both are counters: the totals are the
sum of `points[].value.int64Value`, and the state is a metric *label* (`token_state` /
`verdict_state`), so each state arrives as its own time series. Bucket on
`points[].interval.endTime` for the per-day split.

### Token Result

| Day | Total | `valid` | `missing` | `invalid` | `expired` | Success |
| --- | --- | --- | --- | --- | --- | --- |
| 2026-08-31 | 90 | 82 | 8 | — | — | 91% |
| 2026-09-01 | 89 | 48 | 41 | — | — | 54% |
| 2026-09-02 | 74 | 47 | 26 | 1 | — | 64% |
| 2026-09-03 | 69 | 47 | 22 | — | — | 68% |
| 2026-09-04 | 157 | 147 | 10 | — | — | 94% |
| 2026-09-05 | 185 | 154 | 30 | — | 1 | 83% |
| 2026-09-06 | 42 | 37 | 5 | — | — | 88% |
| 2026-09-07 | 93 | 76 | 17 | — | — | 82% |
| 2026-09-08 | 126 | 100 | 24 | 1 | 1 | 79% |
| 2026-09-09 | 115 | 86 | 29 | — | — | 75% |
| 2026-09-10 | 107 | 64 | 43 | — | — | 60% |
| 2026-09-11 | 87 | 44 | 43 | — | — | 51% |
| 2026-09-12 | 50 | 38 | 11 | 1 | — | 76% |
| 2026-09-13 | 40 | 28 | 12 | — | — | 70% |
| 2026-09-14 | 67 | 54 | 13 | — | — | 81% |
| 2026-09-15 | 105 | 86 | 17 | 2 | — | 82% |
| 2026-09-16 | 66 | 57 | 9 | — | — | 86% |
| 2026-09-17 | 83 | 66 | 15 | 1 | 1 | 80% |
| 2026-09-18 (partial) | 35 | 25 | 9 | 1 | — | 71% |
| **Total** | **1680** | **1286** | **384** | **7** | **3** | **77%** |

An `expired` state has appeared since the last read — 3 requests across the window, first seen
2026-09-05. It behaves like `missing` for verdict purposes.

The two metrics measure different things. `token_state` is about the token alone; `verdict_state` is
the overall outcome, which folds in the score comparison as well:

* `passed` — token valid **and** score below `startScore`
* `failed_in_audit` — would have been denied: token missing or invalid, **or** score at/above
  `startScore`

By the same token `failed_in_audit = missing + invalid + (valid but over threshold)`.

## Reading the score

`sms_tf_risk_scores` is a **risk** score: **near 0 is good, and anything at or above 0.8 is bad.**

| Score | Meaning |
| --- | --- |
| `0.0` – `0.2` | low fraud risk — ordinary users |
| `0.5` – `0.8` | moderate risk |
| `≥ 0.8` | high fraud risk — blocked once `phoneEnforcementState` is `ENFORCE` |

`tollFraudManagedRules[0].startScore` is where blocking begins, so a **higher** threshold is **more
permissive**. Valid range is 0.0–0.9, and `0` would block everything from score 0 upward.

> [!IMPORTANT]
> Two scores appear in the assessment logs on **opposite** scales. `riskAnalysis.score` is the bot
> score, where higher is better (more likely human) as in reCAPTCHA v3. `smsTollFraudVerdict` and
> `sms_tf_risk_scores` are fraud risk, where higher is worse. A request scoring `0.9` on bot analysis
> and `0.15` on fraud risk is a normal user.

## Score distribution

```bash
curl -s -G \
  -H "Authorization: Bearer $(gcloud auth print-access-token)" \
  -H "x-goog-user-project: <firebase_project_id>" \
  --data-urlencode 'filter=metric.type="identitytoolkit.googleapis.com/recaptcha/sms_tf_risk_scores"' \
  --data-urlencode 'interval.startTime=2026-08-31T00:00:00Z' \
  --data-urlencode 'interval.endTime=2026-09-18T12:12:19Z' \
  "https://monitoring.googleapis.com/v3/projects/<firebase_project_id>/timeSeries"
```

Nothing in the response is *named* `sms_tf_risk_scores` — it is the value of `metric.type`, and the
scores live at `points[].value.distributionValue.bucketCounts`:

```json
"metricKind": "DELTA",
"valueType": "DISTRIBUTION",
"points": [
  {
    "interval": { "startTime": "...", "endTime": "..." },
    "value": {
      "distributionValue": {
        "count": "1",
        "bucketOptions": { "linearBuckets": { "numFiniteBuckets": 11, "width": 0.1 } },
        "bucketCounts": [ "0", "1" ]
      }
    }
  }
]
```

### Decoding bucketCounts

In `bucketCounts` the **position** is the score range and the **value** is how many requests landed
in it. `bucketOptions` gives `width: 0.1` with offset 0, so position `i` covers
`[(i-1) x 0.1, i x 0.1)`:

| Position | Score range |
| --- | --- |
| 0 | underflow, `< 0` |
| 1 | `0.0` – `0.1` |
| 2 | `0.1` – `0.2` |
| … | … |
| 8 | `0.7` – `0.8` |
| 9 | `0.8` – `0.9` — blocking starts here |

So the point above reads: one request, scored `0.0`–`0.1`. `count` is the total and always equals the
sum of `bucketCounts`; if the two disagree, the read is wrong rather than the data.

> [!IMPORTANT]
> **Trailing zeros are truncated.** The array is not a fixed 13 entries — it stops after the last
> non-zero bucket, so its length varies from point to point. Find the last non-zero entry and its
> position is the bucket; in practice the array's length gives the answer directly.
>
> | `bucketCounts` | Reads as |
> | --- | --- |
> | `["0","1"]` | 1 request at `0.0`–`0.1` |
> | `["0","0","2"]` | 2 requests at `0.1`–`0.2` |
> | `["0","0","0","0","0","0","0","1"]` | 1 request at `0.6`–`0.7` |
>
> Two consequences: code aggregating points must pad to the longest array rather than assume a fixed
> size, and the longest array in a window tells you the highest band reached. The longest array seen
> in this window is 10 entries, which is the `0.8`–`0.9` band reached on 2026-09-10.

### Score Result (SMS Fraud)

Transposed to one row per day, since the window no longer fits across columns.

| Day | `0.0`–`0.1` | `0.1`–`0.2` | `0.2`–`0.3` | `0.3`–`0.4` | `0.4`–`0.5` | `0.5`–`0.6` | `0.6`–`0.7` | `0.7`–`0.8` | **`≥ 0.8`** | Samples |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 2026-08-31 | 30 | 47 | — | — | — | 1 | 3 | 1 | **0** | 82 |
| 2026-09-01 | 23 | 19 | — | — | — | 1 | 5 | — | **0** | 48 |
| 2026-09-02 | 21 | 23 | — | 1 | — | — | 2 | — | **0** | 47 |
| 2026-09-03 | 22 | 22 | — | 3 | — | — | — | — | **0** | 47 |
| 2026-09-04 | 70 | 68 | 2 | 2 | — | 3 | 1 | 1 | **0** | 147 |
| 2026-09-05 | 63 | 84 | 2 | 3 | 2 | — | — | — | **0** | 154 |
| 2026-09-06 | 7 | 29 | — | — | — | — | 1 | — | **0** | 37 |
| 2026-09-07 | 27 | 45 | 1 | — | — | 1 | — | 2 | **0** | 76 |
| 2026-09-08 | 45 | 52 | 1 | 1 | — | — | — | 1 | **0** | 100 |
| 2026-09-09 | 27 | 55 | 1 | 1 | 2 | — | — | — | **0** | 86 |
| **2026-09-10** | 5 | 53 | — | 1 | 3 | — | — | — | **2** | 64 |
| 2026-09-11 | 3 | 39 | — | 2 | — | — | — | — | **0** | 44 |
| 2026-09-12 | 10 | 28 | — | — | — | — | — | — | **0** | 38 |
| 2026-09-13 | 3 | 21 | — | — | — | — | 3 | 1 | **0** | 28 |
| 2026-09-14 | 16 | 37 | 1 | — | — | — | — | — | **0** | 54 |
| 2026-09-15 | 21 | 59 | — | 2 | 1 | — | 1 | 2 | **0** | 86 |
| 2026-09-16 | 24 | 30 | — | — | 2 | — | 1 | — | **0** | 57 |
| 2026-09-17 | 32 | 30 | — | — | — | — | 1 | 3 | **0** | 66 |
| 2026-09-18 | 16 | 7 | — | — | — | — | 2 | — | **0** | 25 |
| **Total** | **465** | **748** | **8** | **16** | **10** | **6** | **20** | **11** | **2** | **1286** |

94% of scored traffic sits below `0.2`. **The threshold has now fired: two requests on 2026-09-10
scored `0.8`–`0.9`**, the first samples to reach the blocking band since scoring began.

Those two are corroborated independently by the verdict counts — 2026-09-10 shows `valid` 64 against
`passed` 62, and `failed_in_audit` 45 against `missing` 43, so exactly two valid tokens were rejected
on risk grounds rather than on token state. The two readings agree, which is what makes this a real
event rather than a bucketing artefact.

Enforcing at the current provisional `0.8` across this window would therefore have blocked **2 of
1286** scored requests (0.16%).

## Send Verification Code Status

```bash
gcloud logging read \
  'logName="projects/<firebase_project_id>/logs/identitytoolkit.googleapis.com%2Frequests"
   AND jsonPayload.methodName="google.cloud.identitytoolkit.v1.AuthenticationService.SendVerificationCode"
   AND timestamp>="2026-08-31T00:00:00Z"' \
  --project=<firebase_project_id> --limit=1000 --format=json
```

Then tally `jsonPayload.status.message`, treating an absent status as success.

> [!IMPORTANT]
> **Filter on `methodName` server-side, not in your own code.** `--limit` caps the entries the server
> returns *before* any client-side filtering, so reading the whole requests log and then keeping the
> `SendVerificationCode` entries silently drops most of them — the log is dominated by
> `GetRecaptchaConfig`, `SignInWithPhoneNumber` and `GetAccountInfo`. Raise `--limit` until the count
> stops changing; 1890 here is stable at 2000 and 4000.

`SendVerificationCode`, 1890 calls in the window:

| Outcome | Count | Share |
| --- | --- | --- |
| `SUCCESS` | 1522 | 80.5% |
| `OPERATION_NOT_ALLOWED` — region not enabled | 107 | 5.7% |
| `TOO_MANY_ATTEMPTS_TRY_LATER` | 72 | 3.8% |
| `INVALID_APP_CREDENTIAL` | 52 | 2.8% |
| `Error code: 39` | 52 | 2.8% |
| `ALTERNATE_CLIENT_IDENTIFIER_REQUIRED` — invalid Play Integrity token | 43 | 2.3% |
| `MISSING_RECAPTCHA_TOKEN` | 21 | 1.1% |
| `INVALID_APP_CREDENTIAL` — invalid app info in play_integrity_token | 10 | 0.5% |
| `INVALID_PHONE_NUMBER` (`TOO_SHORT` / `TOO_LONG` / unqualified) | 11 | 0.6% |

The overall success rate is unchanged at 80%, but the composition has shifted:
`TOO_MANY_ATTEMPTS_TRY_LATER` has moved from sixth place to third, growing eightfold (9 → 72) against
a 4.8× growth in volume.

## Before Enabling ENFORCE Mode

**Enabling `ENFORCE` while CommCare LTS is absent from the reCAPTCHA key would stop OTP delivery for
those users entirely.** `org.commcare.lts` is not in the key's `allowedPackageNames` and
`allowAllPackageNames` is `false`, so the LTS app cannot mint a token at all — every LTS phone-auth
request necessarily lands in `missing`. Today that is survivable because `AUDIT` falls back to a
silent push and then a visual reCAPTCHA, and the OTP still arrives. Under `ENFORCE` there is no
fallback: the request is blocked and the client gets `onVerificationFailed` immediately.

This is a change in kind, not degree — LTS users would lose phone verification outright rather than
see it slow down. And because package attribution is not available, the size of the affected
population cannot be measured from the logs; the `missing` share (23% of requests in this window) is
the upper bound of what could break. Add the LTS package names to the key, or confirm those users do
not need phone verification, before flipping.

| Scenario | reCAPTCHA Token Status | App Mode | User Experience | Will OTP Send? |
| --- | --- | --- | --- | --- |
| **Current State** (`CommCare LTS` unlisted) | **Failed / Missing** | `AUDIT` | Falls back to Silent Push / Visual Web reCAPTCHA. | **Yes** (if fallback completes) |
| **Current State** (`CommCare LTS` unlisted) | **Failed / Missing** | `ENFORCE` | Client receives instant error (`onVerificationFailed`). No fallback used. | **No** |
| **Fixed State** (Add CommCare LTS package names to key) | **Valid** | `AUDIT / ENFORCE (Score <= 0.8)` | Background Play Integrity check (Silent, no visual reCAPTCHA). | **Yes** |

**Error 39 has not stopped.** 52 events in the window, spread across 15 of the 19 days rather than
clustered — the earlier read's "seven on 2026-09-03" was not a one-off spike but the start of a
steady background rate of roughly three a day.

**The `missing` rate did not settle.** It stepped up on 2026-09-01 (8 → 41) and has stayed elevated,
peaking at 43 on both 2026-09-10 and 2026-09-11 — days when it was 40% and 49% of all requests. Across
the window `missing` is 384 of 1680 requests (23%), down from 28% at the last read only because total
volume grew faster.

**Which app a request came from is not recorded.** No log or metric attributes a request to a
package, so the composition of the `missing` bucket cannot be measured server-side.

**Region rejections are the largest failure category**, at 6%, with app-credential failures next at
5% — together roughly three and a half times error 39. `smsRegionConfig` disallows only `CN`, so the
region rejections are unrelated to reCAPTCHA yet cost more sends than the problem this work addresses.

**Solving the visual reCAPTCHA does not guarantee an SMS.** The two stages are independent gates.
Even when a real person successfully solves the image puzzle on screen, if the target phone number or
IP address trips the toll-fraud rules (`sms_tf_risk_scores` above `0.8`) while in `ENFORCE` mode,
Firebase still blocks the send and fires `onVerificationFailed()` on the device.

This is what the *Proceed to Risk Evaluation Stage* branch in the diagram above means: passing app
verification — whether silently via Play Integrity or by solving the challenge — only gets a request
as far as the risk check. It does not exempt it from the score threshold.

**The threshold has now fired — this reverses the previous finding.** The earlier read concluded
that no valid token had ever been rejected on risk grounds. That is no longer true. On **2026-09-10**
two requests scored in the `0.8`–`0.9` band, and the verdict counts corroborate it: `valid` 64 against
`passed` 62 on the same day. Across the window `valid` is 1286 against `passed` 1284 — a gap of
exactly those two.

The scale is still small: **2 of 1286 scored requests, 0.16%**. But the qualitative claim has changed.
The threshold is no longer untested, and `failed_in_audit` is no longer purely a token problem — it
now contains a genuine fraud judgement, however rare.

What this does and does not change for `ENFORCE`:

* It does **not** materially change the blast radius. The `missing` population (384 requests, 23%)
  still dwarfs the 2 score rejections by two orders of magnitude, and remains the thing that would
  actually break users.
* It does mean the `0.8` threshold is now demonstrably reachable by real traffic, so the provisional
  value is doing something rather than nothing.
* Two samples is far too small to calibrate on. It establishes that the band is reachable, not what
  the right threshold is.

> [!NOTE]
> One request is unaccounted for on 2026-09-01: `token_count` totals 89 for that day while
> `verdict_count` totals 90 (`missing` 41 vs `failed_in_audit` 42). Every other day reconciles. It is
> most likely a bucket-boundary artefact between the two counters rather than a real discrepancy, but
> it is left visible rather than smoothed away.

## Blockers for ENFORCE Mode

| Point | Description | Action |
| --- | --- | --- |
| **#1** Missing / invalid reCAPTCHA token | Hypothesis: users on CommCare LTS are trying to sign up and hitting this, because the current reCAPTCHA key does not carry the CommCare LTS package name. It may go away once CommCare LTS is added to the key. | Team decision needed to move ahead. |
| **#2** Enabling reCAPTCHA on CommCare LTS | Adding the CommCare LTS package names to the key brings LTS into the reCAPTCHA path, which changes behaviour for those users. | Approval needed from higher ups. |



---

<br>
<br>
<br>
<br>
<br>
