package org.fuchss.matrix.tacit.readmeshots

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Password of every demo user. */
internal const val DEMO_PASSWORD = "screenshots-demo-password"

internal data class DemoUsers(
    val tacit: MatrixSession,
    val tacit2: MatrixSession,
    val mira: MatrixSession,
    val ollie: MatrixSession,
    val pip: MatrixSession,
)

/** Registers the demo users (with display names and avatars) on a fresh homeserver. */
internal fun seedDemoUsers(http: MatrixHttp): DemoUsers {
    // The first registered user is made server admin and auto-joined to the admin room by tuwunel,
    // exactly like the original screenshots show.
    val tacit = http.register("tacit", DEMO_PASSWORD, "Tacit (macOS)")
    val tacit2 = http.register("tacit2", DEMO_PASSWORD, "Element (Firefox)")
    tacit.setAvatar(DemoAvatars.png("T", 0xFF2F80ED.toInt(), 0xFF56CCF2.toInt()))
    tacit2.setAvatar(DemoAvatars.png("T", 0xFFF2994A.toInt(), 0xFFF2C94C.toInt()))
    // A few more (invented) demo users so the DM home looks lived-in.
    val mira = http.register("mira", DEMO_PASSWORD, "Element (Android)")
        .apply { setDisplayName("Mira Moon"); setAvatar(DemoAvatars.png("M", 0xFF9B51E0.toInt(), 0xFFBB6BD9.toInt())) }
    val ollie = http.register("ollie", DEMO_PASSWORD, "Element (iOS)")
        .apply { setDisplayName("Ollie Orbit"); setAvatar(DemoAvatars.png("O", 0xFF27AE60.toInt(), 0xFF6FCF97.toInt())) }
    val pip = http.register("pip", DEMO_PASSWORD, "Element (Web)")
        .apply { setDisplayName("Pip Pixel"); setAvatar(DemoAvatars.png("P", 0xFFEB5757.toInt(), 0xFFF2994A.toInt())) }
    return DemoUsers(tacit, tacit2, mira, ollie, pip)
}

/**
 * Seeds the conversations of the README overview purely over the client-server API (no UI involved): two DMs, the
 * "Weekend Hike Planning" group chat with replies and reactions, and an "Adventure" guild (space) with a `#general`
 * room. `tacit` accepts all invitations, so everything is there when the client first syncs. Returns the hike room id.
 */
internal fun seedDemoConversations(users: DemoUsers): String {
    val (tacit, tacit2, mira, ollie, pip) = users
    val miraDm = mira.createChat(listOf(tacit.userId), direct = true)
    val ollieDm = ollie.createChat(listOf(tacit.userId), direct = true)
    val hike = mira.createChat(listOf(tacit.userId, ollie.userId, pip.userId), name = "Weekend Hike Planning")
    listOf(miraDm, ollieDm, hike).forEach { tacit.join(it) }
    ollie.join(hike)
    pip.join(hike)
    tacit.setAccountData(
        "m.direct",
        buildJsonObject {
            put(mira.userId, buildJsonArray { add(JsonPrimitive(miraDm)) })
            put(ollie.userId, buildJsonArray { add(JsonPrimitive(ollieDm)) })
        },
    )
    mira.setPresence("online")
    ollie.setPresence("online")

    mira.sendText(miraDm, "Did you see the new trail photos? 📸")
    ollie.sendText(ollieDm, "Thanks for the tips yesterday, that fixed it!")
    val kickoff = mira.sendText(hike, "Hey all 👋 who's in for the ridge trail on Saturday?")
    mira.sendText(hike, "Forecast says sunny all day ☀️")
    mira.sendText(hike, "I was thinking we start early and do the full loop")
    ollie.sendText(hike, "Count me in 🥾 I'll bring the map")
    pip.sendReply(hike, "Saturday works for me too!", replyTo = kickoff)
    mira.sendText(hike, "Great, 9 o'clock at the trailhead then? 🌄")
    tacit.sendText(hike, "Perfect, see you all there 😊")
    tacit.sendText(hike, "I'll bring snacks for everyone")
    val camera = tacit.sendText(hike, "And the good camera 📷")
    val perfect = tacit.findMessageEventId(hike, "see you all there")
    ollie.react(hike, perfect, "🎉")
    pip.react(hike, perfect, "🎉")
    mira.react(hike, perfect, "🥾")
    mira.react(hike, camera, "👍")

    // A DM from tacit2, so the second client of the demo has a conversation as well.
    val tacit2Dm = tacit2.createChat(listOf(tacit.userId), direct = true)
    tacit.join(tacit2Dm)
    tacit.setAccountData(
        "m.direct",
        buildJsonObject {
            put(mira.userId, buildJsonArray { add(JsonPrimitive(miraDm)) })
            put(ollie.userId, buildJsonArray { add(JsonPrimitive(ollieDm)) })
            put(tacit2.userId, buildJsonArray { add(JsonPrimitive(tacit2Dm)) })
        },
    )
    tacit2.setPresence("online")
    tacit2.sendText(tacit2Dm, "Howdy 👋")
    tacit2.sendText(tacit2Dm, "Just installed Tacit")
    tacit2.sendText(tacit2Dm, "Looks neat so far!")

    // The "Adventure" guild: a space with a #general room, tacit2 joined as well.
    val guild = tacit.createRoom(
        buildJsonObject {
            put("name", "Adventure")
            put("topic", "This is my guild for adventurers :)")
            put("preset", "private_chat")
            put("creation_content", buildJsonObject { put("type", "m.space") })
            put("invite", buildJsonArray { add(JsonPrimitive(tacit2.userId)) })
        },
    )
    val general = tacit.createRoom(
        buildJsonObject {
            put("name", "general")
            put("topic", "General discussion")
            put("preset", "private_chat")
            put("invite", buildJsonArray { add(JsonPrimitive(tacit2.userId)) })
        },
    )
    tacit.sendState(guild, "m.space.child", general, buildJsonObject { put("via", buildJsonArray { add(JsonPrimitive(SERVER_NAME)) }) })
    tacit.sendState(general, "m.space.parent", guild, buildJsonObject {
        put("via", buildJsonArray { add(JsonPrimitive(SERVER_NAME)) })
        put("canonical", true)
    })
    tacit2.join(guild)
    tacit2.join(general)
    tacit.sendText(general, "Welcome to the Adventure guild!")
    tacit2.sendText(general, "Glad to be here 🏕️")
    return hike
}
