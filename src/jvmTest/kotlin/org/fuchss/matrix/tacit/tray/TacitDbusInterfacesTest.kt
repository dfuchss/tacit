package org.fuchss.matrix.tacit.tray

import org.freedesktop.dbus.Marshalling
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.messages.ExportedObject
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * dbus-java only checks at export time whether it can marshal an interface (a non-generic tuple, for one, fails), and
 * Tacit then silently falls back to the AWT tray. Building the introspection data here catches that without a bus.
 */
class TacitDbusInterfacesTest {
    private fun introspect(type: Class<out DBusInterface>): String {
        val stub = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
            if (method.name == "getObjectPath") "/test" else null
        } as DBusInterface
        return ExportedObject(stub, false).introspectiondata
    }

    @Test
    fun statusNotifierItemIsExportable() {
        val xml = introspect(StatusNotifierItemInterface::class.java)
        assertTrue("org.kde.StatusNotifierItem" in xml, xml)
        assertTrue("name=\"Activate\"" in xml, xml)
        assertTrue("name=\"NewIcon\"" in xml, xml)
    }

    @Test
    fun dbusmenuHasTheSpecSignatures() {
        val xml = introspect(DbusMenuInterface::class.java)
        assertTrue("com.canonical.dbusmenu" in xml, xml)
        assertTrue("type=\"(ia{sv}av)\"" in xml, xml)
        assertTrue("type=\"a(ia{sv})\"" in xml, xml)
        assertTrue("type=\"a(isvu)\"" in xml, xml)
        assertTrue("name=\"LayoutUpdated\"" in xml, xml)
    }

    @Test
    fun notifyHasTheSpecSignature() {
        val notify = NotificationsInterface::class.java.methods.single { it.name == "notify" }
        val signature = notify.genericParameterTypes.joinToString("") { Marshalling.getDBusType(it).joinToString("") }
        assertEquals("susssasa{sv}i", signature)
        assertEquals("u", Marshalling.getDBusType(notify.genericReturnType).joinToString(""))
    }
}
