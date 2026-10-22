package ch.fhnw.pepper_realtime.util

import android.graphics.*
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Applies Conserto branding watermark and photo frame to captured pictures.
 */
object PhotoboothWatermarker {

    private const val TAG = "PhotoboothWatermarker"

    /**
     * Applies a branded Conserto frame & footer to the raw captured photo.
     *
     * @param source Raw camera bitmap
     * @param eventName Optional custom event name (e.g., "Conserto Tech Days")
     * @return New bitmap with Conserto branding applied
     */
    fun applyConsertoBranding(
        source: Bitmap,
        eventName: String = "Pepper Photobooth"
    ): Bitmap {
        return try {
            val width = source.width
            val height = source.height

            // Create a mutable copy/canvas for the output
            val bannerHeight = (height * 0.13f).toInt().coerceAtLeast(60)
            val totalHeight = height + bannerHeight

            val output = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)

            // Draw original photo at the top
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            canvas.drawBitmap(source, 0f, 0f, paint)

            // Draw bottom banner background (sleek dark Conserto blue/navy)
            val bannerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A") // Deep slate/navy
                style = Paint.Style.FILL
            }
            val bannerRect = RectF(0f, height.toFloat(), width.toFloat(), totalHeight.toFloat())
            canvas.drawRect(bannerRect, bannerPaint)

            // Draw accent line separating photo and banner (Conserto cyan/teal)
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#06B6D4") // Cyan accent
                strokeWidth = (width * 0.006f).coerceAtLeast(3f)
                style = Paint.Style.STROKE
            }
            canvas.drawLine(0f, height.toFloat(), width.toFloat(), height.toFloat(), linePaint)

            // Draw CONSERTO title (Left-aligned in banner)
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = bannerHeight * 0.38f
            }
            val paddingX = width * 0.05f
            val titleY = height + (bannerHeight * 0.48f)
            canvas.drawText("CONSERTO", paddingX, titleY, titlePaint)

            // Draw Subtitle / Event (Below CONSERTO title)
            val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#94A3B8") // Slate light
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textSize = bannerHeight * 0.22f
            }
            val subtitleY = height + (bannerHeight * 0.80f)
            canvas.drawText(eventName, paddingX, subtitleY, subtitlePaint)

            // Draw Date & Time (Right-aligned in banner)
            val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
            val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#CBD5E1")
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textSize = bannerHeight * 0.24f
                textAlign = Paint.Align.RIGHT
            }
            val dateX = width - paddingX
            canvas.drawText(dateStr, dateX, titleY, datePaint)

            // Draw robot attribution below date
            val robotAttributionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#06B6D4")
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = bannerHeight * 0.20f
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("Photo prise par Pepper 🤖", dateX, subtitleY, robotAttributionPaint)

            output
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply Conserto branding to photo", e)
            source
        }
    }
}
