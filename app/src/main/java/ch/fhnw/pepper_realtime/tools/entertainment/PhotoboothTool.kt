package ch.fhnw.pepper_realtime.tools.entertainment

import ch.fhnw.pepper_realtime.tools.Tool
import ch.fhnw.pepper_realtime.tools.ToolContext
import ch.fhnw.pepper_realtime.ui.ChatActivity
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Tool for triggering the Conserto Photobooth on Pepper's tablet.
 * Displays a countdown, takes a picture, adds a branded Conserto frame, and provides a QR code.
 */
class PhotoboothTool : Tool {

    override fun getName(): String = "start_photobooth"

    override fun getDefinition(): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put("name", getName())
            put("description", "Starts the Conserto Photobooth on Pepper's tablet screen. Shows an animated 3-second countdown, captures a picture with Pepper's head camera, applies a branded Conserto watermark and frame, and generates a QR code allowing guests to download the souvenir photo directly on their smartphones. Use this when the user asks for a photo, photobooth, selfie, souvenir picture, or says 'prends une photo', 'on fait une photo', 'souriez', 'photobooth conserto'.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject())
            })
        }
    }

    override fun execute(args: JSONObject, context: ToolContext): String {
        val activity = context.activity as? ChatActivity
        if (activity == null || !context.hasUi()) {
            return JSONObject().put("error", "No UI available").toString()
        }

        var started = false
        val latch = CountDownLatch(1)

        activity.runOnUiThread {
            started = activity.viewModel.startPhotobooth()
            latch.countDown()
        }

        latch.await(2, TimeUnit.SECONDS)

        return if (started) {
            JSONObject()
                .put("status", "success")
                .put("message", "Le photobooth Conserto est lancé sur la tablette ! Pepper affiche le décompte et prend la photo.")
                .toString()
        } else {
            JSONObject().put("error", "Impossible de démarrer le photobooth").toString()
        }
    }
}
