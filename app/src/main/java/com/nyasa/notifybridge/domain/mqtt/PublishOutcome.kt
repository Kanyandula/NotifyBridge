package com.nyasa.notifybridge.domain.mqtt

sealed interface PublishOutcome {
    data object Success : PublishOutcome

    /** Transient: never counts toward MAX_PUBLISH_ATTEMPTS. */
    data object Transient : PublishOutcome

    /** Counts toward the cap (deterministic or unknown failure). */
    data object Rejected : PublishOutcome
}
