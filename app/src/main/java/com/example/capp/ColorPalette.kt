package com.example.capp

import android.content.Context
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
            setPadding(0, 48, 0, 0)
        }
        frameWrapper.addView(colorBoxesContainer)

        hueShifts.forEachIndexed { i, shift ->
            val box = inflater.inflate(R.layout.palette_item, colorBoxesContainer, false) as ImageView
            val params = box.layoutParams as LinearLayout.LayoutParams

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
            colorBoxesContainer.addView(box)
            boxViews.add(box)
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
    }

    fun collapse() {
        if (!isExpanded) return
        val parent = blurView.parent as? ConstraintLayout ?: return

        TransitionManager.beginDelayedTransition(parent)
        val params = blurView.layoutParams as ConstraintLayout.LayoutParams
        params.verticalWeight = 1f // Return to normal weight
        blurView.layoutParams = params
        isExpanded = false
    }

    fun applyColors(baseHsv: FloatArray) {
        boxViews.forEachIndexed { index: Int, imageView: ImageView ->
            val hueShift = hueShifts[index]
            val satShift = satShifts[index]
            val valShift = valShifts[index]
            val newHsv = baseHsv.copyOf()
            newHsv[0] = ((newHsv[0] + hueShift) % 360 + 360) % 360
            newHsv[1] = ((newHsv[1] + (satShift/100)) % 1 + 1) % 1
            newHsv[2] = ((newHsv[2] + (valShift/100)) % 1 + 1) % 1
            imageView.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.HSVToColor(newHsv))
        }
    }
}