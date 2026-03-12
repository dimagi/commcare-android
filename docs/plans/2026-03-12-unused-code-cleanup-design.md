# Unused Code & Resource Cleanup Design

## Goal
Remove all unused code and resources from commcare-android to reduce codebase size and improve maintainability.

## Scope
- **In scope:** `app/` and `commcare-support-library/` modules
- **Out of scope:** `../commcare-core/` (shared with Formplayer and other consumers)
- **Targets:** Dead classes, methods, fields, unused imports, unreachable code, unused strings (all 11 locales), colors, styles, drawables/images

## Approach: Android Lint + Grep Verification

Use Android lint's `UnusedResources` check for resource detection. Use grep-based static analysis for dead code detection. Verify all candidates against dynamic access patterns before removal.

## Phases

### Phase 1: Unused Resources
1. Run Android lint with `UnusedResources` enabled
2. For each flagged resource, verify it's not referenced via `getIdentifier()` or string-based reflection
3. Check that resources aren't referenced from commcare-core
4. Delete confirmed unused resources from all relevant files (including all locale variants for strings)
5. Commits: one for unused strings, one for unused colors/styles, one for unused drawables/images

### Phase 2: Unused Code
1. Find classes whose name never appears outside their own file
2. Find private methods/fields never referenced within their class
3. Remove unused imports
4. Verify candidates against reflection patterns (`Class.forName`, `Method.invoke`)
5. Check AndroidManifest.xml for XML-declared components (activities, receivers, providers)
6. Commit: one commit for dead code removal

### Phase 3: Verification
1. Run `./gradlew assembleCommcareDebug` to confirm build passes
2. Run unit tests to confirm no regressions
3. Run lint again to confirm no new issues

## Edge Cases
- Resources referenced dynamically via `getIdentifier()` — grep for the string name before removing
- Resources used only in `AndroidManifest.xml` or ProGuard rules
- Classes instantiated via reflection in XML (receivers, providers, activities)
- String resources potentially referenced from commcare-core
- Resources referenced in navigation graphs or menu XML

## Commit Strategy
One commit per category:
1. Unused strings (all locales)
2. Unused colors/styles
3. Unused drawables/images
4. Dead code (classes, methods, fields, imports)
5. Post-cleanup lint/ktlint formatting fixes
