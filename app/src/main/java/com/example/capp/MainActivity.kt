
package com.example.capp

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
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
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import android.widget.PopupWindow
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.widget.RadioGroup
import android.widget.Toast
// more new imports
import androidx.appcompat.app.AppCompatActivity
//import androidx.compose.ui.platform.ViewConfiguration
import android.view.ViewConfiguration
import androidx.constraintlayout.widget.ConstraintLayout

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs


// class MainActivity : androidx.activity.ComponentActivity()
class MainActivity : androidx.appcompat.app.AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var palettes: List<ColorPalette>
    private val REQUEST_CODE_PERMISSIONS = 10
    private val REQUIRED_PERMISSIONS = arrayOf(android.Manifest.permission.CAMERA)

    private lateinit var cameraExecutor: ExecutorService
    private var isCameraFrozen = false
    private var clicked = false
    private var optBtnTop = false
    private var optBtnLeft = false
    private var currentColorMode = "HSV"
    private var currentCameraMode = "BACK"
    private var lastSetColor = floatArrayOf(0f, 1f, 1f)

    private val fromTop: Animation by lazy {AnimationUtils.loadAnimation(this, R.anim.from_top_anim)}
    private val toTop: Animation by lazy {AnimationUtils.loadAnimation(this, R.anim.to_top_anim)}
    private val fromBottom: Animation by lazy {AnimationUtils.loadAnimation(this, R.anim.from_bottom_anim)}
    private val toBottom: Animation by lazy {AnimationUtils.loadAnimation(this, R.anim.to_bottom_anim)}



    private fun setupDraggableButton() {
        val optBtn = binding.optionsBtn
        val subBtn1 = binding.fabSettings
        val subBtn2 = binding.copyBtn
        var dX = 0f
        var dY = 0f
        var startX = 0f
        var startY = 0f
        var isDragging = false

        var sub1OffsetX = 0f
        var sub1OffsetY = 0f
        var sub2OffsetX = 0f
        var sub2OffsetY = 0f

        val touchSlop = ViewConfiguration.get(optBtn.context).scaledTouchSlop

        optBtn.setOnClickListener {
            onOptionsBtnClicked()
        }

        optBtn.setOnTouchListener { view, event ->
            val parent = view.parent as View
            val parentWidth = parent.width
            val parentHeight = parent.height

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dX = view.x - event.rawX
                    dY = view.y - event.rawY
                    startX = event.rawX
                    startY = event.rawY
                    isDragging = false

                    if (subBtn1.visibility == View.VISIBLE) {
                        sub1OffsetX = subBtn1.x - view.x
                        sub1OffsetY = subBtn1.y - view.y
                    }
                    if (subBtn2.visibility == View.VISIBLE) {
                        sub2OffsetX = subBtn2.x - view.x
                        sub2OffsetY = subBtn2.y - view.y
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    val newX = (event.rawX + dX).coerceIn(0f, (parentWidth - view.width).toFloat())
                    val newY = (event.rawY + dY).coerceIn(0f, (parentHeight - view.height).toFloat())
                    view.x = newX
                    view.y = newY

                    if (subBtn1.visibility == View.VISIBLE) {
                        subBtn1.x = newX + sub1OffsetX
                        subBtn1.y = newY + sub1OffsetY
                    }
                    if (subBtn2.visibility == View.VISIBLE) {
                        subBtn2.x = newX + sub2OffsetX
                        subBtn2.y = newY + sub2OffsetY
                    }

                    if (!isDragging && (abs(event.rawX - startX) > touchSlop || abs(event.rawY - startY) > touchSlop)) {
                        isDragging = true
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        view.performClick()
                    } else {
                        val middleW = parentWidth / 2
                        val middleH = parentHeight / 2
                        val isLeft = view.x + (view.width / 2) < middleW

                        val nearestX = if (isLeft) 0f else (parentWidth - view.width).toFloat()
                        val deltaX = nearestX - view.x

                        optBtnLeft = isLeft
                        optBtnTop = view.y + (view.height / 2) < middleH

                        if (subBtn1.visibility == View.VISIBLE) {
                            subBtn1.animate().x(subBtn1.x + deltaX).setDuration(200).start()
                        }
                        if (subBtn2.visibility == View.VISIBLE) {
                            subBtn2.animate().x(subBtn2.x + deltaX).setDuration(200).start()
                        }

                        view.animate()
                            .x(nearestX)
                            .setDuration(200)
                            .withEndAction {
                                val params = view.layoutParams as? ConstraintLayout.LayoutParams
                                if (params != null) {
                                    params.leftToLeft = ConstraintLayout.LayoutParams.UNSET
                                    params.leftToRight = ConstraintLayout.LayoutParams.UNSET
                                    params.rightToLeft = ConstraintLayout.LayoutParams.UNSET
                                    params.rightToRight = ConstraintLayout.LayoutParams.UNSET
                                    params.startToStart = ConstraintLayout.LayoutParams.UNSET
                                    params.startToEnd = ConstraintLayout.LayoutParams.UNSET
                                    params.endToStart = ConstraintLayout.LayoutParams.UNSET
                                    params.endToEnd = ConstraintLayout.LayoutParams.UNSET

                                    params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                                    params.topToBottom = ConstraintLayout.LayoutParams.UNSET
                                    params.bottomToTop = ConstraintLayout.LayoutParams.UNSET
                                    params.bottomToBottom = ConstraintLayout.LayoutParams.UNSET

                                    params.topMargin = view.y.toInt()

                                    if (isLeft) {
                                        params.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID
                                        params.leftMargin = 0
                                    } else {
                                        params.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID
                                        params.rightMargin = 0
                                    }

                                    view.translationX = 0f
                                    view.translationY = 0f

                                    if (subBtn1.visibility == View.VISIBLE) {
                                        subBtn1.translationX = 0f
                                        subBtn1.translationY = 0f
                                    }
                                    if (subBtn2.visibility == View.VISIBLE) {
                                        subBtn2.translationX = 0f
                                        subBtn2.translationY = 0f
                                    }

                                    view.layoutParams = params
                                }
                            }
                            .start()
                    }
                }
            }
            true
        }
    }

    private fun setAnimation(clicked: Boolean, optBtnTop: Boolean, optBtnLeft: Boolean) {
        if (!clicked && optBtnTop) {
            binding.fabSettings.startAnimation(fromTop)
            binding.copyBtn.startAnimation(fromTop)
            binding.optionsBtn.animate().rotation(135f).setDuration(200).start()
        } else if (!clicked) {
            binding.fabSettings.startAnimation(fromBottom)
            binding.copyBtn.startAnimation(fromBottom)
            binding.optionsBtn.animate().rotation(135f).setDuration(200).start()
        } else if (!optBtnTop) {
            binding.fabSettings.startAnimation(toBottom)
            binding.copyBtn.startAnimation(toBottom)
            binding.optionsBtn.animate().rotation(0f).setDuration(200).start()
        }else {
            binding.fabSettings.startAnimation(toTop)
            binding.copyBtn.startAnimation(toTop)
            binding.optionsBtn.animate().rotation(0f).setDuration(200).start()
        }
    }

    private fun setVisibility(clicked: Boolean, optBtnTop: Boolean, optBtnLeft: Boolean){
        if(!clicked){
            binding.fabSettings.visibility=View.VISIBLE
            binding.copyBtn.visibility=View.VISIBLE
        } else {
            binding.fabSettings.visibility=View.INVISIBLE
            binding.copyBtn.visibility=View.INVISIBLE
        }
    }
    private fun onOptionsBtnClicked(){
        setVisibility(clicked, optBtnTop, optBtnLeft)
        setAnimation(clicked, optBtnTop, optBtnLeft)
        clicked = !clicked
    }
    private fun getAverageHsv(bitmap: Bitmap): FloatArray {
        val tinyBitmap = Bitmap.createScaledBitmap(bitmap, 1, 1, true)
        val averageColor = tinyBitmap.getPixel(0, 0)
        val hsv = FloatArray(3)
        Color.colorToHSV(averageColor, hsv)
        tinyBitmap.recycle()
        return hsv
    }

    private fun addColorsToClipboard() {
        val colorClipboardContent = StringBuilder()
        colorClipboardContent.append("Color profile: ${currentColorMode}")
        palettes.forEach { palette ->
            colorClipboardContent.append("\n${palette.returnCurrentColors(currentColorMode)}")
        }
        colorClipboardContent.toString()
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip: ClipData = ClipData.newPlainText("copied colrs", colorClipboardContent)
        clipboard.setPrimaryClip(clip)
        Toast.makeText( this, "$currentColorMode colors copied to clipboard!", Toast.LENGTH_SHORT).show()
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

        setupDraggableButton()

        binding.fabSettings.setOnClickListener{
            showSettingsPopup()
        }
        binding.copyBtn.setOnClickListener{
            addColorsToClipboard()
        }

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
        runOnUiThread {
            try {
                if (debugImageView == null) {
                    debugImageView = ImageView(this).apply {
                        layoutParams = ViewGroup.MarginLayoutParams(300, 300).apply {
                            topMargin = 200
                            marginStart = 50
                        }

                        setBackgroundColor(android.graphics.Color.RED)
                        setPadding(8, 8, 8, 8)
                        scaleType = ImageView.ScaleType.FIT_CENTER

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
