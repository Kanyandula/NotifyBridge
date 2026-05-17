package com.nyasa.notifybridge.domain.usecase

import com.nyasa.notifybridge.domain.discovery.DiscoveryPayloadBuilder
import com.nyasa.notifybridge.domain.model.CapturedNotification
import com.nyasa.notifybridge.domain.model.OutboxItem
import com.nyasa.notifybridge.domain.repo.OutboxRepository
import javax.inject.Inject

class EnqueueNotificationUseCase @Inject constructor(
    private val outbox: OutboxRepository,
    private val discovery: DiscoveryPayloadBuilder,
) {
    suspend operator fun invoke(n: CapturedNotification, device: String) {
        outbox.enqueue(OutboxItem(
            topic = discovery.stateTopic(device),
            payload = discovery.eventPayload(n),
            createdAt = n.postTime))
    }
}
