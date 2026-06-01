package com.example.capp

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.res.Resources
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import eightbitlab.com.blurview.BlurView
import android.graphics.Color
import android.transition.TransitionManager
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import kotlin.math.pow
import kotlin.math.roundToInt
import java.io.File
import java.io.InputStream
import java.io.IOException
import kotlin.math.abs
import kotlin.math.min
import java.math.BigDecimal
import java.math.RoundingMode
import android.util.TypedValue

data class NcsColor(
    val fullCode: String,
    val b: Int,
    val c: Int,
    val hue: String
)
data class ColorMatch(
    val baseColor: FloatArray,
    val hsvColorStr: String,
    val rgbColorStr: String,
    val hexColorStr: String,
    val cmyColorStr: String,
    val ncsColorStr: String,
    val displayColorDefault: FloatArray,
    val displayColorNcs: FloatArray,
    val matchPercentageDefault: Int,
    val matchPercentageNcs: Int,
    val ncsEstimationStr: String
)

data class MatchResult(
    val colorCode: String,
    val matchPercentage: Int,
    val matchHSV: FloatArray,
    val estimatedNcs: String
)
object ColorMatches {
    private var colorDatabase: List<NcsColor> = emptyList()
    private var bucketedDatabase: Map<Int, List<NcsColor>> = emptyMap()

    fun initializeDatabase(context: Context, fileName: String = "colors.txt") {
        if (colorDatabase.isNotEmpty()) return
        val parsedDatabase = mutableListOf<NcsColor>()
        try {
            context.assets.open(fileName).bufferedReader().useLines { lines ->
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.length < 7) continue
                    val color = if (trimmed.startsWith("NCS S ")) trimmed.substring(6) else trimmed
                    try {
                        val parts = color.split("-")
                        if (parts.size < 2) continue
                        val tint = parts[0]
                        val hue = parts[1]
                        if (tint.length < 4) continue
                        val b = tint.substring(0, 2).toInt()
                        val c = tint.substring(2).toInt()
                        parsedDatabase.add(NcsColor(color, b, c, hue))
                    } catch (e: Exception) {
                        continue
                    }
                }
            }
            colorDatabase = parsedDatabase

            // Bucket by continuous Hue segments along the color wheel (0 to 400 total steps)
            bucketedDatabase = parsedDatabase.groupBy { ncs ->
                findCirclePlacement(ncs.hue) / 100
            }

        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun getColorObj(baseHsv: FloatArray): ColorMatch {
        val colorInt = android.graphics.Color.HSVToColor(baseHsv)

        val hex = getHexColor(colorInt)
        val hsv = getHsvColor(baseHsv)
        val rgb = getRgbColor(colorInt)
        val cmy = getCmyColor(colorInt)
        val ncs = getNcsColor(baseHsv)

        return ColorMatch(
            baseHsv, hsv, rgb, hex, cmy,
            "NCS S ${ncs?.colorCode ?: ""}",
            baseHsv, ncs?.matchHSV ?: baseHsv,
            100, ncs?.matchPercentage ?: 0,
            ncs?.estimatedNcs ?: "unknown"
        )
    }

    fun getHexColor(colorInt: Int): String = String.format("#%06X", (0xFFFFFF and colorInt))

    fun getHsvColor(baseHsv: FloatArray): String =
        "Hue: ${baseHsv[0].toInt()}\nSat: ${(baseHsv[1] * 100).toInt()}\nVal: ${(baseHsv[2] * 100).toInt()}"

    fun getRgbColor(colorInt: Int): String {
        return "Red: ${android.graphics.Color.red(colorInt)}\nGreen: ${android.graphics.Color.green(colorInt)}\nBlue: ${android.graphics.Color.blue(colorInt)}"
    }

    fun getCmyColor(colorInt: Int): String {
        val c = (1f - (android.graphics.Color.red(colorInt) / 255f)) * 100
        val m = (1f - (android.graphics.Color.green(colorInt) / 255f)) * 100
        val y = (1f - (android.graphics.Color.blue(colorInt) / 255f)) * 100
        return "Cyan:${c.toInt()}% \nMagenta:${m.toInt()}% \nYellow:${y.toInt()}%"
    }

    fun getNcsColor(baseHsv: FloatArray): MatchResult? {
        val estimatedNcsColor = getEstimatedNcsColor(baseHsv)
        val cleanNcs = if (estimatedNcsColor.startsWith("NCS S ")) estimatedNcsColor.substring(6) else estimatedNcsColor

        return matchToNcsCharts(cleanNcs) ?: MatchResult(cleanNcs, 0, ncsToHsv(cleanNcs), cleanNcs)
    }

    fun getEstimatedNcsColor(baseHsv: FloatArray): String {
        val h = (baseHsv[0] % 360f + 360f) % 360f
        val s = baseHsv[1].coerceIn(0f, 1f)
        val v = baseHsv[2].coerceIn(0f, 1f)

        // Mathematical conversion approximation for Blackness (s) and Chromaticness (c)
        val baseB = (1f - v) * 100f
        val baseC = s * v * 100f

        val b = baseB.roundToInt().coerceIn(0, 99)
        val c = baseC.roundToInt().coerceIn(0, 99)

        val bPad = String.format("%02d", b)
        val cPad = String.format("%02d", c)

        // Account for Grayscale fallback
        if (s < 0.05f || c < 3) {
            val grayBlackness = ((1f - v) * 100).roundToInt().coerceIn(0, 99)
            return "NCS S ${String.format("%02d", grayBlackness)}00-N"
        }

        val ncsHueString = when {
            h >= 0f && h < 60f -> {
                // Yellow-Red Quadrant (Span 60 degrees). Y00R is Yellow (60), Y100R is Red (0)
                val p = (((60f - h) / 60f) * 100).roundToInt().coerceIn(0, 100)
                when (p) {
                    0 -> "Y"
                    100 -> "R"
                    else -> "Y${String.format("%02d", p)}R"
                }
            }
            h >= 60f && h < 120f -> {
                // Green-Yellow Quadrant (Span 60 degrees). G00Y is Green (120), G100Y is Yellow (60)
                val p = (((120f - h) / 60f) * 100).roundToInt().coerceIn(0, 100)
                when (p) {
                    0 -> "G"
                    100 -> "Y"
                    else -> "G${String.format("%02d", p)}Y"
                }
            }
            h >= 120f && h < 240f -> {
                // Blue-Green Quadrant (Span 120 degrees). B00G is Blue (240), B100G is Green (120)
                val p = (((240f - h) / 120f) * 100).roundToInt().coerceIn(0, 100)
                when (p) {
                    0 -> "B"
                    100 -> "G"
                    else -> "B${String.format("%02d", p)}G"
                }
            }
            h >= 240f && h < 360f -> {
                // Red-Blue Quadrant (Span 120 degrees). R00B is Red (360), R100B is Blue (240)
                val p = (((360f - h) / 120f) * 100).roundToInt().coerceIn(0, 100)
                when (p) {
                    0 -> "R"
                    100 -> "B"
                    else -> "R${String.format("%02d", p)}B"
                }
            }
            else -> "N"
        }
        return "NCS S $bPad$cPad-$ncsHueString"
    }

    fun matchToNcsCharts(matchColor: String): MatchResult? {
        val cleanColor = if (matchColor.startsWith("NCS S ")) matchColor.substring(6) else matchColor
        if (colorDatabase.isEmpty() || cleanColor.length < 6) return null

        val targetB = cleanColor.substring(0, 2).toIntOrNull() ?: return null
        val targetC = cleanColor.substring(2, 4).toIntOrNull() ?: return null
        val targetHuePlacement = findCirclePlacement(cleanColor.substring(5))

        var bestFit = -1.0
        var finalColor: String? = null

        val targetBucket = targetHuePlacement / 100
        val searchBuckets = listOf((targetBucket - 1 + 4) % 4, targetBucket, (targetBucket + 1) % 4)

        for (bucketKey in searchBuckets) {
            val candidates = bucketedDatabase[bucketKey] ?: continue
            for (ncs in candidates) {
                val bDiff = abs(ncs.b - targetB)
                val cDiff = abs(ncs.c - targetC)
                val colorHue = findCirclePlacement(ncs.hue)
                val hueDist = min(abs(colorHue - targetHuePlacement), (400 - abs(colorHue - targetHuePlacement)))

                // Convert 0..200 hue distance directly into a percentage penalty map
                val hDiff = (hueDist / 200.0) * 100.0
                val matchScore = 100.0 - (bDiff * 0.3 + cDiff * 0.3 + hDiff * 0.4)

                if (matchScore > bestFit) {
                    bestFit = matchScore
                    finalColor = ncs.fullCode
                }
            }
        }

        return finalColor?.let { MatchResult(it, bestFit.roundToInt().coerceIn(0, 100), ncsToHsv(it), matchColor) }
    }

    private fun findCirclePlacement(hue: String): Int {
        if (hue.isEmpty()) return 1000
        val firstChar = hue[0].uppercaseChar()
        if (firstChar == 'N') return 1000

        // Maps linearly sequentially around the color perimeter
        val segments = mapOf('Y' to 0, 'R' to 100, 'B' to 200, 'G' to 300)
        var seg = segments[firstChar] ?: return 1000

        if (hue.length > 1) {
            val digitPart = hue.drop(1).takeWhile { it.isDigit() }
            val digits = digitPart.toIntOrNull()
            if (digits != null) {
                seg += digits
            }
        }
        return seg
    }

    fun locateOnHsvWheel(ncsHue: String): Float {
        if (ncsHue.isEmpty()) return 0f
        val firstChar = ncsHue[0].uppercaseChar()
        if (firstChar == 'N') return 0f

        val shiftPercentage = if (ncsHue.length > 1) {
            ncsHue.substring(1).takeWhile { it.isDigit() }.toFloatOrNull() ?: 0f
        } else {
            0f
        }

        return when (firstChar) {
            'Y' -> 60f - (shiftPercentage / 100f) * 60f
            'G' -> 120f - (shiftPercentage / 100f) * 60f
            'B' -> 240f - (shiftPercentage / 100f) * 120f
            'R' -> {
                val target = 360f - (shiftPercentage / 100f) * 120f
                if (target >= 360f) 0f else target
            }
            else -> 0f
        }
    }

    fun ncsToHsv(nscColor: String): FloatArray {
        val clean = if (nscColor.startsWith("NCS S ")) nscColor.substring(6) else nscColor
        if (clean.length < 4) return floatArrayOf(0f, 0f, 0f)
        val b = clean.substring(0, 2).toIntOrNull() ?: 0
        val c = clean.substring(2, 4).toIntOrNull() ?: 0
        val huePart = if (clean.length > 5) clean.substring(5) else "N"

        val v = (100f - b.toFloat()) / 100f
        val s = if (100f - b.toFloat() == 0f) 0f else c.toFloat() / (100f - b.toFloat())
        val h = locateOnHsvWheel(huePart)

        return floatArrayOf(
            h.coerceIn(0f, 360f),
            s.coerceIn(0f, 1f),
            v.coerceIn(0f, 1f)
        )
    }
}
data class ColorPalette(
    val name: String,
    val container: LinearLayout,
    val blurView: eightbitlab.com.blurview.BlurView,
    val hueShifts: List<Float>,
    val satShifts: List<Float>,
    val valShifts: List<Float>,
    val onExpandListener: (palette: ColorPalette, isExpanding: Boolean) -> Unit,
    private val context: Context
) {
    // adaptive text size setting:
    val physicalWidth = context.resources.displayMetrics.widthPixels.toFloat()
    val textScale = physicalWidth / 1080f
    val baseNameSize = 40.0f
    val baseTxtSize = 25.0f
    val txtSize = baseTxtSize * textScale
    val nameSize = baseNameSize * textScale

    private var isExpanded = false
    private val boxViews = mutableListOf<ImageView>()
    private val textViews = mutableListOf<TextView>()

    val colorObjects = mutableListOf<ColorMatch>()

    fun Int.dpToPx(): Int {
        val density = Resources.getSystem().displayMetrics.density
        return (this * density).toInt()
    }

    fun inflate(context: Context) {
        container.removeAllViews()
        boxViews.clear()
        textViews.clear()
        colorObjects.clear()

        val inflater = LayoutInflater.from(context)

        val frameWrapper = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(frameWrapper)

        val colorBoxesContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(0, 40, 0, 0)
        }
        frameWrapper.addView(colorBoxesContainer)

        hueShifts.forEachIndexed { i, shift ->
            val itemWrapper = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2f)
            }

            val box = inflater.inflate(R.layout.palette_item, itemWrapper, false) as ImageView
            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            when {
                hueShifts.size == 1 -> {
                    box.setBackgroundResource(R.drawable.rounded_border)
                    params.setMargins(3.dpToPx(), 6.dpToPx(), 3.dpToPx(), 3.dpToPx())
                }
                i == 0 -> {
                    box.setBackgroundResource(R.drawable.palette_item_left)
                    params.setMargins(3.dpToPx(), 6.dpToPx(), 2.dpToPx(), 3.dpToPx())
                }
                i == hueShifts.lastIndex -> {
                    box.setBackgroundResource(R.drawable.palette_item_right)
                    params.setMargins(2.dpToPx(), 6.dpToPx(), 3.dpToPx(), 3.dpToPx())
                }
                else -> {
                    box.setBackgroundResource(R.drawable.palette_item_middle)
                    params.setMargins(2.dpToPx(), 6.dpToPx(), 2.dpToPx(), 3.dpToPx())
                }
            }
            box.layoutParams = params
            box.elevation = 2f

            val hsvText = TextView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setPadding(10.dpToPx(), 20.dpToPx(), 0, 0)
                gravity = Gravity.LEFT
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, txtSize)
                setShadowLayer(4f, 0f, 0f, Color.BLACK)
                alpha = 0f
                elevation = 8f
            }

            itemWrapper.addView(box)
            itemWrapper.addView(hsvText)
            colorBoxesContainer.addView(itemWrapper)

            boxViews.add(box)
            textViews.add(hsvText)
        }

        val label = TextView(context).apply {
            text = name
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, nameSize)
            setPadding(21, 3, 24, 6)
            setShadowLayer(4f, 2f, 2f, Color.BLACK)

            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }
        }
        frameWrapper.addView(label)

        blurView.setOnClickListener {
            if (isExpanded) {
                collapse()
                onExpandListener(this, false)
            } else {
                onExpandListener(this, true)
                expand()
            }
        }
    }

    fun expand() {
        if (isExpanded) return
        val parent = blurView.parent as? ConstraintLayout ?: return

        TransitionManager.beginDelayedTransition(parent)
        val params = blurView.layoutParams as ConstraintLayout.LayoutParams
        params.verticalWeight = 3f
        blurView.layoutParams = params
        isExpanded = true
        textViews.forEach { textView ->
            textView.animate()
                .alpha(1f)
                .setDuration(300)
                .start()
        }
    }

    fun collapse() {
        if (!isExpanded) return
        val parent = blurView.parent as? ConstraintLayout ?: return

        TransitionManager.beginDelayedTransition(parent)
        val params = blurView.layoutParams as ConstraintLayout.LayoutParams
        params.verticalWeight = 1f
        blurView.layoutParams = params
        isExpanded = false
        textViews.forEach { textView ->
            textView.animate()
                .alpha(0f)
                .setDuration(300)
                .start()
        }
    }

    fun updateColorText(baseHsv: FloatArray, mode: String){
        boxViews.forEachIndexed { index: Int, imageView: ImageView ->
            val matchObj = colorObjects[index]
            val colorInt = if (mode == "NCS") {
                android.graphics.Color.HSVToColor(matchObj.displayColorNcs)
            } else {
                android.graphics.Color.HSVToColor(matchObj.displayColorDefault)
            }
            imageView.backgroundTintList = android.content.res.ColorStateList.valueOf(colorInt)

            if (index < textViews.size) {
                val displayString = when (mode) {
                    "RGB" -> matchObj.rgbColorStr
                    "HEX" -> matchObj.hexColorStr
                    "HSV" -> matchObj.hsvColorStr
                    "CMY" -> matchObj.cmyColorStr
                    "NCS" -> "${matchObj.ncsColorStr}\n(${matchObj.matchPercentageNcs}% Match)"
                    else -> matchObj.hsvColorStr
                }
                textViews[index].text = displayString
            }
        }
    }

    fun applyColors(baseHsv: FloatArray, mode: String) {
        colorObjects.clear()

        boxViews.forEachIndexed { index: Int, imageView: ImageView ->
            val hueShift = hueShifts[index]
            val satShift = satShifts[index]
            val valShift = valShifts[index]
            val newHsv = baseHsv.copyOf()
            newHsv[0] = ((newHsv[0] + hueShift) % 360 + 360) % 360
            newHsv[1] = ((newHsv[1] + (satShift/100)) % 1 + 1) % 1
            newHsv[2] = ((newHsv[2] + (valShift/100)) % 1 + 1) % 1

            val matchObj = ColorMatches.getColorObj(newHsv)
            colorObjects.add(matchObj)

            val colorInt = if (mode == "NCS") {
                android.graphics.Color.HSVToColor(matchObj.displayColorNcs)
            } else {
                android.graphics.Color.HSVToColor(matchObj.displayColorDefault)
            }
            imageView.backgroundTintList = android.content.res.ColorStateList.valueOf(colorInt)

            if (index < textViews.size) {
                val displayString = when (mode) {
                    "RGB" -> matchObj.rgbColorStr
                    "HEX" -> matchObj.hexColorStr
                    "HSV" -> matchObj.hsvColorStr
                    "CMY" -> matchObj.cmyColorStr
                    "NCS" -> "${matchObj.ncsColorStr}\n(${matchObj.matchPercentageNcs}% Match)"
                    else -> matchObj.rgbColorStr
                }
                textViews[index].text = displayString
            }
        }
    }

    fun returnCurrentColors(mode: String): String{
        val paletteStr = StringBuilder()
        paletteStr.append("${name}:")
        boxViews.forEachIndexed { index: Int, imageView: ImageView ->
            val matchObj = colorObjects[index]
            if (index < textViews.size) {
                val colorStr = when (mode) {
                    "RGB" -> matchObj.rgbColorStr
                    "HEX" -> matchObj.hexColorStr
                    "HSV" -> matchObj.hsvColorStr
                    "CMY" -> matchObj.cmyColorStr
                    "NCS" -> "${matchObj.ncsColorStr}\n(${matchObj.matchPercentageNcs}% Match)"
                    else -> matchObj.hsvColorStr
                }
                paletteStr.append(" ").append(colorStr).append(",")
            }
        }
        paletteStr.append("\n")
        return paletteStr.toString()
    }
}