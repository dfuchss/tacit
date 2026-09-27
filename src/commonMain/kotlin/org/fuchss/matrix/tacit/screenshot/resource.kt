package org.fuchss.matrix.tacit.screenshot

expect object PlatformResource {
    fun resource(path: String): ByteArray?
}