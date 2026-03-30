package org.commcare.personalId

import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalIdFeatureFlagCheckerTest {
    @Test
    fun `DATA_REFRESH_INDICATOR flag is enabled`() {
        assertTrue(
            PersonalIdFeatureFlagChecker.isFeatureEnabled(
                PersonalIdFeatureFlagChecker.FeatureFlag.DATA_REFRESH_INDICATOR,
            ),
        )
    }
}
