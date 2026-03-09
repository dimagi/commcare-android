# Claude Issue Implementer — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Create a `workflow_dispatch` GitHub Actions workflow that takes a GitHub issue number, invokes Claude Code to implement the change, then verifies it through a tiered test loop (Robolectric → Espresso on Browserstack), iterating on failures up to a configurable cap.

**Architecture:** A single long-running job on `macos-latest` runs Claude to implement the issue and open a draft PR, then enters a bash retry loop that runs Robolectric first (fail-fast, cheap), then Espresso via the existing `scripts/browserstack.py`. On failure, Claude is re-invoked to push a fix commit and the loop restarts from Robolectric.

**Tech Stack:** GitHub Actions, Claude Code CLI (`@anthropic-ai/claude-code`), Gradle, Browserstack App Automate (via existing `scripts/browserstack.py`), Python 3, Bash.

**Design doc:** `docs/plans/2026-03-09-claude-issue-implementer-design.md`

---

## Files to Create

- Create: `.github/workflows/claude-issue-implementer.yml`
- Create: `.github/scripts/ci-retry-loop.sh`

---

## Task 1: Create the retry loop script (skeleton)

**File:** `.github/scripts/ci-retry-loop.sh`

This script runs inside the GitHub Actions job after Claude creates the PR branch. It receives the branch name, PR number, max retries, and all credentials via environment variables.

**Step 1: Create the script file with skeleton**

```bash
#!/usr/bin/env bash
# ci-retry-loop.sh
# Runs Robolectric then Browserstack Espresso in a retry loop.
# Invokes Claude to fix failures between attempts.
#
# Required env vars:
#   BRANCH_NAME, PR_NUMBER, MAX_RETRIES, ANTHROPIC_API_KEY,
#   BROWSERSTACK_USERNAME, BROWSERSTACK_PASSWORD,
#   GITHUB_TOKEN
#
# Exit codes:
#   0 = all tests passed
#   1 = exhausted retries (caller should escalate)

set -euo pipefail

: "${BRANCH_NAME:?}"
: "${PR_NUMBER:?}"
: "${MAX_RETRIES:?}"
: "${ANTHROPIC_API_KEY:?}"
: "${BROWSERSTACK_USERNAME:?}"
: "${BROWSERSTACK_PASSWORD:?}"

echo "Starting CI retry loop: max $MAX_RETRIES attempts"

for attempt in $(seq 1 "$MAX_RETRIES"); do
  echo ""
  echo "========================================="
  echo "Attempt $attempt of $MAX_RETRIES"
  echo "========================================="

  # Steps will be added in subsequent tasks

done

echo "All attempts exhausted without passing."
exit 1
```

**Step 2: Make the script executable**

```bash
chmod +x .github/scripts/ci-retry-loop.sh
```

**Step 3: Verify the script runs without error**

```bash
BRANCH_NAME=test PR_NUMBER=1 MAX_RETRIES=1 ANTHROPIC_API_KEY=x \
  BROWSERSTACK_USERNAME=u BROWSERSTACK_PASSWORD=p \
  GITHUB_TOKEN=t .github/scripts/ci-retry-loop.sh
```

Expected output ends with: `All attempts exhausted without passing.` and exits 1.

**Step 4: Commit**

```bash
git add .github/scripts/ci-retry-loop.sh
git commit -m "[AI] Add CI retry loop script skeleton"
```

---

## Task 2: Add Robolectric tier to the retry loop

Inside the `for` loop body (replace the `# Steps will be added` comment), add:

**Step 1: Add Robolectric execution block**

```bash
  # --- Tier 1: Robolectric ---
  echo "Running Robolectric tests..."
  set +e
  ./gradlew testCommcareDebug 2>&1 | tee robolectric.log
  ROBO_EXIT="${PIPESTATUS[0]}"
  set -e

  if [ "$ROBO_EXIT" -ne 0 ]; then
    echo "Robolectric FAILED."
    if [ "$attempt" -eq "$MAX_RETRIES" ]; then
      echo "No retries remaining."
      exit 1
    fi
    echo "Invoking Claude to fix Robolectric failures..."
    FAILED_SUITE="robolectric"
    LOG_FILE="robolectric.log"
    invoke_claude_fix
    continue
  fi

  echo "Robolectric PASSED."
```

Note: `invoke_claude_fix` is a bash function defined in Task 4. For now the script will error — that's expected until Task 4.

**Step 2: Verify the script still parses cleanly (bash syntax check)**

```bash
bash -n .github/scripts/ci-retry-loop.sh
```

Expected: no output (no syntax errors).

**Step 3: Commit**

```bash
git add .github/scripts/ci-retry-loop.sh
git commit -m "[AI] Add Robolectric tier to CI retry loop"
```

---

## Task 3: Add Browserstack Espresso tier to the retry loop

After the Robolectric block (still inside the `for` loop), add:

**Step 1: Add APK build and Browserstack execution block**

```bash
  # --- Tier 2: Browserstack Espresso ---
  echo "Building release and test APKs..."
  ./gradlew assembleCommcareRelease assembleCommcareReleaseAndroidTest

  echo "Running Browserstack Espresso tests..."
  set +e
  RELEASE_APP_LOCATION="app/build/outputs/apk/commcare/release/app-commcare-release.apk" \
  TEST_APP_LOCATION="app/build/outputs/apk/androidTest/commcare/release/app-commcare-release-androidTest.apk" \
  ghprbPullId="$PR_NUMBER" \
  python3 scripts/browserstack.py 2>&1 | tee browserstack.log
  BS_EXIT="${PIPESTATUS[0]}"
  set -e

  if [ "$BS_EXIT" -eq 0 ]; then
    echo "Browserstack Espresso PASSED. All tests green."
    exit 0
  fi

  echo "Browserstack Espresso FAILED."
  if [ "$attempt" -eq "$MAX_RETRIES" ]; then
    echo "No retries remaining."
    exit 1
  fi
  echo "Invoking Claude to fix Espresso failures..."
  FAILED_SUITE="espresso"
  LOG_FILE="browserstack.log"
  invoke_claude_fix
```

**Step 2: Syntax check**

```bash
bash -n .github/scripts/ci-retry-loop.sh
```

**Step 3: Commit**

```bash
git add .github/scripts/ci-retry-loop.sh
git commit -m "[AI] Add Browserstack Espresso tier to CI retry loop"
```

---

## Task 4: Add the `invoke_claude_fix` function

Add this function **before** the `for` loop in the script:

**Step 1: Add function definition**

```bash
invoke_claude_fix() {
  local log_content
  log_content=$(cat "$LOG_FILE")

  local prompt
  prompt="You are fixing test failures on branch ${BRANCH_NAME} (PR #${PR_NUMBER}).
This is attempt ${attempt} of ${MAX_RETRIES}.

Failed test suite: ${FAILED_SUITE}

Full test output:
---
${log_content}
---

Analyse the failures, identify the root cause, and fix it.
Then push a new commit to branch ${BRANCH_NAME} with message:
  '[AI] Fix ${FAILED_SUITE} failures (attempt ${attempt})'

Do NOT rewrite history. Add a new commit only.
The remote is 'origin'. Use: git push origin ${BRANCH_NAME}"

  claude --print \
    --allowedTools "Bash,Read,Write,Edit,Glob,Grep" \
    "$prompt"

  # Pull the fix commit so the next iteration tests the updated code
  git pull origin "$BRANCH_NAME"
}
```

**Step 2: Syntax check**

```bash
bash -n .github/scripts/ci-retry-loop.sh
```

**Step 3: Commit**

```bash
git add .github/scripts/ci-retry-loop.sh
git commit -m "[AI] Add invoke_claude_fix function to CI retry loop"
```

---

## Task 5: Create the workflow YAML — setup and implementation step

**File:** `.github/workflows/claude-issue-implementer.yml`

**Step 1: Write the workflow file**

```yaml
name: Claude Issue Implementer

on:
  workflow_dispatch:
    inputs:
      issue_number:
        description: 'GitHub issue number to implement'
        required: true
      max_retries:
        description: 'Max Claude fix attempts before escalating to human'
        default: '3'

jobs:
  implement-and-verify:
    name: Implement and Verify
    runs-on: macos-latest

    steps:
      - name: Checkout commcare-android at master
        uses: actions/checkout@v3
        with:
          ref: master
          fetch-depth: 0
          token: ${{ secrets.GITHUB_TOKEN }}

      - name: Checkout commcare-core at master
        uses: actions/checkout@v3
        with:
          repository: dimagi/commcare-core
          ref: master
          path: ../commcare-core
          token: ${{ secrets.GITHUB_TOKEN }}

      - name: Setup Python
        uses: actions/setup-python@v4
        with:
          python-version: '3.x'

      - name: Setup JDK 17
        uses: actions/setup-java@v3
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Install Claude Code CLI
        run: npm install -g @anthropic-ai/claude-code

      - name: Claude — implement issue
        id: claude_impl
        env:
          ANTHROPIC_API_KEY: ${{ secrets.ANTHROPIC_API_KEY }}
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          ISSUE_NUMBER: ${{ inputs.issue_number }}
        run: |
          BRANCH_NAME="claude/issue-${ISSUE_NUMBER}"

          PROMPT="You are implementing a GitHub issue on the commcare-android Android project.

1. Run: gh issue view ${ISSUE_NUMBER} --json title,body
2. Create and checkout branch: ${BRANCH_NAME} from master
3. Implement the changes described in the issue following the coding conventions in CLAUDE.md
4. Write Robolectric unit tests for your changes
5. Commit with message: '[AI] Implement #${ISSUE_NUMBER}: <short description>'
6. Push the branch: git push origin ${BRANCH_NAME}
7. Open a draft PR targeting master with: gh pr create --draft --title '<title>' --body '<body>' --base master
8. On the very last line of your output write exactly (no extra text):
   RESULT: BRANCH=${BRANCH_NAME} PR=<pr_number>"

          claude --print \
            --allowedTools "Bash,Read,Write,Edit,Glob,Grep" \
            "$PROMPT" | tee /tmp/claude-impl.log

          # Parse branch and PR number from Claude's output
          RESULT_LINE=$(grep "^RESULT:" /tmp/claude-impl.log | tail -1)
          BRANCH=$(echo "$RESULT_LINE" | grep -oP 'BRANCH=\K\S+')
          PR=$(echo "$RESULT_LINE" | grep -oP 'PR=\K[0-9]+')

          echo "branch=$BRANCH" >> "$GITHUB_OUTPUT"
          echo "pr_number=$PR" >> "$GITHUB_OUTPUT"
          echo "Implemented on branch=$BRANCH PR=$PR"

      - name: Checkout implementation branch
        uses: actions/checkout@v3
        with:
          ref: ${{ steps.claude_impl.outputs.branch }}
          fetch-depth: 0
          token: ${{ secrets.GITHUB_TOKEN }}

      - name: Run CI retry loop
        env:
          BRANCH_NAME: ${{ steps.claude_impl.outputs.branch }}
          PR_NUMBER: ${{ steps.claude_impl.outputs.pr_number }}
          MAX_RETRIES: ${{ inputs.max_retries }}
          ANTHROPIC_API_KEY: ${{ secrets.ANTHROPIC_API_KEY }}
          BROWSERSTACK_USERNAME: ${{ secrets.BROWSERSTACK_USERNAME }}
          BROWSERSTACK_PASSWORD: ${{ secrets.BROWSERSTACK_PASSWORD }}
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: |
          chmod +x .github/scripts/ci-retry-loop.sh
          .github/scripts/ci-retry-loop.sh
        continue-on-error: true
        id: retry_loop

      - name: Mark PR ready for review
        if: steps.retry_loop.outcome == 'success'
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          PR_NUMBER: ${{ steps.claude_impl.outputs.pr_number }}
        run: gh pr ready "$PR_NUMBER"

      - name: Escalate — post failure comment
        if: steps.retry_loop.outcome == 'failure'
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          PR_NUMBER: ${{ steps.claude_impl.outputs.pr_number }}
          MAX_RETRIES: ${{ inputs.max_retries }}
        run: |
          gh pr comment "$PR_NUMBER" --body \
            "⚠️ Claude was unable to get all tests passing after $MAX_RETRIES attempts. Needs human review."
          exit 1
```

**Step 2: Validate the YAML is well-formed**

```bash
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/claude-issue-implementer.yml'))"
```

Expected: no output (no parse errors).

**Step 3: Commit**

```bash
git add .github/workflows/claude-issue-implementer.yml
git commit -m "[AI] Add Claude issue implementer GitHub Actions workflow"
```

---

## Task 6: End-to-end smoke test

There is no unit test framework for GitHub Actions YAML. Verification is done by triggering the workflow on a real (or trivial) issue.

**Step 1: Create a trivial test issue**

On GitHub, create an issue such as:
> "Add a code comment to `app/src/main/java/org/commcare/CommCareApp.java` explaining what the class does"

Note the issue number (e.g. `9999`).

**Step 2: Ensure secrets are configured in the repo**

In repo Settings → Secrets and variables → Actions, verify these secrets exist:
- `ANTHROPIC_API_KEY`
- `BROWSERSTACK_USERNAME`
- `BROWSERSTACK_PASSWORD`

**Step 3: Trigger the workflow**

```
GitHub UI → Actions → Claude Issue Implementer → Run workflow
  issue_number: 9999
  max_retries: 2
```

**Step 4: Verify expected behaviour**

| What to check | Expected |
|---|---|
| A branch `claude/issue-9999` is created | ✓ |
| A draft PR is opened targeting master | ✓ |
| Robolectric step appears in logs | ✓ |
| Browserstack step appears in logs | ✓ |
| On all-pass: PR is marked ready for review | ✓ |
| On exhaustion: PR comment posted, workflow fails | ✓ |

**Step 5: If the workflow fails to parse Claude's output**

Check `/tmp/claude-impl.log` in the Actions step log. The `RESULT:` line must appear as the last line of Claude's output exactly as: `RESULT: BRANCH=claude/issue-9999 PR=1234`. Adjust the implementation prompt in the workflow if Claude is not following the format reliably.

---

## Secrets Reference

| Secret name | Where used |
|---|---|
| `ANTHROPIC_API_KEY` | Claude Code CLI authentication |
| `BROWSERSTACK_USERNAME` | `scripts/browserstack.py` env var |
| `BROWSERSTACK_PASSWORD` | `scripts/browserstack.py` env var |
| `GITHUB_TOKEN` | Built-in — PR creation, branch push, comments, checkout |