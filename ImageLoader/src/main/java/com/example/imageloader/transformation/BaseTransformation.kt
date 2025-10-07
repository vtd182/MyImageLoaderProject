package com.example.imageloader.transformation

import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.createBitmap
import com.example.imageloader.core.abstract.BitmapPool


abstract class BaseTransformation(
    private val id: String
) : Transformation {

    protected fun getOrCreateBitmap(
        pool: BitmapPool,
        width: Int,
        height: Int,
        config: Bitmap.Config = Bitmap.Config.ARGB_8888
    ): Bitmap {
        val fromPool = pool.get(width, height, config)
        return if (fromPool != null) {
            Log.d(
                "BaseTransformation",
                "Reusing bitmap from pool: ${width}x${height}, config=$config"
            )
            fromPool
        } else {
            Log.d("BaseTransformation", "Creating new bitmap: ${width}x${height}, config=$config")
            createBitmap(width, height, config)
        }
    }

    override fun key(): String = id
}