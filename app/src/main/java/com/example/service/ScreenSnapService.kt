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
import android.os.BaseBundle
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream

/**
 * 🪞 TAWAZUN SCREEN SNAP — bachay ke screen ka LIVE snapshot (har 30 second).
 *
 * MediaProjection se screen parhta hai, chhoti JPEG banata hai, aur Firestore
 * mein "families/{fid}/screenSnaps/{childId}" doc par likhta hai — parent ka
 * Mirror tab wahan se LIVE photo dikhata hai.
 *
 * FREE tareeqa: Cloud Storage skip kiya (wo card/Blaze maangta hai) —
 * chhoti compressed image Firestore ke free Spark plan mein hi chale jaati hai.
 *
 * Transparency: capture ke pooray waqt phone par notification chalti rehti hai.
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
        private const val INTERVAL_MS = 30_000L // har 30 second ek snapshot
        private const val MAX_WIDTH = 540 // chhota rakhte hain (Firestore 1MB limit + tez load)

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
        // Notification ka Stop button
        if (intent?.action == ACTION_STOP) {
            stopSnap()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: return START_NOT_STICKY
        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        } ?: return START_NOT_STICKY
        val familyId = intent.getStringExtra(EXTRA_FAMILY_ID) ?: return START_NOT_STICKY
        val childId = intent.getStringExtra(EXTRA_CHILD_ID) ?: return START_NOT_STICKY

        // Foreground notification (Android rule + transparency)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }

        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(resultCode, data)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopSnap()
            }
        }, null)

        val metrics = resources.displayMetrics
        val scale = (MAX_WIDTH.toFloat() / metrics.widthPixels).coerceAtMost(1f)
        val w = ((metrics.widthPixels * scale).toInt() and 0xFFFE)
        val h = ((metrics.heightPixels * scale).toInt() and 0xFFFE)

        imageReader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        virtualDisplay = projection?.createVirtualDisplay(
            "tawazun_snap",
            w, h, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null, null
        )

        handler = Handler(Looper.getMainLooper())
        running = true
        isRunning = true
        handler?.postDelayed({ captureLoop(familyId, childId) }, 1500L) // pehla snap jaldi
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
        } catch (e: Exception) {
            null
        } ?: return

        try {
            val bitmap = imageToBitmap(image)
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 55, baos)
            val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

            // Firestore doc limit ~1MB — base64 ~1.33x hota hai, is liye guard
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
        } catch (e: Exception) {
            // Koi frame skip — agli koshish 30 sec mein
        } finally {
            image.close()
        }
    }

    /** ImageReader ke Image ko Bitmap mein badlo (row padding handle kar ke). */
    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val full = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        buffer.rewind()
        full.copyPixelsFromBuffer(buffer)
        return Bitmap.createBitmap(full, 0, 0, image.width, image.height)
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tawazun Screen Snap",
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, ScreenSnapService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🪞 Tawazun Screen Snap chal raha hai")
            .setContentText("Parent aap ki screen ka live snapshot dekh sakte hain (3-Day Privacy Vault).")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    private fun stopSnap() {
        running = false
        isRunning = false
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        projection?.stop()
        projection = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopSnap()
        super.onDestroy()
    }
}
