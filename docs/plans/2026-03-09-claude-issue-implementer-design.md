# Claude Issue Implementer — Design

**Date:** 2026-03-09
**Status:** Approved

## Overview

A `workflow_dispatch` GitHub Actions workflow that takes a GitHub issue number, invokes Claude Code to implement the change, then verifies the implementation through a tiered test loop (Robolectric → Espresso on Browserstack). Claude iterates on failures up to a configurable cap before escalating to a human.

## Scope

- Robolectric unit tests (Gradle, no device)
- Espresso instrumentation tests (Browserstack App Automate)
- Single repository (`commcare-android`)
- Manual trigger only (`workflow_dispatch`)

Out of scope: Appium tests (separate repo), linting as a gate, automatic issue triage.

## Trigger & Inputs

```yaml
workflow_dispatch:
  inputs:
    issue_number:
      description: GitHub issue number to implement
      required: true
    max_retries:
      description: Max Claude fix attempts before escalating
      default: '3'
```

## Workflow Structure

Single job `claude-implement-and-verify` on `macos-latest`.

### Setup Steps

1. Checkout `commcare-android` at `master` (pull latest)
2. Checkout `commcare-core` at `master` into sibling directory
3. Setup Python 3.x
4. Setup JDK 17 (temurin distribution) — matches existing PR workflow
5. Install Claude Code CLI: `npm install -g @anthropic-ai/claude-code`

### Implementation Step (runs once)

Claude is invoked non-interactively (`--print`) with the following prompt:

```
You are implementing a GitHub issue on the commcare-android project.

1. Run: gh issue view $ISSUE_NUMBER --json title,body
2. Create and checkout branch: claude/issue-$ISSUE_NUMBER from master
3. Implement the changes described in the issue
4. Write Robolectric tests for your changes
5. Commit with message: "[AI] Implement #$ISSUE_NUMBER: <short description>"
6. Push the branch and open a draft PR targeting master
7. Output exactly: BRANCH=claude/issue-$ISSUE_NUMBER PR=<pr_number>
```

Allowed tools: `Bash, Read, Write, Edit, Glob, Grep`

The step parses `BRANCH=` and `PR=` from Claude's stdout into step outputs.

### Retry Loop

After implementation, the workflow checks out the new branch and enters a loop from attempt `1` to `max_retries`.

**Tier 1 — Robolectric:**

```bash
./gradlew clean testCommcareDebug 2>&1 | tee robolectric.log
```

- Exit code 0 → proceed to Tier 2
- Non-zero → invoke Claude fix step with `robolectric.log`, push fix commit, continue loop

**Tier 2 — Espresso on Browserstack:**

1. Build APKs:
   - `assembleCommcareRelease` → `app-commcare-release.apk`
   - `assembleCommcareReleaseAndroidTest` → `app-commcare-release-androidTest.apk`
2. Upload app APK to Browserstack App Automate
3. Upload test APK to Browserstack App Automate
4. Trigger Espresso build (device: `<PLACEHOLDER_DEVICE>`)
5. Poll build status every 60s (timeout: 30 min)
6. Fetch full session logs → `browserstack.log`

- Build status `passed` → mark PR ready for review → **exit success**
- Build status `failed` → invoke Claude fix step with `browserstack.log`, push fix commit, continue loop

**Loop exhausted:**

Post comment on PR:
```
Max retries ($max_retries) reached without all tests passing. Needs human review.
Last failing suite: $SUITE
```
Exit with failure.

### Claude Fix Step Prompt

```
You are fixing test failures on branch $BRANCH_NAME (PR #$PR_NUMBER).
This is attempt $ATTEMPT of $MAX_RETRIES.

Failed test suite: $SUITE  (robolectric | espresso)

Full test output:
---
<contents of log file>
---

Analyse the failures, fix the root cause, and push a new commit to
branch $BRANCH_NAME with message: "[AI] Fix $SUITE failures (attempt $ATTEMPT)"
Do NOT rewrite history. Add a new commit only.
```

## Secrets Required

| Secret | Purpose |
|--------|---------|
| `ANTHROPIC_API_KEY` | Claude Code CLI authentication |
| `BROWSERSTACK_USERNAME` | Browserstack App Automate API |
| `BROWSERSTACK_ACCESS_KEY` | Browserstack App Automate API |
| `GITHUB_TOKEN` | Built-in — PR creation, branch push, comments |

## New File

`.github/workflows/claude-issue-implementer.yml`