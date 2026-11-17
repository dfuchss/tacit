package org.fuchss.matrix.tacit.views.room.timeline

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TacitStickerButtonTest {
    @Test
    fun showsForNewMessagesWhenStickersAreAvailable() {
        assertTrue(isStickerButtonVisible(hasStickerPacks = true, canSendMessages = true, isEdit = false))
    }

    @Test
    fun hiddenWithoutPacksPermissionOrWhileEditing() {
        assertFalse(isStickerButtonVisible(hasStickerPacks = false, canSendMessages = true, isEdit = false))
        assertFalse(isStickerButtonVisible(hasStickerPacks = true, canSendMessages = false, isEdit = false))
        assertFalse(isStickerButtonVisible(hasStickerPacks = true, canSendMessages = true, isEdit = true))
    }
}
