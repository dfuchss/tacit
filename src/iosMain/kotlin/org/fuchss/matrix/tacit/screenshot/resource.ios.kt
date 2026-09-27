package org.fuchss.matrix.tacit.screenshot

import de.connect2x.trixnity.messenger.compose.view.room.timeline.element.details.toByteArray
import platform.Foundation.NSBundle
import platform.UIKit.UIImage

actual object PlatformResource {
    actual fun resource(path: String): ByteArray? {
        // for some unknown reason, we cannot load the file's byte data directly as iOS will change the png when copying
        // it to the bundle (e.g. inflate the size to nearly double the size). The pure read bytes method does then not
        // produce a ByteArray that Compose can interprete as an image.
        // So instead, load it as an image on iOS, convert it back to ByteArray, to then convert it to Compose later.
        val bundle = NSBundle.mainBundle
        val path = bundle.pathForResource(path.split(".")[0], path.split(".")[1]) ?: error("Unable to locate resource")
        val image = UIImage.imageWithContentsOfFile(path)
        return image?.toByteArray(1.0)
    }
}