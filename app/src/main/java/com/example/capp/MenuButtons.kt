package com.example.capp

import android.view.MotionEvent
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import kotlin.math.abs
import android.animation.ValueAnimator
import android.content.Context
import androidx.core.view.isVisible



class MenuButtons(
    private val optBtn: View,
    private val subButtons: List<MenuSubButton>,
    private val context: Context
) {
    // SETUP VARIABLES
    // Coordinate Tracking
    private var dX = 0f
    private var dY = 0f
    private var startX = 0f
    private var startY = 0f
    private var isDragging = false
    // display adjusted sizing
    val density = context.resources.displayMetrics.density
    val baseSpacingDp = 66f
    val standardSpacing = baseSpacingDp * density
    // Animation States
    private var physicsAnimator: ValueAnimator? = null
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

                    if (!isDragging && (abs(event.rawX - startX) > touchSlop || abs(event.rawY - startY) > touchSlop)) {
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
                                    view.layoutParams = params

                                    view.post {
                                        subButtons.forEach { subButton ->
                                            if (!clicked) {
                                                subButton.view.x = view.x
                                                subButton.view.y = view.y
                                            } else {
                                                val adjustedOffset = subButton.buttonId * standardSpacing * (if (optBtnTop) 1 else -1).toFloat()
                                                subButton.view.x = view.x
                                                subButton.view.y = view.y + adjustedOffset
                                            }
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
        val interpolationFactor = 0.22f

        subButtons.forEachIndexed { index, subButton ->
            val subView = subButton.view
            val leaderX: Float
            val leaderY: Float

            if (index == 0) {
                leaderX = optBtn.x
                leaderY = if (clicked) {
                    val adjustedOffset = (subButton.buttonId + 1) * standardSpacing * (if (optBtnTop) 1 else -1).toFloat()
                    optBtn.y + adjustedOffset
                } else {
                    optBtn.y
                }
            } else {
                val leaderButton = subButtons[index - 1].view
                leaderX = leaderButton.x
                leaderY = if (clicked) {
                    val directionalSpacing = standardSpacing * (if (optBtnTop) 1 else -1).toFloat()
                    leaderButton.y + directionalSpacing
                } else {
                    leaderButton.y
                }
            }
            val nextX = subView.x + (leaderX - subView.x) * interpolationFactor
            val nextY = subView.y + (leaderY - subView.y) * interpolationFactor

            if (subView.isVisible || clicked) {
                subView.x = nextX
                subView.y = nextY
            } else {
                subView.x = optBtn.x
                subView.y = optBtn.y
            }
        }
    }
}