package org.commcare.connect.network.connect

object ConnectApiEndpoints {
    const val OPPORTUNITIES_URL = "/api/opportunity/"
    const val START_LEARNING_URL = "/users/start_learn_app/"
    const val LEARN_PROGRESS_URL = "/api/opportunity/{id}/learn_progress"
    const val CLAIM_JOB_URL = "/api/opportunity/{id}/claim"
    const val DELIVERIES_URL = "/api/opportunity/{id}/delivery_progress"
    const val PAYMENT_CONFIRMAITONS = "/api/payment/confirm"
}
