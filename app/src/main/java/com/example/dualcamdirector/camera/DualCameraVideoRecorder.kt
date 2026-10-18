package com.example.dualcamdirector.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.RectF
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.net.Uri
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.core.content.ContextCompat
import com.example.dualcamdirector.ui.frames.FancyFrameType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "DualCamVideoRecorder"

class DualCameraVideoRecorder(private val context: Context) {

    data class InsetBounds(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val parentWidth: Float,
        val parentHeight: Float
    )

    private var isRecording = false
    private var recordingJob: Job? = null
    private var audioJob: Job? = null

    private var videoCodec: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var audioCodec: MediaCodec? = null
    private var audioRecord: AudioRecord? = null
    private var muxer: MediaMuxer? = null
    private var muxerStarted = false

    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var hasAudio = false

    private var videoFirstPtsUs = -1L
    private var videoLastPtsUs = -1L
    private var audioFirstPtsUs = -1L
    private var audioLastPtsUs = -1L

    private var tempVideoFile: File? = null
    private val muxerLock = Any()

    private var reusableMain: Bitmap? = null
    private var reusableInset: Bitmap? = null

    fun isRecording(): Boolean = isRecording

    fun startRecording(
        mainTextureView: TextureView,
        insetTextureView: TextureView,
        getFrameType: () -> FancyFrameType,
        getInsetBounds: () -> InsetBounds,
        onDurationUpdate: (Long) -> Unit,
        onError: (String) -> Unit
    ) {
        if (isRecording) return

        val initialBounds = getInsetBounds()
        val pw = initialBounds.parentWidth.toInt().coerceAtLeast(1)
        val ph = initialBounds.parentHeight.toInt().coerceAtLeast(1)
        val isLandscape = pw > ph

        // 720p HD ensures smooth 30fps without thermal throttling or frame drops
        val videoW = if (isLandscape) 1280 else 720
        val videoH = if (isLandscape) 720 else 1280

        try {
            // 1. Initialize Video Hardware Encoder
            val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, videoW, videoH).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, 5_000_000) // 5 Mbps
                setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // Keyframe every 1s
            }

            val vCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            vCodec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = vCodec.createInputSurface()
            vCodec.start()

            videoCodec = vCodec
            inputSurface = surface
            videoTrackIndex = -1
            videoFirstPtsUs = -1L
            videoLastPtsUs = -1L

            // 2. Initialize MediaMuxer to a temp cache file
            val tempFile = File(context.cacheDir, "dual_cam_rec_${System.currentTimeMillis()}.mp4")
            if (tempFile.exists()) tempFile.delete()
            tempVideoFile = tempFile

            muxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxerStarted = false

            // 3. Audio Setup (graceful optional fallback)
            hasAudio = false
            audioTrackIndex = -1
            audioFirstPtsUs = -1L
            audioLastPtsUs = -1L
            audioRecord = null
            audioCodec = null

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                try {
                    val sampleRate = 44100
                    val channelConfig = AudioFormat.CHANNEL_IN_MONO
                    val audioEncFormat = AudioFormat.ENCODING_PCM_16BIT
                    val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioEncFormat)
                    val bufSize = maxOf(minBuf, 4096 * 2)

                    val record = try {
                        AudioRecord(MediaRecorder.AudioSource.CAMCORDER, sampleRate, channelConfig, audioEncFormat, bufSize)
                    } catch (_: Exception) {
                        AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioEncFormat, bufSize)
                    }

                    if (record.state == AudioRecord.STATE_INITIALIZED) {
                        val aFormat = MediaFormat.createAudioFormat("audio/mp4a-latm", sampleRate, 1).apply {
                            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                            setInteger(MediaFormat.KEY_BIT_RATE, 128000) // 128 kbps
                            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
                        }
                        val aCodec = MediaCodec.createEncoderByType("audio/mp4a-latm")
                        aCodec.configure(aFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                        aCodec.start()
                        record.startRecording()

                        audioRecord = record
                        audioCodec = aCodec
                        hasAudio = true
                    } else {
                        record.release()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Audio recording setup failed; proceeding with video-only", e)
                    hasAudio = false
                }
            }

            isRecording = true

            // Start Audio thread if initialized
            if (hasAudio && audioRecord != null && audioCodec != null && muxer != null) {
                startAudioLoop(audioRecord!!, audioCodec!!, muxer!!)
            }

            // Start Video capture loop
            startVideoLoop(
                vCodec = vCodec,
                surface = surface,
                videoW = videoW,
                videoH = videoH,
                mainTextureView = mainTextureView,
                insetTextureView = insetTextureView,
                getFrameType = getFrameType,
                getInsetBounds = getInsetBounds,
                onDurationUpdate = onDurationUpdate
            )

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize video recorder", e)
            cleanup()
            onError(e.message ?: "Failed to start recording")
        }
    }

    private fun startVideoLoop(
        vCodec: MediaCodec,
        surface: Surface,
        videoW: Int,
        videoH: Int,
        mainTextureView: TextureView,
        insetTextureView: TextureView,
        getFrameType: () -> FancyFrameType,
        getInsetBounds: () -> InsetBounds,
        onDurationUpdate: (Long) -> Unit
    ) {
        recordingJob = CoroutineScope(Dispatchers.Default).launch {
            val fullVideoRect = RectF(0f, 0f, videoW.toFloat(), videoH.toFloat())
            val startTimeMs = System.currentTimeMillis()
            var lastSecReported = -1L

            while (isRecording) {
                val frameStart = System.currentTimeMillis()
                val elapsedSec = (frameStart - startTimeMs) / 1000L
                if (elapsedSec != lastSecReported) {
                    lastSecReported = elapsedSec
                    withContext(Dispatchers.Main) {
                        onDurationUpdate(elapsedSec)
                    }
                }

                // 1. Capture TextureView contents safely on Main thread into pre-allocated reusable bitmaps
                var frameReady = false
                withContext(Dispatchers.Main) {
                    try {
                        if (mainTextureView.isAvailable && insetTextureView.isAvailable &&
                            mainTextureView.width > 0 && insetTextureView.width > 0
                        ) {
                            val mw = mainTextureView.width
                            val mh = mainTextureView.height
                            if (reusableMain == null || reusableMain?.width != mw || reusableMain?.height != mh) {
                                reusableMain = Bitmap.createBitmap(mw, mh, Bitmap.Config.ARGB_8888)
                            }
                            val iw = insetTextureView.width
                            val ih = insetTextureView.height
                            if (reusableInset == null || reusableInset?.width != iw || reusableInset?.height != ih) {
                                reusableInset = Bitmap.createBitmap(iw, ih, Bitmap.Config.ARGB_8888)
                            }

                            mainTextureView.getBitmap(reusableMain!!)
                            insetTextureView.getBitmap(reusableInset!!)
                            frameReady = true
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error acquiring texture bitmaps", e)
                    }
                }

                // 2. Render composite to MediaCodec hardware canvas on background thread
                if (frameReady && reusableMain != null && reusableInset != null) {
                    try {
                        val canvas = surface.lockHardwareCanvas()
                        try {
                            // Main fullscreen camera with aspect ratio preservation
                            DualCameraCaptureHelper.drawScaledBitmapCenterCrop(canvas, reusableMain!!, fullVideoRect)

                            // Scaled inset bounds and fancy frame
                            val bounds = getInsetBounds()
                            val frameType = getFrameType()
                            val pw = if (bounds.parentWidth > 0) bounds.parentWidth else videoW.toFloat()
                            val ph = if (bounds.parentHeight > 0) bounds.parentHeight else videoH.toFloat()
                            val sx = videoW.toFloat() / pw
                            val sy = videoH.toFloat() / ph

                            val targetRect = RectF(
                                bounds.x * sx,
                                bounds.y * sy,
                                (bounds.x + bounds.width) * sx,
                                (bounds.y + bounds.height) * sy
                            )

                            DualCameraCaptureHelper.drawFramedInset(canvas, reusableInset!!, frameType, targetRect)
                        } finally {
                            surface.unlockCanvasAndPost(canvas)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error rendering canvas frame", e)
                    }

                    // 3. Drain video encoder buffers
                    drainVideo(vCodec, endOfStream = false)
                }

                // Frame pacing: aim for ~30 fps (33ms per frame)
                val elapsed = System.currentTimeMillis() - frameStart
                val delayTime = 33L - elapsed
                if (delayTime > 0) {
                    delay(delayTime)
                }
            }
        }
    }

    private fun drainVideo(codec: MediaCodec, endOfStream: Boolean) {
        val currentMuxer = muxer ?: return
        val bufferInfo = MediaCodec.BufferInfo()

        while (true) {
            val status = codec.dequeueOutputBuffer(bufferInfo, 0)
            if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
            } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                synchronized(muxerLock) {
                    if (videoTrackIndex < 0 && !muxerStarted) {
                        videoTrackIndex = currentMuxer.addTrack(codec.outputFormat)
                        if (!hasAudio || audioTrackIndex >= 0) {
                            currentMuxer.start()
                            muxerStarted = true
                        }
                    }
                }
            } else if (status >= 0) {
                val encodedData = codec.getOutputBuffer(status)
                if (encodedData != null) {
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size != 0) {
                        synchronized(muxerLock) {
                            if (!muxerStarted && videoTrackIndex >= 0) {
                                currentMuxer.start()
                                muxerStarted = true
                            }

                            if (muxerStarted && videoTrackIndex >= 0) {
                                encodedData.position(bufferInfo.offset)
                                encodedData.limit(bufferInfo.offset + bufferInfo.size)

                                if (videoFirstPtsUs < 0) {
                                    videoFirstPtsUs = bufferInfo.presentationTimeUs
                                }
                                bufferInfo.presentationTimeUs = maxOf(0L, bufferInfo.presentationTimeUs - videoFirstPtsUs)
                                if (bufferInfo.presentationTimeUs <= videoLastPtsUs) {
                                    bufferInfo.presentationTimeUs = videoLastPtsUs + 1000L
                                }
                                videoLastPtsUs = bufferInfo.presentationTimeUs

                                currentMuxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                            }
                        }
                    }
                }
                codec.releaseOutputBuffer(status, false)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break
                }
            }
        }
    }

    private fun startAudioLoop(record: AudioRecord, codec: MediaCodec, currentMuxer: MediaMuxer) {
        audioJob = CoroutineScope(Dispatchers.IO).launch {
            val chunk = ByteArray(4096)
            val bufferInfo = MediaCodec.BufferInfo()
            var totalBytesRead = 0L
            val sampleRate = 44100
            val bytesPerSec = sampleRate * 2L // 16-bit mono

            try {
                while (isRecording) {
                    val read = record.read(chunk, 0, chunk.size)
                    if (read > 0) {
                        val inputIndex = codec.dequeueInputBuffer(10000)
                        if (inputIndex >= 0) {
                            val inputBuf = codec.getInputBuffer(inputIndex)
                            inputBuf?.clear()
                            inputBuf?.put(chunk, 0, read)
                            val ptsUs = (totalBytesRead * 1_000_000L) / bytesPerSec
                            totalBytesRead += read
                            codec.queueInputBuffer(inputIndex, 0, read, ptsUs, 0)
                        }
                    }

                    var outIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                    while (outIndex >= 0) {
                        val outBuf = codec.getOutputBuffer(outIndex)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            bufferInfo.size = 0
                        }

                        if (bufferInfo.size != 0) {
                            synchronized(muxerLock) {
                                if (muxerStarted && audioTrackIndex >= 0 && outBuf != null) {
                                    outBuf.position(bufferInfo.offset)
                                    outBuf.limit(bufferInfo.offset + bufferInfo.size)

                                    if (audioFirstPtsUs < 0) {
                                        audioFirstPtsUs = bufferInfo.presentationTimeUs
                                    }
                                    bufferInfo.presentationTimeUs = maxOf(0L, bufferInfo.presentationTimeUs - audioFirstPtsUs)
                                    if (bufferInfo.presentationTimeUs <= audioLastPtsUs) {
                                        bufferInfo.presentationTimeUs = audioLastPtsUs + 1000L
                                    }
                                    audioLastPtsUs = bufferInfo.presentationTimeUs

                                    currentMuxer.writeSampleData(audioTrackIndex, outBuf, bufferInfo)
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        outIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                    }

                    if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        synchronized(muxerLock) {
                            if (audioTrackIndex < 0 && !muxerStarted) {
                                audioTrackIndex = currentMuxer.addTrack(codec.outputFormat)
                                if (videoTrackIndex >= 0) {
                                    currentMuxer.start()
                                    muxerStarted = true
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Audio recording loop error", e)
            }
        }
    }

    fun stopRecording(onComplete: (Uri?, Bitmap?) -> Unit) {
        if (!isRecording) {
            onComplete(null, null)
            return
        }
        isRecording = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Wait for video capture loop to finish
                recordingJob?.join()
                recordingJob = null

                // 2. Signal EOS to video encoder and drain remaining packets
                videoCodec?.let { codec ->
                    try {
                        codec.signalEndOfInputStream()
                        drainVideo(codec, endOfStream = true)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error draining video encoder on EOS", e)
                    }
                }

                // 3. Stop audio capture and release
                try {
                    audioJob?.cancelAndJoin()
                } catch (_: Exception) {}
                audioJob = null

                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (_: Exception) {}
                audioRecord = null

                try {
                    audioCodec?.stop()
                    audioCodec?.release()
                } catch (_: Exception) {}
                audioCodec = null

                // 4. Release video codec & input surface
                try {
                    videoCodec?.stop()
                    videoCodec?.release()
                } catch (_: Exception) {}
                videoCodec = null

                try {
                    inputSurface?.release()
                } catch (_: Exception) {}
                inputSurface = null

                // 5. Finalize MediaMuxer
                synchronized(muxerLock) {
                    try {
                        if (muxerStarted) {
                            muxer?.stop()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error stopping MediaMuxer", e)
                    }
                    try {
                        muxer?.release()
                    } catch (_: Exception) {}
                    muxer = null
                    muxerStarted = false
                }

                // 6. Save final MP4 video into MediaStore DCIM/Camera
                val tempFile = tempVideoFile
                var savedUri: Uri? = null
                var thumbnail: Bitmap? = null

                if (tempFile != null && tempFile.exists() && tempFile.length() > 0) {
                    savedUri = DualCameraCaptureHelper.saveVideoToGallery(context, tempFile)
                    if (savedUri != null) {
                        thumbnail = DualCameraCaptureHelper.extractVideoThumbnail(context, savedUri)
                    }
                    tempFile.delete()
                }
                tempVideoFile = null

                withContext(Dispatchers.Main) {
                    onComplete(savedUri, thumbnail)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error finalizing video recording", e)
                withContext(Dispatchers.Main) {
                    onComplete(null, null)
                }
            } finally {
                cleanup()
            }
        }
    }

    fun release() {
        if (isRecording) {
            isRecording = false
        }
        cleanup()
    }

    private fun cleanup() {
        try { recordingJob?.cancel() } catch (_: Exception) {}
        try { audioJob?.cancel() } catch (_: Exception) {}
        recordingJob = null
        audioJob = null
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
        try { audioCodec?.release() } catch (_: Exception) {}
        audioCodec = null
        try { videoCodec?.release() } catch (_: Exception) {}
        videoCodec = null
        try { inputSurface?.release() } catch (_: Exception) {}
        inputSurface = null
        try { muxer?.release() } catch (_: Exception) {}
        muxer = null
        muxerStarted = false
        tempVideoFile?.delete()
        tempVideoFile = null
        reusableMain?.recycle()
        reusableMain = null
        reusableInset?.recycle()
        reusableInset = null
    }
}
