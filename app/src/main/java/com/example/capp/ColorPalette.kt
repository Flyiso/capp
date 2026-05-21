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
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import java.io.File
import java.io.InputStream
import java.io.IOException
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

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
    private var colorMatch: List<ColorMatch> = emptyList()

    fun initializeDatabase(context: Context, fileName: String = "colors.txt") {
        if (ColorMatches.colorDatabase.isNotEmpty()) return
        val parsedDatabase = mutableListOf<NcsColor>()
        try {
            context.assets.open(fileName).bufferedReader().useLines { lines ->
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.length < 7) continue
                    val color = trimmed.substring(6)
                    try {
                        val parts = color.split("-")
                        if (parts.size < 2) continue
                        val tint = parts[0]
                        val hue = parts[1]
                        val b = tint.substring(0, 2).toInt()
                        val c = tint.substring(2).toInt()
                        parsedDatabase.add(NcsColor(color, b, c, hue))
                    } catch (e: Exception) {
                        continue
                    }
                }
            }
            ColorMatches.colorDatabase = parsedDatabase
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
    fun getColorObj(baseHsv: FloatArray): ColorMatch{
        val colorInt = android.graphics.Color.HSVToColor(baseHsv)
        val hex = getHexColor(colorInt)
        val hsv = getHsvColor(baseHsv)
        val rgb = getRgbColor(colorInt)
        val cmy = getCmyColor(colorInt)
        val ncs = getNcsColor(baseHsv)
        return ColorMatch(
            baseHsv,hsv,
            rgb,hex,
            cmy, "NCS S ${ncs?.colorCode ?: ""}",
            baseHsv, ncs?.matchHSV ?: baseHsv,
            100, ncs?.matchPercentage ?: 0,
            ncs?.estimatedNcs ?: "unknown"
        )
    }

    fun getHexColor(colorInt: Int): String{
        return String.format("#%06X", (0xFFFFFF and colorInt))
    }
    fun getHsvColor(baseHsv: FloatArray): String{
        return "Hue: ${baseHsv[0].toInt()}\nSat: ${(baseHsv[1]*100).toInt()}\nVal: ${(baseHsv[2]*100).toInt()}"
    }
    fun getRgbColor(colorInt: Int): String{
        val r = android.graphics.Color.red(colorInt)
        val g = android.graphics.Color.green(colorInt)
        val b = android.graphics.Color.blue(colorInt)
        return "Red: ${r}\nGreen: ${g}\nBlue: ${b}"
    }
    fun getCmyColor(colorInt: Int): String{
        val c = (1f - (android.graphics.Color.red(colorInt) / 255f)) * 100
        val m = (1f - (android.graphics.Color.green(colorInt) / 255f)) * 100
        val y = (1f - (android.graphics.Color.blue(colorInt) / 255f)) * 100
        return "Cyan:${c.toInt()}% \nMagenta:${m.toInt()}% \nYellow:${y.toInt()}%"
    }

    fun getNcsColor(baseHsv: FloatArray): MatchResult? {
        val estimated_ncs_color = getEstimatedNcsColor(baseHsv)
        val cleanNcs = if (estimated_ncs_color.startsWith("NCS S ")) estimated_ncs_color.substring(6) else estimated_ncs_color
        val result = matchToNcsCharts(cleanNcs)
        if (result != null) {
            return result
        }
        return MatchResult(cleanNcs,0, ncsToHsv(cleanNcs), cleanNcs)
    }

    fun getEstimatedNcsColor(baseHsv: FloatArray):String{
        val baseB = (1f - baseHsv[2]) * 100f
        val b = baseB.toInt().coerceIn(0, 99)
        val baseC = baseHsv[1] * baseHsv[2] * 100f
        val c = baseC.toInt().coerceIn(0, 99)
        if (baseHsv[1] < 0.06f){
            val grayBlackness = ((1f - baseHsv[2]) * 100).toInt().coerceIn(0, 99)
            val paddedGray = grayBlackness.toString().padStart(2, '0')
            return "NCS S ${paddedGray}00-N"
        }
        val ncsHueString: String
        val h = baseHsv[0] % 360f
        when {
            h >= 0f && h < 60f -> {
                val percentageOfRed = String.format("%02d", (((60f - h) / 60f) * 100).toInt().coerceIn(0, 99))
                ncsHueString = if (percentageOfRed == "00") "Y" else "Y${percentageOfRed}R"
            }
            h >= 240f && h <= 360f -> {
                val percentageOfBlue = String.format("%02d", (((360f - h) / 120f) * 100).toInt().coerceIn(0, 99))
                ncsHueString = if (percentageOfBlue == "00") "R" else "R${percentageOfBlue}B"
            }
            h >= 120f && h < 240f -> {
                val percentageOfGreen = String.format("%02d", (((240f - h) / 120f) * 100).toInt().coerceIn(0, 99))
                ncsHueString = if (percentageOfGreen == "00") "B" else "B${percentageOfGreen}G"
            }
            else -> {
                val percentageOfYellow = String.format("%02d", (((h - 120f) / 60f) * 100).toInt().coerceIn(0, 99))
                ncsHueString = if (percentageOfYellow == "00") "G" else "G${percentageOfYellow}Y"
            }
        }
        val bPad = b.toString().padStart(2, '0')
        val cPad = c.toString().padStart(2, '0')
        return "NCS S $bPad$cPad-$ncsHueString"
    }

    fun matchToNcsCharts(matchColor: String): MatchResult? {
        val cleanColor = if (matchColor.startsWith("NCS S ")) matchColor.substring(6) else matchColor
        if ( ColorMatches.colorDatabase.isEmpty() || cleanColor.length < 9) return null
        val targetB = cleanColor.substring(0, 2).toIntOrNull() ?: return null
        val targetC = cleanColor.substring(2, 4).toIntOrNull() ?: return null
        val targetHuePlacement = findCirclePlacement(cleanColor.substring(5))
        var bestFit = -1.0
        var finalColor: String? = null

        for (ncs in  ColorMatches.colorDatabase) {
            val bDiff = abs(ncs.b - targetB)
            val cDiff = abs(ncs.c - targetC)
            val colorHue = findCirclePlacement(ncs.hue)
            val hueDist = abs(colorHue - targetHuePlacement)
            val hDiff = min((min(hueDist, 400 - hueDist) / 2.0), 100.0)
            val matchScore = 100.0 - ((abs(bDiff) / 3.0) + (abs(cDiff) / 3.0) + (abs(hDiff) / 3.0))

            if (matchScore > bestFit) {
                bestFit = matchScore
                finalColor = ncs.fullCode
            }
        }
        return finalColor?.let { MatchResult(
            it, bestFit.roundToInt(),
            ncsToHsv(it), matchColor)}
    }

    private fun findCirclePlacement(hue: String): Int {
        if (hue.isEmpty()) return 0
        val firstChar = hue[0]
        if (firstChar == 'N') return 1000

        val segments = mapOf('Y' to 0, 'R' to 100, 'B' to 200)
        var seg = segments[firstChar] ?: 300

        if (hue.length > 1) {
            val endIdx = min(3, hue.length)
            val digits = hue.substring(1, endIdx).toIntOrNull()
            if (digits != null) {
                seg += digits
            }
        }
        return seg
    }

    fun ncsToHsv(nscColor:String): FloatArray {
        if (nscColor.length < 4) return floatArrayOf(0f, 0f, 0f)
        val b = nscColor.substring(0, 2).toIntOrNull() ?: 0
        val c = nscColor.substring(2, 4).toIntOrNull() ?: 0
        val huePart = if (nscColor.length > 5) nscColor.substring(5) else "N"
        val huePlacement = findCirclePlacement(huePart)
        val s = if (b + c == 100) 1.0f else c.toFloat() / (100f - b.toFloat())
        val v = (100f - b.toFloat()) / 100f
        val h = (huePlacement.toFloat() / 400f) * 360f
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
    val onExpandListener: (palette: ColorPalette, isExpanding: Boolean) -> Unit
) {

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
                textSize = 10f
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
            textSize = 16f
            setTextColor(Color.WHITE)
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

            if (index < textViews.size) {
                val displayString = when (mode) {
                    "RGB" -> matchObj.rgbColorStr
                    "HEX" -> matchObj.hexColorStr
                    "HSV" -> matchObj.hsvColorStr
                    "CMY" -> matchObj.cmyColorStr
                    "NCS" -> matchObj.ncsColorStr
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
                    "NCS" -> "${matchObj.ncsColorStr}\n${matchObj.matchPercentageNcs}% Match. \n(${matchObj.ncsEstimationStr})"
                    else -> matchObj.rgbColorStr
                }
                textViews[index].text = displayString
            }
        }
    }
}