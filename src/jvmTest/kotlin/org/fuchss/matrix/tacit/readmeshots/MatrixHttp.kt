package org.fuchss.matrix.tacit.readmeshots

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

/**
 * A deliberately tiny Matrix client-server API client (JDK HttpClient + kotlinx JSON) used to seed the homeserver
 * with the demo users/rooms the screenshots show. Everything the logged-in Tacit user does goes through the real
 * app instead; this is only for the "other" user and for registration.
 */
internal class MatrixHttp(private val baseUrl: String) {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    private val json = Json { ignoreUnknownKeys = true }

    fun call(
        method: String,
        path: String,
        body: JsonObject? = null,
        token: String? = null,
        expect: Set<Int> = setOf(200),
    ): JsonObject {
        val request = HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .method(
                method,
                body?.let { HttpRequest.BodyPublishers.ofString(it.toString()) } ?: HttpRequest.BodyPublishers.noBody(),
            )
            .build()
        return send(request, "$method $path", expect)
    }

    fun upload(bytes: ByteArray, contentType: String, fileName: String, token: String): String {
        val request = HttpRequest.newBuilder(URI.create("$baseUrl/_matrix/media/v3/upload?filename=${encodePath(fileName)}"))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", contentType)
            .header("Authorization", "Bearer $token")
            .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
            .build()
        return send(request, "POST /_matrix/media/v3/upload", setOf(200)).getValue("content_uri").jsonPrimitive.content
    }

    private fun send(request: HttpRequest, description: String, expect: Set<Int>): JsonObject {
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in expect) { "$description -> HTTP ${response.statusCode()}: ${response.body()}" }
        return runCatching { json.parseToJsonElement(response.body()).jsonObject }.getOrDefault(JsonObject(emptyMap()))
    }

    /** Registers via the standard UIA `m.login.dummy` flow and returns the logged-in session. */
    fun register(username: String, password: String, deviceName: String): MatrixSession {
        val base = buildJsonObject {
            put("username", username)
            put("password", password)
            put("initial_device_display_name", deviceName)
            put("inhibit_login", false)
        }
        val first = call("POST", "/_matrix/client/v3/register", base, expect = setOf(200, 401))
        val result = if (first.containsKey("access_token")) first else {
            val auth = buildJsonObject {
                put("type", "m.login.dummy")
                first["session"]?.jsonPrimitive?.content?.let { put("session", it) }
            }
            call("POST", "/_matrix/client/v3/register", JsonObject(base + ("auth" to auth)))
        }
        return MatrixSession(
            http = this,
            userId = result.getValue("user_id").jsonPrimitive.content,
            accessToken = result.getValue("access_token").jsonPrimitive.content,
        )
    }
}

internal fun encodePath(segment: String): String = URLEncoder.encode(segment, Charsets.UTF_8).replace("+", "%20")

internal class MatrixSession(private val http: MatrixHttp, val userId: String, val accessToken: String) {
    private fun call(method: String, path: String, body: JsonObject? = null, expect: Set<Int> = setOf(200)) =
        http.call(method, path, body, accessToken, expect)

    fun createRoom(body: JsonObject): String =
        call("POST", "/_matrix/client/v3/createRoom", body).getValue("room_id").jsonPrimitive.content

    /** Joins a room. Retries for a while because invites/space memberships propagate asynchronously. */
    fun join(roomId: String, attempts: Int = 40): Unit = repeat(attempts) { attempt ->
        val result = runCatching {
            call("POST", "/_matrix/client/v3/rooms/${encodePath(roomId)}/join", buildJsonObject {})
        }
        if (result.isSuccess) return
        if (attempt == attempts - 1) result.getOrThrow()
        Thread.sleep(500)
    }

    fun sendMessage(roomId: String, content: JsonObject): String =
        call(
            "PUT",
            "/_matrix/client/v3/rooms/${encodePath(roomId)}/send/m.room.message/${UUID.randomUUID()}",
            content,
        ).getValue("event_id").jsonPrimitive.content

    fun sendText(roomId: String, body: String): String =
        sendMessage(roomId, buildJsonObject { put("msgtype", "m.text"); put("body", body) })

    fun setDisplayName(name: String) {
        call("PUT", "/_matrix/client/v3/profile/${encodePath(userId)}/displayname", buildJsonObject { put("displayname", name) })
    }

    /** A private room (DM when [direct]) created by this user with [invitees] invited. */
    fun createChat(invitees: List<String>, name: String? = null, direct: Boolean = false): String = createRoom(
        buildJsonObject {
            if (name != null) put("name", name)
            put("is_direct", direct)
            put("preset", if (direct) "trusted_private_chat" else "private_chat")
            put("invite", buildJsonArray { invitees.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
        }
    )

    /** Sends a text message that replies to [replyTo]. */
    fun sendReply(roomId: String, body: String, replyTo: String): String = sendMessage(
        roomId,
        buildJsonObject {
            put("msgtype", "m.text")
            put("body", body)
            put("m.relates_to", buildJsonObject { put("m.in_reply_to", buildJsonObject { put("event_id", replyTo) }) })
        },
    )

    /** Uploads [png] and sets it as the user's profile avatar; returns the `mxc://` uri. */
    fun setAvatar(png: ByteArray): String {
        val mxc = http.upload(png, "image/png", "avatar.png", accessToken)
        call("PUT", "/_matrix/client/v3/profile/${encodePath(userId)}/avatar_url", buildJsonObject { put("avatar_url", mxc) })
        return mxc
    }

    fun setPresence(presence: String) {
        call("PUT", "/_matrix/client/v3/presence/${encodePath(userId)}/status", buildJsonObject { put("presence", presence) })
    }

    fun setAccountData(type: String, content: JsonObject) {
        call("PUT", "/_matrix/client/v3/user/${encodePath(userId)}/account_data/${encodePath(type)}", content)
    }

    fun markDirect(otherUserId: String, roomId: String) {
        setAccountData("m.direct", buildJsonObject { put(otherUserId, buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(roomId)) }) })
    }

    /** Room ids of the children of a space (via the space hierarchy endpoint). */
    fun spaceChildren(spaceId: String): List<String> {
        val rooms = call("GET", "/_matrix/client/v1/rooms/${encodePath(spaceId)}/hierarchy")["rooms"]?.jsonArray
            ?: JsonArray(emptyList())
        return rooms.map { it.jsonObject.getValue("room_id").jsonPrimitive.content }.filter { it != spaceId }
    }
}
