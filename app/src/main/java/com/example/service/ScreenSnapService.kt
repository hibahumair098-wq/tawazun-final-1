package com.example.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream

/**
 * Captures a transparent, consented screen snapshot every 30 seconds.
 * Android's MediaProjection consent must be obtained by the activity for every
 * capture session. A persistent foreground notification provides an immediate
 * Stop action while capture is active.
 */
class ScreenSnapService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_FAMILY_ID = "family_id"
        const val EXTRA_CHILD_ID = "child_id"
        const val ACTION_STOP = "com.tawazun.pksafe.ACTION_STOP_SNAP"
        private const val CHANNEL_ID = "tawazun_screen_snap"
        private const val NOTIF_ID = 4210
        private const val INTERVAL_MS = 30_000L
        private const val MAX_WIDTH = 540

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handler: Handler? = null
    private var running = false
    private val db = FirebaseFirestore.getInstance()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSnap()
            return START_NOT_STICKY
        }
        // Android 14+ requires a new projection consent token for each session.
        // Never attempt to create another projection from an already-used token.
        if (running || projection != null) return START_NOT_STICKY

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        val familyId = intent?.getStringExtra(EXTRA_FAMILY_ID)
        val childId = intent?.getStringExtra(EXTRA_CHILD_ID)
        if (resultCode != Activity.RESULT_OK || data == null || familyId.isNullOrBlank() || childId.isNullOrBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        try {
            // Promote before calling getMediaProjection; required for Android 14+.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIF_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIF_ID, buildNotification())
            }

            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val newProjection = manager.getMediaProjection(resultCode, data)
                ?: throw IllegalStateException("Android did not create a screen-capture session")
            projection = newProjection
            newProjection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    stopSnap()
                }
            }, Handler(Looper.getMainLooper()))

            val metrics = resources.displayMetrics
            val scale = (MAX_WIDTH.toFloat() / metrics.widthPixels).coerceAtMost(1f)
            val width = ((metrics.widthPixels * scale).toInt() and 0xFFFE).coerceAtLeast(2)
            val height = ((metrics.heightPixels * scale).toInt() and 0xFFFE).coerceAtLeast(2)
            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            imageReader = reader
            virtualDisplay = newProjection.createVirtualDisplay(
                "tawazun_snap",
                width,
                height,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                null
            ) ?: throw IllegalStateException("Could not create screen capture display")

            handler = Handler(Looper.getMainLooper())
            running = true
            isRunning = true
            handler?.postDelayed({ captureLoop(familyId, childId) }, 1_500L)
        } catch (_: SecurityException) {
            stopSnap()
        } catch (_: Exception) {
            stopSnap()
        }
        return START_NOT_STICKY
    }

    private fun captureLoop(familyId: String, childId: String) {
        if (!running) return
        captureAndUpload(familyId, childId)
        handler?.postDelayed({ captureLoop(familyId, childId) }, INTERVAL_MS)
    }

    private fun captureAndUpload(familyId: String, childId: String) {
        val image = try {
            imageReader?.acquireLatestImage()
        } catch (_: Exception) {
            null
        } ?: return

        try {
            val bitmap = imageToBitmap(image)
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 55, output)
            bitmap.recycle()
            val base64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
            // Firestore document size is limited; skip oversized frames.
            if (base64.length < 900_000) {
                db.collection("families").document(familyId)
                    .collection("screenSnaps").document(childId)
                    .set(
                        mapOf(
                            "childId" to childId,
                            "snapBase64" to base64,
                            "snapAt" to System.currentTimeMillis()
                        )
                    )
            }
        } catch (_: Exception) {
            // Skip a failed frame; the next scheduled capture can retry.
        } finally {
            image.close()
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val padded = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        buffer.rewind()
        padded.copyPixelsFromBuffer(buffer)
        val cropped = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
        if (cropped !== padded) padded.recycle()
        return cropped
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tawazun Screen Snap",
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ScreenSnapService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tawazun screen snapshot is active")
            .setContentText("Screen snapshots are shared with the linked parent. Tap Stop to end capture.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    private fun stopSnap() {
        running = false
        isRunning = false
        handler?.removeCallbacksAndMessages(null)
        handler = null
        try { virtualDisplay?.setSurface(null) } catch (_: Exception) { }
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        val activeProjection = projection
        projection = null
        try { activeProjection?.stop() } catch (_: Exception) { }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopSnap()
        super.onDestroy()
    }
}
