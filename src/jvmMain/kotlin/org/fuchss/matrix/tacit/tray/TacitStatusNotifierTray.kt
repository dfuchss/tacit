package org.fuchss.matrix.tacit.tray

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.lognity.api.logger.error
import de.connect2x.lognity.api.logger.info
import de.connect2x.lognity.api.logger.warn
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.errors.InvalidMethodArgument
import org.freedesktop.dbus.errors.PropertyReadOnly
import org.freedesktop.dbus.errors.UnknownInterface
import org.freedesktop.dbus.errors.UnknownProperty
import org.freedesktop.dbus.interfaces.DBus
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import java.awt.image.BufferedImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities

private val log: Logger = Logger("org.fuchss.matrix.tacit.tray.TacitStatusNotifierTray")

/**
 * The Linux tray as a StatusNotifierItem with a dbusmenu context menu on the session bus.
 *
 * AWT's `SystemTray` (and with it Compose's `Tray`) speaks XEmbed on Linux. Plasma, GNOME's AppIndicator extension and
 * most other current panels only host StatusNotifierItems and wrap XEmbed icons through a proxy that draws them on a
 * white square and does not forward clicks for the menu, so Tacit registers a StatusNotifierItem itself.
 *
 * The item and the menu are exported once by [connectOrNull]; [show] makes them visible by taking a fresh well-known
 * name (`org.kde.StatusNotifierItem-<pid>-<n>`) and registering it with the watcher, [hide] releases that name, which
 * removes the item from every host. [close] hides the item and closes the connection.
 *
 * Threading: the setters are called from the UI thread and only swap immutable snapshots; all outgoing D-Bus traffic
 * runs on one background thread in call order, so the UI thread never waits for the bus. Incoming calls arrive on
 * dbus-java's worker threads and hand click handlers to the AWT event thread.
 */
internal class TacitStatusNotifierTray private constructor(
    private val connection: DBusConnection,
    private val id: String,
    private val title: String,
) : AutoCloseable {
    private val busNames = AtomicInteger()
    private val outgoing: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "tacit-tray-dbus").apply { isDaemon = true }
    }

    @Volatile
    private var pixmaps: List<SniPixmap> = emptyList()

    @Volatile
    private var menu = TacitDbusMenu(emptyList(), revision = 1)

    @Volatile
    private var onActivate: () -> Unit = {}

    /** The well-known name while the item is shown; only touched on [outgoing]. */
    private var busName: String? = null

    private val item = Item()
    private val menuObject = Menu()

    private fun start() {
        connection.exportObject(SNI_ITEM_PATH, item)
        connection.exportObject(SNI_MENU_PATH, menuObject)
        // A restarted panel (e.g. plasmashell) brings a new watcher that does not know the item yet.
        connection.addSigHandler(DBus.NameOwnerChanged::class.java) { signal ->
            if (signal.name == WATCHER_NAME && signal.newOwner.isNotEmpty()) {
                submit("re-register with the new watcher") { busName?.let(::register) }
            }
        }
    }

    /** Shows the item in the panel. */
    fun show() = submit("show the tray item") {
        if (busName != null) return@submit
        val name = "$ITEM_NAME_PREFIX-${ProcessHandle.current().pid()}-${busNames.incrementAndGet()}"
        connection.requestBusName(name)
        busName = name
        register(name)
    }

    /** Removes the item from the panel; [show] brings it back. */
    fun hide() = submit("hide the tray item") {
        val name = busName ?: return@submit
        busName = null
        connection.releaseBusName(name)
    }

    /** Sets the icon (one image per size) and tells the hosts to fetch it. */
    fun setIcon(images: List<BufferedImage>) {
        pixmaps = images.map { SniPixmap(it.width, it.height, argb32NetworkOrder(it)) }
        emit { listOf(StatusNotifierItemInterface.NewIcon(SNI_ITEM_PATH), StatusNotifierItemInterface.NewToolTip(SNI_ITEM_PATH)) }
    }

    /** Called on the AWT event thread when the item is activated (left click). */
    fun setOnActivate(action: () -> Unit) {
        onActivate = action
    }

    /** Replaces the menu; hosts are told to reload it only if labels or structure changed. */
    fun setMenu(entries: List<TacitTrayMenuEntry>) {
        val current = menu
        if (TacitDbusMenu.structure(entries) == TacitDbusMenu.structure(current.entries)) {
            menu = TacitDbusMenu(entries, current.revision)
            return
        }
        val updated = TacitDbusMenu(entries, current.revision + 1)
        menu = updated
        emit { listOf(DbusMenuInterface.LayoutUpdated(SNI_MENU_PATH, UInt32(updated.revision), TacitDbusMenu.ROOT_ID)) }
    }

    /** Shows a desktop notification through `org.freedesktop.Notifications` (the AWT tray's are not available). */
    fun notify(summary: String, body: String) = submit("send a notification") {
        val notifications = connection.getRemoteObject(
            NOTIFICATIONS_NAME,
            NOTIFICATIONS_PATH,
            NotificationsInterface::class.java,
        )
        notifications.notify(title, UInt32(0), id, summary, body, emptyList(), emptyMap(), -1)
    }

    override fun close() {
        hide()
        outgoing.shutdown()
        runCatching { outgoing.awaitTermination(2, TimeUnit.SECONDS) }
        runCatching { connection.close() }.onFailure { log.warn(it) { "closing the tray's D-Bus connection failed" } }
    }

    private fun register(name: String) {
        connection.getRemoteObject(WATCHER_NAME, WATCHER_PATH, StatusNotifierWatcherInterface::class.java)
            .registerStatusNotifierItem(name)
    }

    /** Sends the signals of [signals] while the item is shown. */
    private fun emit(signals: () -> List<DBusSignal>) = submit("emit a tray signal") {
        if (busName != null) signals().forEach(connection::sendMessage)
    }

    private fun submit(what: String, action: () -> Unit) {
        if (outgoing.isShutdown) return
        runCatching {
            outgoing.execute {
                try {
                    action()
                } catch (e: Exception) {
                    log.error(e) { "Linux tray: could not $what" }
                }
            }
        }
    }

    private fun onUiThread(action: () -> Unit) = SwingUtilities.invokeLater(action)

    private inner class Item : StatusNotifierItemInterface, Properties {
        override fun getObjectPath(): String = SNI_ITEM_PATH

        override fun activate(x: Int, y: Int) {
            val action = onActivate
            onUiThread(action)
        }

        override fun secondaryActivate(x: Int, y: Int) = Unit

        override fun contextMenu(x: Int, y: Int) = Unit

        override fun scroll(delta: Int, orientation: String) = Unit

        private fun properties(): Map<String, Variant<*>> {
            val icon = pixmaps
            return mapOf(
                "Category" to Variant("Communications"),
                "Id" to Variant(id),
                "Title" to Variant(title),
                "Status" to Variant("Active"),
                "WindowId" to Variant(0),
                "IconName" to Variant(""),
                "IconThemePath" to Variant(""),
                "IconPixmap" to Variant(icon, PIXMAPS_SIGNATURE),
                "OverlayIconName" to Variant(""),
                "OverlayIconPixmap" to Variant(emptyList<SniPixmap>(), PIXMAPS_SIGNATURE),
                "AttentionIconName" to Variant(""),
                "AttentionIconPixmap" to Variant(emptyList<SniPixmap>(), PIXMAPS_SIGNATURE),
                "AttentionMovieName" to Variant(""),
                "ToolTip" to Variant(SniToolTip("", icon, title, ""), TOOLTIP_SIGNATURE),
                "ItemIsMenu" to Variant(false),
                "Menu" to Variant(sniMenuDbusPath),
            )
        }

        @Suppress("UNCHECKED_CAST")
        override fun <A : Any?> Get(interfaceName: String, propertyName: String): A {
            checkInterface(interfaceName, ITEM_INTERFACE)
            return (properties()[propertyName] ?: throw UnknownProperty(propertyName)) as A
        }

        override fun <A : Any?> Set(interfaceName: String, propertyName: String, value: A) =
            throw PropertyReadOnly(propertyName)

        override fun GetAll(interfaceName: String): Map<String, Variant<*>> {
            checkInterface(interfaceName, ITEM_INTERFACE)
            return properties()
        }
    }

    private inner class Menu : DbusMenuInterface, Properties {
        override fun getObjectPath(): String = SNI_MENU_PATH

        override fun getLayout(parentId: Int, recursionDepth: Int, propertyNames: List<String>): DbusMenuLayoutResult {
            val current = menu
            val layout = current.layout(parentId, recursionDepth, propertyNames)
                ?: throw InvalidMethodArgument("unknown menu item $parentId")
            return DbusMenuLayoutResult(UInt32(current.revision), layout.toStruct())
        }

        override fun getGroupProperties(ids: List<Int>, propertyNames: List<String>): List<DbusMenuItemProperties> {
            val current = menu
            val requested = ids.ifEmpty { listOf(TacitDbusMenu.ROOT_ID) + current.entries.indices.map { it + 1 } }
            return requested.mapNotNull { id ->
                current.properties(id, propertyNames)?.let { DbusMenuItemProperties(id, it.toVariants()) }
            }
        }

        override fun getProperty(id: Int, name: String): Variant<*> {
            val value = menu.properties(id)?.get(name) ?: throw UnknownProperty("$name of menu item $id")
            return Variant(value)
        }

        override fun event(id: Int, eventId: String, data: Variant<*>, timestamp: UInt32) {
            if (eventId != "clicked") return
            val entry = menu.entry(id) as? TacitTrayMenuEntry.Item ?: return
            onUiThread(entry.onClick)
        }

        override fun eventGroup(events: List<DbusMenuEvent>): List<Int> {
            val current = menu
            events.forEach { event(it.id, it.eventId, it.data, it.timestamp) }
            return events.map { it.id }.filterNot(current::contains)
        }

        override fun aboutToShow(id: Int): Boolean = false

        override fun aboutToShowGroup(ids: List<Int>): DbusMenuAboutToShowGroupResult {
            val current = menu
            return DbusMenuAboutToShowGroupResult(emptyList(), ids.filterNot(current::contains))
        }

        private fun properties(): Map<String, Variant<*>> = mapOf(
            "Version" to Variant(UInt32(DBUSMENU_VERSION)),
            "TextDirection" to Variant("ltr"),
            "Status" to Variant("normal"),
            "IconThemePath" to Variant(emptyList<String>(), "as"),
        )

        @Suppress("UNCHECKED_CAST")
        override fun <A : Any?> Get(interfaceName: String, propertyName: String): A {
            checkInterface(interfaceName, MENU_INTERFACE)
            return (properties()[propertyName] ?: throw UnknownProperty(propertyName)) as A
        }

        override fun <A : Any?> Set(interfaceName: String, propertyName: String, value: A) =
            throw PropertyReadOnly(propertyName)

        override fun GetAll(interfaceName: String): Map<String, Variant<*>> {
            checkInterface(interfaceName, MENU_INTERFACE)
            return properties()
        }
    }

    companion object {
        private const val WATCHER_NAME = "org.kde.StatusNotifierWatcher"
        private const val WATCHER_PATH = "/StatusNotifierWatcher"
        private const val ITEM_NAME_PREFIX = "org.kde.StatusNotifierItem"
        private const val ITEM_INTERFACE = "org.kde.StatusNotifierItem"
        private const val MENU_INTERFACE = "com.canonical.dbusmenu"
        private const val NOTIFICATIONS_NAME = "org.freedesktop.Notifications"
        private const val NOTIFICATIONS_PATH = "/org/freedesktop/Notifications"
        private const val PIXMAPS_SIGNATURE = "a(iiay)"
        private const val TOOLTIP_SIGNATURE = "(sa(iiay)ss)"
        private const val DBUSMENU_VERSION = 3L

        /**
         * Connects to the session bus and exports the item, or returns `null` (the caller falls back to the AWT tray)
         * if there is no session bus or no StatusNotifierWatcher, i.e. no panel that hosts StatusNotifierItems.
         * Blocks for a few round trips on the bus.
         */
        fun connectOrNull(id: String, title: String): TacitStatusNotifierTray? {
            val connection = try {
                DBusConnectionBuilder.forSessionBus().withShared(false).build()
            } catch (e: Exception) {
                log.warn(e) { "Linux tray: no session bus, using the AWT tray" }
                return null
            }
            return try {
                val bus = connection.getRemoteObject("org.freedesktop.DBus", "/org/freedesktop/DBus", DBus::class.java)
                if (!bus.NameHasOwner(WATCHER_NAME)) {
                    log.info { "Linux tray: no $WATCHER_NAME on the session bus, using the AWT tray" }
                    connection.close()
                    null
                } else {
                    TacitStatusNotifierTray(connection, id, title).also { it.start() }
                }
            } catch (e: Exception) {
                log.warn(e) { "Linux tray: setting up the StatusNotifierItem failed, using the AWT tray" }
                runCatching { connection.close() }
                null
            }
        }

        private fun checkInterface(actual: String, expected: String) {
            if (actual != expected) throw UnknownInterface(actual)
        }
    }
}

/** The dbus-java form of [TacitDbusMenuNode]: `(ia{sv}av)` with every child wrapped in a variant. */
internal fun TacitDbusMenuNode.toStruct(): DbusMenuLayoutItem = DbusMenuLayoutItem(
    id,
    properties.toVariants(),
    children.map { Variant(it.toStruct(), DBUSMENU_LAYOUT_SIGNATURE) },
)

internal const val DBUSMENU_LAYOUT_SIGNATURE = "(ia{sv}av)"

private fun Map<String, Any>.toVariants(): Map<String, Variant<*>> = mapValues { (_, value) -> Variant(value) }
