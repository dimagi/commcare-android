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
covering `2026-09-24` onwards — the two are kept separate because the key changed between them. That
second window was extended to `2026-10-13T07:21:34Z` on 2026-10-13; the key and `recaptchaConfig` were
re-read first and are unchanged, so it remains one continuous configuration.

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
separate window — `2026-09-24T00:00:00Z` to `2026-10-13T07:21:34Z`, read at that end time, so
2026-10-13 is a partial day. It is deliberately not merged into the tables above: those describe the
state *before* the key change, and blending the two would hide the effect.

> [!WARNING]
> **The threshold now fires.** The first read of this window found zero samples at or above `0.8`.
> Over the full window there are **34**, and they are not noise: `passed` (1490) is exactly `valid`
> (1524) minus 34, so 34 valid tokens were rejected on risk grounds. Under `AUDIT` they proceeded
> anyway. Under `ENFORCE` they would have been blocked. See
> [Score Result](#score-result-sms-fraud-1).

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
two windows is the package list. Re-read on 2026-10-13: unchanged, both for the key above and for
`recaptchaConfig`.

**Changed 2026-10-20.** `org.commcare.dalvik.debug` and `org.commcare.lts.debug` were added, giving
all four names. Everything else on the key is untouched. Readings taken from 20 October onwards are
therefore not comparable with the two windows above — see
[Debug packages added to the key](#debug-packages-added-to-the-key--20-october).

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
| 2026-09-29 | 204 | 161 | 38 | 4 | 1 | 79% |
| 2026-09-30 | 124 | 114 | 10 | — | — | 92% |
| 2026-10-01 | 102 | 81 | 21 | — | — | 79% |
| 2026-10-02 | 105 | 79 | 25 | 1 | — | 75% |
| 2026-10-03 | 71 | 62 | 9 | — | — | 87% |
| 2026-10-04 | 50 | 45 | 5 | — | — | 90% |
| 2026-10-05 | 119 | 98 | 21 | — | — | 82% |
| 2026-10-06 | 152 | 121 | 31 | — | — | 80% |
| 2026-10-07 | 87 | 79 | 7 | 1 | — | 91% |
| 2026-10-08 | 105 | 83 | 22 | — | — | 79% |
| 2026-10-09 | 91 | 71 | 18 | 2 | — | 78% |
| 2026-10-10 | 49 | 47 | 2 | — | — | 96% |
| 2026-10-11 | 35 | 32 | 3 | — | — | 91% |
| 2026-10-12 | 78 | 75 | 3 | — | — | 96% |
| 2026-10-13 (partial) | 14 | 13 | 1 | — | — | 93% |
| **Total** | **1799** | **1524** | **262** | **11** | **2** | **85%** |

2026-09-29 was a partial day in the first read (64 of 204); the full day is shown here, which is why
its figures differ from the earlier version of this table.

### Before and after

| | 2026-08-31 → 09-18 | 2026-09-24 → 10-13 |
| --- | --- | --- |
| Requests | 1680 | 1799 |
| `valid` | 1286 — 77% | 1524 — **85%** |
| `missing` | 384 — **23%** | 262 — **15%** |
| `invalid` | 7 — 0.4% | 11 — 0.6% |
| `expired` | 3 — 0.2% | 2 — 0.1% |

**Missing tokens are still present.** The `missing` share fell from 23% to 15% and has held there
across a window now slightly longer than the original, so the improvement is real rather than a
small-sample effect. But 262 requests over 20 days still fail to carry a token, so **the LTS package
name was not the only cause**.

The 13% reported in the first read of this window was optimistic — it rested on 5.5 days, and
2026-09-29 was then only a third complete. With the full day counted, that single date contributes 38
missing tokens, the worst in the window. `invalid` settled at 0.6%, close to the original 0.4% rather
than the 1.3% the short window suggested.

**Package attribution is still unavailable** — `SendVerificationCode` log entries carry only
`callerIp` and a device user agent (`Dalvik/2.1.0 (Linux; U; Android 12; …)`), with no package
anywhere in the payload. So the residual `missing` cannot be attributed to LTS, to CommCare, or to
anything else from the server side.

### Score Result (SMS Fraud)

| Day | `0.0`–`0.1` | `0.1`–`0.2` | `0.2`–`0.3` | `0.3`–`0.4` | `0.4`–`0.5` | `0.5`–`0.6` | `0.6`–`0.7` | `0.7`–`0.8` | **`≥ 0.8`** | Samples |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 2026-09-24 | 50 | 46 | — | — | 1 | 4 | — | 2 | **0** | 103 |
| 2026-09-25 | 42 | 33 | — | 2 | — | — | 2 | — | **0** | 79 |
| 2026-09-26 | 9 | 19 | — | — | — | — | — | 1 | **0** | 29 |
| 2026-09-27 | 26 | 33 | — | — | — | — | — | — | **0** | 59 |
| 2026-09-28 | 31 | 52 | — | — | 5 | 3 | 1 | 1 | **0** | 93 |
| 2026-09-29 | 34 | 45 | 9 | 17 | — | 1 | 42 | 10 | **3** | 161 |
| 2026-09-30 | 23 | 38 | 13 | 16 | — | — | 23 | 1 | **0** | 114 |
| 2026-10-01 | 16 | 29 | 6 | 14 | — | 1 | 13 | 2 | **0** | 81 |
| 2026-10-02 | 15 | 30 | 9 | 5 | — | — | 18 | 1 | **1** | 79 |
| 2026-10-03 | 7 | 22 | 4 | 8 | — | — | 16 | 1 | **4** | 62 |
| 2026-10-04 | 3 | 11 | 8 | 7 | — | 2 | 9 | 2 | **3** | 45 |
| 2026-10-05 | 9 | 20 | 10 | 17 | — | 5 | 32 | — | **5** | 98 |
| 2026-10-06 | 20 | 33 | 12 | 15 | — | 2 | 26 | 5 | **8** | 121 |
| 2026-10-07 | 4 | 25 | 8 | 16 | — | 2 | 21 | — | **3** | 79 |
| 2026-10-08 | 3 | 26 | 14 | 19 | — | — | 18 | 3 | **0** | 83 |
| 2026-10-09 | 3 | 11 | 12 | 12 | — | 1 | 21 | 5 | **6** | 71 |
| 2026-10-10 | 4 | 14 | 5 | 11 | — | 1 | 12 | — | **0** | 47 |
| 2026-10-11 | — | 8 | 4 | 11 | — | — | 7 | 2 | **0** | 32 |
| 2026-10-12 | 1 | 31 | 11 | 9 | — | 1 | 19 | 2 | **1** | 75 |
| 2026-10-13 (partial) | — | 3 | 1 | 1 | — | 1 | 4 | 3 | **0** | 13 |
| **Total** | **300** | **529** | **126** | **180** | **6** | **24** | **284** | **41** | **34** | **1524** |

**The threshold now fires.** `bucketCounts` reaches 11 entries, so the `0.9`–`1.0` band is populated
for the first time. The counting is unambiguous: scored samples (1524) equal `valid` (1524) exactly,
`passed` (1490) is `valid` minus 34, and `failed_in_audit` (309) is `missing` + `invalid` + `expired`
(275) plus the same 34. **34 valid tokens were rejected on risk grounds.** `AUDIT` let them through;
`ENFORCE` would not have.

That is 2.2% of scored traffic, spread across nine separate days rather than concentrated in one
incident, with 2026-10-06 the worst at eight.

**The whole distribution moved up on 2026-09-29 and stayed there.** Through 09-28, 92% of samples sat
below `0.2` and the `0.6`–`0.7` band was nearly empty. From 09-29 onward the mass spreads: `0.2`–`0.4`
goes from 6 samples to 300, and `0.6`–`0.7` from 3 to 281. Over the full window only 54% sits below
`0.2`, against 94% in the first window. Nothing in the reCAPTCHA configuration changed on that date,
so the cause is on the traffic side and is not explained by this audit.

### Send Verification Code Status

`SendVerificationCode`, 1986 calls in the window. Shares are of this window; the count is stable at
both `--limit=2000` and `--limit=4000`.

| Outcome | Count | Share | Was (08-31 → 09-18) |
| --- | --- | --- | --- |
| `SUCCESS` | 1637 | **82.4%** | 80.5% |
| `TOO_MANY_ATTEMPTS_TRY_LATER` | 86 | **4.3%** | 3.8% |
| `OPERATION_NOT_ALLOWED` — region not enabled | 77 | 3.9% | 5.7% |
| `INVALID_APP_CREDENTIAL` (all three variants) | 62 | 3.1% | 3.3% |
| `ALTERNATE_CLIENT_IDENTIFIER_REQUIRED` — invalid Play Integrity token | 57 | 2.9% | 2.3% |
| `Error code: 39` | 43 | 2.2% | 2.8% |
| `MISSING_RECAPTCHA_TOKEN` | 16 | 0.8% | 1.1% |
| `INVALID_PHONE_NUMBER` (incl. `TOO_SHORT`) | 8 | 0.4% | 0.6% |

> [!IMPORTANT]
> **`MISSING_RECAPTCHA_TOKEN` at 0.8% does not mean missing tokens affect 0.8% of sends.** The metric
> counts 262 for the same traffic — a sixteenfold difference. Most missing-token requests surface in
> the `INVALID_APP_CREDENTIAL` and `ALTERNATE_CLIENT_IDENTIFIER_REQUIRED` rows, or succeed outright
> via the `AUDIT` fallback and appear as `SUCCESS`. See
> [What produces a missing token](#what-produces-a-missing-token--controlled-test-30-september).

Success settles at 82.4%, about two points above the first window rather than the six the 5.5-day
read suggested. **The optimistic readings in that short window did not hold.** `TOO_MANY_ATTEMPTS_TRY_LATER`
was reported as having collapsed to 3 (0.6%); over the full window it is 86 (4.3%), slightly *worse*
than the 3.8% before — so it did not collapse, the short window simply missed it. `Error code: 39` is
likewise 43 rather than 7, tracking volume rather than falling.

Region rejections fell from 5.7% to 3.9% and remain unrelated to reCAPTCHA.

## What produces a missing token — controlled test, 30 September

Adding CommCare LTS to the key halved the `missing` rate but did not clear it. This section records a
deliberate experiment to identify one of the remaining sources. It was **re-run on 2026-10-13** and
reproduced exactly — see [Repeat, 13 October](#repeat-13-october).

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

### Repeat, 13 October

Same method, same developer device, 13 days later. The point of repeating it was to check whether the
upward shift in risk scores from 2026-09-29 had changed how a debug build is treated. It has not.

| `token_state` | Baseline `07:42:34Z` | After `07:46:16Z` | Delta |
| --- | --- | --- | --- |
| `valid` | 15 | 15 | **+0** |
| `missing` | 1 | 3 | **+2** |
| `invalid` | 0 | 0 | +0 |
| `expired` | 0 | 0 | +0 |

Both `missing` tokens land in the single one-minute bucket ending `07:46:01Z`. Only five
identitytoolkit entries exist across the whole project between `07:42:00Z` and the read, all from one
caller IP, containing exactly two `SendVerificationCode` calls — so the attribution is again certain
rather than correlational.

```
07:44:55  GetRecaptchaConfig     SUCCESS
07:44:56  GetRecaptchaParam      SUCCESS
07:44:58  SendVerificationCode   ALTERNATE_CLIENT_IDENTIFIER_REQUIRED
                                 "Invalid PlayIntegrity token; app not Recognized by Play Store"
07:44:58  GetProjectConfig       INVALID_CERT_HASH
07:44:59  SendVerificationCode   INVALID_APP_CREDENTIAL
```

The sequence is identical to 30 September call for call, including the order of the two failures and
the `INVALID_CERT_HASH` on `GetProjectConfig` between them. Two sign-in attempts, two missing tokens,
no valid token, in both runs — which is the basis for the "roughly two missing tokens per sign-in
attempt" figure used below.

`INVALID_CERT_HASH` is worth noting as a marker: no Play-Store-signed build produces it, so debug
traffic can be isolated in the audit log even when the project is not idle.

### Why a debug build cannot mint a token

It fails app verification on both available paths, and the package allow-list is not the reason:

1. **Play Integrity fails** — a locally built APK is not distributed through the Play Store, so it
   cannot attest. This is the `ALTERNATE_CLIENT_IDENTIFIER_REQUIRED` line.
2. **reCAPTCHA fails** — `INVALID_CERT_HASH` on `GetProjectConfig` says the debug signing certificate
   is not registered on the Firebase project.

Separately, `applicationIdSuffix '.debug'` makes the package `org.commcare.dalvik.debug`, which is not
in the key's `allowedPackageNames` either. **Adding LTS to the key was never going to help debug
builds.**

> [!WARNING]
> **The last sentence of this section was wrong, and the 20 October test below disproves it.** It
> claimed the `.debug` package names would not help while the certificate hash was unregistered. In
> fact the package allow-list was the only thing blocking the token: once `.debug` was added, a debug
> build minted a valid token with its certificate still unregistered. reCAPTCHA token minting does
> not consult the Firebase certificate list — the `INVALID_CERT_HASH` seen here is a `GetProjectConfig`
> result, a different call on a different path. Point 2 above describes a real failure, but not the
> one that caused the missing tokens.

### Debug packages added to the key — 20 October

`org.commcare.dalvik.debug` and `org.commcare.lts.debug` were added to the key's
`allowedPackageNames` at **2026-10-20T05:54:51Z**, leaving all four names in place. Earlier attempts
on 13 October had returned HTTP 200 and silently reverted; this one held, confirmed by re-reading the
key. Same method as the two tests above, same developer device, same unregistered debug certificate —
the only variable changed is the package list.

| `token_state` | Baseline `10:50:55Z` | After `10:53:12Z` | Delta |
| --- | --- | --- | --- |
| `valid` | 47 | 48 | **+1** |
| `missing` | 1 | 1 | +0 |
| `invalid` | 1 | 1 | +0 |
| `expired` | 0 | 0 | +0 |

**The debug build minted a valid token.** Both previous runs produced `+2 missing` and `+0 valid`.

Attribution is certain rather than correlational, because the assessment log names the package:

| Field | Value |
| --- | --- |
| `tokenProperties.valid` | `true` |
| `tokenProperties.invalidReason` | none |
| `tokenProperties.androidPackageName` | `org.commcare.dalvik.debug` |
| `riskAnalysis.score` | 0.9 |
| timestamp | 2026-10-20T10:52:22Z |

The call sequence has no failures in it at all:

```
10:52:20  GetRecaptchaConfig     SUCCESS
10:52:26  SendVerificationCode   SUCCESS
10:52:45  SignInWithPhoneNumber  SUCCESS
10:52:45  GetAccountInfo         SUCCESS
```

On 13 October the same device failed three times in this sequence. All three failures are gone:

| 13 October | 20 October |
| --- | --- |
| `SendVerificationCode` → `ALTERNATE_CLIENT_IDENTIFIER_REQUIRED` | SUCCESS |
| `GetProjectConfig` → `INVALID_CERT_HASH` | call never made |
| `SendVerificationCode` → `INVALID_APP_CREDENTIAL` | SUCCESS |

The OTP arrived and the sign-in finished. Neither earlier run got that far.

**What this proves.** The only thing that changed is the package list. The debug certificate is still
unregistered, and the token was accepted anyway. So the package list was the whole problem, and
developers do not need to register their debug certificates.

`INVALID_CERT_HASH` was a red herring. The app only calls `GetProjectConfig` as a fallback after
reCAPTCHA fails. Once reCAPTCHA works, that call never happens, so the certificate is never checked.

**What it means for the missing tokens.** Every developer running a `.debug` build before today was
sending no token, purely because `applicationIdSuffix '.debug'` kept the package off the key. The
30 September and 13 October tests suspected this; this one confirms it.

**Attribution is still not solved.** `tokenProperties.androidPackageName` names the package, but
only on assessments that succeeded. Comparing the two sources over the 7 days to 2026-10-20 shows it
plainly — the failures are entirely absent from the log:

| `token_state` | Metric | Assessment log |
| --- | --- | --- |
| `valid` | 373 | 374 |
| `missing` | 49 | 0 |
| `invalid` | 1 | 0 |
| **Total** | **423** | **374** |

(The one-entry difference on `valid` is the window boundary.) Those 7 days had 50 failures and not
one produced an assessment entry. A missing token has nothing to assess, and an invalid one cannot
be decoded far enough to read the package. So the field proves *this* debug request worked; it
cannot count the ones that fail.

Two approaches remain, both still unsatisfactory:

- `callerSuppliedUserAgent` — only a device model. It caused the wrong conclusion that "69 of 88
  failing devices also succeed, so they are release installs"; one developer running both builds on
  one phone looks exactly like that.
- Matching against team test phone numbers (below) — workable, but reads field users' numbers.

The cheapest measurement is now the simplest one: the `.debug` packages are allowed from
2026-10-20T05:54:51Z, so **any sustained fall in the daily missing rate is the developer share**,
with no attribution needed. That requires several days of data.

### How much of the residual this accounts for

Unresolved. The test proves debug builds contribute; it does not size the contribution. At roughly two
missing tokens per sign-in attempt, the 62 in the 24–29 September window would correspond to about 31
attempts, which is plausibly the whole developer and QA population — but that is arithmetic, not
evidence. Package attribution is still unavailable, so a developer's debug build cannot be separated
from a field user whose Play Integrity failed for an unrelated reason.

**Updated 2026-10-13.** Extending the window to 20 days gives 262 missing tokens — 13.1 a day against
11.3 in the 24–29 window. The rate is essentially flat while daily send volume varies, which is what a
fixed population of developer and QA devices would look like, and not what traffic-proportional field
failures would. The 13 October repeat confirms the two-per-attempt ratio holds, so 262 corresponds to
roughly 131 sign-in attempts over 20 days, about 6.5 a day. That is consistent with the hypothesis
above but still does not measure it; the phone-number matching below remains the way to settle it.

**Superseded 2026-10-20.** Package attribution *is* available after all —
`tokenProperties.androidPackageName` on each assessment log entry names the calling package, so the
developer share can be counted rather than inferred. The phone-number matching described in earlier
revisions of this section is no longer needed, and should not be used, since it would read field
users' numbers to answer a question the package name already answers.

Sizing still needs several days of data: the daily missing rate ranged 3.8%–21% over the ten days to
20 October, so the drop has to clear that spread before it means anything. The 20 October figure of
1 missing in 50 is a single partial day and 11 October reached 4.2% with no change in place at all.

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
| **#1** Missing / invalid reCAPTCHA token | **Still open, cause fixed for one source.** LTS was added on 2026-09-24 and `missing` fell from 23% to 15%, but 262 requests over the following 20 days still carried no token. Debug builds were confirmed as one source, and on 2026-10-20 the `.debug` packages were added to the key: a debug build then minted a valid token (score 0.9) with its signing cert still unregistered, so the package allow-list — not the cert — was what blocked them. The share they account for is still unmeasured. | Track the daily missing rate for several days from 2026-10-20; a sustained fall below the 3.8%–21% spread measures the developer share without needing attribution. |
| **#2** Enabling reCAPTCHA on CommCare LTS | **Done 2026-09-24.** `org.commcare.lts` is in `allowedPackageNames` on the key referenced by `recaptchaConfig`; no regression over the 20 days since — success rate 82.4% against 80.5% before. | Closed. |
| **#3** Valid tokens now scoring at or above the threshold | **New, opened 2026-10-13.** 34 valid tokens scored `≥ 0.8` between 2026-09-29 and 2026-10-13, confirmed by `passed` being exactly `valid` minus 34. `AUDIT` let them through; `ENFORCE` would block them — 2.2% of scored traffic, where the previous read measured none. The distribution shifted upward on 2026-09-29 with no configuration change, so the cause is unidentified. | Establish whether these are genuine fraud or false positives before `ENFORCE`, and re-check whether `0.8` is the right threshold. |



---

<br>
<br>
<br>
<br>
<br>
