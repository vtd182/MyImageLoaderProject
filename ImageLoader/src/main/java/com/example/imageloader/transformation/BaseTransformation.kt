package com.example.imageloader.transformation

import android.graphics.Bitmap
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
        return pool.get(width, height, config) ?: createBitmap(width, height, config)
    }

    protected fun createBitmap(width: Int, height: Int, config: Bitmap.Config): Bitmap {
        return Bitmap.createBitmap(width, height, config)
    }

    override fun key(): String = id
}