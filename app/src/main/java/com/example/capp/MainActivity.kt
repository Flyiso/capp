
package com.example.capp

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.capp.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors





class MainActivity : androidx.activity.ComponentActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var palettes: List<ColorPalette>
    private val REQUEST_CODE_PERMISSIONS = 10
    private val REQUIRED_PERMISSIONS = arrayOf(android.Manifest.permission.CAMERA)

    private lateinit var cameraExecutor: ExecutorService
    private var isCameraFrozen = false

    private fun getAverageHsv(bitmap: Bitmap): FloatArray {
        val tinyBitmap = Bitmap.createScaledBitmap(bitmap, 1, 1, true)
        val averageColor = tinyBitmap.getPixel(0, 0)
        val hsv = FloatArray(3)
        Color.colorToHSV(averageColor, hsv)
        tinyBitmap.recycle()
        return hsv
    }
    val onExpandCallback = { clickedPalette: ColorPalette, isExpanding: Boolean ->
        isCameraFrozen = isExpanding
        if (isExpanding) {
            binding.frozenOverlay.visibility = View.VISIBLE
        } else {
            binding.frozenOverlay.visibility = View.GONE
        }
        palettes.forEach { if (it != clickedPalette) it.collapse() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.scannerOverlay.setOnRectChangedListener(object : ScannerOverlay.OnRectChangedListener {
            override fun onRectChanged(rect: RectF) {
                if (isCameraFrozen) {
                    val sourceBitmap = lastFullBitmap ?: return
                    val cropped = cropToScanner(sourceBitmap, rect)
                    val hsv = getAverageHsv(cropped)
                    palettes.forEach { it.applyColors(hsv) }
                }
            }
        })
        hideSystemUI()

        val onExpandCallback = { clickedPalette: ColorPalette, isExpanding: Boolean ->
            isCameraFrozen = isExpanding
            palettes.forEach {
                if (it != clickedPalette) it.collapse()
            }
        }
        val allPalettes = mutableListOf<ColorPalette>()
        palettes = listOf(
            ColorPalette("Complementary", binding.sectionComp, binding.sectionCompBlur,
                listOf(0f, 180f), listOf(0f, 0f), listOf(0f, 0f),
                onExpandCallback),
            ColorPalette("Analogous", binding.sectionAnalog, binding.sectionAnalogBlur,
                listOf(0f, 30f, -30f), listOf(0f, 0f, 0f), listOf(0f, 0f, 0f),
                onExpandCallback),
            ColorPalette("Split Complementary", binding.sectionSplitcomp, binding.sectionSplitcompBlur,
                listOf(0f, 150f, 210f) , listOf(0f, 0f, 0f), listOf(0f, 0f, 0f),
                onExpandCallback),
            ColorPalette("Triadic", binding.sectionTri, binding.sectionTriBlur,
                listOf(0f, 120f, 240f), listOf(0f, 0f, 0f), listOf(0f, 0f, 0f),
                onExpandCallback),
            ColorPalette("Square", binding.sectionSquare, binding.sectionSquareBlur,
                listOf(0f, 90f, 180f, 270f), listOf(0f, 0f, 0f, 0f), listOf(0f, 0f, 0f, 0f),
                onExpandCallback),
            ColorPalette("Tetradic", binding.sectionTet, binding.sectionTetBlur,
                listOf(0f, 60f, 180f, 240f), listOf(0f, 0f, 0f, 0f), listOf(0f, 0f, 0f, 0f),
                onExpandCallback),
            ColorPalette("Monochromatic",binding.sectionMono,binding.sectionMonoBlur,
                listOf(0f, 0f, 0f, 0f, 0f),listOf(-35f, -0f, 10f, 20f, -70f),listOf(15f, 0f, -25f, -50f, 20f),
                onExpandCallback))

        binding.root.post {
            palettes.forEach { palette ->
                allPalettes.add(palette)
                palette.blurView.setupWith(binding.blurTargetRoot)
                    .setBlurRadius(15f)

                palette.inflate(this)
            }
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    private fun hideSystemUI() {
        val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)

        windowInsetsController.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    }

    private var lastFullBitmap: Bitmap? = null

    private fun startCamera() {

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = binding.viewFinder.surfaceProvider
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                if (isCameraFrozen) {
                    imageProxy.close()
                    return@setAnalyzer
                }

                val rotation = imageProxy.imageInfo.rotationDegrees
                val uiRect = binding.scannerOverlay.getSelectionRect()
                val bitmap = imageProxy.toBitmap()
                val rotatedBitmap = rotateBitmap(bitmap, rotation)
                lastFullBitmap = rotatedBitmap

                val finalBitmap = cropToScanner(rotatedBitmap, uiRect)
                //showDebugCrop(finalBitmap)
                val tinyBitmap = Bitmap.createScaledBitmap(finalBitmap, 1, 1, true)
                val averageColor = tinyBitmap.getPixel(0, 0)
                val hsv = FloatArray(3)
                Color.colorToHSV(averageColor, hsv)


                runOnUiThread {

                    binding.frozenOverlay.setImageBitmap(rotatedBitmap)
                    binding.frozenOverlay.visibility = View.VISIBLE

                    palettes.forEach { palette ->
                        palette.applyColors(hsv)
                    }

                }

                imageProxy.close()
            }


            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
            } catch(exc: Exception) {
                Log.e("CameraX", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))

    }


    private fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return bitmap
        val matrix = android.graphics.Matrix()
        matrix.postRotate(rotationDegrees.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    private fun cropToScanner(fullFrame: Bitmap, uiRect: RectF): Bitmap {
        val overlay = binding.scannerOverlay

        val viewW = overlay.width.toFloat()

        val bmpW = fullFrame.width.toFloat()

        val scale = bmpW / viewW

        val cropL = (uiRect.left * scale).toInt()
        val cropT = (uiRect.top * scale).toInt()
        val cropW = (uiRect.width() * scale).toInt()
        val cropH = ((uiRect.height() * scale)).toInt()

        val x = cropL.coerceIn(0, fullFrame.width - 1)
        val y = cropT.coerceIn(0, fullFrame.height - 1)
        val w = cropW.coerceAtMost(fullFrame.width - x).coerceAtLeast(1)
        val h = cropH.coerceAtMost(fullFrame.height - y).coerceAtLeast(1)

        return Bitmap.createBitmap(fullFrame, x, y, w, h)
    }


    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private var debugImageView: ImageView? = null

    private fun showDebugCrop(bitmap: Bitmap) {
        // This forces the following code to execute on the UI thread
        runOnUiThread {
            try {
                if (debugImageView == null) {
                    debugImageView = ImageView(this).apply {
                        // Define size and position
                        layoutParams = ViewGroup.MarginLayoutParams(300, 300).apply {
                            topMargin = 200 // Pushed down so it's not under the status bar
                            marginStart = 50
                        }

                        // Visual styling for the debug window
                        setBackgroundColor(android.graphics.Color.RED)
                        setPadding(8, 8, 8, 8)
                        scaleType = ImageView.ScaleType.FIT_CENTER

                        // Add it to the activity's root layout
                        (binding.root as? ViewGroup)?.addView(this)
                    }
                }

                // Set the bitmap to the view
                debugImageView?.setImageBitmap(bitmap)

            } catch (e: Exception) {
                android.util.Log.e("DebugCrop", "Failed to show debug view: ${e.message}")
            }
        }
    }
}
