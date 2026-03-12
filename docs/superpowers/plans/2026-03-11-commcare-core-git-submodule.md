# commcare-core Git Submodule Migration Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the sibling-directory local Gradle module dependency on commcare-core with a git submodule inside commcare-android, following the same pattern already used by formplayer.

**Architecture:** commcare-core is added as a git submodule at `libs/commcare-core/` tracking the `master` branch. `settings.gradle` is updated to point to the new path. The CI workflow replaces the Python cross-repo PR script with native submodule checkout (`submodules: true`), since cross-repo changes are now expressed by updating the submodule pointer within the commcare-android PR. The `browserstack-tests` CI job does not need `submodules: true` as it only consumes pre-built artifacts.

**Tech Stack:** Git submodules, Gradle multi-project builds, GitHub Actions

---

## Chunk 1: Add the git submodule and update Gradle config

### Task 1: Add commcare-core as a git submodule

**Files:**
- Modify: `.gitmodules` (currently exists but is empty — safe to overwrite)
- Create: `libs/commcare-core/` (populated by git submodule add)

- [ ] **Step 1: Verify `.gitmodules` is empty (pre-check)**

```bash
cat .gitmodules
```

Expected: no output (empty file). If there is unexpected content, do not proceed — investigate first.

- [ ] **Step 2: Add the submodule**

Run from the repo root:
```bash
git submodule add -b master https://github.com/dimagi/commcare-core.git libs/commcare-core
```

Expected output:
```
Cloning into '/Users/.../commcare-android/libs/commcare-core'...
```

This will:
- Populate `.gitmodules` with the submodule entry
- Clone commcare-core into `libs/commcare-core/`
- Stage `.gitmodules` and `libs/commcare-core` (as a submodule pointer) in git

- [ ] **Step 3: Verify `.gitmodules` content**

```bash
cat .gitmodules
```

Expected:
```
[submodule "libs/commcare-core"]
	path = libs/commcare-core
	url = https://github.com/dimagi/commcare-core.git
	branch = master
```

- [ ] **Step 4: Verify submodule is on master**

```bash
git -C libs/commcare-core branch
```

Expected: `* master` or detached HEAD at master tip — both are fine.

---

### Task 2: Update settings.gradle to point to the new submodule path

**Files:**
- Modify: `settings.gradle`

Current content:
```gradle
include ':app'
include ':commcare-core'
include ':commcare-support-library'
project(':commcare-core').projectDir = new File('../commcare-core')
```

- [ ] **Step 1: Update the path**

Change line 4 from:
```gradle
project(':commcare-core').projectDir = new File('../commcare-core')
```
to:
```gradle
project(':commcare-core').projectDir = new File('libs/commcare-core')
```

- [ ] **Step 2: Verify the build resolves commcare-core**

```bash
./gradlew projects
```

Expected: output lists `:commcare-core` as a project without errors.

---

### Task 3: Verify build and tests pass

**Files:** No changes — verification only.

- [ ] **Step 1: Run unit tests**

```bash
./gradlew testCommcareDebug
```

Expected: `BUILD SUCCESSFUL`. The `testImplementation project(path: ':commcare-core', configuration: 'testsAsJar')` dependency in `app/build.gradle` requires no changes since the module name `:commcare-core` is unchanged.

- [ ] **Step 2: Verify debug build assembles**

```bash
./gradlew assembleCommcareDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add .gitmodules libs/commcare-core settings.gradle
git commit -m "chore: migrate commcare-core to git submodule at libs/commcare-core"
```

---

## Chunk 2: Update CI workflow

### Task 4: Update GitHub Actions workflow to use submodule checkout

**Files:**
- Modify: `.github/workflows/commcare-android-pr-workflow.yml`

**Context:** The current `build-test-assemble` job:
1. Checks out commcare-android into a `commcare-android/` subdirectory
2. Downloads and runs `checkout_cross_request_repo.py` from `mobile-deploy` to find a matching commcare-core PR branch and check it out as a sibling directory at `../commcare-core`

With submodules, cross-repo changes are expressed by pinning the submodule pointer to the commcare-core PR branch commit in the commcare-android PR. CI uses `submodules: true` to automatically get the correct commcare-core commit — no Python script needed.

The `browserstack-tests` job checks out commcare-android but only downloads pre-built APK artifacts — it does **not** build from source, so it does not need `submodules: true` and requires no changes.

- [ ] **Step 1: Update the `Checkout commcare-android` step in the `build-test-assemble` job**

Change:
```yaml
      - name: Checkout commcare-android
        uses: actions/checkout@v6
        with:
          path: commcare-android
```
To:
```yaml
      - name: Checkout commcare-android
        uses: actions/checkout@v6
        with:
          path: commcare-android
          submodules: true
```

- [ ] **Step 2: Remove the four Python cross-request script steps from `build-test-assemble`**

Remove these steps entirely:
```yaml
      - name: Install python
        uses: actions/setup-python@v6
        with:
          python-version: '3.9'
      - name: Download cross request script
        run: |
          curl https://raw.githubusercontent.com/dimagi/mobile-deploy/master/requirements.txt -o requirements.txt
          curl https://raw.githubusercontent.com/dimagi/mobile-deploy/master/checkout_cross_request_repo.py -o checkout_cross_request_repo.py
      - name: Install Python dependencies
        run: python -m pip install -r requirements.txt
      - name: Run cross request script
        run: python checkout_cross_request_repo.py commcare-android ${{ github.event.number }} commcare-core
```

- [ ] **Step 3: Verify the workflow file is valid YAML**

```bash
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/commcare-android-pr-workflow.yml'))" && echo "Valid YAML"
```

Expected: `Valid YAML`

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/commcare-android-pr-workflow.yml
git commit -m "ci: replace cross-repo Python script with submodule checkout"
```

---

## Chunk 3: Update developer documentation

### Task 5: Update all files referencing the sibling directory structure

**Files:**
- Modify: `CLAUDE.md`
- Modify: `AGENTS.md`
- Modify: `README.md`
- Modify: `app/build.gradle` (remove stale comment block at lines 6–11)

- [ ] **Step 1: Update CLAUDE.md — two occurrences**

**Occurrence 1** (line 7, Project Overview):

Find:
```
It depends on [commcare-core](https://github.com/dimagi/commcare-core) which must live as a sibling directory (`../commcare-core/`).
```
Replace with:
```
It depends on [commcare-core](https://github.com/dimagi/commcare-core) which is included as a git submodule at `libs/commcare-core/`. Clone with `--recurse-submodules` to get it automatically.
```

**Occurrence 2** (line 46, Architecture > Module Structure table):

Find:
```
- **commcare-core** — Sibling project (`../commcare-core/`): XForm engine, case management, lookup tables, core business logic
```
Replace with:
```
- **commcare-core** — Git submodule (`libs/commcare-core/`): XForm engine, case management, lookup tables, core business logic
```

- [ ] **Step 2: Update AGENTS.md**

Find (line 48):
```
- CommCare Core is in `../commcare-core/`, always include it to the working directory for the current session
```

Replace with:
```
- CommCare Core is in `libs/commcare-core/` (a git submodule), always include it to the working directory for the current session
```

- [ ] **Step 3: Update README.md**

Read the file first. Around lines 24–36 there is a paragraph and bash block that looks like:

```
CommCare Android depends on CommCare Core, and CommCare Android expects the core directory to live side by side
in your directory structure. You can acheive this with the following commands (in Bash):

```bash
cd ~/AndroidStudioProjects
mkdir CommCare
cd CommCare
git clone https://github.com/dimagi/commcare-android.git
git clone https://github.com/dimagi/commcare-core.git
```
```

Replace the prose and bash block with:

```
CommCare Android includes CommCare Core as a git submodule. Clone with:

```bash
cd ~/AndroidStudioProjects
git clone --recurse-submodules https://github.com/dimagi/commcare-android.git
```
```

- [ ] **Step 4: Remove stale comment from app/build.gradle**

Find and remove lines 6–11 in `app/build.gradle`:
```gradle
// This build script assumes the following directory structure:
// - somewhere/your/code/directory/is
// -- commcare-android (github: https://github.com/dimagi/commcare-android/)
// -- commcare-core (github: https://github.com/dimagi/commcare-core/)
// these directories MUST be named like this, or it won't work
```

(Read the file first to confirm exact line numbers before editing.)

- [ ] **Step 5: Commit**

```bash
git add CLAUDE.md AGENTS.md README.md app/build.gradle
git commit -m "docs: update onboarding instructions for git submodule setup"
```

---

## Cross-repo PR workflow for developers (reference)

After this migration, the workflow for cross-repo changes (commcare-android + commcare-core together) is:

1. Open a PR in commcare-core, note the branch name (e.g. `my-feature`)
2. In commcare-android, point the submodule to that branch's current tip commit:
   ```bash
   cd libs/commcare-core
   git fetch origin my-feature
   git checkout origin/my-feature
   cd ../..
   git add libs/commcare-core
   git commit -m "chore: point commcare-core submodule to my-feature for testing"
   ```
3. Open a commcare-android PR — CI will checkout commcare-android with `submodules: true`, picking up the pinned commit automatically
4. **Once the commcare-core PR is merged to master**, immediately update the submodule pointer to the merged master tip — this is required, not optional:
   ```bash
   cd libs/commcare-core
   git fetch origin master
   git checkout origin/master
   cd ../..
   git add libs/commcare-core
   git commit -m "chore: update commcare-core submodule to merged master"
   ```

> **Important:** Submodules track a specific commit SHA, not a branch. If the commcare-core branch is force-pushed or rebased before merging, the pinned SHA may become unreachable after merge. Always update the pointer to the final merged commit on master before closing the commcare-android PR.