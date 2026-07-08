package org.commcare.fragments.personalId

/**
 * PersonalID work flow
 *
 *  - [CONFIGURATION]: the PersonalID sign-up / account recovery -  configuration flow
 *  - [USER_PROMPT]: the user is acting on a prompt surfaced outside the config flow,
 *    e.g. the email-collection prompt shown to a logged-in user from
 *    {@code StandardHomeActivity} (EmailOfferHelper.checkEmailCollection).
 *  - [EDIT_PROFILE]: reserved for the future edit-profile feature.
 */
enum class PersonalIdWorkflow {
    CONFIGURATION,
    USER_PROMPT,
    EDIT_PROFILE,
    ;

    /** Stable lowercase string emitted to analytics ("configuration", "user_prompt", "edit_profile"). */
    val analyticsValue: String get() = name.lowercase()

    companion object {
        @JvmStatic
        fun fromEmailWorkFlow(flow: EmailWorkFlow): PersonalIdWorkflow =
            when (flow) {
                EmailWorkFlow.EXISTING_USER -> USER_PROMPT
                EmailWorkFlow.REGISTRATION, EmailWorkFlow.RECOVERY -> CONFIGURATION
            }
    }
}
