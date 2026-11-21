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

data class RecItem(
    val uri: Uri,
    val label: String,
    val location: String,
    val sizeMb: String,
    val dateLabel: String,
    val dateMs: Long
)

/**
 * Screen recording is started only with a fresh MediaProjection consent result.
 * The foreground notification remains visible with an immediate Stop action.
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
        private const val MAX_WIDTH = 540
        private const val BIT_RATE = 1_200_000
        private const val FRAME_RATE = 24
        private const val MAX_FILE_BYTES = 400L * 1024 * 1024

        @Volatile
        var isRunning: Boolean = false
            private set

        fun listRecordings(context: Context): List<RecItem> {
            val items = mutableListOf<RecItem>()
            try {
                val dir = File(context.filesDir, "recordings")
                dir.listFiles()?.filter { it.extension.equals("mp4", true) }?.forEach { file ->
                    items += RecItem(
                        uri = Uri.fromFile(file),
                        label = file.name,
                        location = "App",
                        sizeMb = String.format(Locale.US, "%.1f MB", file.length() / 1024.0 / 1024.0),
                        dateLabel = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(file.lastModified())),
                        dateMs = file.lastModified()
                    )
                }
            } catch (_: Exception) { }
            if (Build.VERSION.SDK_INT >= 29) {
                try {
                    context.contentResolver.query(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME,
                            MediaStore.Video.Media.SIZE, MediaStore.Video.Media.DATE_MODIFIED),
                        "${MediaStore.Video.Media.RELATIVE_PATH}=?",
                        arrayOf("${Environment.DIRECTORY_MOVIES}/Tawazun"),
                        "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
                    )?.use { cursor ->
                        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                        val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                        val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                        while (cursor.moveToNext()) {
                            val dateSeconds = cursor.getLong(dateIndex)
                            items += RecItem(
                                uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cursor.getLong(idIndex)),
                                label = cursor.getString(nameIndex) ?: "video",
                                location = "Gallery",
                                sizeMb = String.format(Locale.US, "%.1f MB", cursor.getLong(sizeIndex) / 1024.0 / 1024.0),
                                dateLabel = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(dateSeconds * 1000)),
                                dateMs = dateSeconds * 1000
                            )
                        }
                    }
                } catch (_: Exception) { }
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
    private var activeFile: File? = null
    private var activeUri: Uri? = null
    private var activePfd: ParcelFileDescriptor? = null
    private var nextFile: File? = null
    private var nextUri: Uri? = null
    private var nextPfd: ParcelFileDescriptor? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRecord()
            return START_NOT_STICKY
        }
        // Do not attempt to reuse a MediaProjection token or start a second capture session.
        if (running) return START_NOT_STICKY
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (resultCode == 0 || resultData == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        toGallery = intent.getBooleanExtra(EXTRA_TO_GALLERY, false) && Build.VERSION.SDK_INT >= 29
        try {
            // Promote immediately, before obtaining/using MediaProjection (Android 14+ requirement).
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(NOTIF_ID, buildNotification())
            }
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, resultData)
                ?: throw IllegalStateException("Android did not create a screen-capture session")
            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() = stopRecord()
            }, android.os.Handler(mainLooper))

            val metrics = resources.displayMetrics
            val scale = (MAX_WIDTH.toFloat() / metrics.widthPixels).coerceAtMost(1f)
            width = ((metrics.widthPixels * scale).toInt() and 0xFFFE)
            height = ((metrics.heightPixels * scale).toInt() and 0xFFFE)
            running = true
            isRunning = true
            if (!startSegment()) stopRecord()
        } catch (_: SecurityException) {
            stopRecord()
        } catch (_: Exception) {
            stopRecord()
        }
        return START_NOT_STICKY
    }

    private fun startSegment(): Boolean = try {
        segment++
        val nextRecorder = createRecorder()
        if (toGallery) {
            val uri = newGalleryUri() ?: return false
            val pfd = contentResolver.openFileDescriptor(uri, "rw") ?: return false
            activeUri = uri
            activePfd = pfd
            nextRecorder.setOutputFile(pfd.fileDescriptor)
        } else {
            val dir = File(filesDir, "recordings")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "Tawazun_${stamp()}_$segment.mp4")
            activeFile = file
            nextRecorder.setOutputFile(file.absolutePath)
        }
        nextRecorder.prepare()
        nextRecorder.start()
        recorder = nextRecorder

        // Android 14 allows one VirtualDisplay per MediaProjection session. Reuse it
        // when rotating recording segments and change only its output surface.
        val existingDisplay = virtualDisplay
        if (existingDisplay == null) {
            virtualDisplay = projection?.createVirtualDisplay(
                "tawazun_record", width, height, resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, nextRecorder.surface, null, null
            ) ?: throw IllegalStateException("Could not create screen recording display")
        } else {
            existingDisplay.setSurface(nextRecorder.surface)
        }
        updateNotification()
        true
    } catch (_: Exception) {
        false
    }

    private fun createRecorder(): MediaRecorder {
        @Suppress("DEPRECATION")
        val mediaRecorder = MediaRecorder()
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        mediaRecorder.setVideoSize(width, height)
        mediaRecorder.setVideoEncodingBitRate(BIT_RATE)
        mediaRecorder.setVideoFrameRate(FRAME_RATE)
        mediaRecorder.setMaxFileSize(MAX_FILE_BYTES)
        mediaRecorder.setOnInfoListener { _, what, _ ->
            when (what) {
                MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_APPROACHING -> prepareNextOutput()
                MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED -> rotateSegment()
            }
        }
        return mediaRecorder
    }

    private fun prepareNextOutput() {
        if (Build.VERSION.SDK_INT < 26) return
        try {
            val currentRecorder = recorder ?: return
            if (toGallery) {
                if (nextUri != null) return
                val uri = newGalleryUri() ?: return
                val pfd = contentResolver.openFileDescriptor(uri, "rw") ?: return
                nextUri = uri
                nextPfd = pfd
                currentRecorder.setNextOutputFile(pfd.fileDescriptor)
            } else {
                if (nextFile != null) return
                val dir = File(filesDir, "recordings")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, "Tawazun_${stamp()}_${segment + 1}.mp4")
                nextFile = file
                currentRecorder.setNextOutputFile(file)
            }
        } catch (_: Exception) { }
    }

    private fun rotateSegment() {
        if (!running) return
        val switched = (toGallery && nextUri != null) || (!toGallery && nextFile != null)
        if (switched) {
            val uri = nextUri
            val pfd = nextPfd
            val file = nextFile
            finalizeActive()
            activeUri = uri
            activePfd = pfd
            activeFile = file
            nextUri = null
            nextPfd = null
            nextFile = null
            segment++
            updateNotification()
        } else {
            try { recorder?.stop() } catch (_: Exception) { }
            try { recorder?.release() } catch (_: Exception) { }
            recorder = null
            finalizeActive()
            if (!startSegment()) stopRecord()
        }
    }

    private fun finalizeActive() {
        activeUri?.let { uri ->
            try {
                contentResolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            } catch (_: Exception) { }
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
        } catch (_: Exception) { null }
    }

    private fun stamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Tawazun Screen Record", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, ScreenRecordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tawazun — screen recording is active")
            .setContentText("Screen video is being saved to ${if (toGallery) "Movies/Tawazun" else "the app's private folder"}.")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    private fun updateNotification() {
        try { (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, buildNotification()) }
        catch (_: Exception) { }
    }

    private fun stopRecord() {
        if (!running && projection == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        running = false
        isRunning = false
        try { recorder?.stop() } catch (_: Exception) { }
        try { recorder?.release() } catch (_: Exception) { }
        recorder = null
        try { virtualDisplay?.setSurface(null) } catch (_: Exception) { }
        virtualDisplay?.release()
        virtualDisplay = null
        try { projection?.stop() } catch (_: Exception) { }
        projection = null
        finalizeActive()
        nextUri?.let { uri -> try { contentResolver.delete(uri, null, null) } catch (_: Exception) { } }
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
