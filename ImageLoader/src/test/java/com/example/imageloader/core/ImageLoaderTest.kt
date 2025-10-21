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
    fun `getInstance returns singleton`() {
        val context = RuntimeEnvironment.getApplication()
        val instance1 = ImageLoader.getInstance(context)
        val instance2 = ImageLoader.getInstance(context)
        assertSame(instance1, instance2)
    }

    @Test
    fun `with returns RequestBuilder`() {
        val context = RuntimeEnvironment.getApplication()
        val builder = ImageLoader.with(context)
        assertNotNull(builder)
        assertEquals(RequestBuilder::class.java, builder.javaClass)
    }

    @Test
    fun `ImageLoader has engine`() {
        val context = RuntimeEnvironment.getApplication()
        val loader = ImageLoader.getInstance(context)
        assertNotNull(loader.engine)
    }
}
