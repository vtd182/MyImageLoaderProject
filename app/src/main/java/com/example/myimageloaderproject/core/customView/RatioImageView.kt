package com.example.myimageloaderproject.core.customView

import android.content.Context
import android.util.AttributeSet

class RatioImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : androidx.appcompat.widget.AppCompatImageView(context, attrs) {

    var ratio: Float = 1f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (width * ratio).toInt()
        setMeasuredDimension(width, height)
    }
}
