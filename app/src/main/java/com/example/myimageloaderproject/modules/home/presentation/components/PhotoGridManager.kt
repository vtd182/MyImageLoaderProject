package com.example.myimageloaderproject.modules.home.presentation.components

import android.content.Context
import android.view.ScaleGestureDetector
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class PhotoGridManager(
    private val context: Context,
    private val recyclerView: RecyclerView
) {
    private var spanCount = 2
    private lateinit var layoutManager: GridLayoutManager
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var onSpanCountChanged: ((Int) -> Unit)? = null
    
    fun initialize() {
        layoutManager = GridLayoutManager(context, spanCount)
        recyclerView.layoutManager = layoutManager
        
        scaleGestureDetector = ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                private var accumulatedScale = 1f

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    accumulatedScale *= detector.scaleFactor
                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    if (accumulatedScale > 1.2f && spanCount > 1) {
                        spanCount--
                        updateSpanCount()
                    } else if (accumulatedScale < 0.8f && spanCount < 3) {
                        spanCount++
                        updateSpanCount()
                    }
                    accumulatedScale = 1f
                }
            }
        )
    }
    
    fun handleTouchEvent(event: android.view.MotionEvent): Boolean {
        return if (event.pointerCount > 1) {
            scaleGestureDetector.onTouchEvent(event)
            true
        } else {
            false
        }
    }
    
    fun setSpanCount(count: Int) {
        if (count in 1..3 && count != spanCount) {
            spanCount = count
            updateSpanCount()
        }
    }
    
    fun getSpanCount(): Int = spanCount
    
    fun setOnSpanCountChangedListener(listener: (Int) -> Unit) {
        onSpanCountChanged = listener
    }
    
    private fun updateSpanCount() {
        layoutManager.spanCount = spanCount
        onSpanCountChanged?.invoke(spanCount)
    }
}
