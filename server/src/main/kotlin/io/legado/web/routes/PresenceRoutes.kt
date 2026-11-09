package io.legado.web.routes

import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.legado.web.auth.AUTH_JWT
import io.legado.web.auth.requireAdmin
import io.legado.web.auth.user
import io.legado.web.db.ActivityDaily
import io.legado.web.db.Users
import io.legado.web.db.dbQuery
import io.legado.web.service.PresenceService
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Serializable
data class PresenceReport(
    /** reading / listening / idle */
    val state: String = "idle",
    val bookName: String = "",
    val chapter: String = "",
    /** 距离上次心跳的秒数（增量，最多按 120 秒计） */
    val seconds: Int = 0,
    /** 用户本地日期 yyyy-MM-dd */
    val day: String = "",
)

@Serializable
data class PresenceUser(
    val userId: Long,
    val username: String,
    val state: String,
    val bookName: String,
    val chapter: String,
    val lastSeen: Long,
)

@Serializable
data class PresenceSummary(
    val online: Int,
    val reading: Int,
    val listening: Int,
    val idle: Int,
    val users: List<PresenceUser>,
)

@Serializable
data class DayActivity(
    val day: String,
    val registered: Int,
    val activeUsers: Int,
    val readingSeconds: Long,
    val listeningSeconds: Long,
)

@Serializable
data class ActivitySummary(
    val totalUsers: Int,
    val days: List<DayActivity>,
)

private val DAY_PATTERN = Regex("\\d{4}-\\d{2}-\\d{2}")

/**
 * 在线状态与活跃统计
 * - POST /api/presence                  客户端心跳：{ state, bookName, chapter, seconds, day }
 * - GET  /api/admin/presence            管理员：在线人数、阅读/听书/空闲人数、在线用户列表
 * - GET  /api/admin/activity?days=7     管理员：最近 N 天注册数、活跃人数、阅读/听书时长
 */
fun Route.presenceRoutes(presenceService: PresenceService) {
    authenticate(AUTH_JWT) {
        post("/api/presence") {
            val u = call.user
            val req = call.receive<PresenceReport>()
            val state = when (req.state) {
                "reading", "listening" -> req.state
                else -> "idle"
            }
            presenceService.update(u.id, u.username, state, req.bookName, req.chapter)

            val seconds = req.seconds.coerceIn(0, 120)
            if (seconds > 0 && DAY_PATTERN.matches(req.day)) {
                val reading = if (state == "reading") seconds.toLong() else 0L
                val listening = if (state == "listening") seconds.toLong() else 0L
                if (reading > 0 || listening > 0) {
                    dbQuery {
                        val where = (ActivityDaily.userId eq u.id) and (ActivityDaily.day eq req.day)
                        val updated = ActivityDaily.update({ where }) {
                            it[ActivityDaily.readingSeconds] = ActivityDaily.readingSeconds + reading
                            it[ActivityDaily.listeningSeconds] = ActivityDaily.listeningSeconds + listening
                        }
                        if (updated == 0) {
                            ActivityDaily.insert {
                                it[userId] = u.id
                                it[day] = req.day
                                it[readingSeconds] = reading
                                it[listeningSeconds] = listening
                            }
                        }
                    }
                }
            }
            call.respond(OkResponse())
        }

        route("/api/admin") {
            get("/presence") {
                call.requireAdmin()
                val users = presenceService.online()
                call.respond(
                    PresenceSummary(
                        online = users.size,
                        reading = users.count { it.state == "reading" },
                        listening = users.count { it.state == "listening" },
                        idle = users.count { it.state == "idle" },
                        users = users.map {
                            PresenceUser(it.userId, it.username, it.state, it.bookName, it.chapter, it.lastSeen)
                        },
                    )
                )
            }

            get("/activity") {
                call.requireAdmin()
                val days = call.request.queryParameters["days"]?.toIntOrNull()?.coerceIn(1, 90) ?: 7
                val today = LocalDate.now()
                val from = today.minusDays((days - 1).toLong()).toString()

                val totalUsers = dbQuery { Users.selectAll().toList().size }
                val registeredByDay = dbQuery { Users.selectAll().toList() }
                    .groupBy { epochDay(it[Users.createdAt]) }
                    .mapValues { it.value.size }
                val activityByDay = dbQuery { ActivityDaily.selectAll().toList() }
                    .filter { it[ActivityDaily.day] >= from }
                    .groupBy { it[ActivityDaily.day] }

                val list = (0 until days).map { offset ->
                    val day = today.minusDays((days - 1 - offset).toLong()).toString()
                    val rows = activityByDay[day].orEmpty()
                    DayActivity(
                        day = day,
                        registered = registeredByDay[day] ?: 0,
                        activeUsers = rows.map { it[ActivityDaily.userId] }.distinct().size,
                        readingSeconds = rows.sumOf { it[ActivityDaily.readingSeconds] },
                        listeningSeconds = rows.sumOf { it[ActivityDaily.listeningSeconds] },
                    )
                }
                call.respond(ActivitySummary(totalUsers, list))
            }
        }
    }
}

private fun epochDay(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString()
