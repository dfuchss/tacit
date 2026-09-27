package org.fuchss.matrix.tacit.screenshot

actual object PlatformResource {
    actual fun resource(path: String): ByteArray? = object {}.javaClass.classLoader?.getResource(path)?.readBytes()
}