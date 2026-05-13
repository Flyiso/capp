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

    val hsvStrings: MutableList<String> = mutableListOf()
    val rgbStrings: MutableList<String> = mutableListOf()
    val hexStrings: MutableList<String> = mutableListOf()
    val cmyStrings: MutableList<String> = mutableListOf()

    fun Int.dpToPx(): Int {
        val density = Resources.getSystem().displayMetrics.density
        return (this * density).toInt()
    }

    fun inflate(context: Context) {
        container.removeAllViews() // Clear any existing content
        boxViews.clear()

        val inflater = LayoutInflater.from(context)

        // 1. Create a FrameLayout to layer text and color boxes
        val frameWrapper = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(frameWrapper)

        // 2. Add the Color Boxes (bottom layer)
        val colorBoxesContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            // Add padding at the top so text doesn't cover boxes if you want
            setPadding(0, 40, 0, 0)
        }
        frameWrapper.addView(colorBoxesContainer)

        hueShifts.forEachIndexed { i, shift ->

            val itemWrapper = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2f)
            }

            // 2. YOUR ORIGINAL BOX LOGIC (Unchanged, just added to itemWrapper)
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

            // 3. THE TEXT VIEW (Created here, stacked on top of the box)
            val hsvText = TextView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setPadding(16.dpToPx(), 20.dpToPx(), 0, 0)
                gravity = Gravity.LEFT
                setTextColor(Color.WHITE)
                textSize = 10f
                setShadowLayer(4f, 0f, 0f, Color.BLACK)
                alpha = 0f
                elevation = 8f //
            }

            // 4. ADDING TO LAYOUT
            itemWrapper.addView(box)      // Bottom Layer
            itemWrapper.addView(hsvText)  // Top Layer

            colorBoxesContainer.addView(itemWrapper) // Add the whole unit to your row

            boxViews.add(box)
            textViews.add(hsvText)
        }

        // 3. Add the Name Label (top layer)
        val label = TextView(context).apply {
            text = name
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(24, 3, 24, 6) // Left, Top, Right, Bottom
            // Subtle shadow makes white text visible over light colors
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
                // Tell the coordinator to collapse others before we expand
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
                .start() }
    }

    fun collapse() {
        if (!isExpanded) return
        val parent = blurView.parent as? ConstraintLayout ?: return

        TransitionManager.beginDelayedTransition(parent)
        val params = blurView.layoutParams as ConstraintLayout.LayoutParams
        params.verticalWeight = 1f // Return to normal weight
        blurView.layoutParams = params
        isExpanded = false
        textViews.forEach { textView ->
            textView.animate()
                .alpha(0f)
                .setDuration(300)
                .start()}
    }
    fun updateColorText(baseHsv: FloatArray, mode: String){
        hsvStrings.clear()
        rgbStrings.clear()
        hexStrings.clear()
        cmyStrings.clear()
        boxViews.forEachIndexed { index: Int, imageView: ImageView ->
            val hueShift = hueShifts[index]
            val satShift = satShifts[index]
            val valShift = valShifts[index]
            val newHsv = baseHsv.copyOf()
            newHsv[0] = ((newHsv[0] + hueShift) % 360 + 360) % 360
            newHsv[1] = ((newHsv[1] + (satShift/100)) % 1 + 1) % 1
            newHsv[2] = ((newHsv[2] + (valShift/100)) % 1 + 1) % 1
            val colorInt = android.graphics.Color.HSVToColor(newHsv)

            // 1. Store HSV String
            val h = newHsv[0].toInt()
            val s = (newHsv[1] * 100).toInt()
            val v = (newHsv[2] * 100).toInt()
            hsvStrings.add("Hue: ${newHsv[0].toInt()}\nSat: ${(newHsv[1]*100).toInt()}\nVal: ${(newHsv[2]*100).toInt()}")

            // 3. Store RGB String
            val r = android.graphics.Color.red(colorInt)
            val g = android.graphics.Color.green(colorInt)
            val b = android.graphics.Color.blue(colorInt)
            rgbStrings.add("Red: ${r}\nGreen: ${g}\nBlue: ${b}")

            // 4. Store HEX String
            val hex = String.format("#%06X", (0xFFFFFF and colorInt))
            hexStrings.add(hex)

            // 5. Store CMY String
            val c = (1f - (r / 255f)) * 100
            val m = (1f - (g / 255f)) * 100
            val y = (1f - (b / 255f)) * 100
            cmyStrings.add("Cyan:${c.toInt()}% \nMagenta:${m.toInt()}% \nYellow:${y.toInt()}%")

            if (index < textViews.size) {

                val displayString = when (mode) {
                    "RGB" -> rgbStrings[index]
                    "HEX" -> hexStrings[index]
                    "HSV" -> hsvStrings[index]
                    "CMY" -> cmyStrings[index]
                    else -> hsvStrings[index] // Default fallback
                }
                textViews[index].text = displayString
            }
        }
    }
    fun applyColors(baseHsv: FloatArray, mode: String) {
        hsvStrings.clear()
        rgbStrings.clear()
        hexStrings.clear()
        cmyStrings.clear()
        boxViews.forEachIndexed { index: Int, imageView: ImageView ->
            val hueShift = hueShifts[index]
            val satShift = satShifts[index]
            val valShift = valShifts[index]
            val newHsv = baseHsv.copyOf()
            newHsv[0] = ((newHsv[0] + hueShift) % 360 + 360) % 360
            newHsv[1] = ((newHsv[1] + (satShift/100)) % 1 + 1) % 1
            newHsv[2] = ((newHsv[2] + (valShift/100)) % 1 + 1) % 1
            val colorInt = android.graphics.Color.HSVToColor(newHsv)
            imageView.backgroundTintList = android.content.res.ColorStateList.valueOf(colorInt)


            // 1. Store HSV String
            val h = newHsv[0].toInt()
            val s = (newHsv[1] * 100).toInt()
            val v = (newHsv[2] * 100).toInt()
            hsvStrings.add("Hue: ${newHsv[0].toInt()}\nSat: ${(newHsv[1]*100).toInt()}\nVal: ${(newHsv[2]*100).toInt()}")


            // 3. Store RGB String
            val r = android.graphics.Color.red(colorInt)
            val g = android.graphics.Color.green(colorInt)
            val b = android.graphics.Color.blue(colorInt)
            rgbStrings.add("Red: ${r}\nGreen: ${g}\nBlue: ${b}")

            // 4. Store HEX String
            val hex = String.format("#%06X", (0xFFFFFF and colorInt))
            hexStrings.add(hex)

            // 5. Store CMY String
            val c = (1f - (r / 255f)) * 100
            val m = (1f - (g / 255f)) * 100
            val y = (1f - (b / 255f)) * 100
            cmyStrings.add("Cyan:${c.toInt()}% \nMagenta:${m.toInt()}% \nYellow:${y.toInt()}%")


            if (index < textViews.size) {
                val h = newHsv[0].toInt()
                val s = (newHsv[1] * 100).toInt()
                val v = (newHsv[2] * 100).toInt()

                // This sets the string that appears over the color box
                //textViews[index].text = "H:$h\nS:$s\nV:$v"
            }
            if (index < textViews.size) {
                // Determine which list of strings to use based on the user's setting
                val displayString = when (mode) {
                    "RGB" -> rgbStrings[index]
                    "HEX" -> hexStrings[index]
                    "HSV" -> hsvStrings[index]
                    "CMY" -> cmyStrings[index]
                    else -> hsvStrings[index] // Default fallback
                }
                textViews[index].text = displayString
            }
        }
    }
}