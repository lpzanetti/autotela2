package com.example.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.data.database.AutomationDatabase
import com.example.data.model.AutomationAction
import com.example.data.model.AutomationTemplate
import com.example.data.repository.AutomationRepository
import com.example.util.OpenCVHelper
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.roundToInt

class AutomationForegroundService : Service() {

    private val db get() = AutomationDatabase.getInstance(this)
    private val repository get() = AutomationRepository(db.dao)

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var automationJob: Job? = null

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    var currentTemplateId: Long? = null
        private set

    @Volatile
    private var isAutomationActive = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START -> {
                val templateId = intent.getLongExtra(EXTRA_TEMPLATE_ID, -1L)
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData: Intent? = intent.getParcelableExtra(EXTRA_RESULT_DATA)
                if (templateId != -1L && resultCode != 0 && resultData != null) {
                    startAutomation(templateId, resultCode, resultData)
                } else {
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                stopAutomation()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun startAutomation(templateId: Long, resultCode: Int, resultData: Intent) {
        if (isAutomationActive) return
        isAutomationActive = true
        currentTemplateId = templateId

        // Start Foreground Service with Notifications
        val notification = createNotification("Automation em execução", "Procurando templates na tela...")
        startForeground(NOTIFICATION_ID, notification)

        // Initialize MediaProjection
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = projectionManager.getMediaProjection(resultCode, resultData)
        if (projection != null) {
            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.w(TAG, "MediaProjection stopped by system.")
                    stopAutomation()
                }
            }, Handler(Looper.getMainLooper()))
            mediaProjection = projection
        } else {
            Log.e(TAG, "Failed to get MediaProjection instance.")
            stopAutomation()
            stopSelf()
            return
        }

        // Initialize Screen Capture Flow
        setupVirtualDisplay()

        // Create overlay button to let user cancel easily
        showFloatingStopButton()

        // Sync visual running status to database
        serviceScope.launch {
            val template = repository.getTemplateByIdSync(templateId)
            if (template != null) {
                repository.updateTemplate(template.copy(isRunning = true))
            }
        }

        // Launch Loop Engine
        automationJob = serviceScope.launch {
            runAutomationLoop(templateId)
        }
    }

    private fun setupVirtualDisplay() {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        // Acquire 2 buffer size image reader for smooth processing
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).apply {
            setOnImageAvailableListener({
                // Real-time reader is pulled on demand during loop
            }, null)
        }

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "AutomationCapture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )
        Log.d(TAG, "VirtualDisplay created: ${width}x${height} @ ${density}dpi")
    }

    private suspend fun runAutomationLoop(templateId: Long) {
        val actions = repository.getActionsForTemplateSync(templateId)
        if (actions.isEmpty()) {
            Log.e(TAG, "No actions found for template $templateId.")
            withContext(Dispatchers.Main) { stopAutomation() }
            return
        }

        var actionIndex = 0

        while (isAutomationActive) {
            val action = actions[actionIndex]
            Log.d(TAG, "Starting action sequence [${actionIndex + 1}/${actions.size}]: ${action.name}")

            val cropFile = action.cropImagePath?.let { File(it) }
            if (cropFile == null || !cropFile.exists()) {
                Log.e(TAG, "Crop file missing or invalid for action: ${action.name}. Advancing.")
                actionIndex = (actionIndex + 1) % actions.size
                delay(1000)
                continue
            }

            val cropBitmap = BitmapFactory.decodeFile(cropFile.absolutePath)
            if (cropBitmap == null) {
                Log.e(TAG, "Failed to decode crop bitmap. Advancing.")
                actionIndex = (actionIndex + 1) % actions.size
                delay(1000)
                continue
            }

            var detectedCenter: Point? = null
            val startTime = System.currentTimeMillis()
            val timeoutMs = action.timeoutSeconds * 1000L

            // Try to match template until target is found or Timeout occurs
            while (isAutomationActive && detectedCenter == null) {
                val screenBitmap = captureLatestScreen()
                if (screenBitmap != null) {
                    detectedCenter = OpenCVHelper.findTemplate(
                        screenBitmap,
                        cropBitmap,
                        action.threshold
                    )
                }

                if (detectedCenter != null) {
                    Log.d(TAG, "Target localized at: $detectedCenter")
                    break
                }

                if (System.currentTimeMillis() - startTime >= timeoutMs) {
                    Log.w(TAG, "Timeout of ${action.timeoutSeconds}s elapsed for action: ${action.name}")
                    break
                }

                // Check interval frequency to conserve CPU performance (approx. 5 matches per second)
                delay(200)
            }

            // Execute Touch or Scroll gesture on accessibility service
            if (isAutomationActive) {
                var executedSuccessfully = false
                val accessory = ScreenAccessibilityService.instance

                if (accessory == null) {
                    Log.e(TAG, "Accessibility Service is not connected! Please activate it in Android Settings.")
                } else {
                    if (action.actionType.uppercase() == "ROLAGEM") {
                        executedSuccessfully = accessory.performScroll(action.scrollDirection)
                    } else {
                        // Click gesture (TOQUE)
                        val targetX = action.manualX ?: detectedCenter?.x
                        val targetY = action.manualY ?: detectedCenter?.y

                        if (targetX != null && targetY != null) {
                            executedSuccessfully = accessory.performClick(targetX, targetY)
                        } else {
                            Log.w(TAG, "Action skips touch because coordinates are null (image not found & no manual coord).")
                        }
                    }
                }

                cropBitmap.recycle()

                // Wait standard debounce time between inputs (default 2 seconds)
                delay(2000)

                // Advance to next action sequence
                actionIndex = (actionIndex + 1) % actions.size
            }
        }
    }

    private fun captureLatestScreen(): Bitmap? {
        val reader = imageReader ?: return null
        var image: android.media.Image? = null
        try {
            image = reader.acquireLatestImage()
            if (image == null) return null

            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val metrics = resources.displayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels

            val rowPadding = rowStride - pixelStride * width
            val bitmapWidth = width + rowPadding / pixelStride

            val rawBitmap = Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888)
            rawBitmap.copyPixelsFromBuffer(buffer)

            val cleanBitmap = if (rowPadding > 0) {
                Bitmap.createBitmap(rawBitmap, 0, 0, width, height)
            } else {
                rawBitmap
            }

            if (scaleCorrectionNeeded(width, height)) {
                // Apply optional safe scale/DPI conversions as normalizations if device resolution deviates significantly
            }

            return cleanBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Failed capturing screen frame buffer", e)
            return null
        } finally {
            image?.close()
        }
    }

    private fun scaleCorrectionNeeded(width: Int, height: Int): Boolean {
        // Return check if we need custom DPI normalizations
        return false
    }

    private fun stopAutomation() {
        if (!isAutomationActive) return
        isAutomationActive = false
        Log.d(TAG, "Stopping screen automation motor.")

        automationJob?.cancel()
        automationJob = null

        // Reset database indicator
        currentTemplateId?.let { id ->
            serviceScope.launch {
                val template = repository.getTemplateByIdSync(id)
                if (template != null) {
                    repository.updateTemplate(template.copy(isRunning = false))
                }
            }
        }
        currentTemplateId = null

        // Clear display resources
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null

        mediaProjection?.stop()
        mediaProjection = null

        // Remove float controls overlay from Window
        hideFloatingStopButton()

        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingStopButton() {
        if (overlayView != null || !Settings.canDrawOverlays(this)) return

        // Inflate stop circle button visual dynamically
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val frame = FrameLayout(this)
        val view = Button(this).apply {
            text = "🛑 STOP"
            setBackgroundColor(0xFFFF2222.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(32, 20, 32, 20)
            textSize = 14f
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        frame.addView(view)
        overlayView = frame

        val layoutParams = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }

        // Add Drag-to-Move gestures to the overlay
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0.0f
        var initialTouchY = 0.0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).roundToInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).roundToInt()
                    windowManager?.updateViewLayout(frame, layoutParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val diffX = event.rawX - initialTouchX
                    val diffY = event.rawY - initialTouchY
                    if (diffX * diffX + diffY * diffY < 100) {
                        // Triggers cancel stopping on clean short press tap
                        Log.d(TAG, "Floating action clicked. Stopping automation service.")
                        stopAutomation()
                        stopSelf()
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(frame, layoutParams)
        } catch (e: Exception) {
            Log.e(TAG, "Failed showing floating overlay button", e)
        }
    }

    private fun hideFloatingStopButton() {
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay", e)
            }
            overlayView = null
        }
    }

    override fun onDestroy() {
        stopAutomation()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Automation Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificação ativa de controle do motor de visão do bot"
            }
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(title: String, content: String): Notification {
        val stopIntent = Intent(this, AutomationForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Point back safely to normal app MainActivity
        val appIntent = packageManager.getLaunchIntentForPackage(packageName)
        val appPendingIntent = PendingIntent.getActivity(
            this,
            1,
            appIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(appPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Parar Motor", stopPendingIntent)

        return builder.build()
    }

    companion object {
        private const val TAG = "AutomationService"
        private const val NOTIFICATION_ID = 54321
        private const val CHANNEL_ID = "automation_foreground_service_channel"

        const val ACTION_START = "com.example.service.ACTION_START"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"

        const val EXTRA_TEMPLATE_ID = "EXTRA_TEMPLATE_ID"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
    }
}
