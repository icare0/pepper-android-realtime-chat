package ch.fhnw.pepper_realtime.ui

import android.content.Context
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class YouTubePlayerDialogTest {
    @Test
    fun `repeated close notifies listener only once`() {
        val listener = mock<YouTubePlayerDialog.PlayerEventListener>()
        val player = YouTubePlayerDialog(mock<Context>(), listener)
        player.closePlayer()
        player.closePlayer()
        verify(listener, times(1)).onPlayerClosed()
    }
}
