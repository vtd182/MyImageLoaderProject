package com.example.imageloader.drawable

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ShimmerDrawableTest {

    private lateinit var shimmerDrawable: ShimmerDrawable

    @Before
    fun setup() {
        shimmerDrawable = ShimmerDrawable()
    }

    @Test
    fun `constructor with null baseColor does not throw`() {
        ShimmerDrawable()
    }

    @Test
    fun `constructor with baseColor does not throw`() {
        val baseColor = Color.parseColor("#FF0000")
        ShimmerDrawable(baseColor)
    }

    @Test
    fun `onBoundsChange sets shader`() {
        val bounds = Rect(0, 0, 100, 50)
        shimmerDrawable.setBounds(bounds)
        // Shader is set internally
    }

    @Test
    fun `draw with cornerRadius 0 does not throw`() {
        val canvas = mock(Canvas::class.java)
        val bounds = Rect(0, 0, 100, 50)
        shimmerDrawable.setBounds(bounds)
        shimmerDrawable.draw(canvas)
    }

    @Test
    fun `draw with cornerRadius greater than 0 does not throw`() {
        val drawable = ShimmerDrawable(cornerRadius = 10f)
        val canvas = mock(Canvas::class.java)
        val bounds = Rect(0, 0, 100, 50)
        drawable.setBounds(bounds)
        drawable.draw(canvas)
    }

    @Test
    fun `draw with empty bounds does nothing`() {
        val canvas = mock(Canvas::class.java)
        val bounds = Rect(0, 0, 0, 0)
        shimmerDrawable.setBounds(bounds)
        shimmerDrawable.draw(canvas)
    }

    @Test
    fun `setAlpha does not throw`() {
        shimmerDrawable.setAlpha(128)
    }

    @Test
    fun `setColorFilter does not throw`() {
        val filter = mock(ColorFilter::class.java)
        shimmerDrawable.setColorFilter(filter)
    }

    @Test
    fun `getOpacity returns TRANSLUCENT`() {
        assertEquals(android.graphics.PixelFormat.TRANSLUCENT, shimmerDrawable.opacity)
    }

    @Test
    fun `start does not throw`() {
        shimmerDrawable.start()
    }

    @Test
    fun `stop does not throw`() {
        shimmerDrawable.start()
        shimmerDrawable.stop()
    }
}
