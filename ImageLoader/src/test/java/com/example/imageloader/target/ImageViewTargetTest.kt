package com.example.imageloader.target

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.widget.FrameLayout
import android.widget.ImageView
import com.example.imageloader.core.EngineResource
import com.example.imageloader.core.abstract.ResourceListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.verifyNoInteractions
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImageViewTargetTest {

    @Test
    fun `onResourceReady should set bitmap and acquire resource`() {
        val activity =
            Robolectric.buildActivity(Activity::class.java).create().start().resume().get()
        val imageView = ImageView(activity)
        val target = ImageViewTarget(imageView)
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val resourceListener = mock(ResourceListener::class.java)
        val engineResource = EngineResource("key", bitmap, resourceListener)

        target.onResourceReady(engineResource)
        Robolectric.flushForegroundThreadScheduler()

        val drawable = imageView.drawable
        assertNotNull("Drawable should not be null after onResourceReady", drawable)
        assertTrue("Drawable should be a BitmapDrawable", drawable is BitmapDrawable)
        val setBitmap = (drawable as BitmapDrawable).bitmap
        assertSame("Bitmap instance should be the same as provided", bitmap, setBitmap)

        assertEquals(engineResource, getCurrent(target))
    }

    @Test
    fun `onResourceReady should release previous resource`() {
        val activity =
            Robolectric.buildActivity(Activity::class.java).create().start().resume().get()
        val imageView = ImageView(activity)
        val target = ImageViewTarget(imageView)
        val oldBitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val oldListener = mock(ResourceListener::class.java)
        val oldResource = EngineResource("oldKey", oldBitmap, oldListener)
        val newBitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val newListener = mock(ResourceListener::class.java)
        val newResource = EngineResource("newKey", newBitmap, newListener)

        // Set old resource
        target.onResourceReady(oldResource)
        Robolectric.flushForegroundThreadScheduler()

        // Set new resource -> should release old
        target.onResourceReady(newResource)
        Robolectric.flushForegroundThreadScheduler()

        // Verify old listener received release call with exact args
        verify(oldListener).onResourceReleased("oldKey", oldResource)
        // new listener shouldn't have been released (no interactions at all)
        verifyNoInteractions(newListener)

        val drawable = imageView.drawable
        assertNotNull("Drawable should not be null after setting new resource", drawable)
        assertTrue(drawable is BitmapDrawable)
        assertSame(newBitmap, (drawable as BitmapDrawable).bitmap)
    }


    @Test
    fun `onResourceReady should skip setting if bitmap is recycled`() {
        val activity =
            Robolectric.buildActivity(Activity::class.java).create().start().resume().get()
        val imageView = ImageView(activity)
        val target = ImageViewTarget(imageView)
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888).apply { recycle() }
        val resourceListener = mock(ResourceListener::class.java)
        val engineResource = EngineResource("key", bitmap, resourceListener)

        target.onResourceReady(engineResource)
        Robolectric.flushForegroundThreadScheduler()

        assertNull("Drawable should be null when bitmap is recycled", imageView.drawable)
        verifyNoInteractions(resourceListener)
    }

    @Test
    fun `onLoadFailed should set drawable to error drawable or null`() {
        val activity =
            Robolectric.buildActivity(Activity::class.java).create().start().resume().get()
        val imageView = ImageView(activity)
        val errorDrawable = ColorDrawable(0xFFFF0000.toInt())
        val target = ImageViewTarget(imageView, errorDrawable)

        imageView.setImageBitmap(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))

        target.onLoadFailed()
        Robolectric.flushForegroundThreadScheduler()

        assertNotNull("Drawable should be error drawable after onLoadFailed", imageView.drawable)
        assertEquals(errorDrawable, imageView.drawable)
    }

    @Test
    fun `onLoadFailed without error drawable should set drawable to null`() {
        val activity =
            Robolectric.buildActivity(Activity::class.java).create().start().resume().get()
        val imageView = ImageView(activity)
        val target = ImageViewTarget(imageView, null)

        imageView.setImageBitmap(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))

        target.onLoadFailed()
        Robolectric.flushForegroundThreadScheduler()

        assertNull("Drawable should be null after onLoadFailed", imageView.drawable)
    }

    @Test
    fun `onPlaceholderColor should set background color`() {
        val activity =
            Robolectric.buildActivity(Activity::class.java).create().start().resume().get()
        val imageView = ImageView(activity)
        val target = ImageViewTarget(imageView)
        val color = 0xFF123456.toInt()

        target.onPlaceholderColor(color)
        Robolectric.flushForegroundThreadScheduler()

        val bg = imageView.background
        assertNotNull("Background should not be null after onPlaceholderColor", bg)
        assertTrue("Background should be ColorDrawable", bg is ColorDrawable)
        assertEquals(color, (bg as ColorDrawable).color)
    }

    @Test
    fun `isValidFor should return true if key matches`() {
        val activity =
            Robolectric.buildActivity(Activity::class.java).create().start().resume().get()
        val parent = FrameLayout(activity)
        val imageView = ImageView(activity)
        parent.addView(imageView)
        val target = ImageViewTarget(imageView)

        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val resourceListener = mock(ResourceListener::class.java)
        val engineResource = EngineResource("testKey", bitmap, resourceListener)

        target.onResourceReady(engineResource)
        Robolectric.flushForegroundThreadScheduler()

        assertTrue(target.isValidFor("testKey"))
        assertFalse(target.isValidFor("otherKey"))
    }


    @Test
    fun `clear should release current resource and set image to null`() {
        val activity =
            Robolectric.buildActivity(Activity::class.java).create().start().resume().get()
        val imageView = ImageView(activity)
        val target = ImageViewTarget(imageView)
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val resourceListener = mock(ResourceListener::class.java)
        val engineResource = EngineResource("key", bitmap, resourceListener)

        target.onResourceReady(engineResource)
        Robolectric.flushForegroundThreadScheduler()

        target.clear()
        Robolectric.flushForegroundThreadScheduler()

        verify(resourceListener).onResourceReleased("key", engineResource)
        assertNull("Drawable should be null after clear", imageView.drawable)
        assertNull(getCurrent(target))
    }

    private fun getCurrent(target: ImageViewTarget): EngineResource? {
        val field = ImageViewTarget::class.java.getDeclaredField("current")
        field.isAccessible = true
        return field.get(target) as EngineResource?
    }

    @Test
    fun `onLoadStarted should clear background when shimmer disabled`() {
        val activity =
            Robolectric.buildActivity(Activity::class.java).create().start().resume().get()
        val imageView = ImageView(activity).apply {
            setBackgroundColor(0xFF0000FF.toInt()) // màu cũ
        }
        val target = ImageViewTarget(imageView, enableShimmer = false)

        target.onLoadStarted()
        Robolectric.flushForegroundThreadScheduler()

        // Background phải clear (transparent)
        val color = (imageView.background as? ColorDrawable)?.color
        assertEquals(android.graphics.Color.TRANSPARENT, color)
    }

    @Test
    fun `onLoadStarted should trigger shimmer when enabled`() {
        val activity =
            Robolectric.buildActivity(Activity::class.java).create().start().resume().get()
        val imageView = ImageView(activity)
        imageView.setImageDrawable(android.graphics.drawable.ColorDrawable(0xFFEEEEEE.toInt()))

        val target = ImageViewTarget(imageView, enableShimmer = true)
        target.onLoadStarted()
        Robolectric.flushForegroundThreadScheduler()

        val drawable = imageView.drawable
        assertNotNull(drawable)
        assertTrue(drawable is android.graphics.drawable.LayerDrawable)
    }

//    @Test
//    fun `onLoadFailed with retry should set click listener and reset on retry`() {
//        val activity =
//            Robolectric.buildActivity(Activity::class.java).create().start().resume().get()
//        val imageView = ImageView(activity)
//        val errorDrawable = ColorDrawable(0xFFFF0000.toInt())
//        val target = ImageViewTarget(imageView, errorDrawable)
//
//        var retried = false
//        val retryCallback = { retried = true }
//
//        // simulate failure with retry
//        target.onLoadFailed(retryCallback)
//        Robolectric.flushForegroundThreadScheduler()
//
//        assertNotNull(imageView.hasOnClickListeners())
//
//        // Giả lập click retry
//        imageView.performClick()
//        Robolectric.flushForegroundThreadScheduler()
//
//        // Retry callback phải chạy
//        assertTrue(retried)
//
//        // Drawable nên bị clear sau click (đang retry)
//        val bgColor = (imageView.background as? ColorDrawable)?.color
//        assertEquals(android.graphics.Color.TRANSPARENT, bgColor)
//    }

    @Test
    fun `showShimmer should wrap current drawable into LayerDrawable`() {
        val activity =
            Robolectric.buildActivity(Activity::class.java).create().start().resume().get()
        val imageView = ImageView(activity)
        val target = ImageViewTarget(imageView, enableShimmer = true)

        // Giả lập drawable gốc
        imageView.setImageDrawable(android.graphics.drawable.ColorDrawable(0xFFCCCCCC.toInt()))

        // Gọi showShimmer thông qua onLoadStarted()
        val method = ImageViewTarget::class.java.getDeclaredMethod("showShimmer")
        method.isAccessible = true
        method.invoke(target)

        val drawable = imageView.drawable
        assertTrue(drawable is android.graphics.drawable.LayerDrawable)
        val layerDrawable = drawable as android.graphics.drawable.LayerDrawable
        assertEquals(2, layerDrawable.numberOfLayers)
    }

    @Test
    fun `hideShimmer should stop and clear shimmer reference`() {
        val activity =
            Robolectric.buildActivity(Activity::class.java).create().start().resume().get()
        val imageView = ImageView(activity)
        val target = ImageViewTarget(imageView, enableShimmer = true)

        // Tạo shimmer giả
        val shimmerDrawableField =
            ImageViewTarget::class.java.getDeclaredField("shimmerDrawable")
                .apply { isAccessible = true }
        val shimmer = com.example.imageloader.drawable.ShimmerDrawable()
        shimmerDrawableField.set(target, shimmer)

        val method = ImageViewTarget::class.java.getDeclaredMethod("hideShimmer")
        method.isAccessible = true
        method.invoke(target)

        val after = shimmerDrawableField.get(target)
        assertNull(after)
    }
}
