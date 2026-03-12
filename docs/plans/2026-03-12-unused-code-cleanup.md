# Unused Code & Resource Cleanup Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Remove all unused code and resources from commcare-android to reduce codebase size.

**Architecture:** Run Android lint to detect unused resources, then use grep-based static analysis for dead code. Verify each candidate before removal. One commit per category.

**Tech Stack:** Android Lint, Gradle, grep/ripgrep, ktlint

---

### Task 1: Run Android Lint to Detect Unused Resources

**Files:**
- Modify: `app/lint.xml` (temporarily enable UnusedResources)

**Step 1: Enable UnusedResources lint check**

Add the `UnusedResources` check to `app/lint.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<lint>
    <issue id="LogNotTimber" severity="ignore" />
    <issue id="UnusedResources" severity="warning" />
</lint>
```

**Step 2: Run lint and capture results**

Run:
```bash
cd /Users/conroyricketts/Code/CommCare/commcare-android
./gradlew :app:lintCommcareDebug 2>&1 | tee lint-output.txt
```

Expected: Lint report generated at `app/build/reports/lint-results-commcareDebug.xml` and/or `.html`

**Step 3: Extract unused resource list**

Run:
```bash
grep -A2 'UnusedResources' app/build/reports/lint-results-commcareDebug.xml | grep 'location file' > unused-resources-list.txt
cat unused-resources-list.txt | wc -l
```

Expected: A list of files and resource names flagged as unused.

**Step 4: Revert lint.xml change**

Restore `app/lint.xml` to its original state (remove the UnusedResources line).

**Step 5: Save the unused resources list for subsequent tasks**

Keep `unused-resources-list.txt` and the lint HTML report for reference. Parse the lint XML report to create categorized lists:
- `unused-strings.txt` — string resource names
- `unused-drawables.txt` — drawable file paths
- `unused-colors.txt` — color resource names
- `unused-other.txt` — styles, dimens, arrays, etc.

---

### Task 2: Remove Unused String Resources

**Files:**
- Modify: `app/res/values/strings.xml` and all locale variants:
  - `app/res/values-es/strings.xml`
  - `app/res/values-fr/strings.xml`
  - `app/res/values-ha/strings.xml`
  - `app/res/values-hi/strings.xml`
  - `app/res/values-lt/strings.xml`
  - `app/res/values-no/strings.xml`
  - `app/res/values-pt/strings.xml`
  - `app/res/values-sw/strings.xml`
  - `app/res/values-ti/strings.xml`
  - `app/res/values-large/strings.xml`
  - `app/res/values-small/strings.xml`
  - `app/res/values-sw720dp-land/strings.xml`
  - `app/res/values-xlarge/strings.xml`

**Step 1: Verify each unused string**

For each string name from `unused-strings.txt`, verify it is truly unused:

```bash
# Check it's not referenced in Java/Kotlin code
grep -r "R.string.<name>" app/src/ app/unit-tests/ app/instrumentation-tests/
# Check it's not referenced in XML layouts/menus/navigation
grep -r "@string/<name>" app/res/
# Check it's not referenced from commcare-core
grep -r "<name>" ../commcare-core/src/
# Check it's not used via getIdentifier (only AndroidArrayDataSource does this for arrays, not strings)
```

Expected: No matches for truly unused strings.

**Step 2: Remove confirmed unused strings from base strings.xml**

Remove the `<string name="...">` entries from `app/res/values/strings.xml`.

**Step 3: Remove from all locale variants**

For each locale file, remove the same string keys. Not all locales will have all keys — skip missing ones.

**Step 4: Verify build compiles**

Run:
```bash
./gradlew :app:assembleCommcareDebug
```

Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add app/res/values*/strings.xml
git commit -m "[AI] Remove unused string resources from all locales"
```

---

### Task 3: Remove Unused Colors, Styles, and Other Value Resources

**Files:**
- Modify: `app/res/values/colors.xml`
- Modify: `app/res/values/styles.xml`
- Modify: `app/res/values/themes.xml`
- Modify: `app/res/values/dimens.xml` and dimension variants
- Modify: `app/res/values/arrays.xml`
- Modify: `app/res/values/dotscolors.xml`
- Modify: `app/res/values/integers.xml`
- Modify: `app/res/values/attrs.xml`
- Modify: `app/res/values/mydialog.xml`

**Step 1: Verify each unused resource from `unused-colors.txt` and `unused-other.txt`**

For each resource name, verify:

```bash
# Check Java/Kotlin references
grep -r "R.color.<name>\|R.style.<name>\|R.dimen.<name>\|R.array.<name>\|R.integer.<name>\|R.attr.<name>" app/src/
# Check XML references
grep -r "@color/<name>\|@style/<name>\|@dimen/<name>\|@array/<name>\|@integer/<name>\|?attr/<name>" app/res/
# Check for string-based dynamic lookups (getIdentifier uses "array" type in AndroidArrayDataSource)
grep -r "getIdentifier.*<name>" app/src/
```

**Step 2: Remove confirmed unused value resources**

Edit each XML file to remove the unused entries.

**Step 3: Verify build compiles**

Run:
```bash
./gradlew :app:assembleCommcareDebug
```

Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/res/values/
git commit -m "[AI] Remove unused color, style, and other value resources"
```

---

### Task 4: Remove Unused Drawable and Image Resources

**Files:**
- Delete: Unused files from `app/res/drawable*/`, `app/res/mipmap-*/`

**Step 1: Verify each unused drawable from `unused-drawables.txt`**

For each drawable file, verify:

```bash
# Extract resource name (filename without extension)
name=$(basename "$file" | sed 's/\.[^.]*$//')
# Check Java/Kotlin references
grep -r "R.drawable.$name\|R.mipmap.$name" app/src/
# Check XML references
grep -r "@drawable/$name\|@mipmap/$name" app/res/
# Check AndroidManifest.xml
grep -r "$name" app/AndroidManifest.xml
```

**Step 2: Delete confirmed unused drawable files**

Remove files from all density variants (ldpi, mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi, anydpi).

**Step 3: Verify build compiles**

Run:
```bash
./gradlew :app:assembleCommcareDebug
```

Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add -A app/res/drawable* app/res/mipmap-*
git commit -m "[AI] Remove unused drawable and image resources"
```

---

### Task 5: Remove Unused Code — Dead Classes

**Files:**
- Delete or modify: Various Java/Kotlin files in `app/src/org/commcare/`

**Step 1: Find candidate dead classes**

For each `.java` and `.kt` file in `app/src/`, check if the class name is referenced anywhere else:

```bash
# Get class name from file
class_name=$(basename "$file" | sed 's/\.[^.]*$//')
# Check references outside the file itself
grep -r --include="*.java" --include="*.kt" --include="*.xml" "$class_name" app/src/ app/res/ app/AndroidManifest.xml | grep -v "$file"
```

Classes with zero external references are candidates.

**Step 2: Verify candidates against reflection and manifest**

For each candidate:
- Check `AndroidManifest.xml` for declared activities, services, receivers, providers
- Check ProGuard rules (`app/proguard.cfg`) for keep rules
- Check navigation graphs for fragment references
- Check if the class extends a framework component (Activity, Service, BroadcastReceiver, ContentProvider, Fragment)
- Check test files for references

**Step 3: Remove confirmed dead classes**

Delete the files.

**Step 4: Verify build compiles**

Run:
```bash
./gradlew :app:assembleCommcareDebug
```

Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add -A app/src/
git commit -m "[AI] Remove unused classes"
```

---

### Task 6: Remove Unused Methods, Fields, and Imports

**Files:**
- Modify: Various Java/Kotlin files in `app/src/org/commcare/`

**Step 1: Find unused imports**

Use Android Studio's lint or grep for imports that don't match any usage in the file:

```bash
# For each file, extract imports and check usage
# This is best done with lint: UnusedImport check
# Or with ktlint for Kotlin files
ktlint "app/src/**/*.kt" 2>&1 | grep "no-unused-imports"
```

**Step 2: Remove unused imports from Kotlin files**

Run:
```bash
ktlint --format "app/src/**/*.kt"
```

**Step 3: Find unused private methods and fields**

For each Java/Kotlin file, find private methods/fields and check if they're referenced within the same file:

```bash
# For private methods in Java files
grep -n "private.*\b\w\+\s*(" "$file" | while read line; do
    method_name=$(echo "$line" | grep -oP 'private\s+\w+\s+\K\w+(?=\s*\()')
    count=$(grep -c "\b$method_name\b" "$file")
    if [ "$count" -le 1 ]; then
        echo "UNUSED: $file:$method_name"
    fi
done
```

**Step 4: Remove confirmed unused private methods and fields**

Edit files to remove unused private members. Be careful with:
- Serialization fields (serialVersionUID)
- Fields used via reflection
- Methods annotated with @Override, @Test, @OnClick, etc.

**Step 5: Verify build compiles and tests pass**

Run:
```bash
./gradlew :app:assembleCommcareDebug
./gradlew :app:testCommcareDebugUnitTest
```

Expected: BUILD SUCCESSFUL, all tests pass

**Step 6: Commit**

```bash
git add app/src/
git commit -m "[AI] Remove unused methods, fields, and imports"
```

---

### Task 7: Run Lint/Ktlint Formatting and Final Verification

**Files:**
- Modify: Any files changed in previous tasks

**Step 1: Run ktlint formatting on modified Kotlin files**

```bash
ktlint --format "app/src/**/*.kt"
```

**Step 2: Verify ktlint compliance**

```bash
ktlint "app/src/**/*.kt"
```

Expected: No violations

**Step 3: Run full build**

```bash
./gradlew :app:assembleCommcareDebug
```

Expected: BUILD SUCCESSFUL

**Step 4: Run unit tests**

```bash
./gradlew :app:testCommcareDebugUnitTest
```

Expected: All tests pass

**Step 5: Run lint one more time to confirm no new issues**

```bash
./gradlew :app:lintCommcareDebug
```

Expected: No new issues introduced

**Step 6: Commit any formatting fixes**

```bash
git add app/src/
git commit -m "[AI] Apply ktlint formatting after cleanup"
```

---

## Key Safety Checks (Apply to Every Task)

1. **Never remove resources referenced via `getIdentifier()`** — `AndroidArrayDataSource.java` uses this for array resources
2. **Never remove classes declared in `AndroidManifest.xml`** — activities, services, receivers, providers
3. **Never remove classes referenced in navigation graphs** — `nav_graph_connect.xml`, `nav_graph_connect_messaging.xml`, `nav_graph_personalid.xml`
4. **Never remove resources referenced from `commcare-core`** — grep `../commcare-core/src/` before removing
5. **Never remove annotated methods** — `@Override`, `@Test`, `@OnClick`, `@Provides`, etc.
6. **Never remove `serialVersionUID`** fields
7. **Build and verify after each removal batch** — catch errors early
