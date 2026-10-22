package ch.fhnw.pepper_realtime.controller

import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Stub implementation of GestureController for standalone mode (no robot hardware).
 * Logs all gesture/animation commands without executing them.
 */
class GestureController {
    
    fun interface BoolSupplier {
        fun get(): Boolean
    }

    fun interface IntSupplier {
        fun get(): Int?
    }

    companion object {
        private const val TAG = "GestureController[STUB]"
    }

    @Volatile
    private var running = false

    /**
     * Simulates starting gesture loop
     */
    @Suppress("UNUSED_PARAMETER")
    fun start(qiContext: Any?, keepRunning: BoolSupplier, nextResId: IntSupplier) {
        if (running) {
            Log.d(TAG, "🤖 [SIMULATED] GestureController already running")
            return
        }
        running = true
        Log.i(TAG, "🤖 [SIMULATED] GestureController started - gestures will be logged")
    }

    /**
     * Simulates stopping gesture loop
     */
    fun stop() {
        if (!running) {
            Log.d(TAG, "🤖 [SIMULATED] GestureController already stopped")
            return
        }
        running = false
        Log.i(TAG, "🤖 [SIMULATED] GestureController stopped")
    }

    /**
     * Simulates playing a single animation
     */
    @Suppress("UNUSED_PARAMETER")
    fun playAnimation(qiContext: Any?, animationResourceId: Int, onComplete: Runnable?) {
        Log.i(TAG, "🤖 [SIMULATED] Playing animation with resource ID: $animationResourceId")

        onComplete?.let {
            Handler(Looper.getMainLooper()).postDelayed(it, 300)
        }
    }

    /**
     * Simulates entering listening state
     */
    @Suppress("UNUSED_PARAMETER")
    fun onEnterListening(robotContext: Any?) {
        Log.d(TAG, "🤖 [SIMULATED] GestureController entered LISTENING state")
    }

    /**
     * Simulates entering thinking state
     */
    @Suppress("UNUSED_PARAMETER")
    fun onEnterThinking(robotContext: Any?) {
        Log.d(TAG, "🤖 [SIMULATED] GestureController entered THINKING state")
    }

    /**
     * Simulates stopping thinking state
     */
    fun stopThinking() {
        Log.d(TAG, "🤖 [SIMULATED] GestureController stopped THINKING state")
    }

    /**
     * Simulates exiting speaking with grace period
     */
    fun onExitSpeaking() {
        Log.i(TAG, "🤖 [SIMULATED] GestureController exited SPEAKING state (grace period active)")
        stopNow(immediateReset = false)
    }

    /**
     * Simulates scheduling safe posture reset
     */
    fun scheduleResetToSafePosture(delayMs: Long = 2000L) {
        Log.d(TAG, "🤖 [SIMULATED] Scheduled safe posture reset in ${delayMs}ms")
    }

    /**
     * Simulates cancelling pending safe posture reset
     */
    fun cancelPendingSafePostureReset() {
        Log.d(TAG, "🤖 [SIMULATED] Cancelled pending safe posture reset")
    }

    /**
     * Simulates stopping immediately
     */
    fun stopNow(immediateReset: Boolean = false) {
        running = false
        Log.i(TAG, "🤖 [SIMULATED] GestureController stopped immediately (immediateReset=$immediateReset)")
    }

    /**
     * Simulates resetting posture to safe position
     */
    fun resetToSafePosture() {
        Log.i(TAG, "🤖 [SIMULATED] GestureController reset to safe posture")
    }

    /**
     * Checks if controller is running
     */
    fun isRunning(): Boolean = running

    /**
     * Pause gesture controller (for background transitions)
     */
    fun pause() {
        Log.i(TAG, "🤖 [SIMULATED] GestureController paused")
        stopNow()
    }

    /**
     * Resume gesture controller (from background)
     */
    fun resume() {
        Log.d(TAG, "🤖 [SIMULATED] GestureController resumed")
    }

    /**
     * Shuts down the controller
     */
    fun shutdown() {
        running = false
        Log.i(TAG, "🤖 [SIMULATED] GestureController shutdown")
    }

    @Suppress("SameReturnValue")
    fun getRandomExplainAnimationResId(): Int {
        // Return a dummy ID for standalone mode
        return 0
    }
}

