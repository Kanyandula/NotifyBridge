package com.nyasa.notifybridge.data.recent

import android.util.Log
import com.nyasa.notifybridge.data.db.RecentNotificationDao
import com.nyasa.notifybridge.data.db.RecentNotificationEntity
import com.nyasa.notifybridge.domain.model.OutboxItem
import com.nyasa.notifybridge.domain.model.RecentItem
import com.nyasa.notifybridge.domain.repo.RecentNotificationsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecentNotificationsRepositoryImpl @Inject constructor(
    private val dao: RecentNotificationDao,
) : RecentNotificationsRepository {
    override val recent: Flow<List<RecentItem>> =
        dao.observeNewest(RecentNotificationsRepository.MAX_ITEMS)
            .map { rows -> rows.map { it.toRecentItem() } }

    override suspend fun recordPublished(item: OutboxItem, publishedAt: Long) {
        val entity = item.toRecentEntity(publishedAt) ?: run {
            // runCatching guards pure-JVM unit tests (no android.util.Log).
            runCatching { Log.w(LOG_TAG, "recent payload unparseable; skipping recordPublished") }
            return
        }
        dao.insertAndTrim(entity, RecentNotificationsRepository.MAX_ITEMS)
    }

    private fun OutboxItem.toRecentEntity(publishedAt: Long): RecentNotificationEntity? =
        runCatching {
            val payloadJson = json.parseToJsonElement(payload).jsonObject
            RecentNotificationEntity(
                packageName = payloadJson[PACKAGE_KEY]?.jsonPrimitive?.contentOrNull.orEmpty(),
                app = payloadJson[APP_KEY]?.jsonPrimitive?.contentOrNull.orEmpty(),
                title = payloadJson[TITLE_KEY]?.jsonPrimitive?.contentOrNull.orEmpty(),
                body = payloadJson[TEXT_KEY]?.jsonPrimitive?.contentOrNull.orEmpty(),
                postTime = payloadJson[POST_TIME_KEY]?.jsonPrimitive?.longOrNull ?: createdAt,
                publishedAt = publishedAt,
            )
        }.getOrNull()

    private fun RecentNotificationEntity.toRecentItem() =
        RecentItem(
            id = id,
            packageName = packageName,
            app = app,
            title = title,
            body = body,
            postTime = postTime,
        )

    private companion object {
        const val LOG_TAG = "RecentNotificationsRepo"
        const val PACKAGE_KEY = "package"
        const val APP_KEY = "app"
        const val TITLE_KEY = "title"
        const val TEXT_KEY = "text"
        const val POST_TIME_KEY = "post_time"

        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}
