package com.example.capp

import android.view.MotionEvent
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import kotlin.math.abs
import android.animation.ValueAnimator


class MenuButtons(
    private val optBtn: View,
    private val subButtons: List<MenuSubButton>
) {
    // SETUP VARIABLES
    // Coordinate Tracking
    private var dX = 0f
    private var dY = 0f
    private var startX = 0f
    private var startY = 0f
    private var isDragging = false
    // Animation States
    val standardSpacing = 150f
    private var physicsAnimator: android.animation.ValueAnimator? = null
    private var clicked = false
    private var optBtnTop = true
    private var optBtnLeft = true


    init {
        val touchSlop = android.view.ViewConfiguration.get(optBtn.context).scaledTouchSlop
        setupListeners(touchSlop)
    }
    private fun setupListeners(touchSlop: Int) {
        optBtn.setOnClickListener {
            onMainBtnClicked()
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

                    startPhysicsLoop()
                }

                MotionEvent.ACTION_MOVE -> {
                    val newX = (event.rawX + dX).coerceIn(0f, (parentWidth - view.width).toFloat())
                    val newY = (event.rawY + dY).coerceIn(0f, (parentHeight - view.height).toFloat())
                    view.x = newX
                    view.y = newY

                    val middleH = parentHeight / 2
                    optBtnTop = view.y + (view.height / 2) < middleH

                    if (!isDragging && (java.lang.Math.abs(event.rawX - startX) > touchSlop || java.lang.Math.abs(event.rawY - startY) > touchSlop)) {
                        isDragging = true
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        stopPhysicsLoop()
                        view.performClick()
                    } else {
                        val middleW = parentWidth / 2
                        val middleH = parentHeight / 2
                        val isLeft = view.x + (view.width / 2) < middleW

                        val nearestX = if (isLeft) 0f else (parentWidth - view.width).toFloat()

                        optBtnLeft = isLeft
                        optBtnTop = view.y + (view.height / 2) < middleH

                        view.animate()
                            .x(nearestX)
                            .setDuration(250)
                            .setUpdateListener {
                                updateSubButtonPhysics()
                            }
                            .withEndAction {
                                stopPhysicsLoop()

                                val params = view.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                                if (params != null) {
                                    params.leftToLeft = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                                    params.leftToRight = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                                    params.rightToLeft = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                                    params.rightToRight = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                                    params.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                                    params.startToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                                    params.endToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                                    params.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET

                                    params.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                                    params.topToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                                    params.bottomToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                                    params.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET

                                    params.topMargin = view.y.toInt()

                                    if (isLeft) {
                                        params.leftToLeft = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                                        params.leftMargin = 0
                                    } else {
                                        params.rightToRight = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                                        params.rightMargin = 0
                                    }
                                    view.translationX = 0f
                                    view.translationY = 0f
                                    view.layoutParams = params

                                    subButtons.forEach { subButton ->
                                        if (!clicked) {
                                            subButton.view.x = view.x
                                            subButton.view.y = view.y
                                        }
                                    }
                                }
                            }
                            .start()
                    }
                }
            }
            true
        }
        subButtons.forEach { subButton ->
            subButton.view.setOnClickListener {
                subButton.onClickAction()
                //onMainBtnClicked()
            }
        }
    }
    private fun onMainBtnClicked() {
        subButtons.forEach { subButton ->
            val adjustedOffset = subButton.buttonId * standardSpacing * (if (optBtnTop) 1 else -1).toFloat()
            subBtnAnimation(subButton.view, adjustedOffset)
        }
        clicked = !clicked
    }
    private fun subBtnAnimation(subBtn: View, targetOffset: Float){
        if (!clicked){
            subBtn.x = optBtn.x
            subBtn.y = optBtn.y
            subBtn.alpha = 0f
            subBtn.scaleX = 1.0f
            subBtn.scaleY = 1.0f
            subBtn.visibility = View.VISIBLE
            subBtn.animate()
                .x(optBtn.x)
                .y(optBtn.y + targetOffset)
                .alpha(1f)
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(500)
                .setListener(null)
                .start()
        } else {
            subBtn.animate()
                .x(optBtn.x)
                .y(optBtn.y)
                .alpha(0f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(300)
                .withEndAction { subBtn.visibility = View.GONE }
                .start()
        }
    }
    private fun startPhysicsLoop() {
        physicsAnimator?.cancel()
        physicsAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                updateSubButtonPhysics()
            }
            start()
        }
    }

    private fun stopPhysicsLoop() {
        physicsAnimator?.cancel()
        physicsAnimator = null
    }

    private fun updateSubButtonPhysics() {
        val interpolationFactor = 0.18f

        subButtons.forEach { subButton ->
            val subView = subButton.view

            val targetX = optBtn.x
            val targetY = if (clicked) {
                val adjustedOffset = subButton.buttonId * standardSpacing * (if (optBtnTop) 1 else -1).toFloat()
                optBtn.y + adjustedOffset
            } else {
                optBtn.y
            }
            val nextX = subView.x + (targetX - subView.x) * interpolationFactor
            val nextY = subView.y + (targetY - subView.y) * interpolationFactor

            if (subView.visibility == View.VISIBLE || clicked) {
                subView.x = nextX
                subView.y = nextY
            } else {
                subView.x = optBtn.x
                subView.y = optBtn.y
            }
        }
    }
}