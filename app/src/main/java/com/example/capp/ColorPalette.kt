package com.example.capp

import android.content.Context
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import eightbitlab.com.blurview.BlurView
import android.graphics.Color
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView

data class ColorPalette(
    val name: String,
    val container: LinearLayout,
    val blurView: eightbitlab.com.blurview.BlurView,
    val hueShifts: List<Float>
) {
    private val boxViews = mutableListOf<ImageView>()
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

        hueShifts.forEach { _ ->
            val box = inflater.inflate(R.layout.palette_item, colorBoxesContainer, false) as ImageView
            colorBoxesContainer.addView(box)
            boxViews.add(box)
        }

        // 3. Add the Name Label (top layer)
        val label = TextView(context).apply {
            text = name
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(24, 16, 24, 8) // Left, Top, Right, Bottom
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
    }

    fun applyColors(baseHsv: FloatArray) {
        boxViews.forEachIndexed { index: Int, imageView: ImageView ->
            val shift = hueShifts[index]
            val newHsv = baseHsv.copyOf()
            newHsv[0] = ((newHsv[0] + shift) % 360 + 360) % 360
            imageView.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.HSVToColor(newHsv))
        }
    }
}