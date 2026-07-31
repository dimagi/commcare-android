package org.commcare.android.tests.personalid

import org.robolectric.annotation.Implements

/**
 * Marker class plus an empty Robolectric shadow. Referenced only from
 * [PersonalIdDrawerVisibilityTest]'s `@Config(shadows = [...])` so that test runs in its OWN
 * Robolectric sandbox (classloader), isolated from the default configuration the rest of the suite
 * shares. The shadow overrides no behaviour.
 *
 * Why: that test boots three full activities (setup, login, home) and static-mocks five Connect
 * classes on every one of its twelve tests. In the single shared-sandbox JVM that instrumentation
 * churn accumulates and corrupts Google-Play-Services state for later tests that call the real
 * `GoogleApiAvailability`. Adding a shadow nothing else uses changes the sandbox's instrumentation
 * config, which earns the test a separate classloader and keeps its churn contained.
 *
 * This is deliberately a *separate* marker from `org.commcare.activities.HomeTestSandbox` rather
 * than a shared one: both suites do heavy Connect instrumentation, and giving each its own sandbox
 * keeps them from polluting each other as well as the default sandbox.
 */
class PersonalIdTestSandbox

@Implements(PersonalIdTestSandbox::class)
class ShadowPersonalIdTestSandbox
