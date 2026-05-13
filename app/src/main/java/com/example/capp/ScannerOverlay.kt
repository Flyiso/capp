package com.example.capp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View


class ScannerOverlay(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val defaultPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 20f
        strokeCap = Paint.Cap.ROUND
    }
    private val highlightPaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 20f
        strokeCap = Paint.Cap.ROUND
    }
    private val outlinePaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 23f
        alpha = 150
        strokeCap = Paint.Cap.ROUND
    }
    private var activeHandles = mutableSetOf<Handle>()
    enum class Handle {
        TOP, BOTTOM, LEFT, RIGHT,
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
        MOVE
    }
    private val touchThreshold = 60f // How close finger must be to a border/corner

    private var rect = RectF()
    private var lastX = 0f
    private var lastY = 0f
    private val minSize = 100f

    fun getSelectionRect(): RectF {
        // Return a copy so the original isn't accidentally modified by other threads
        return RectF(rect)
    }

    private fun getTouchHandles(x: Float, y: Float): Set<Handle> {
        val handles = mutableSetOf<Handle>()

        val nearLeft = Math.abs(x - rect.left) < touchThreshold
        val nearRight = Math.abs(x - rect.right) < touchThreshold
        val nearTop = Math.abs(y - rect.top) < touchThreshold
        val nearBottom = Math.abs(y - rect.bottom) < touchThreshold

        // Corners first (higher priority)
        if (nearTop && nearLeft) handles.add(Handle.TOP_LEFT)
        else if (nearTop && nearRight) handles.add(Handle.TOP_RIGHT)
        else if (nearBottom && nearLeft) handles.add(Handle.BOTTOM_LEFT)
        else if (nearBottom && nearRight) handles.add(Handle.BOTTOM_RIGHT)
        else if (nearTop) handles.add(Handle.TOP)
        else if (nearBottom) handles.add(Handle.BOTTOM)
        else if (nearLeft) handles.add(Handle.LEFT)
        else if (nearRight) handles.add(Handle.RIGHT)
        else if (rect.contains(x, y)) {
            handles.add(Handle.MOVE)
        }
        return handles
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Determine if side should be highlighted
        val isMoving = activeHandles.contains(Handle.MOVE)
        if (isMoving) {
            canvas.drawRect(rect, outlinePaint)
        }
        val topH = !isMoving && activeHandles.any { it == Handle.TOP || it == Handle.TOP_LEFT || it == Handle.TOP_RIGHT }
        val bottomH = !isMoving && activeHandles.any { it == Handle.BOTTOM || it == Handle.BOTTOM_LEFT || it == Handle.BOTTOM_RIGHT }
        val leftH = !isMoving && activeHandles.any { it == Handle.LEFT || it == Handle.TOP_LEFT || it == Handle.BOTTOM_LEFT }
        val rightH = !isMoving && activeHandles.any { it == Handle.RIGHT || it == Handle.TOP_RIGHT || it == Handle.BOTTOM_RIGHT }

        // Draw Top
        canvas.drawLine(rect.left, rect.top, rect.right, rect.top, if (topH) highlightPaint else defaultPaint)
        // Draw Bottom
        canvas.drawLine(rect.left, rect.bottom, rect.right, rect.bottom, if (bottomH) highlightPaint else defaultPaint)
        // Draw Left
        canvas.drawLine(rect.left, rect.top, rect.left, rect.bottom, if (leftH) highlightPaint else defaultPaint)
        // Draw Right
        canvas.drawLine(rect.right, rect.top, rect.right, rect.bottom, if (rightH) highlightPaint else defaultPaint)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val initialWidth = w * 0.5f
        val initialHeight = h * 0.5f
        rect.set((w - initialWidth) / 2, (h - initialHeight) / 2, (w + initialWidth) / 2, (h + initialHeight) / 2)
    }
    interface OnRectChangedListener {
        fun onRectChanged(rect: RectF)
    }
    private var onRectChangedListener: OnRectChangedListener? = null

    fun setOnRectChangedListener(listener: OnRectChangedListener) {
        this.onRectChangedListener = listener
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {

            val x = event.x
            val y = event.y
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = x
                    lastY = y
                    activeHandles.addAll(getTouchHandles(x, y))
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    // For multi-touch, we just add the new handles
                    val px = event.getX(event.actionIndex)
                    val py = event.getY(event.actionIndex)
                    activeHandles.addAll(getTouchHandles(px, py))
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = x - lastX
                    val dy = y - lastY

                    // 1. Handle MOVE (Entire Rectangle)
                    if (activeHandles.contains(Handle.MOVE)) {
                        rect.offset(dx, dy)
                        // Clamp to view bounds
                        if (rect.left < 0) rect.offset(-rect.left, 0f)
                        if (rect.top < 0) rect.offset(0f, -rect.top)
                        if (rect.right > width) rect.offset(width - rect.right, 0f)
                        if (rect.bottom > height) rect.offset(0f, height - rect.bottom)
                    }

                    // 2. Handle RESIZING (Individual fingers)
                    // We use a loop for resizing to support multi-finger stretching
                    for (i in 0 until event.pointerCount) {
                        val px = event.getX(i)
                        val py = event.getY(i)

                        // Important: Only resize if we aren't currently "Moving" the whole box
                        if (!activeHandles.contains(Handle.MOVE)) {
                            // We check which handle this specific finger is near
                            val fingerHandles = getTouchHandles(px, py)
                            for (handle in fingerHandles) {
                                if (activeHandles.contains(handle)) {
                                    when (handle) {
                                        Handle.LEFT -> rect.left = px.coerceIn(0f, rect.right - minSize)
                                        Handle.RIGHT -> rect.right = px.coerceIn(rect.left + minSize, width.toFloat())
                                        Handle.TOP -> rect.top = py.coerceIn(0f, rect.bottom - minSize)
                                        Handle.BOTTOM -> rect.bottom = py.coerceIn(rect.top + minSize, height.toFloat())
                                        Handle.TOP_LEFT -> {
                                            rect.left = px.coerceIn(0f, rect.right - minSize)
                                            rect.top = py.coerceIn(0f, rect.bottom - minSize)
                                        }
                                        Handle.TOP_RIGHT -> {
                                            rect.right = px.coerceIn(rect.left + minSize, width.toFloat())
                                            rect.top = py.coerceIn(0f, rect.bottom - minSize)
                                        }
                                        Handle.BOTTOM_LEFT -> {
                                            rect.left = px.coerceIn(0f, rect.right - minSize)
                                            rect.bottom = py.coerceIn(rect.top + minSize, height.toFloat())
                                        }
                                        Handle.BOTTOM_RIGHT -> {
                                            rect.right = px.coerceIn(rect.left + minSize, width.toFloat())
                                            rect.bottom = py.coerceIn(rect.top + minSize, height.toFloat())
                                        }
                                        else -> {}
                                    }
                                }
                            }
                        }
                    }
                    lastX = x
                    lastY = y
                    onRectChangedListener?.onRectChanged(getSelectionRect())
                    invalidate()
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                    activeHandles.clear()
                    invalidate()
                }
            }
        return true
    }

}