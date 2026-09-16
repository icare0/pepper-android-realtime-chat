package ch.fhnw.pepper_realtime.manager

/** Tracks the local playback stages independently of server-side speech detection. */
internal class AudioPlaybackTiming(private val nowMs: () -> Long) {
    private var queuedAt: Long? = null
    private var submitted = false
    private var progressed = false

    @Synchronized
    fun queued(): Long? {
        if (queuedAt != null) return null
        return nowMs().also { queuedAt = it }
    }

    @Synchronized
    fun submitted(): Long? {
        val start = queuedAt ?: return null
        if (submitted) return null
        submitted = true
        return nowMs() - start
    }

    @Synchronized
    fun progressed(): Long? {
        val start = queuedAt ?: return null
        if (!submitted || progressed) return null
        progressed = true
        return nowMs() - start
    }

    @Synchronized
    fun reset() {
        queuedAt = null
        submitted = false
        progressed = false
    }
}
