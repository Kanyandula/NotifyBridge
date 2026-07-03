package com.nyasa.notifybridge.data.db

/** Spec §3.2 — outbox row state machine. Stored as String (the enum [.name])
 *  to avoid adding a TypeConverter for a single field. */
enum class OutboxStatus { PENDING, FAILED_TERMINAL }
