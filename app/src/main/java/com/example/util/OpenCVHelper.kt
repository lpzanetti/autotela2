package com.example.util

import android.graphics.Bitmap
import android.graphics.Point
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

object OpenCVHelper {
    private const val TAG = "OpenCVHelper"
    var isInitialized = false
        private set

    init {
        try {
            if (OpenCVLoader.initDebug()) {
                isInitialized = true
                Log.d(TAG, "OpenCV initialized successfully.")
            } else {
                Log.e(TAG, "OpenCV initialization failed.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "OpenCV loader exception", e)
        }
    }

    /**
     * Locates a sub-image template within a target screen image using template matching.
     * Releasing OpenCV Mats properly to avoid memory leaks.
     *
     * @param screen The full screenshot Bitmap.
     * @param crop The template sub-image Bitmap.
     * @param threshold The confidence value (0.0 to 1.0) required for matching.
     * @param resizeWidth Optional target width for screen inside matching to limit resolution and save CPU.
     * @return Point of the matched sub-image center in screen coordinates, or null if not found.
     */
    fun findTemplate(
        screen: Bitmap,
        crop: Bitmap,
        threshold: Double,
        resizeWidth: Int = 540 // downscale target width for faster match if original is too high-res
    ): Point? {
        if (!isInitialized) {
            Log.e(TAG, "OpenCV not initialized. Cannot matching templates.")
            return null
        }

        // 1. Calculate downscale ratio for execution efficiency
        val scale = if (screen.width > resizeWidth && resizeWidth > 0) {
            resizeWidth.toFloat() / screen.width.toFloat()
        } else {
            1.0f
        }

        val runScreen: Bitmap
        val runCrop: Bitmap
        if (scale < 1.0f) {
            runScreen = Bitmap.createScaledBitmap(screen, (screen.width * scale).toInt(), (screen.height * scale).toInt(), true)
            runCrop = Bitmap.createScaledBitmap(crop, (crop.width * scale).toInt(), (crop.height * scale).toInt(), true)
        } else {
            runScreen = screen
            runCrop = crop
        }

        // Protect against empty or invalid scaled bitmaps
        if (runCrop.width <= 0 || runCrop.height <= 0 || runScreen.width < runCrop.width || runScreen.height < runCrop.height) {
            Log.e(TAG, "Invalid dimensions: Template matches size restrictions. Screen: ${runScreen.width}x${runScreen.height}, Crop: ${runCrop.width}x${runCrop.height}")
            return null
        }

        val imgMat = Mat()
        val tplMat = Mat()
        val imgGray = Mat()
        val tplGray = Mat()
        val resultMat = Mat()

        try {
            // Conversion safely to Mats
            Utils.bitmapToMat(runScreen, imgMat)
            Utils.bitmapToMat(runCrop, tplMat)

            // Grayscale conversions for maximum processing speed and reliability
            Imgproc.cvtColor(imgMat, imgGray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.cvtColor(tplMat, tplGray, Imgproc.COLOR_RGBA2GRAY)

            // In case of transparency or different formats, ensure standard CvType
            imgGray.convertTo(imgGray, CvType.CV_8U)
            tplGray.convertTo(tplGray, CvType.CV_8U)

            val width = imgGray.cols() - tplGray.cols() + 1
            val height = imgGray.rows() - tplGray.rows() + 1
            if (width <= 0 || height <= 0) return null

            // OpenCV template matching
            Imgproc.matchTemplate(imgGray, tplGray, resultMat, Imgproc.TM_CCOEFF_NORMED)

            // Localize high correlation peaks
            val result = Core.minMaxLoc(resultMat)
            val maxVal = result.maxVal
            val maxLoc = result.maxLoc

            Log.d(TAG, "Template matching result maxVal: $maxVal vs threshold: $threshold")

            if (maxVal >= threshold) {
                // Determine center coordinates of matching template bounding box
                val centerX = maxLoc.x + tplGray.cols() / 2.0
                val centerY = maxLoc.y + tplGray.rows() / 2.0

                // Rescale coordinates back to the original screen space
                val finalX = (centerX / scale).toInt()
                val finalY = (centerY / scale).toInt()

                return Point(finalX, finalY)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Template matching computation failed", e)
        } finally {
            // Explicit Memory Cleanups to prevent CPU/Memory bottlenecks
            imgMat.release()
            tplMat.release()
            imgGray.release()
            tplGray.release()
            resultMat.release()
        }

        return null
    }
}
