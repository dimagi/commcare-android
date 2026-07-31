package org.commcare.activities

import org.robolectric.annotation.Implements

/**
 * Marker class plus an empty Robolectric shadow. Referenced only from the home-screen test base's
 * `@Config(shadows = [...])` so those tests run in their OWN Robolectric sandbox (classloader),
 * isolated from other suites that share the default configuration.
 *
 * Why: the home tests boot the full activity + application (Firebase/Play-Services init) and do
 * heavy inline static/object mocking on every test. That instrumentation churn accumulates and, in
 * the single shared-sandbox JVM, corrupts Google-Play-Services state for later tests that call the
 * real `GoogleApiAvailability` (e.g. the PersonalId fragment tests via `PersonalIdPhoneFragment`).
 * Adding a shadow that nothing else uses changes the sandbox's instrumentation config, so the home
 * tests get a separate classloader and their churn stays contained. The shadow overrides no
 * behaviour.
 */
class HomeTestSandbox

@Implements(HomeTestSandbox::class)
class ShadowHomeTestSandbox
