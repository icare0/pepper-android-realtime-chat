package ch.fhnw.pepper_realtime.manager

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import ch.fhnw.pepper_realtime.network.PhotoboothServer
import ch.fhnw.pepper_realtime.service.VisionService
import ch.fhnw.pepper_realtime.ui.PhotoboothPhase
import ch.fhnw.pepper_realtime.ui.PhotoboothUiState
import ch.fhnw.pepper_realtime.util.PhotoboothWatermarker
import ch.fhnw.pepper_realtime.util.QrCodeGenerator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Conserto Photobooth workflow:
 * - Animated countdown (3..2..1)
 * - Photo capture via Pepper's head camera (VisionService)
 * - Branded Conserto frame/watermark application
 * - Local Wi-Fi HTTP server hosting
 * - QR code generation for smartphone downloading
 */
@Singleton
class PhotoboothManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val visionService: VisionService
) {

    companion object {
        private const val TAG = "PhotoboothManager"
    }

    private val _state = MutableStateFlow(PhotoboothUiState())
    val state: StateFlow<PhotoboothUiState> = _state.asStateFlow()

    private val photoboothServer = PhotoboothServer()
    private var scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var activeJob: Job? = null
    private var lastCapturedFile: File? = null

    fun setCoroutineScope(newScope: CoroutineScope) {
        this.scope = newScope
    }

    /**
     * Start the photobooth flow with countdown and capture.
     */
    fun startPhotobooth(
        customVisionService: VisionService? = null,
        onPhotoReady: ((String) -> Unit)? = null
    ): Boolean {
        val targetVisionService = customVisionService ?: visionService
        activeJob?.cancel()

        _state.value = PhotoboothUiState(
            isVisible = true,
            phase = PhotoboothPhase.COUNTDOWN,
            countdown = 3,
            statusMessage = "Attention, souriez ! Regardez la tête de Pepper 🤖"
        )

        activeJob = scope.launch {
            try {
                // Countdown 3 -> 2 -> 1
                delay(1000)
                _state.update { it.copy(countdown = 2) }
                delay(1000)
                _state.update { it.copy(countdown = 1) }
                delay(1000)

                // Flash transition
                _state.update { it.copy(phase = PhotoboothPhase.FLASH, countdown = 0, statusMessage = "Clic ! 📸") }
                delay(250) // Small visual flash pause

                if (!targetVisionService.isInitialized) {
                    _state.update {
                        it.copy(
                            phase = PhotoboothPhase.RESULT,
                            statusMessage = "Caméra non disponible"
                        )
                    }
                    return@launch
                }

                // Capture photo from camera
                targetVisionService.captureRawPhoto { rawBitmap ->
                    scope.launch(Dispatchers.IO) {
                        if (rawBitmap == null) {
                            withContext(Dispatchers.Main) {
                                _state.update {
                                    it.copy(
                                        phase = PhotoboothPhase.RESULT,
                                        statusMessage = "Erreur lors de la capture photo"
                                    )
                                }
                            }
                            return@launch
                        }

                        // Apply Conserto watermark
                        val brandedBitmap = PhotoboothWatermarker.applyConsertoBranding(rawBitmap)
                        if (rawBitmap != brandedBitmap) {
                            rawBitmap.recycle()
                        }

                        // Save JPEG to cache
                        val photoFile = File(context.cacheDir, "conserto_photo_${System.currentTimeMillis()}.jpg")
                        try {
                            FileOutputStream(photoFile).use { fos ->
                                brandedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                            }
                            lastCapturedFile = photoFile
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to save photobooth JPEG file", e)
                        }

                        // Start HTTP server & generate QR code
                        val downloadUrl = photoboothServer.start(photoFile)
                        val qrBitmap = if (downloadUrl != null) {
                            QrCodeGenerator.generateQrCode(downloadUrl, 380, 380)
                        } else null

                        withContext(Dispatchers.Main) {
                            _state.update {
                                it.copy(
                                    phase = PhotoboothPhase.RESULT,
                                    photoBitmap = brandedBitmap,
                                    qrBitmap = qrBitmap,
                                    downloadUrl = downloadUrl,
                                    statusMessage = if (downloadUrl != null) {
                                        "Scannez le QR code pour télécharger votre photo !"
                                    } else {
                                        "Photo prise ! (Connectez-vous au Wi-Fi pour le QR code)"
                                    }
                                )
                            }
                            onPhotoReady?.invoke(photoFile.absolutePath)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Photobooth sequence error", e)
                _state.update {
                    it.copy(
                        phase = PhotoboothPhase.RESULT,
                        statusMessage = "Erreur: ${e.message}"
                    )
                }
            }
        }

        return true
    }

    /**
     * Retake photo.
     */
    fun retakePhotobooth(customVisionService: VisionService? = null, onPhotoReady: ((String) -> Unit)? = null) {
        startPhotobooth(customVisionService, onPhotoReady)
    }

    /**
     * Close the photobooth overlay and stop HTTP server.
     */
    fun dismissPhotobooth() {
        activeJob?.cancel()
        activeJob = null
        photoboothServer.stop()
        // Displayed bitmaps are not recycled manually: Compose may still draw them during the
        // overlay exit, and on API 23 the GC reclaims bitmap memory once they are unreferenced.
        _state.value = PhotoboothUiState(isVisible = false, phase = PhotoboothPhase.IDLE)
    }
}
