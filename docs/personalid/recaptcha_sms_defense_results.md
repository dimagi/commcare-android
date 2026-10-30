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
with no attribution needed.

#### The result, read 2026-10-27

| Day | `missing` | `invalid` | `valid` | Total | Missing | `Error code: 39` |
| --- | --- | --- | --- | --- | --- | --- |
| 2026-10-06 | 31 | — | 121 | 152 | 20.4% | 4 |
| 2026-10-07 | 7 | 1 | 79 | 87 | 8.0% | 2 |
| 2026-10-08 | 22 | — | 83 | 105 | 21.0% | 2 |
| 2026-10-09 | 18 | 2 | 71 | 91 | 19.8% | 1 |
| 2026-10-10 | 2 | — | 47 | 49 | 4.1% | 1 |
| 2026-10-11 | 3 | — | 32 | 35 | 8.6% | 1 |
| 2026-10-12 | 3 | — | 75 | 78 | 3.8% | 1 |
| 2026-10-13 | 6 | — | 79 | 85 | 7.1% | 1 |
| 2026-10-14 | 22 | — | 75 | 97 | 22.7% | 1 |
| 2026-10-15 | 4 | — | 49 | 53 | 7.5% | — |
| 2026-10-16 | 8 | — | 61 | 69 | 11.6% | 2 |
| 2026-10-17 | 8 | — | 53 | 61 | 13.1% | 1 |
| 2026-10-18 | — | — | 24 | 24 | 0.0% | — |
| 2026-10-19 | 3 | — | 51 | 54 | 5.6% | 1 |
| **Before, 14 days** | **137** | **3** | **900** | **1040** | **13.2%** | **18** |
| 2026-10-20 | 1 | 1 | 52 | 54 | 1.9% | — |
| 2026-10-21 | 13 | — | 78 | 91 | 14.3% | 1 |
| 2026-10-22 | 1 | — | 88 | 89 | 1.1% | — |
| 2026-10-23 | 11 | — | 118 | 129 | 8.5% | 5 |
| 2026-10-24 | 8 | — | 58 | 66 | 12.1% | 1 |
| 2026-10-25 | 2 | — | 24 | 26 | 7.7% | — |
| 2026-10-26 | 5 | — | 116 | 122 | 4.1% | 2 |
| **After, 7 days** | **41** | **1** | **534** | **577** | **7.1%** | **9** |

**The missing-token rate roughly halved: 13.2% to 7.1%, a 46% relative reduction.** In absolute
terms 9.8 missing tokens a day became 5.9.

**Developer debug builds were roughly half of the missing tokens.** Allowing the `.debug`
packages removed that half permanently — it is a fix, not a probe, so the key should stay as it
is.

**The other half is real field traffic.** 7.1% of phone-auth requests still arrive with no token,
and 2026-10-21 (14.3%) and 2026-10-24 (12.1%) show the pre-change range has not gone away, only
thinned. Blocker #1 is therefore smaller than it was but not closed: `ENFORCE` would still deny
roughly one request in fourteen.

#### `Error code: 39`

Nine of the 610 requests since 2026-10-20 failed with a bare `Error code: 39` — 1.5% of traffic,
most recently 2026-10-26. Firebase gives no message with it. Nigeria 6, Tanzania 1, Myanmar 1,
Malawi 1, across eight different handsets.

**It is probably a missing token under a different name.** 67% of them happened in a minute that
also recorded a missing token, against 7.9% for requests generally; four were the only request in
their minute, so there is nothing else they could be. So these are most likely part of the 7.1%,
not extra on top of it.

The rate did not change on 2026-10-20 — 1.3 a day before and after — while missing tokens halved.
That fits: the key change removed developer missing tokens, and these look like the field-device
ones that remain.

Nine cases is not enough to be sure, and `39` is not a documented status, so Firebase support is
the way to settle it.

#### Where the residual comes from, by region

All `SendVerificationCode` traffic, 2026-10-20 to 2026-10-27:

| Country | Requests | Missing tokens (attributed) | App verification failures | `Error code: 39` | Region not enabled |
| --- | --- | --- | --- | --- | --- |
| +234 NG | 225 | 18 | 15 | 6 | — |
| +91 IN | 57 | — | 1 | — | — |
| +256 UG | 28 | — | — | — | — |
| +254 KE | 27 | — | — | — | — |
| +95 MM | 25 | 1 | 1 | 1 | — |
| +1 US/CA | 19 | — | 2 | — | — |
| +265 MW | 17 | — | 1 | 1 | — |
| +502 GT | 16 | — | — | — | — |
| +233 GH | 16 | 1 | — | — | — |
| +258 MZ | 14 | — | — | — | — |
| +251 ET | 14 | — | — | — | — |
| +963 SY | 12 | — | — | — | 12 |
| +92 PK | 10 | — | — | — | — |
| +58 VE | 8 | — | — | — | — |
| +229 BJ | 8 | — | — | — | — |
| +263 ZW | 8 | — | — | — | — |
| +62 ID | 7 | — | — | — | — |
| +250 RW | 7 | — | — | — | — |
| +33 FR | 7 | — | 3 | — | — |
| +27 ZA | 6 | — | — | — | — |
| +44 GB | 6 | — | — | — | — |
| +232 SL | 6 | — | — | — | — |
| +243 CD | 6 | — | — | — | — |
| +231 LR | 6 | — | — | — | — |
| +235 TD | 5 | — | — | — | — |
| +57 CO | 5 | — | — | — | — |
| +211 SS | 5 | — | — | — | — |
| Other (21 countries, <5 requests each) | 51 | 1 | 1 | 1 | 2 |
| *Subtotal, attributed* | *621* | *21* | *24* | *9* | *14* |
| Not attributable — window held several requests | — | 16 | — | — | — |
| Not attributable — no matching request | — | 4 | — | — | — |
| **Total** | **621** | **41** | **24** | **9** | **14** |

*App verification failures* are `INVALID_APP_CREDENTIAL` (21) plus `MISSING_RECAPTCHA_TOKEN` (3) —
calls rejected at the app-verification stage, by Play Integrity or by reCAPTCHA. Most are not
user-visible: see [Before Enabling ENFORCE Mode](#before-enabling-enforce-mode).

**Nigeria dominates, but largely because it dominates the traffic.** 225 of 621 requests (36%) and
18 of the 21 attributed misses. Over-represented, but 21 data points is too few to act on.

**One number produced 5 of the 41 missing tokens** — a single Nigerian device in a retry loop, so
the residual is fewer distinct users than the count suggests. That number was also malformed: it
carried the national trunk `0` after the country code (`+234` followed by 11 digits where E.164
allows 10), which the app should have stripped before calling Firebase.

### How much of the residual this accounts for

Adding the `.debug` packages removed about half the missing tokens. What is left is 7.1% of
requests, and it is real field traffic rather than developers.

Almost all of it is Nigeria. Of the twenty-one missing tokens we could pin to a single request,
eighteen were Nigerian numbers, on ordinary low-cost handsets.

The leading explanation is old app versions. Phone auth can only produce a reCAPTCHA token from
`firebase-auth` 23.1.0, which first shipped in CommCare 2.63.0. Around one user in nine is still
below that and cannot send a token at all.

This is not proven. Those version figures cover every CommCare user, while only Connect users ever
request a phone OTP. And one device that should have been incapable did send a valid token once,
so something else is also at work.

It is not old phones. Devices that miss tokens run the same Android versions as everyone else.

> [!IMPORTANT]
> **A missing token and a `MISSING_RECAPTCHA_TOKEN` status are not the same measurement.** The
> metric records what arrived with the request. The status records how the whole call ended.
>
> Because we are only auditing, a missing token does not stop the call. It falls through to Play
> Integrity, and the status then reports that result instead. This is why most missing tokens never
> show up as a failure anywhere, and why the status table understates the problem.
>
> Plan against the metric, not the status table.

## Before Enabling ENFORCE Mode

**Missing tokens are the blast radius.** Today a missing token is survivable: the call falls back to
Play Integrity and the OTP still arrives. Under `ENFORCE` there is no fallback — the request is
blocked and the user loses phone verification outright. At 7.1% that is roughly one request in
fourteen.

**We do not know whether the users who currently get through still would.** Of the twenty-one
missing tokens we could attribute, eight ended in success, and none of those eight were rejected by
Play Integrity either. Whether enforcement would block them is the most important open question
here.

**`INVALID_APP_CREDENTIAL` is mostly not a real failure.** It means Play Integrity declined to vouch
for the app. It is usually the first of two log entries for a single OTP attempt — the app retries a
few seconds later with reCAPTCHA and the SMS arrives. Thirteen of the twenty-one are that harmless
first entry, invisible to the user.

**One install is genuinely untrusted.** A single Nigerian device failed eight times across two days
and never once recovered. Everyone else recovers on the retry. That one is worth chasing; the rest
are noise.

**`Error code: 39` is probably missing tokens under another name.** There are nine, and seven of
them happened at the moment a token went missing. It is most likely part of the 7.1% rather than
something extra on top.

**Some valid tokens would also be blocked.** Nine scored at or above the `0.8` threshold in the
week to 2026-10-27 — 1.6% of scored traffic, let through today and denied under `ENFORCE`. A
quarter of all traffic sits in the 0.6–0.7 band just below the line, so a small upward drift would
turn that 1.6% into roughly 30%.

**Together that is about 8.7% of requests denied** — 7.1% with no token plus 1.6% scoring too
high. Counted by people rather than requests it is lower, since a few devices retry.

## Blockers for ENFORCE Mode

| Point | Where it stands | What to do |
| --- | --- | --- |
| **#1** Missing reCAPTCHA token | Open. Was 13.2% of requests, now 7.1% since the `.debug` packages went on the key. What remains is field traffic, almost all Nigeria. Old app versions are the leading suspect but are not proven. Counted by people rather than requests it is 16 of 392 numbers (4.1%), because a few devices retry. | Confirm whether old versions explain it. Ask Firebase support whether Play Integrity still rescues a tokenless request under `ENFORCE` — if it does, most of this evaporates. Re-read the rate in a week. |
| **#2** `INVALID_APP_CREDENTIAL` | Mostly benign. 3.4% of requests, and two-thirds are the first half of a retry that then succeeds. One device fails persistently. | Look at that one install. No action otherwise. |
| **#3** `Error code: 39` | Probably not separate. 1.5% of requests, and most coincide with a missing token. | Treat as part of #1. Ask Firebase support to confirm, since 39 is not a documented status. |
| **#4** Valid tokens scoring at or above the threshold | Open. 9 valid tokens scored `≥ 0.8` between 2026-10-20 and 2026-10-27 — 1.6% of scored traffic. `AUDIT` lets them through; `ENFORCE` blocks them. A quarter of all traffic sits in the 0.6–0.7 band, one step below the threshold, so a small upward drift would turn 1.6% into roughly 30%. | Establish whether these are fraud or false positives, and whether `0.8` is the right threshold given how close that cluster sits. |

---

<br>
<br>
<br>
<br>
<br>
