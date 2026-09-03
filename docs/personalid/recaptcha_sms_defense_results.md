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

All timestamps are **UTC**, and every table covers `2026-08-31T00:00:00Z` onwards, so 2026-09-02 is a
partial day. Every section was read at `2026-09-03T07:08:48Z`, so the counts are directly comparable
across them.

## Token and verdict counts

```bash
curl -s -G \
  -H "Authorization: Bearer $(gcloud auth print-access-token)" \
  -H "x-goog-user-project: <firebase_project_id>" \
  --data-urlencode 'filter=metric.type="identitytoolkit.googleapis.com/recaptcha/token_count"' \
  --data-urlencode 'interval.startTime=2026-08-31T00:00:00Z' \
  --data-urlencode 'interval.endTime=2026-09-03T07:08:48Z' \
  "https://monitoring.googleapis.com/v3/projects/<firebase_project_id>/timeSeries"
```

Swap `token_count` for `verdict_count` to get the second table. Both are counters: the totals are the
sum of `points[].value.int64Value`, and the state is a metric *label* (`token_state` /
`verdict_state`), so each state arrives as its own time series. Bucket on
`points[].interval.endTime` for the per-day split.

### Token Result

| Day | Total | `valid` | `missing` | `invalid` | Success |
| --- | --- | --- | --- | --- | --- |
| 2026-08-31 | 90 | 82 | 8 | — | 91% |
| 2026-09-01 | 89 | 48 | 41 | — | 54% |
| 2026-09-02 | 74 | 47 | 26 | 1 | 64% |
| 2026-09-03 (partial) | 3 | 2 | 1 | — | 67% |
| **Total** | **256** | **179** | **76** | **1** | **70%** |

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
  --data-urlencode 'interval.endTime=2026-09-03T07:08:48Z' \
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
> size, and the longest array in a window tells you the highest band reached. Nothing here exceeds 9
> entries, which is why `≥ 0.8` is zero.

### Score Result (SMS Fraud)

| Range | 08-31 | 09-01 | 09-02 | 09-03 | Total |
| --- | --- | --- | --- | --- | --- |
| `0.0` – `0.1` | 30 | 23 | 21 | — | 74 |
| `0.1` – `0.2` | 47 | 19 | 23 | 2 | 91 |
| `0.3` – `0.4` | — | — | 1 | — | 1 |
| `0.5` – `0.6` | 1 | 1 | — | — | 2 |
| `0.6` – `0.7` | 3 | 5 | 2 | — | 10 |
| `0.7` – `0.8` | 1 | — | — | — | 1 |
| **`≥ 0.8`** | **0** | **0** | **0** | **0** | **0** |
| Samples | 82 | 48 | 47 | 2 | 179 |

92% of scored traffic sits below `0.2`, and **nothing has reached `0.8` on any day**. Enforcing at
the current provisional `0.8` would therefore block none of these 179 requests — safe for users, and
equally no protection against anything present in this window. The highest sample observed falls in
`0.7`–`0.8`, leaving about one bucket of headroom.

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
> stops changing; 234 here is stable at 300 and 1000.

`SendVerificationCode`, 283 calls in the window:

| Outcome | Count | Share |
| --- | --- | --- |
| `SUCCESS` | 223 | 79% |
| `OPERATION_NOT_ALLOWED` — region not enabled | 21 | 7% |
| `INVALID_APP_CREDENTIAL` | 17 | 6% |
| `MISSING_RECAPTCHA_TOKEN` | 6 | 2% |
| `ALTERNATE_CLIENT_IDENTIFIER_REQUIRED` — invalid Play Integrity token | 6 | 2% |
| `TOO_MANY_ATTEMPTS_TRY_LATER` | 5 | 2% |
| `Error code: 39` | 5 | 2% |

## Before Enabling ENFORCE Mode

**Enabling `ENFORCE` while CommCare LTS is absent from the reCAPTCHA key would stop OTP delivery for
those users entirely.** `org.commcare.lts` is not in the key's `allowedPackageNames` and
`allowAllPackageNames` is `false`, so the LTS app cannot mint a token at all — every LTS phone-auth
request necessarily lands in `missing`. Today that is survivable because `AUDIT` falls back to a
silent push and then a visual reCAPTCHA, and the OTP still arrives. Under `ENFORCE` there is no
fallback: the request is blocked and the client gets `onVerificationFailed` immediately.

This is a change in kind, not degree — LTS users would lose phone verification outright rather than
see it slow down. And because package attribution is not available, the size of the affected
population cannot be measured from the logs; the `missing` share (30% of requests in this window) is
the upper bound of what could break. Add the LTS package names to the key, or confirm those users do
not need phone verification, before flipping.

| Scenario | reCAPTCHA Token Status | App Mode | User Experience | Will OTP Send? |
| --- | --- | --- | --- | --- |
| **Current State** (`CommCare LTS` unlisted) | **Failed / Missing** | `AUDIT` | Falls back to Silent Push / Visual Web reCAPTCHA. | **Yes** (if fallback completes) |
| **Current State** (`CommCare LTS` unlisted) | **Failed / Missing** | `ENFORCE` | Client receives instant error (`onVerificationFailed`). No fallback used. | **No** |
| **Fixed State** (Add CommCare LTS package names to key) | **Valid** | `AUDIT / ENFORCE (Score <= 0.8)` | Background Play Integrity check (Silent, no visual reCAPTCHA). | **Yes** |

**Error 39 has not stopped.** Five events in the window.

**The `missing` rate stepped up sharply on 2026-09-01.** Requests without a token went from 8 to 41
on almost identical total volume (90 → 89).

**Which app a request came from is not recorded.** No log or metric attributes a request to a
package, so the composition of the `missing` bucket cannot be measured server-side.

**Region rejections are the largest failure category**, at 7%, with app-credential failures next at
6% — together roughly seven times error 39. `smsRegionConfig` disallows only `CN`, so the region rejections
are unrelated to reCAPTCHA yet cost more sends than the problem this work addresses.

**Solving the visual reCAPTCHA does not guarantee an SMS.** The two stages are independent gates.
Even when a real person successfully solves the image puzzle on screen, if the target phone number or
IP address trips the toll-fraud rules (`sms_tf_risk_scores` above `0.8`) while in `ENFORCE` mode,
Firebase still blocks the send and fires `onVerificationFailed()` on the device.

This is what the *Proceed to Risk Evaluation Stage* branch in the diagram above means: passing app
verification — whether silently via Play Integrity or by solving the challenge — only gets a request
as far as the risk check. It does not exempt it from the score threshold.

**The threshold itself has never fired.** Across the whole period since scoring began
(`2026-08-27T11:55Z`, when SMS defense was enabled on the key) there have been **352 scored requests
and none at `≥ 0.8`** — the highest band reached is `0.7`–`0.8`, twice, leaving a full band of
headroom. Over the same period `passed` tracks `valid` exactly (351 against 352, the difference being
ingestion lag on the newest sample), which is the direct evidence: no valid token has ever been
rejected on risk grounds.

So every `failed_in_audit` recorded so far is a token problem, not a fraud judgement. That narrows
the `ENFORCE` decision usefully — on current traffic the score threshold would block nobody, and the
only real blast radius is the `missing` population described above.

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
