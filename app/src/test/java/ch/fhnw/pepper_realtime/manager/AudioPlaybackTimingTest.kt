package ch.fhnw.pepper_realtime.manager

import org.junit.Assert.*
import org.junit.Test

class AudioPlaybackTimingTest {
    @Test
    fun `records only the first event per stage and separates submission from playback`() {
        var now = 100L
        val timing = AudioPlaybackTiming { now }
        assertNull(timing.submitted())
        assertNull(timing.progressed())
        assertEquals(100L, timing.queued())
        now = 120
        assertNull(timing.queued())
        assertNull(timing.progressed())
        assertEquals(20L, timing.submitted())
        now = 180
        assertEquals(80L, timing.progressed())
        assertNull(timing.submitted())
        assertNull(timing.progressed())
        timing.reset()
        assertNull(timing.progressed())
        assertEquals(180L, timing.queued())
        now = 190
        assertEquals(10L, timing.submitted())
    }
}
