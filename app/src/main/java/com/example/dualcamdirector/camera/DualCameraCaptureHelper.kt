package com.example.dualcamdirector.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.TextureView
import com.example.dualcamdirector.ui.frames.FancyFrameType
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "DualCameraCapture"

object DualCameraCaptureHelper {

    /**
     * Safely captures a TextureView frame and applies its active Matrix transform
     * (center-crop scaling, rotation, front-camera flip) so the resulting Bitmap
     * matches the exact on-screen visual presentation without any distortion or stretching.
     */
    fun captureTransformedBitmap(tv: TextureView): Bitmap? {
        if (!tv.isAvailable || tv.width <= 0 || tv.height <= 0) return null
        val w = tv.width
        val h = tv.height

        val raw = try {
            tv.bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring texture bitmap", e)
            null
        } ?: return null

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.BLACK)

        val matrix = Matrix()
        tv.getTransform(matrix)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
        canvas.drawBitmap(raw, matrix, paint)

        raw.recycle()
        return result
    }

    /**
     * Composites the fullscreen main camera bitmap and the draggable inset camera bitmap
     * with the selected fancy frame, matching on-screen positioning and aspect ratio.
     */
    fun compositeDualShot(
        mainBitmap: Bitmap,
        insetBitmap: Bitmap,
        frameType: FancyFrameType,
        insetX: Float,
        insetY: Float,
        insetW: Float,
        insetH: Float,
        viewW: Float,
        viewH: Float
    ): Bitmap {
        val result = Bitmap.createBitmap(mainBitmap.width, mainBitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // 1. Draw main camera fullscreen
        canvas.drawBitmap(mainBitmap, 0f, 0f, null)

        // 2. Map screen coordinates to main bitmap coordinates
        val scaleX = if (viewW > 0) mainBitmap.width.toFloat() / viewW else 1f
        val scaleY = if (viewH > 0) mainBitmap.height.toFloat() / viewH else 1f

        val targetLeft = insetX * scaleX
        val targetTop = insetY * scaleY
        val targetWidth = insetW * scaleX
        val targetHeight = insetH * scaleY
        val targetRect = RectF(targetLeft, targetTop, targetLeft + targetWidth, targetTop + targetHeight)

        // 3. Draw the fancy frame and inset camera bitmap onto canvas
        drawFramedInset(canvas, insetBitmap, frameType, targetRect)

        return result
    }

    fun drawFramedInset(
        canvas: Canvas,
        insetBitmap: Bitmap,
        frameType: FancyFrameType,
        rect: RectF
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

        when (frameType) {
            FancyFrameType.POSTAGE_STAMP -> {
                drawPostageStampFrame(canvas, insetBitmap, rect)
            }
            FancyFrameType.HOLO_RING -> {
                drawCircularHoloFrame(canvas, insetBitmap, rect)
            }
            FancyFrameType.POLAROID -> {
                drawPolaroidFrame(canvas, insetBitmap, rect)
            }
            else -> {
                // Minimal / Rounded Frame
                val cornerRadius = 24f
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    style = Paint.Style.STROKE
                    strokeWidth = 6f
                }
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    style = Paint.Style.FILL
                }

                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)

                // Clip inset bitmap inside rounded rectangle
                canvas.save()
                val clipPath = Path().apply {
                    addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
                }
                canvas.clipPath(clipPath)
                drawScaledBitmapCenterCrop(canvas, insetBitmap, rect)
                canvas.restore()

                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)
            }
        }
    }

    private fun drawPostageStampFrame(canvas: Canvas, insetBitmap: Bitmap, rect: RectF) {
        val stampBgColor = Color.parseColor("#FBF8F0") // Classic ivory postal paper
        val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = stampBgColor
            style = Paint.Style.FILL
        }

        // 1. Draw Ivory Paper Base
        canvas.drawRect(rect, paperPaint)

        // 2. Draw scalloped perforation holes along all 4 edges (vintage postage stamp teeth)
        val holeRadius = rect.width() * 0.024f
        val holeSpacing = holeRadius * 2.8f
        val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.TRANSPARENT
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }

        // Top & Bottom teeth
        var cx = rect.left + holeSpacing * 0.75f
        while (cx < rect.right - holeRadius) {
            canvas.drawCircle(cx, rect.top, holeRadius, clearPaint)
            canvas.drawCircle(cx, rect.bottom, holeRadius, clearPaint)
            cx += holeSpacing
        }

        // Left & Right teeth
        var cy = rect.top + holeSpacing * 0.75f
        while (cy < rect.bottom - holeRadius) {
            canvas.drawCircle(rect.left, cy, holeRadius, clearPaint)
            canvas.drawCircle(rect.right, cy, holeRadius, clearPaint)
            cy += holeSpacing
        }

        // 3. Draw Inner Inset Camera Stream with uniform padding
        val padding = rect.width() * 0.065f
        val innerRect = RectF(
            rect.left + padding,
            rect.top + padding,
            rect.right - padding,
            rect.bottom - padding
        )

        // Inner photo border (subtle beige border matching Compose)
        val innerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DCD6CA")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRect(innerRect, innerBorderPaint)

        canvas.save()
        val innerPath = Path().apply {
            addRect(innerRect, Path.Direction.CW)
        }
        canvas.clipPath(innerPath)
        drawScaledBitmapCenterCrop(canvas, insetBitmap, innerRect)
        canvas.restore()

        // 4. Postal Postmark stamp overlay (top-left cancellation lines, matching preview HUD)
        val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(100, 45, 45, 45)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        val waveStartX = rect.left + rect.width() * 0.05f
        val waveWidth = rect.width() * 0.42f
        for (i in 0..2) {
            val y = rect.top + rect.height() * (0.07f + i * 0.032f)
            val wavePath = Path().apply {
                moveTo(waveStartX, y)
                cubicTo(
                    waveStartX + waveWidth * 0.33f, y - 5f,
                    waveStartX + waveWidth * 0.66f, y + 5f,
                    waveStartX + waveWidth, y
                )
            }
            canvas.drawPath(wavePath, inkPaint)
        }

        // Circular Postmark Stamp at top-left
        val stampCenterX = rect.left + rect.width() * 0.22f
        val stampCenterY = rect.top + rect.height() * 0.11f
        val stampRadius = rect.width() * 0.10f
        canvas.drawCircle(stampCenterX, stampCenterY, stampRadius, inkPaint)
        canvas.drawCircle(stampCenterX, stampCenterY, stampRadius * 0.85f, inkPaint)

        // Inner date stamp text
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 45, 45, 45)
            textSize = rect.width() * 0.032f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val dateStr = SimpleDateFormat("dd.MM.yy", Locale.US).format(Date())
        canvas.drawText("DUAL SHOT", stampCenterX, stampCenterY - 2f, textPaint)
        canvas.drawText(dateStr, stampCenterX, stampCenterY + textPaint.textSize + 2f, textPaint)
    }

    private fun drawCircularHoloFrame(canvas: Canvas, insetBitmap: Bitmap, rect: RectF) {
        val size = minOf(rect.width(), rect.height())
        val cx = rect.centerX()
        val cy = rect.centerY()
        val radius = size / 2f

        // Border ring
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00E5FF")
            style = Paint.Style.STROKE
            strokeWidth = 8f
        }

        canvas.save()
        val circlePath = Path().apply {
            addCircle(cx, cy, radius - 4f, Path.Direction.CW)
        }
        canvas.clipPath(circlePath)
        val squareRect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        drawScaledBitmapCenterCrop(canvas, insetBitmap, squareRect)
        canvas.restore()

        canvas.drawCircle(cx, cy, radius - 4f, ringPaint)
    }

    private fun drawPolaroidFrame(canvas: Canvas, insetBitmap: Bitmap, rect: RectF) {
        val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(rect, 12f, 12f, whitePaint)

        val padding = rect.width() * 0.06f
        val photoRect = RectF(
            rect.left + padding,
            rect.top + padding,
            rect.right - padding,
            rect.bottom - padding * 3.5f
        )

        canvas.save()
        val clipPath = Path().apply { addRect(photoRect, Path.Direction.CW) }
        canvas.clipPath(clipPath)
        drawScaledBitmapCenterCrop(canvas, insetBitmap, photoRect)
        canvas.restore()

        // Polaroid text at bottom
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#333333")
            textSize = rect.width() * 0.06f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        canvas.drawText("DUAL DIRECTOR", rect.centerX(), rect.bottom - padding * 1.2f, textPaint)
    }

    fun drawScaledBitmapCenterCrop(canvas: Canvas, src: Bitmap, dst: RectF) {
        val srcRatio = src.width.toFloat() / src.height.toFloat()
        val dstRatio = dst.width() / dst.height()

        val srcRect: Rect
        if (srcRatio > dstRatio) {
            val targetW = (src.height * dstRatio).toInt()
            val left = (src.width - targetW) / 2
            srcRect = Rect(left, 0, left + targetW, src.height)
        } else {
            val targetH = (src.width / dstRatio).toInt()
            val top = (src.height - targetH) / 2
            srcRect = Rect(0, top, src.width, top + targetH)
        }

        canvas.drawBitmap(src, srcRect, dst, null)
    }

    /**
     * Saves a bitmap to the public Android MediaStore Gallery (DCIM/Camera)
     * and returns the content Uri.
     */
    fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Uri? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "DUAL_CAM_$timestamp.jpg"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/Camera")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null

        try {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 96, out)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }

            MediaScannerConnection.scanFile(context, arrayOf(uri.toString()), arrayOf("image/jpeg"), null)
            Log.i(TAG, "Successfully saved dual-shot photo to gallery: $uri")
            return uri
        } catch (e: Exception) {
            Log.e(TAG, "Error saving dual-shot photo to MediaStore", e)
            resolver.delete(uri, null, null)
            return null
        }
    }

    /**
     * Saves an MP4 video file to the public Android MediaStore Gallery (DCIM/Camera)
     * and returns the content Uri.
     */
    fun saveVideoToGallery(context: Context, tempFile: File): Uri? {
        if (!tempFile.exists() || tempFile.length() == 0L) {
            Log.e(TAG, "Video temp file is missing or empty")
            return null
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "DUAL_CAM_$timestamp.mp4"

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, filename)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis())

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/Camera")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null

        try {
            resolver.openOutputStream(uri)?.use { out ->
                tempFile.inputStream().use { input ->
                    input.copyTo(out)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }

            MediaScannerConnection.scanFile(context, arrayOf(uri.toString()), arrayOf("video/mp4"), null)
            Log.i(TAG, "Successfully saved dual-shot video to gallery: $uri")
            return uri
        } catch (e: Exception) {
            Log.e(TAG, "Error saving video to MediaStore", e)
            resolver.delete(uri, null, null)
            return null
        }
    }

    /**
     * Extracts a frame thumbnail from a saved video Uri for the circular UI button.
     */
    fun extractVideoThumbnail(context: Context, uri: Uri): Bitmap? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(500000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            retriever.release()
            frame
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting video thumbnail", e)
            null
        }
    }
}
