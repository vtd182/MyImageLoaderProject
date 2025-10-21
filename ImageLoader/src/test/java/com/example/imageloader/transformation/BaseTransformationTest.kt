package com.example.imageloader.transformation

import android.graphics.Bitmap
import com.example.imageloader.core.abstract.BitmapPool
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BaseTransformationTest {

    private class TestTransformation(id: String) : BaseTransformation(id) {
        override fun transform(pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
            return toTransform // Dummy implementation
        }
    }

    @Test
    fun `key should return the id`() {
        val transformation = TestTransformation("testId")
        assertEquals("testId", transformation.key())
    }
}
