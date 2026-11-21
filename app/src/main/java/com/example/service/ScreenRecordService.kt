package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ek saved recording ki jankari (student screen ki "My Recordings" list ke liye).
 */
data class RecItem(
    val uri: Uri,
    val label: String,
    val location: String, // "App" ya "Gallery"
    val sizeMb: String,
    val dateLabel: String,
    val dateMs: Long
)

/**
 * 🎞️ TAWAZUN SCREEN RECORD — bachay ki poori screen ki PROPER VIDEO (mp4).
 *
 * MediaProjection se screen ko MediaRecorder par likhte hain — jo SECOND nahi
 * chhoota, poori video proper format mein save hoti hai:
 *
 *   • Gallery mode  → Phone ki Gallery → Movies → Tawazun (parent khud choose karta hai)
 *   • App mode      → App ke private folder (Gallery se chhupi hui)
 *
 * FREE plan-friendly: video cloud par NAHI jati (3 ghante ki video = 100s of MB) —
 * bachay ke phone par hi rehti hai, parent phone lekar dekh sakta hai.
 *
 * Chhoti files: har 400 MB par nayi file (segment) — purane phone par 1-2 sec ka
 * gap, naye phone par (API 26+) bilkul seamless switch.
 *
 * Transparency: recording ke pooray waqt phone par notification chalti rehti hai.
 */
class ScreenRecordService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_CHILD_ID = "child_id"
        const val EXTRA_TO_GALLERY = "to_gallery"
        const val ACTION_STOP = "com.tawazun.pksafe.ACTION_STOP_RECORD"
        private const val CHANNEL_ID = "tawazun_screen_record"
        private const val NOTIF_ID = 4211
        private const val MAX_WIDTH = 540 // 540p — size aur quality ka balance
        private const val BIT_RATE = 1_200_000 // ~540 MB/hour
        private const val FRAME_RATE = 24
        private const val MAX_FILE_BYTES = 400L * 1024 * 1024 // har segment max 400 MB

        @Volatile
        var isRunning: Boolean = false
            private set

        /** App folder + Gallery (Movies/Tawazun) — dono ki videos ki list. */
        fun listRecordings(context: Context): List<RecItem> {
            val items = mutableListOf<RecItem>()
            // App ke private folder
            try {
                val dir = File(context.filesDir, "recordings")
                dir.listFiles()?.filter { it.extension.equals("mp4", true) }?.forEach { f ->
                    items += RecItem(
                        uri = Uri.fromFile(f),
                        label = f.name,
                        location = "App",
                        sizeMb = String.format(Locale.US, "%.1f MB", f.length() / 1024.0 / 1024.0),
                        dateLabel = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
                            .format(Date(f.lastModified())),
                        dateMs = f.lastModified()
                    )
                }
            } catch (_: Exception) {
            }
            // Gallery → Movies → Tawazun (API 29+)
            if (Build.VERSION.SDK_INT >= 29) {
                try {
                    context.contentResolver.query(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        arrayOf(
                            MediaStore.Video.Media._ID,
                            MediaStore.Video.Media.DISPLAY_NAME,
                            MediaStore.Video.Media.SIZE,
                            MediaStore.Video.Media.DATE_MODIFIED
                        ),
                        "${MediaStore.Video.Media.RELATIVE_PATH}=?",
                        arrayOf("${Environment.DIRECTORY_MOVIES}/Tawazun"),
                        "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
                    )?.use { c ->
                        val idIdx = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                        val nameIdx = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                        val sizeIdx = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                        val dateIdx = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                        while (c.moveToNext()) {
                            val id = c.getLong(idIdx)
                            val dateSec = c.getLong(dateIdx)
                            items += RecItem(
                                uri = ContentUris.withAppendedId(
                                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id
                                ),
                                label = c.getString(nameIdx) ?: "video",
                                location = "Gallery",
                                sizeMb = String.format(
                                    Locale.US, "%.1f MB", c.getLong(sizeIdx) / 1024.0 / 1024.0
                                ),
                                dateLabel = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
                                    .format(Date(dateSec * 1000)),
                                dateMs = dateSec * 1000
                            )
                        }
                    }
                } catch (_: Exception) {
                }
            }
            return items.sortedByDescending { it.dateMs }
        }
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var toGallery = false
    private var width = 0
    private var height = 0
    private var segment = 0
    private var running = false

    // Filhal jis file par likh rahe hain
    private var activeFile: File? = null
    private var activeUri: Uri? = null
    private var activePfd: ParcelFileDescriptor? = null

    // Agli file (seamless switch — API 26+)
    private var nextFile: File? = null
    private var nextUri: Uri? = null
    private var nextPfd: ParcelFileDescriptor? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRecord()
            return START_NOT_STICKY
        }
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (resultCode == 0 || resultData == null || running) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Gallery sirf Android 10+ par (us se purane par app folder fallback)
        toGallery = intent?.getBooleanExtra(EXTRA_TO_GALLERY, false) == true && Build.VERSION.SDK_INT >= 29
        running = true
        isRunning = true

        // Foreground notification (Android rule + transparency)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }

        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(resultCode, resultData)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopRecord()
            }
        }, null)

        val metrics = resources.displayMetrics
        val scale = (MAX_WIDTH.toFloat() / metrics.widthPixels).coerceAtMost(1f)
        width = ((metrics.widthPixels * scale).toInt() and 0xFFFE)
        height = ((metrics.heightPixels * scale).toInt() and 0xFFFE)

        if (!startSegment()) stopRecord()
        return START_NOT_STICKY
    }

    /** Nayi video segment shuru karo. */
    private fun startSegment(): Boolean {
        return try {
            segment++
            val mr = createRecorder()
            if (toGallery) {
                val uri = newGalleryUri() ?: return false
                val pfd = contentResolver.openFileDescriptor(uri, "rw") ?: return false
                activeUri = uri
                activePfd = pfd
                mr.setOutputFile(pfd.fileDescriptor)
            } else {
                val dir = File(filesDir, "recordings")
                if (!dir.exists()) dir.mkdirs()
                val f = File(dir, "Tawazun_${stamp()}_$segment.mp4")
                activeFile = f
                mr.setOutputFile(f.absolutePath)
            }
            mr.prepare()
            mr.start()
            recorder = mr
            virtualDisplay?.release()
            virtualDisplay = projection?.createVirtualDisplay(
                "tawazun_record",
                width, height, resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mr.surface,
                null, null
            )
            updateNotification()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun createRecorder(): MediaRecorder {
        @Suppress("DEPRECATION")
        val mr = MediaRecorder()
        mr.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mr.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        mr.setVideoSize(width, height)
        mr.setVideoEncodingBitRate(BIT_RATE)
        mr.setVideoFrameRate(FRAME_RATE)
        mr.setMaxFileSize(MAX_FILE_BYTES)
        mr.setOnInfoListener { _, what, _ ->
            when (what) {
                MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_APPROACHING -> prepareNextOutput()
                MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED -> rotateSegment()
            }
        }
        return mr
    }

    /**
     * File bharne wali hai — agli file pehle se tayyar kar do (API 26+ par
     * bilkul seamless: ek second bhi nahi rukta).
     */
    private fun prepareNextOutput() {
        if (Build.VERSION.SDK_INT < 26) return // purane phone par fallback = rotate
        try {
            val mr = recorder ?: return
            if (toGallery) {
                if (nextUri != null) return
                val uri = newGalleryUri() ?: return
                val pfd = contentResolver.openFileDescriptor(uri, "rw") ?: return
                nextUri = uri
                nextPfd = pfd
                mr.setNextOutputFile(pfd.fileDescriptor)
            } else {
                if (nextFile != null) return
                val dir = File(filesDir, "recordings")
                if (!dir.exists()) dir.mkdirs()
                val f = File(dir, "Tawazun_${stamp()}_${segment + 1}.mp4")
                nextFile = f
                mr.setNextOutputFile(f)
            }
        } catch (_: Exception) {
        }
    }

    /** Segment bhar gaya — agle par chalein. */
    private fun rotateSegment() {
        if (!running) return
        val switched = (toGallery && nextUri != null) || (!toGallery && nextFile != null)
        if (switched) {
            // Recorder pehle se agle file par chala gaya — sirf bookkeeping
            val nu = nextUri
            val np = nextPfd
            val nf = nextFile
            finalizeActive()
            activeUri = nu
            activePfd = np
            activeFile = nf
            nextUri = null
            nextPfd = null
            nextFile = null
            segment++
            updateNotification()
        } else {
            // Fallback (purane Android): restart ke sath nayi file
            try { recorder?.stop() } catch (_: Exception) { }
            try { recorder?.release() } catch (_: Exception) { }
            recorder = null
            finalizeActive()
            if (!startSegment()) stopRecord()
        }
    }

    /** Active file ko pakka karo (Gallery mein visible) aur handle band karo. */
    private fun finalizeActive() {
        activeUri?.let { uri ->
            try {
                contentResolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
                    null, null
                )
            } catch (_: Exception) {
            }
        }
        try { activePfd?.close() } catch (_: Exception) { }
        activePfd = null
        activeFile = null
        activeUri = null
    }

    private fun newGalleryUri(): Uri? {
        if (Build.VERSION.SDK_INT < 29) return null
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "Tawazun_${stamp()}_$segment.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Tawazun")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        } catch (e: Exception) {
            null
        }
    }

    private fun stamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tawazun Screen Record",
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, ScreenRecordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🎞️ Tawazun — Video Recording chal rahi hai")
            .setContentText(
                "Screen ki video record ho rahi hai — save: " +
                    (if (toGallery) "Gallery → Movies → Tawazun" else "App folder") +
                    " (segment $segment)"
            )
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    private fun updateNotification() {
        try {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIF_ID, buildNotification())
        } catch (_: Exception) {
        }
    }

    private fun stopRecord() {
        if (!running) {
            stopSelf()
            return
        }
        running = false
        isRunning = false
        try { recorder?.stop() } catch (_: Exception) { }
        try { recorder?.release() } catch (_: Exception) { }
        recorder = null
        virtualDisplay?.release()
        virtualDisplay = null
        try { projection?.stop() } catch (_: Exception) { }
        projection = null
        finalizeActive()
        // Bani hui magar use na hui agli file — saaf karo
        nextUri?.let { uri ->
            try { contentResolver.delete(uri, null, null) } catch (_: Exception) { }
        }
        try { nextPfd?.close() } catch (_: Exception) { }
        nextUri = null
        nextPfd = null
        nextFile = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopRecord()
        super.onDestroy()
    }
}
