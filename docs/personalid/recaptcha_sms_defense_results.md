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

All timestamps are **UTC**. Sections up to and including *Send Verification Code Status* cover
`2026-08-31T00:00:00Z` onwards and were all read at `2026-09-18T12:12:19Z`, so 2026-09-18 is a
partial day and the counts are directly comparable across them. A second window follows in
[After adding CommCare LTS to the key](#after-adding-commcare-lts-to-the-key--24-september-onwards),
covering `2026-09-24` onwards — the two are kept separate because the key changed between them.

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

## After adding CommCare LTS to the key — 24 September onwards

`org.commcare.lts` was added to the reCAPTCHA key on **2026-09-24**. This section is a second,
separate window — `2026-09-24T00:00:00Z` to `2026-09-29T11:36:28Z`, read at that end time, so
2026-09-29 is a partial day. It is deliberately not merged into the tables above: those describe the
state *before* the key change, and blending the two would hide the effect.

The commands are identical to the ones above with the interval moved forward.

### Configuration at the time of reading

The key wired into `recaptchaConfig` is `6Le4epkt…` — *Android - CommCare reCAPTCHA Key*:

```json
"androidSettings": {
  "allowAllPackageNames": false,
  "allowedPackageNames": ["org.commcare.dalvik", "org.commcare.lts"]
}
```

`phoneEnforcementState` is still `AUDIT`, `useSmsTollFraudProtection` is `true`, and
`tollFraudManagedRules[0].startScore` is still `0.8` — so the only variable that moved between the
two windows is the package list.

> [!NOTE]
> Three further keys named *Key for Identity Platform reCAPTCHA integration* (web, iOS, Android) were
> auto-provisioned on 2026-08-27. The Android one carries `allowAllPackageNames: true`, but it is
> **not** the key referenced by `recaptchaConfig`, so it has no bearing on these numbers. The 2022
> web checkbox key for commcarehq.org is likewise unrelated.

### Token Result

| Day | Total | `valid` | `missing` | `invalid` | `expired` | Success |
| --- | --- | --- | --- | --- | --- | --- |
| 2026-09-24 | 111 | 103 | 8 | — | — | 93% |
| 2026-09-25 | 100 | 79 | 19 | 2 | — | 79% |
| 2026-09-26 | 34 | 29 | 4 | 1 | — | 85% |
| 2026-09-27 | 63 | 59 | 4 | — | — | 94% |
| 2026-09-28 | 105 | 93 | 11 | — | 1 | 89% |
| 2026-09-29 (partial) | 64 | 44 | 16 | 3 | 1 | 69% |
| **Total** | **477** | **407** | **62** | **6** | **2** | **85%** |

### Before and after

| | 2026-08-31 → 09-18 | 2026-09-24 → 09-29 |
| --- | --- | --- |
| Requests | 1680 | 477 |
| `valid` | 1286 — 77% | 407 — **85%** |
| `missing` | 384 — **23%** | 62 — **13%** |
| `invalid` | 7 — 0.4% | 6 — **1.3%** |
| `expired` | 3 — 0.2% | 2 — 0.4% |

**Missing tokens are still present.** The `missing` share roughly halved, from 23% to 13%, which is
the largest single improvement this work has produced. But 62 requests in five and a half days still
fail to carry a token, so **the LTS package name was not the only cause**. `invalid` moved the other
way — six occurrences against seven across a window three and a half times longer, so it roughly
tripled as a share.

Two caveats on how far this can be pushed. The window is 5.5 days against 18.5, so day-to-day noise
carries more weight; 2026-09-29 alone contributes 16 of the 62. And **package attribution is still
unavailable** — `SendVerificationCode` log entries carry only `callerIp` and a device user agent
(`Dalvik/2.1.0 (Linux; U; Android 12; …)`), with no package anywhere in the payload. So the residual
`missing` cannot be attributed to LTS, to CommCare, or to anything else from the server side.

### Score Result (SMS Fraud)

| Day | `0.0`–`0.1` | `0.1`–`0.2` | `0.2`–`0.3` | `0.3`–`0.4` | `0.4`–`0.5` | `0.5`–`0.6` | `0.6`–`0.7` | `0.7`–`0.8` | **`≥ 0.8`** | Samples |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 2026-09-24 | 50 | 46 | — | — | 1 | 4 | — | 2 | **0** | 103 |
| 2026-09-25 | 42 | 33 | — | 2 | — | — | 2 | — | **0** | 79 |
| 2026-09-26 | 9 | 19 | — | — | — | — | — | 1 | **0** | 29 |
| 2026-09-27 | 26 | 33 | — | — | — | — | — | — | **0** | 59 |
| 2026-09-28 | 31 | 52 | — | — | 5 | 3 | 1 | 1 | **0** | 93 |
| 2026-09-29 (partial) | 19 | 10 | 1 | 3 | — | — | 11 | — | **0** | 44 |
| **Total** | **177** | **193** | **1** | **5** | **6** | **7** | **14** | **4** | **0** | **407** |

**The threshold has not fired again.** The longest `bucketCounts` array in this window is 9 entries,
so the highest band reached is `0.7`–`0.8`. `passed` (407) equals `valid` (407) exactly, and
`failed_in_audit` (70) equals `missing` + `invalid` + `expired` (62 + 6 + 2), so not one valid token
was rejected on risk grounds. The two `0.8`–`0.9` samples on 2026-09-10 remain the only ones on
record.

91% of scored traffic sits below `0.2`, against 94% before — the small shift comes from
2026-09-29's eleven samples in the `0.6`–`0.7` band, which is the single most unusual day in either
window.

### Send Verification Code Status

`SendVerificationCode`, 520 calls in the window. Shares are of this window, not comparable in
absolute count to the 1890-call window above.

| Outcome | Count | Share | Was |
| --- | --- | --- | --- |
| `SUCCESS` | 449 | **86.3%** | 80.5% |
| `OPERATION_NOT_ALLOWED` — region not enabled | 36 | 6.9% | 5.7% |
| `INVALID_APP_CREDENTIAL` (all three variants) | 12 | 2.3% | 3.3% |
| `Error code: 39` | 7 | **1.3%** | 2.8% |
| `ALTERNATE_CLIENT_IDENTIFIER_REQUIRED` — invalid Play Integrity token | 7 | 1.3% | 2.3% |
| `MISSING_RECAPTCHA_TOKEN` | 5 | 1.0% | 1.1% |
| `TOO_MANY_ATTEMPTS_TRY_LATER` | 3 | **0.6%** | 3.8% |
| `INVALID_PHONE_NUMBER` | 1 | 0.2% | 0.6% |

> [!IMPORTANT]
> **`MISSING_RECAPTCHA_TOKEN` at 1.0% does not mean missing tokens affect 1% of sends.** The metric
> counts 62 for the same traffic. Most missing-token requests surface in the
> `INVALID_APP_CREDENTIAL` and `ALTERNATE_CLIENT_IDENTIFIER_REQUIRED` rows, or succeed outright via
> the `AUDIT` fallback and appear as `SUCCESS`. See
> [What produces a missing token](#what-produces-a-missing-token--controlled-test-30-september).

Success is up nearly six points. **Error 39 is down but not gone** — 7 events against a background
rate of roughly three a day previously, so it has fallen by more than the drop in volume alone
explains. `TOO_MANY_ATTEMPTS_TRY_LATER`, which had grown eightfold and was the headline regression in
the previous read, has collapsed back to 3. Region rejections are unchanged in character and remain
the largest failure category, and they have nothing to do with reCAPTCHA.

## What produces a missing token — controlled test, 30 September

Adding CommCare LTS to the key halved the `missing` rate but did not clear it. This section records a
deliberate experiment to identify one of the remaining sources.

**Method.** Read the token counter on an idle project, have a developer sign in from a locally built
**debug** APK, then read the counter again and reconcile against the audit log.

### Result

| `token_state` | Baseline `06:07:05Z` | After `06:23:04Z` | Delta |
| --- | --- | --- | --- |
| `valid` | 9 | 9 | **+0** |
| `missing` | 0 | 2 | **+2** |
| `invalid` | 0 | 0 | +0 |
| `expired` | 0 | 0 | +0 |

The attribution is certain rather than correlational: across the whole project between
`06:00Z` and `06:30Z` there were **exactly two** `SendVerificationCode` calls, both from the test
device, and all five identitytoolkit entries in the window came from its IP. The counter recorded two
`missing` tokens in the one-minute bucket ending `06:23:01Z`, which spans both calls. No other request
existed for them to belong to.

The device's full sequence:

```
06:21:59  GetRecaptchaConfig     SUCCESS
06:22:00  GetRecaptchaParam      SUCCESS
06:22:02  SendVerificationCode   ALTERNATE_CLIENT_IDENTIFIER_REQUIRED
                                 "Invalid PlayIntegrity token; app not Recognized by Play Store"
06:22:03  GetProjectConfig       INVALID_CERT_HASH
06:22:04  SendVerificationCode   INVALID_APP_CREDENTIAL
```

### Why a debug build cannot mint a token

It fails app verification on both available paths, and the package allow-list is not the reason:

1. **Play Integrity fails** — a locally built APK is not distributed through the Play Store, so it
   cannot attest. This is the `ALTERNATE_CLIENT_IDENTIFIER_REQUIRED` line.
2. **reCAPTCHA fails** — `INVALID_CERT_HASH` on `GetProjectConfig` says the debug signing certificate
   is not registered on the Firebase project.

Separately, `applicationIdSuffix '.debug'` makes the package `org.commcare.dalvik.debug`, which is not
in the key's `allowedPackageNames` either. But that is not what this test exercised: the build fails
earlier, on app identity. **Adding LTS to the key was never going to help debug builds**, and adding
the `.debug` package names alone would not fix them either while the certificate hash is unregistered.

### How much of the residual this accounts for

Unresolved. The test proves debug builds contribute; it does not size the contribution. At roughly two
missing tokens per sign-in attempt, the 62 in the 24–29 September window would correspond to about 31
attempts, which is plausibly the whole developer and QA population — but that is arithmetic, not
evidence. Package attribution is still unavailable, so a developer's debug build cannot be separated
from a field user whose Play Integrity failed for an unrelated reason.

The `SendVerificationCode` log does carry `jsonPayload.request.phoneNumber` on every entry. Since
developers and testers reuse known numbers and field users do not, matching the failing requests
against a supplied list of team test numbers would bound the developer share without exposing any
field user's number. That is the cheapest way to close this.

> [!IMPORTANT]
> **`token_state=missing` and the `MISSING_RECAPTCHA_TOKEN` status are different measurements.**
> In the 24–29 September window they differ twelvefold — **62** against **5** — over the same traffic.
>
> `token_state` is an input condition, recorded by the reCAPTCHA subsystem for every request it
> evaluates: what token arrived. `status.message` is the outcome of the whole call. Because
> `phoneEnforcementState` is `AUDIT`, a missing token does not deny the request; it falls through to
> Play Integrity, and the status then names *that* result instead. The test above demonstrates it
> directly — two requests with no token, reported as `ALTERNATE_CLIENT_IDENTIFIER_REQUIRED` and
> `INVALID_APP_CREDENTIAL`, neither of which mentions a token.
>
> Approximately 24 of the 62 are visible as failures — the `INVALID_APP_CREDENTIAL` (12),
> `ALTERNATE_CLIENT_IDENTIFIER_REQUIRED` (7) and `MISSING_RECAPTCHA_TOKEN` (5) rows. The other ~38
> **succeeded**: the token was missing, Play Integrity passed, and the OTP was delivered with the user
> noticing nothing. The split is approximate because the denominators differ (477 token evaluations
> against 520 sends; the ~36 region rejections fail before reCAPTCHA evaluates, which roughly accounts
> for the gap).
>
> **Consequence for `ENFORCE`:** the status table suggests missing tokens affect 1% of sends. The
> metric says 13%. `ENFORCE` removes the fallback that currently rescues the difference, so the
> population at risk is the full 62 — plan against the metric, not the status table.

## Before Enabling ENFORCE Mode

> [!IMPORTANT]
> **Resolved on 2026-09-24, but only partly.** `org.commcare.lts` is now in the key's
> `allowedPackageNames`, so LTS can mint a token and the blanket failure described below no longer
> applies. The `missing` share fell from 23% to 13% — it did not fall to zero. See
> [After adding CommCare LTS to the key](#after-adding-commcare-lts-to-the-key--24-september-onwards).

**Any request without a valid token is blocked outright under `ENFORCE`.** Under `AUDIT` a missing
token is survivable: the flow falls back to a silent push and then a visual reCAPTCHA, and the OTP
still arrives. Under `ENFORCE` there is no fallback — the request is blocked and the client gets
`onVerificationFailed` immediately. That is a change in kind, not degree: affected users lose phone
verification outright rather than see it slow down.

The residual 13% is therefore still the blast radius. Debug builds are now confirmed as one
contributor, but the share is unmeasured and package attribution remains unavailable, so the split
between developers (harmless) and field users (blocking) is still unknown. That split is what has to
be settled before flipping.

| Scenario | reCAPTCHA Token Status | App Mode | User Experience | Will OTP Send? |
| --- | --- | --- | --- | --- |
| **Before 2026-09-24** (`CommCare LTS` unlisted) | **Failed / Missing** | `AUDIT` | Falls back to Silent Push / Visual Web reCAPTCHA. | **Yes** (if fallback completes) |
| **Before 2026-09-24** (`CommCare LTS` unlisted) | **Failed / Missing** | `ENFORCE` | Client receives instant error (`onVerificationFailed`). No fallback used. | **No** |
| **Since 2026-09-24** (LTS added to key) | **Valid** | `AUDIT / ENFORCE (Score <= 0.8)` | Background Play Integrity check (Silent, no visual reCAPTCHA). | **Yes** |

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
| **#1** Missing / invalid reCAPTCHA token | **Still open, one source identified.** LTS was added to the key on 2026-09-24 and `missing` fell from 23% to 13%, but 62 requests in five and a half days still carry no token. A [controlled test on 2026-09-30](#what-produces-a-missing-token--controlled-test-30-september) confirmed debug builds as one source — they fail Play Integrity *and* carry an unregistered signing cert, so they can never mint a token. The share they account for is unmeasured. | Bound the developer share by matching failing requests against known team test numbers. |
| **#2** Enabling reCAPTCHA on CommCare LTS | **Done 2026-09-24.** `org.commcare.lts` is in `allowedPackageNames` on the key referenced by `recaptchaConfig`; no regression visible in the window since — success rate rose from 80.5% to 86.3%. | Closed. |



---

<br>
<br>
<br>
<br>
<br>
