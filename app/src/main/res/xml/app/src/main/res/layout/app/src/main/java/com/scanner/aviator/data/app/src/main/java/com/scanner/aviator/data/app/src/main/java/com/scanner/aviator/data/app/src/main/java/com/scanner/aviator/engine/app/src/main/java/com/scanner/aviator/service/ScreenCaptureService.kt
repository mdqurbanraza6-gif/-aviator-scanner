package com.scanner.aviator.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.scanner.aviator.MainActivity
import com.scanner.aviator.data.AppDatabase
import com.scanner.aviator.data.RoundEntity
import com.scanner.aviator.engine.PatternAnalyzer
import kotlinx.coroutines.*
import java.util.regex.Pattern

class ScreenCaptureService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var lastRecordedMultiplier: Double? = null
    private var lastRecordTime: Long = 0

    companion object {
        const val CHANNEL_ID = "AviatorScannerChannel"
        const val NOTIFICATION_ID = 101
        const val ALERT_NOTIFICATION_ID = 102
        const val EXTRA_RESULT_CODE = "RESULT_CODE"
        const val EXTRA_RESULT_DATA = "RESULT_DATA"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification("Scanner active and running."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val resultData = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)

        if (resultCode == Activity.RESULT_OK && resultData != null) {
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpManager.getMediaProjection(resultCode, resultData)
            setupVirtualDisplay()
            startCaptureLoop()
        } else {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    @SuppressLint("WrongConstant")
    private fun setupVirtualDisplay() {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "AviatorScannerCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
    }

    private fun startCaptureLoop() {
        serviceScope.launch {
            while (isActive) {
                delay(1200)
                captureAndProcessFrame()
            }
        }
    }

    private fun captureAndProcessFrame() {
        val image = imageReader?.acquireLatestImage() ?: return
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        image.close()

        val cropHeight = (bitmap.height * 0.35).toInt()
        val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, cropHeight)

        val inputImage = InputImage.fromBitmap(croppedBitmap, 0)
        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                parseMultipliers(visionText.text)
            }
    }

    private fun parseMultipliers(text: String) {
        val pattern = Pattern.compile("(\\d{1,4}\\.\\d{1,2})[xX]")
        val matcher = pattern.matcher(text)

        val foundList = mutableListOf<Double>()
        while (matcher.find()) {
            matcher.group(1)?.toDoubleOrNull()?.let { foundList.add(it) }
        }

        if (foundList.isNotEmpty()) {
            val latest = foundList.first()
            val now = System.currentTimeMillis()

            if (latest != lastRecordedMultiplier || (now - lastRecordTime > 6000)) {
                lastRecordedMultiplier = latest
                lastRecordTime = now
                saveRound(latest)
            }
        }
    }

    private fun saveRound(multiplier: Double) {
        serviceScope.launch {
            val dao = AppDatabase.getInstance(applicationContext).roundDao()
            dao.insert(RoundEntity(multiplier = multiplier))
            dao.pruneOldRecords()

            val all = dao.getAllRoundsList()
            val match = PatternAnalyzer.analyzeRecentSequence(all)
            if (match != null && match.occurrences >= 2) {
                notifyPatternMatch(match.matchedSequence, match.occurrences)
            }
        }
    }

    private fun notifyPatternMatch(seq: List<Double>, occurrences: Int) {
        val seqStr = seq.joinToString(", ") { "${it}x" }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Pattern Recurred")
            .setContentText("Sequence [$seqStr] was recorded $occurrences times previously.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(ALERT_NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Aviator Scanner Notifications",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Aviator Pattern Scanner Active")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
    }
}
