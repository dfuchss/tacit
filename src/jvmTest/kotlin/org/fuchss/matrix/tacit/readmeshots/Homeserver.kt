package org.fuchss.matrix.tacit.readmeshots

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName

/** Update from time to time: https://github.com/matrix-construct/tuwunel/releases */
internal const val TUWUNEL_VERSION = "v1.9.3"

/**
 * The homeserver name the screenshots were originally taken against. Federation is disabled, so the name is purely
 * cosmetic (it only shows up in user and room ids such as `@tacit:localhost:8008`).
 */
internal const val SERVER_NAME = "localhost:8008"

/** A throwaway tuwunel homeserver with open registration and a user directory that lists all local users. */
internal fun tuwunelDocker(): GenericContainer<Nothing> =
    GenericContainer<Nothing>(DockerImageName.parse("ghcr.io/matrix-construct/tuwunel:$TUWUNEL_VERSION")).apply {
        withEnv(
            mapOf(
                "TUWUNEL_SERVER_NAME" to SERVER_NAME,
                "TUWUNEL_ADDRESS" to "[\"0.0.0.0\"]", // default is loopback-only => unreachable via published port
                "TUWUNEL_ALLOW_REGISTRATION" to "true",
                "TUWUNEL_YES_I_AM_VERY_VERY_SURE_I_WANT_AN_OPEN_REGISTRATION_SERVER_PRONE_TO_ABUSE" to "true",
                "TUWUNEL_ALLOW_FEDERATION" to "false",
                "TUWUNEL_SHOW_ALL_LOCAL_USERS_IN_USER_DIRECTORY" to "true", // otherwise user search returns nothing
                "TUWUNEL_NEW_USER_DISPLAYNAME_SUFFIX" to "", // default appends " 💕" to every display name
                "TUWUNEL_LOG" to "warn",
            )
        )
        withExposedPorts(8008)
        waitingFor(Wait.forHttp("/_matrix/client/versions").forStatusCode(200))
        withNetwork(Network.SHARED)
    }

internal fun GenericContainer<*>.clientServerUrl(): String = "http://${host}:${getMappedPort(8008)}"
