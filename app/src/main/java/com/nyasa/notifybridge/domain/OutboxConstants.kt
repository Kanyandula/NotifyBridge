package com.nyasa.notifybridge.domain

/** Per-row publish attempt cap. After this many consecutive failures, a
 *  row transitions to OutboxStatus.FAILED_TERMINAL and is skipped by
 *  future drain cycles. Spec §3.3. */
const val MAX_PUBLISH_ATTEMPTS: Int = 5
