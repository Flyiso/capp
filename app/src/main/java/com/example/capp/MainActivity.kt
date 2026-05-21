
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
// new imports
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.PopupWindow
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.widget.RadioGroup
// more new imports
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


// class MainActivity : androidx.activity.ComponentActivity()
class MainActivity : androidx.appcompat.app.AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var palettes: List<ColorPalette>
    private val REQUEST_CODE_PERMISSIONS = 10
    private val REQUIRED_PERMISSIONS = arrayOf(android.Manifest.permission.CAMERA)

    private lateinit var cameraExecutor: ExecutorService
    private var isCameraFrozen = false
    private var currentColorMode = "HSV"
    private var currentCameraMode = "BACK"
    private var lastSetColor = floatArrayOf(0f, 1f, 1f)

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
        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        currentColorMode = prefs.getString("color_mode", "HSL") ?: "HSL"
        currentCameraMode = prefs.getString("camera_mode", "BACK") ?: "BACK"

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // this is also new
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                ColorMatches.initializeDatabase(applicationContext)
            }
        }
        // end of this new section

        binding.scannerOverlay.setOnRectChangedListener(object : ScannerOverlay.OnRectChangedListener {
            override fun onRectChanged(rect: RectF) {
                if (isCameraFrozen) {
                    val sourceBitmap = lastFullBitmap ?: return
                    val cropped = cropToScanner(sourceBitmap, rect)
                    val hsv = getAverageHsv(cropped)
                    palettes.forEach { it.applyColors(hsv, currentColorMode) }
                    lastSetColor = hsv
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
        setupDraggableFab()  // new

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
                        palette.applyColors(hsv, currentColorMode)
                        lastSetColor = hsv
                    }

                }

                imageProxy.close()
            }

            //val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            val cameraSelector = if (currentCameraMode == "FRONT") {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

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

    // new below here
    private fun setupDraggableFab() {
        val fab = binding.fabSettings
        var dX = 0f
        var dY = 0f
        var lastAction = 0

        // 1. Standard click listener for Accessibility and Taps
        fab.setOnClickListener {
            showSettingsPopup()
        }

        // 2. Touch listener for Dragging logic
        fab.setOnTouchListener { view, event ->
            val screenWidth = resources.displayMetrics.widthPixels
            val screenHeight = resources.displayMetrics.heightPixels

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dX = view.x - event.rawX
                    dY = view.y - event.rawY
                    lastAction = MotionEvent.ACTION_DOWN
                }
                MotionEvent.ACTION_MOVE -> {
                    var newX = event.rawX + dX
                    var newY = event.rawY + dY

                    newX = newX.coerceIn(0f, (screenWidth - view.width).toFloat())
                    newY = newY.coerceIn(0f, (screenHeight - view.height).toFloat())

                    view.x = newX
                    view.y = newY

                    // If the finger moves significantly, mark it as a MOVE
                    if (Math.abs(event.rawX + dX - view.x) > 5 || Math.abs(event.rawY + dY - view.y) > 5) {
                        lastAction = MotionEvent.ACTION_MOVE
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (lastAction == MotionEvent.ACTION_DOWN) {
                        // This triggers the setOnClickListener defined above
                        view.performClick()
                    } else {
                        // Snap to edge
                        val middle = screenWidth / 2
                        val nearestX = if (view.x + (view.width / 2) < middle) 0f
                        else (screenWidth - view.width).toFloat()

                        view.animate().x(nearestX).setDuration(200).start()
                    }
                }
            }
            true
        }
    }

    private fun showSettingsPopup() {

        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val popupView = inflater.inflate(R.layout.popup_settings, null)
        val popupWindow = PopupWindow(
            popupView,
            (resources.displayMetrics.widthPixels * 0.8).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        )

        val radioGroup = popupView.findViewById<RadioGroup>(R.id.radioGroupFormat)

        // --- ADD THIS PART TO REMEMBER THE CHOICE WHILE THE APP IS RUNNING ---
        when (currentColorMode) {
            "RGB" -> radioGroup.check(R.id.radioRGB)
            "HEX" -> radioGroup.check(R.id.radioHEX)
            "HSV" -> radioGroup.check(R.id.radioHSV)
            "CMY" -> radioGroup.check(R.id.radioCMY)
            "NCS" -> radioGroup.check(R.id.radioNCS)
        }

        radioGroup.setOnCheckedChangeListener { group, checkedId ->
            when (checkedId) {
                R.id.radioRGB -> {
                    Log.d("Settings", "Selected format: RGB")
                    currentColorMode = "RGB"
                }
                R.id.radioHEX -> {
                    Log.d("Settings", "Selected format: HEX")
                    currentColorMode = "HEX"
                }
                R.id.radioHSV -> {
                    Log.d("Settings", "Selected format: HSV")
                    currentColorMode = "HSV"
                }
                R.id.radioCMY -> {
                    Log.d("Settings", "Selected format: CMY")
                    currentColorMode = "CMY"
                }
                R.id.radioNCS -> {
                    Log.d("Settings", "Selected format: NCS")
                    currentColorMode = "NCS"
                }
            }
            val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
            prefs.edit().putString("color_mode", currentColorMode).apply()

            palettes.forEach { palette ->
                palette.updateColorText(lastSetColor, currentColorMode)
            }
        }
        val radioGroupCamera = popupView.findViewById<RadioGroup>(R.id.radioGroupCamera)

        // Sync UI to current camera state
        when (currentCameraMode) {
            "BACK" -> radioGroupCamera.check(R.id.radioBACK)
            "FRONT" -> radioGroupCamera.check(R.id.radioFRONT)
        }

        radioGroupCamera.setOnCheckedChangeListener { _, checkedId ->
            currentCameraMode = when (checkedId) {
                R.id.radioBACK -> "BACK"
                R.id.radioFRONT -> "FRONT"
                else -> "BACK"
            }

            getSharedPreferences("AppSettings", MODE_PRIVATE).edit()
                .putString("camera_mode", currentCameraMode).apply()

            startCamera()
        }

        binding.root.alpha = 0.5f
        popupWindow.setOnDismissListener { binding.root.alpha = 1.0f }
        popupWindow.showAtLocation(binding.root, Gravity.CENTER, 0, 0)
    }
}
