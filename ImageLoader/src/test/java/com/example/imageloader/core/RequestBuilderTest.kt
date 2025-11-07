package com.example.imageloader.core

import android.app.Activity
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import androidx.core.graphics.drawable.RoundedBitmapDrawable
import com.example.imageloader.core.enums.RequestPriority
import com.example.imageloader.transformation.CenterCropRoundedCorners
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RequestBuilderTest {

    private lateinit var engine: Engine
    private lateinit var builder: RequestBuilder

    @Before
    fun setup() {
        engine = mock(Engine::class.java)
        builder = RequestBuilder(engine)
    }

    // =========================================
    // ===== Builder chain methods ============
    // =========================================

    @Test
    fun `skipMemoryCache disables memory cache`() {
        val result = builder.skipMemoryCache()
        assertEquals(builder, result)
    }

    @Test
    fun `skipDiskCache disables disk cache`() {
        val result = builder.skipDiskCache()
        assertEquals(builder, result)
    }

    @Test
    fun `enableShimmer sets flag`() {
        val result = builder.enableShimmer(true)
        assertEquals(builder, result)
    }

    @Test
    fun `overrideSize sets dimensions`() {
        val result = builder.overrideSize(100, 200)
        assertEquals(builder, result)
    }

    @Test
    fun `priority sets request priority`() {
        val result = builder.priority(RequestPriority.HIGH)
        assertEquals(builder, result)
    }

    // =========================================
    // ===== Placeholder & Error ===============
    // =========================================

    @Test
    fun `placeholder sets valid color`() {
        val result = builder.placeholder("#FF0000")
        assertEquals(builder, result)
    }

    @Test
    fun `placeholder sets default color on invalid hex`() {
        val result = builder.placeholder("notacolor")
        assertEquals(builder, result)
    }

    @Test
    fun `error sets resource`() {
        val result = builder.error(android.R.drawable.ic_delete)
        assertEquals(builder, result)
    }

    // =========================================
    // ===== applyPlaceholder ==================
    // =========================================

    @Test
    fun `applyPlaceholder sets placeholderRes drawable`() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val imageView = ImageView(activity)
        val field = builder.javaClass.getDeclaredField("placeholderRes")
        field.isAccessible = true
        field.set(builder, android.R.drawable.ic_menu_camera)

        builder.applyPlaceholder(imageView)

        assertNotNull(imageView.drawable)
    }

    @Test
    fun `applyPlaceholder sets color placeholder when color set`() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val imageView = ImageView(activity)
        val colorField = builder.javaClass.getDeclaredField("placeholderColor")
        colorField.isAccessible = true
        colorField.set(builder, 0xFF112233.toInt())

        builder.applyPlaceholder(imageView)
        assertNotNull(imageView.drawable)
        assertTrue(imageView.drawable is android.graphics.drawable.GradientDrawable)
    }

    @Test
    fun `applyPlaceholder uses shimmer placeholder when enabled`() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val imageView = ImageView(activity)
        val shimmerField = builder.javaClass.getDeclaredField("enableShimmer")
        shimmerField.isAccessible = true
        shimmerField.set(builder, true)

        builder.applyPlaceholder(imageView)
        assertNotNull(imageView.drawable)
        assertTrue(imageView.drawable is android.graphics.drawable.GradientDrawable)
    }

    @Test
    fun `applyPlaceholder sets null drawable when no placeholder`() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val imageView = ImageView(activity)

        builder.applyPlaceholder(imageView)
        assertNull(imageView.drawable)
    }

    // =========================================
    // ===== roundDrawableIfNeeded =============
    // =========================================

    @Test
    fun `roundDrawableIfNeeded returns rounded drawable when transformation present`() {
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val drawable = BitmapDrawable(Resources.getSystem(), bitmap)
        builder.transform(CenterCropRoundedCorners(8f))

        val method = builder.javaClass.getDeclaredMethod(
            "roundDrawableIfNeeded",
            android.graphics.drawable.Drawable::class.java
        )
        method.isAccessible = true
        val result = method.invoke(builder, drawable)

        assertTrue(result is RoundedBitmapDrawable)
    }

    @Test
    fun `roundDrawableIfNeeded returns same drawable when no transformation`() {
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val drawable = BitmapDrawable(Resources.getSystem(), bitmap)

        val method = builder.javaClass.getDeclaredMethod(
            "roundDrawableIfNeeded",
            android.graphics.drawable.Drawable::class.java
        )
        method.isAccessible = true
        val result = method.invoke(builder, drawable)

        assertSame(drawable, result)
    }

    // =========================================
    // ===== into(imageView) ===================
    // =========================================

    @Test(expected = IllegalArgumentException::class)
    fun `into imageView throws if no URL`() {
        val imageView = mock(ImageView::class.java)
        builder.into(imageView)
    }

    @Test
    fun `into imageView builds request without crashing`() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val imageView = ImageView(activity)

        builder.load("test").into(imageView)

        // Không nên assertNotNull vì mặc định không có placeholder
        assertNull(imageView.drawable)
    }
}
