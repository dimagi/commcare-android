package org.commcare.personalId

import org.junit.Assert.assertFalse
import org.junit.Test

class PersonalIdFeatureFlagCheckerTest {
    @Test
    fun `DATA_REFRESH_INDICATOR flag is enabled`() {
        assertFalse(
            PersonalIdFeatureFlagChecker.isFeatureEnabled(
                PersonalIdFeatureFlagChecker.FeatureFlag.DATA_REFRESH_INDICATOR,
            ),
        )
    }
}
