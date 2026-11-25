
CommCare Mobile Standards and Best Practices
============================================


This document provides CommCare Mobile specific best practices that are to be followed while writing code for our mobile projects. Please read through [our general code standards first](https://github.com/dimagi/open-source/blob/master/docs/standards.rst) which apply to all our projects including CommCare Mobile.


### Code Style and Readability

- Code should be easy to read, understand, and maintain.
- Meaningful variable and method names should be used. Variables should generally follow camel case except for static constants which should follow the `STATIC_CONSTANT` pattern
- Code should be formatted as per the [code style settings](https://github.com/dimagi/commcare-android/?tab=readme-ov-file#code-style-settings).
- Code should pass the coding standards set by the [checkstyle config](https://github.com/dimagi/commcare-android/blob/master/.github/linters/checkstyle.xml)
- All subcontexts should be braced, even one-liners  (if {})
- keyword expressions with arguments should be separated by a space (if (), not if())
- Braced expressions should begin a newline after opening brace, and before and after the close brace
- Typecasts should be adjacent to the value being cast: (int)value
- To improve code readability, newly added code should limit size of Classes under 500 lines and size of methods under 50 lines

### Fail First instead of programming defensively

- Write code that fails fast and visibly when assumptions are violated, instead of defensively hiding errors or trying to continue execution in an invalid state. Fail-first code is easier to debug, test, and maintain. It prevents silent failures, unclear app states, and hard-to-reproduce bugs.

- Validate early and loudly: Use require(), check(), or explicit exception throws to assert invariants or impossible states. Example: `requireNotNull(user.id) { "User ID must not be null" }`

- Avoid silent fallbacks: Don’t catch exceptions only to log or ignore them. Let them propagate unless recovery is truly possible.[Look at over extended exception handling approach for more details](mobile_exception_handling.md)

In short: “Crash early, fix quickly, and handle gracefully where it matters.”

### Modularity and Resusability

- Code should be modular, with a clear separation of concerns, reusable components (e.g., utility classes, helper functions) should be identified and utilized.

### Performance

- Code should be optimized for performance where necessary (e.g., efficient data structures, and minimal resource usage).
- Code should not introduce performance bottlenecks or memory leaks.

###  Error Handling:

- Proper error-handling mechanisms should be implemented (e.g., try-catch blocks, exception handling).
- Errors should be logged appropriately for debugging purposes.
- Don't include exceptions that extend `RuntimeException` in method signatures (as in "throws StorageFullException"). The compiler provides no static guarantees on runtime exceptions. Recoverable runtime exceptions should be documented in the javadoc (as in "@throws StorageFullException reason/how to recover").
- [Look at over extended exception handling approach](mobile_exception_handling.md)

### Logging

- `System.out` should be avoided.
- `Logger.log` statements logs are submitted back to HQ. These should be for top level events that help us get a feel for what's going on on the phone, as well as any unexpected states that the app gets into. For Ex. Beginning unsent data submission: N forms to be submitted" or "Unexpected cache miss for recently serialized records".
- `Logger.log` messages are persisted so it is important to be judicious when deciding to use logger.log. Log events should occur with a frequency that is bounded by user events (login, sync, form entry start, etc), not looping or machine driven. For instance "Migrated 512 cases", not "Migrating case 23 of 512"
- Always include as much context as possible. "Error - Invalid date for parsing" is much less helpful than "Error - Invalid date for parsing: 'January 2015'"

### Java-Specific Criteria:

- Synchronization mechanisms (e.g., synchronized blocks, locks) should be used where shared resources are accessed concurrently.
- Code should properly handle multithreading scenarios to avoid race conditions and deadlocks.
- Proper memory management practices should be followed (e.g., avoiding memory leaks, efficient use of data structures).
 
### Kotlin-Specific Criteria:

- Nullable types are handled appropriately with safe calls and nullability checks.
- Use of Kotlin's built-in null safety features where necessary.
- Change Java files to Kotlin when Java-Kotlin intercompatibility introduces unnecessary and significant boilerplate code.

### Color, Theme, and Style Resources

Color resources are organized in three layers, each with a single responsibility. This keeps the palette reusable, centralizes design decisions, and makes multi-theme support (e.g. light/dark mode) a matter of swapping one mapping rather than editing layouts.

| Layer | File | Contains | References |
|-------|------|----------|------------|
| 1. Palette | `colors.xml` | Base colors only — named by hue/shade | Raw hex (nothing) |
| 2. Semantic roles (named by purpose, e.g. `colorCtaBackground`) | `themes.xml` | Color *roles* — named for their UI purpose, not their hue — mapped to a base color per theme, so the same role resolves to a different color when the theme switches (e.g. light/dark) | `@color/...` (base colors) |
| 3. Reusable styling | `styles.xml` | Reusable element styles | `?attr/...` roles (or `@color/...` for theme-invariant colors) |

**The core rule:** for any color that could differ between themes, layouts and styles must reference a semantic role (`?attr/colorRole`) rather than a base color (`@color/...`) directly — so changing a role's mapping in `themes.xml` propagates everywhere. Theme-invariant colors may reference a base color directly, but never a raw hex value.

#### Base Colors (colors.xml)

- `colors.xml` contains **base colors only** — named for the color itself (its hue or shade, e.g. `lavender_mist`, `slate_gray`), holding a raw hex value.
- **Never** define feature/usage-specific colors here (e.g. `secondary_cta_btn_background` does not belong in `colors.xml`).
- Before adding a new base color, check `colors.xml` for an existing entry with the same hex value and reuse it instead of duplicating.
- Example:
  ```xml
  <color name="lavender_mist">#D8D9F4</color>
  <color name="deep_lavender">#3A3B7A</color>
  ```

#### Semantic Color Roles (themes.xml)

- Map base colors to semantic **color roles** in `themes.xml`, defined once per theme (e.g. `Theme.CommCare.Light`, `Theme.CommCare.Dark`).
- **Prefer Material Components' built-in role attributes** wherever one fits: `colorPrimary`, `colorSecondary`, `colorSurface`, `colorOnSurface`, `colorError`, `colorOnPrimary`, etc. Standard widgets pick these up automatically and get light/dark theming for free.
- **Only when no built-in role fits**, declare a custom attribute in `attrs.xml` and map it in each theme. Name custom attributes by *role*, prefixed with `color…` (e.g. `colorCtaBackground`) — never by hue.
- Example:
  ```xml
  <!-- attrs.xml — custom role, only when no built-in fits -->
  <attr name="colorCtaBackground" format="color"/>

  <!-- themes.xml — Theme.CommCare.Light -->
  <item name="colorSecondary">@color/lavender_mist</item>       <!-- built-in role -->
  <item name="colorCtaBackground">@color/lavender_mist</item>   <!-- custom fallback -->

  <!-- themes.xml — Theme.CommCare.Dark: same roles, different base colors -->
  <item name="colorSecondary">@color/deep_lavender</item>
  <item name="colorCtaBackground">@color/deep_lavender</item>
  ```
- Dark mode then requires **zero layout/style changes** — only a second theme mapping.

#### Styles (styles.xml)

- When a new UI element (button, card, etc.) is created with specific styling requirements that need to be reused across the app, define a dedicated style for it in `styles.xml` rather than repeating attributes inline on each element.
- Apply the style to the element via `style="@style/YourStyleName"` so visual changes can be made in one place and stay consistent everywhere the element is used.
- **Choosing between a role and a base color in a style:**
  - Reference a semantic role (`?attr/colorRole`) when the color is themable — it may differ between light/dark themes, or the same role is reused across components. Define these in `themes.xml`.
  - Referencing a base color directly (`@color/lavender_mist`) is acceptable for theme-invariant or minor, localized colors where defining a theme attribute would add unnecessary overhead.
  - Do not use raw hex values in a style — always reference a named base color from `colors.xml` so it remains the single source of truth for color values.
- Example:
  ```xml
  <style name="CustomSecondaryCtaButtonStyle">
      <!-- themable / reused across components: use a role -->
      <item name="android:background">?attr/colorCtaBackground</item>
      <!-- theme-invariant, minor one-off: a base color is fine -->
      <item name="android:shadowColor">@color/lavender_mist</item>
  </style>
  ```
- For reference, see an existing reusable style in [`styles.xml`](https://github.com/dimagi/commcare-android/blob/e7b31c0671a4fabb62f56da540ae6f033753b983/app/res/values/styles.xml#L231) (e.g. `CustomSecondaryCtaButtonStyle`).

### Mobile Data Storage

- [Sqlite vs Shared Preferences](mobile_storage_standards.md)

### Async Processing

- **Prefer Kotlin Coroutines** for asynchronous work and background processing.
  - Use structured concurrency (`viewModelScope`, `lifecycleScope`, etc.).
  - Explicitly specify dispatchers (`Dispatchers.IO`, `Dispatchers.Default`) instead of relying on defaults.
  - Ensure proper cancellation handling to avoid memory leaks.

- **Use Android WorkManager** for:
  - Deferrable background work
  - Periodic tasks
  - Guaranteed execution (even after process death)
  - Tasks that require constraints (network, charging, etc.)

- **Avoid `AsyncTask`.**
  - `AsyncTask` is deprecated.
  - It should only remain in legacy code that has not yet been refactored.
  - Do not introduce new usages of `AsyncTask` unless working with existing code structure.

- **Avoid introducing arbitrary async models into the codebase** to minimize:
  - Inconsistent threading patterns
  - Hard-to-debug race conditions
  - Lifecycle leaks
  - Increased cognitive load for contributors

- **Do not mix concurrency paradigms unnecessarily** (e.g., raw threads, executors, RxJava, coroutines) unless:
  - There is a strong architectural reason
  - The choice is documented clearly in code

- **UI Thread Safety**
  - Never block the main thread.
  - All long-running or IO-bound operations must be moved off the UI thread.
  - UI updates must occur on the main thread.

### Testing Criteria:

- Code should be well-covered by unit tests to ensure individual components work as expected.
- Tests should cover edge cases, error scenarios, and critical functionalities.
- Any feature work should be accompained by [corresponding instrumentation tests](https://developer.android.com/training/testing/instrumented-tests) in cases where we are not able to verify complete functionality with the help of unit tests.
- Any new unit testing classes should be written in Kotlin and utilize backticks to write test names as natural-language sentences (e.g. "\`ccc_generic_opportunity with session_endpoint_id routes to DispatchActivity\`").
     
### Documentation and Comments:

- Code should be adequately documented using appropriate comments, especially for complex algorithms, business logic, or non-obvious functionalities.
- Code should follow JavaDoc or KDoc standards for documenting classes, methods, and parameters.
- Comments should be used sparingly and effectively, providing insights into code logic or reasoning where necessary.
- Redundant or outdated comments should be avoided as that can lead to confusion.


In addition to above code standards, any contributions are required to follow [the due process to write a pull request](https://github.com/dimagi/open-source/blob/master/docs/Writing_PRs.md) and PR reviews are expected to follow [these norms](mobile_pr_review_norms.md)
