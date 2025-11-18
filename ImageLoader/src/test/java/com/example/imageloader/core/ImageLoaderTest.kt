package com.example.imageloader.core

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ImageLoaderTest {

    @Test
    fun `with returns singleton RequestBuilder`() {
        val context = RuntimeEnvironment.getApplication()
        val builder1 = ImageLoader.with(context)
        val builder2 = ImageLoader.with(context)
        assertNotNull(builder1)
        assertNotNull(builder2)
        assertEquals(RequestBuilder::class.java, builder1.javaClass)
        assertEquals(RequestBuilder::class.java, builder2.javaClass)
    }

    @Test
    fun `with returns RequestBuilder`() {
        val context = RuntimeEnvironment.getApplication()
        val builder = ImageLoader.with(context)
        assertNotNull(builder)
        assertEquals(RequestBuilder::class.java, builder.javaClass)
    }

    @Test
    fun `init with config enables bitmap pool`() {
        val context = RuntimeEnvironment.getApplication()
        ImageLoader.init(context) {
            enableBitmapPool(0.3f)
        }
        val builder = ImageLoader.with(context)
        assertNotNull(builder)
    }
}
