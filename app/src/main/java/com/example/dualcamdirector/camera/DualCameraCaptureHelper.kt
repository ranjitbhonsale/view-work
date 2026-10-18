package com.example.dualcamdirector.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.dualcamdirector.ui.frames.FancyFrameType
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "DualCameraCapture"

object DualCameraCaptureHelper {

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

    private fun drawFramedInset(
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

        // 3. Draw Inner Inset Camera Stream with padding
        val padding = rect.width() * 0.07f
        val innerRect = RectF(
            rect.left + padding,
            rect.top + padding,
            rect.right - padding,
            rect.bottom - padding * 1.5f
        )

        canvas.save()
        val innerPath = Path().apply {
            addRect(innerRect, Path.Direction.CW)
        }
        canvas.clipPath(innerPath)
        drawScaledBitmapCenterCrop(canvas, insetBitmap, innerRect)
        canvas.restore()

        // 4. Postal Postmark stamp overlay (wavy cancellation lines)
        val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(130, 40, 40, 40)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        val waveStartX = rect.left + rect.width() * 0.12f
        val waveWidth = rect.width() * 0.65f
        for (i in 0..2) {
            val y = rect.top + rect.height() * (0.12f + i * 0.045f)
            val wavePath = Path().apply {
                moveTo(waveStartX, y)
                cubicTo(
                    waveStartX + waveWidth * 0.33f, y - 8f,
                    waveStartX + waveWidth * 0.66f, y + 8f,
                    waveStartX + waveWidth, y
                )
            }
            canvas.drawPath(wavePath, inkPaint)
        }

        // Circular Postmark Stamp
        val stampCenterX = rect.right - rect.width() * 0.28f
        val stampCenterY = rect.bottom - rect.height() * 0.22f
        val stampRadius = rect.width() * 0.20f
        canvas.drawCircle(stampCenterX, stampCenterY, stampRadius, inkPaint)
        canvas.drawCircle(stampCenterX, stampCenterY, stampRadius * 0.88f, inkPaint)

        // Inner date stamp text
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(150, 40, 40, 40)
            textSize = rect.width() * 0.048f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val dateStr = SimpleDateFormat("dd.MM.yy", Locale.US).format(Date())
        canvas.drawText("DUAL SHOT", stampCenterX, stampCenterY - 4f, textPaint)
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

    private fun drawScaledBitmapCenterCrop(canvas: Canvas, src: Bitmap, dst: RectF) {
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
            Log.i(TAG, "Successfully saved dual-shot to gallery: $uri")
            return uri
        } catch (e: Exception) {
            Log.e(TAG, "Error saving dual-shot to MediaStore", e)
            resolver.delete(uri, null, null)
            return null
        }
    }
}
