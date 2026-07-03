package com.nyasa.notifybridge.data.db

/** Spec §3.2 — outbox row state machine. Stored as String (the enum [.name])
 *  to avoid adding a TypeConverter for a single field. */
enum class OutboxStatus { PENDING, FAILED_TERMINAL }

/** Convenience accessor; reads [OutboxEntity.status] (String) as the enum. */
val OutboxEntity.statusEnum: OutboxStatus
    get() = OutboxStatus.valueOf(status)
