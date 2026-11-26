# Mobile Team — PR Review Norms

> These norms were agreed on collaboratively by the team. The goal is to make reviews faster, more consistent, and less draining — without sacrificing code quality.

---

## Comment Labels

Use these prefixes to set expectations upfront so the author knows exactly what they're dealing with.

| Label | Meaning |
|---|---|
| **`Nit:`** | Small, nit-picky comment. Fine to merge without addressing. Reviewer is OK with the author resolving it on GitHub without making the corresponding change. |
| **`Blocking:`** | Must be addressed before merge. Reviewer feels strongly about this one. |
| *(no label)* | Default — somewhere in between. Use judgement. |

**Example:**
```
Nit: cboIt → comboItem, easier to read at a glance

Blocking: !! removed on topBarErrorViewController — we rely on fail-fast behaviour here, please revert
```

---

## When to Raise a Comment

- **Pattern-level feedback → always raise it.** Structural issues (bad abstractions, duplicate utilities, unsafe null handling) compound over time and are worth the conversation.
- **Statement-level style → only if there's a concrete reason.** Ask yourself: would this genuinely confuse a future reader, or am I just preferring my own style?
- **Before leaving a nit, ask: does this actually matter?** If the answer is no, consider skipping it.
- **Would this still bother me a year down the line?** If not, it's probably a nit at most.

---

## How to Leave a Comment

- **If you're unsure, ask a question — don't make a demand.** `"What do you think about extracting this into a helper?"` lands better than `"Extract this into a helper"`.
- **One resolved reply is enough — don't escalate small things.** If the author gives a reasonable explanation, accept it and move on.
- **For complex disagreements, reach out directly.** A 5-minute call resolves what 10 back-and-forth comments can't. Don't let a PR thread become a debate.

---

## Things We've Agreed Are Worth Raising

These came up repeatedly in our PRs and the team agreed they're fair game regardless of label:

- **Naming clarity** — names that are too generic, abbreviated, or misleading (e.g. `cboIt` → `comboItem`)
- **Magic values** — hardcoded values that should reference named constants or colour resources
- **Repeated or inline logic** — copy-pasted blocks or complex lambdas that deserve a named method
- **Duplicate utilities** — if something already exists in the codebase, point it out
- **Null safety (`!!`)** — we use the fail-fast approach; removing `!!` without discussion should be flagged
- **TODO hygiene** — every TODO needs a sentence explaining the deferral or a Jira ticket as a comment on the PR itself (avoid putting Jira ticket in the code)
- **strings.xml discipline** — only user-visible strings belong there

---
