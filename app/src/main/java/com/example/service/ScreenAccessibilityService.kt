package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class ScreenAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        sInstance = this
        Log.d(TAG, "ScreenAccessibilityService connected successfully.")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        sInstance = null
        Log.d(TAG, "ScreenAccessibilityService disconnected.")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        sInstance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No specific events processing is required for gesture dispatching,
        // but this method must be overridden.
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service interrupted.")
    }

    /**
     * Injects a click gesture at (x, y) coordinates globally.
     */
    fun performClick(x: Int, y: Int): Boolean {
        Log.d(TAG, "Simulating click at ($x, $y)")
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 50L)
        val gestureBuilder = GestureDescription.Builder().apply {
            addStroke(stroke)
        }

        return try {
            dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    Log.d(TAG, "Click gesture successfully dispatched.")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    Log.e(TAG, "Click gesture cancelled.")
                }
            }, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed dispatching click gesture", e)
            false
        }
    }

    /**
     * Injects a scrolling/swiping gesture globally depending on the direction.
     */
    fun performScroll(direction: String): Boolean {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels

        val startX: Float
        val startY: Float
        val endX: Float
        val endY: Float

        when (direction.uppercase()) {
            "UP" -> {
                // Drag down to scroll UP
                startX = (width / 2).toFloat()
                startY = (height * 0.3f)
                endX = (width / 2).toFloat()
                endY = (height * 0.8f)
            }
            "DOWN" -> {
                // Drag up to scroll DOWN
                startX = (width / 2).toFloat()
                startY = (height * 0.8f)
                endX = (width / 2).toFloat()
                endY = (height * 0.3f)
            }
            "LEFT" -> {
                // Drag right to scroll LEFT
                startX = (width * 0.2f)
                startY = (height / 2).toFloat()
                endX = (width * 0.8f)
                endY = (height / 2).toFloat()
            }
            "RIGHT" -> {
                // Drag left to scroll RIGHT
                startX = (width * 0.8f)
                startY = (height / 2).toFloat()
                endX = (width * 0.2f)
                endY = (height / 2).toFloat()
            }
            else -> {
                Log.e(TAG, "Unknown drag direction $direction")
                return false
            }
        }

        Log.d(TAG, "Simulating drag from ($startX, $startY) to ($endX, $endY) for scrolling $direction")

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0L, 350L)
        val gestureBuilder = GestureDescription.Builder().apply {
            addStroke(stroke)
        }

        return try {
            dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    Log.d(TAG, "Scroll/Drag gesture successful.")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    Log.e(TAG, "Scroll/Drag gesture cancelled.")
                }
            }, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed dispatching scroll gesture", e)
            false
        }
    }

    companion object {
        private const val TAG = "AccessibilityService"

        @Volatile
        private var sInstance: ScreenAccessibilityService? = null

        val instance: ScreenAccessibilityService? get() = sInstance
        val isServiceConnected: Boolean get() = sInstance != null
    }
}
