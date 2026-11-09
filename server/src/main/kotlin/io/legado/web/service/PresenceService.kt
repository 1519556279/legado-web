package io.legado.web.service

import java.util.concurrent.ConcurrentHashMap

/** 一个在线用户的状态 */
data class PresenceEntry(
    val userId: Long,
    var username: String,
    var state: String = "idle",
    var bookName: String = "",
    var chapter: String = "",
    var lastSeen: Long = System.currentTimeMillis(),
)

/** 返回给管理员的在线条目 */
data class PresenceView(
    val userId: Long,
    val username: String,
    val state: String,
    val bookName: String,
    val chapter: String,
    val lastSeen: Long,
)

/**
 * 在线状态：客户端每 30 秒心跳一次（阅读页、朗读时上报状态和书名章节）。
 * 超过 2 分钟没有心跳就从在线列表里消失；只保存在内存，服务重启后清空。
 */
class PresenceService {
    private val entries = ConcurrentHashMap<Long, PresenceEntry>()

    fun update(userId: Long, username: String, state: String, bookName: String?, chapter: String?) {
        val now = System.currentTimeMillis()
        entries.compute(userId) { _, old ->
            (old ?: PresenceEntry(userId, username)).apply {
                this.username = username
                this.state = when (state) {
                    "reading", "listening" -> state
                    else -> "idle"
                }
                this.bookName = bookName.orEmpty().take(120)
                this.chapter = chapter.orEmpty().take(120)
                this.lastSeen = now
            }
        }
        if (entries.size > 500) cleanup(now)
    }

    fun remove(userId: Long) {
        entries.remove(userId)
    }

    fun online(): List<PresenceView> {
        val now = System.currentTimeMillis()
        cleanup(now)
        return entries.values
            .filter { now - it.lastSeen <= ONLINE_WINDOW_MS }
            .sortedByDescending { it.lastSeen }
            .map { PresenceView(it.userId, it.username, it.state, it.bookName, it.chapter, it.lastSeen) }
    }

    private fun cleanup(now: Long) {
        entries.entries.removeIf { now - it.value.lastSeen > ONLINE_WINDOW_MS }
    }

    companion object {
        /** 最近 2 分钟有心跳就算在线 */
        const val ONLINE_WINDOW_MS = 120_000L
    }
}
