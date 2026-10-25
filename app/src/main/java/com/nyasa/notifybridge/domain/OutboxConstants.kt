package com.nyasa.notifybridge.domain

/** Per-row publish attempt cap. After this many Rejected publishes, a row
 *  transitions to OutboxStatus.FAILED_TERMINAL and is skipped by future
 *  drain cycles. Transient failures (broker unreachable etc.) never count;
 *  see PublishOutcome. Spec §3.3, remediation K. */
const val MAX_PUBLISH_ATTEMPTS: Int = 5
