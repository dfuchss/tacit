package org.fuchss.matrix.tacit.tray

import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.Tuple
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.DBusMemberName
import org.freedesktop.dbus.annotations.Position
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant

/*
 * D-Bus interfaces of the Linux tray: the KDE/freedesktop StatusNotifierItem spec
 * (https://www.freedesktop.org/wiki/Specifications/StatusNotifierItem/), libdbusmenu's com.canonical.dbusmenu
 * (https://github.com/AyatanaIndicators/libdbusmenu/blob/master/libdbusmenu-glib/dbus-menu.xml) and the
 * org.freedesktop.Notifications call used for update notifications. Only the members Tacit uses are declared.
 */

/** The item Tacit exports; its properties are served through `org.freedesktop.DBus.Properties`. */
@DBusInterfaceName("org.kde.StatusNotifierItem")
internal interface StatusNotifierItemInterface : DBusInterface {
    /** Primary (left) click. */
    @DBusMemberName("Activate")
    fun activate(x: Int, y: Int)

    /** Middle click. */
    @DBusMemberName("SecondaryActivate")
    fun secondaryActivate(x: Int, y: Int)

    /** Only called by hosts that ignore the `Menu` property; the dbusmenu serves the context menu. */
    @DBusMemberName("ContextMenu")
    fun contextMenu(x: Int, y: Int)

    @DBusMemberName("Scroll")
    fun scroll(delta: Int, orientation: String)

    class NewIcon(path: String) : DBusSignal(path)

    class NewToolTip(path: String) : DBusSignal(path)
}

@DBusInterfaceName("org.kde.StatusNotifierWatcher")
internal interface StatusNotifierWatcherInterface : DBusInterface {
    @DBusMemberName("RegisterStatusNotifierItem")
    fun registerStatusNotifierItem(service: String)
}

/** The context menu of the item. Item ids are those of [TacitDbusMenu]; `0` is the root. */
@DBusInterfaceName("com.canonical.dbusmenu")
internal interface DbusMenuInterface : DBusInterface {
    @DBusMemberName("GetLayout")
    fun getLayout(parentId: Int, recursionDepth: Int, propertyNames: List<String>): DbusMenuLayoutResult

    @DBusMemberName("GetGroupProperties")
    fun getGroupProperties(ids: List<Int>, propertyNames: List<String>): List<DbusMenuItemProperties>

    @DBusMemberName("GetProperty")
    fun getProperty(id: Int, name: String): Variant<*>

    @DBusMemberName("Event")
    fun event(id: Int, eventId: String, data: Variant<*>, timestamp: UInt32)

    /** Returns the ids that were not found. */
    @DBusMemberName("EventGroup")
    fun eventGroup(events: List<DbusMenuEvent>): List<Int>

    /** Returns whether the layout has to be fetched again; Tacit always announces changes with `LayoutUpdated`. */
    @DBusMemberName("AboutToShow")
    fun aboutToShow(id: Int): Boolean

    @DBusMemberName("AboutToShowGroup")
    fun aboutToShowGroup(ids: List<Int>): DbusMenuAboutToShowGroupResult

    class LayoutUpdated(path: String, val revision: UInt32, val parent: Int) : DBusSignal(path, revision, parent)
}

@DBusInterfaceName("org.freedesktop.Notifications")
internal interface NotificationsInterface : DBusInterface {
    @Suppress("LongParameterList")
    @DBusMemberName("Notify")
    fun notify(
        appName: String,
        replacesId: UInt32,
        appIcon: String,
        summary: String,
        body: String,
        actions: List<String>,
        hints: Map<String, @JvmSuppressWildcards Variant<*>>,
        expireTimeout: Int,
    ): UInt32
}

/** `(iiay)`: one icon size, ARGB32 in network byte order (see [argb32NetworkOrder]). */
internal class SniPixmap(
    @field:Position(0) @JvmField val width: Int,
    @field:Position(1) @JvmField val height: Int,
    @field:Position(2) @JvmField val data: ByteArray,
) : Struct()

/** `(sa(iiay)ss)`: icon name, icon pixmaps, title, description. */
internal class SniToolTip(
    @field:Position(0) @JvmField val iconName: String,
    @field:Position(1) @JvmField val iconPixmaps: List<SniPixmap>,
    @field:Position(2) @JvmField val title: String,
    @field:Position(3) @JvmField val description: String,
) : Struct()

/** `(ia{sv}av)`: a menu item, its properties and its children (each a variant holding this struct again). */
internal class DbusMenuLayoutItem(
    @field:Position(0) @JvmField val id: Int,
    @field:Position(1) @JvmField val properties: Map<String, Variant<*>>,
    @field:Position(2) @JvmField val children: List<Variant<*>>,
) : Struct()

/**
 * A reply with two out arguments. dbus-java derives their signature from the type arguments of the declared return
 * type, so a tuple has to be generic; a plain class with typed fields cannot be exported.
 */
internal class DbusReplyPair<A, B>(
    @field:Position(0) @JvmField val first: A,
    @field:Position(1) @JvmField val second: B,
) : Tuple()

/** Reply of `GetLayout`: `u (ia{sv}av)`, the layout revision and the layout. */
internal typealias DbusMenuLayoutResult = DbusReplyPair<UInt32, DbusMenuLayoutItem>

/** `(ia{sv})` of `GetGroupProperties`. */
internal class DbusMenuItemProperties(
    @field:Position(0) @JvmField val id: Int,
    @field:Position(1) @JvmField val properties: Map<String, Variant<*>>,
) : Struct()

/** `(isvu)` of `EventGroup`. */
internal class DbusMenuEvent(
    @field:Position(0) @JvmField val id: Int,
    @field:Position(1) @JvmField val eventId: String,
    @field:Position(2) @JvmField val data: Variant<*>,
    @field:Position(3) @JvmField val timestamp: UInt32,
) : Struct()

/** Reply of `AboutToShowGroup`: `ai ai` (ids needing an update, ids not found). */
internal typealias DbusMenuAboutToShowGroupResult = DbusReplyPair<List<Int>, List<Int>>

internal const val SNI_ITEM_PATH = "/StatusNotifierItem"
internal const val SNI_MENU_PATH = "/MenuBar"
internal val sniMenuDbusPath = DBusPath(SNI_MENU_PATH)
