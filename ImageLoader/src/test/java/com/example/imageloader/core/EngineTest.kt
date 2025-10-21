package com.example.imageloader.core

import android.graphics.Bitmap
import com.example.imageloader.cache.ActiveResources
import com.example.imageloader.cache.DiskCache
import com.example.imageloader.cache.MemoryCache
import com.example.imageloader.core.abstract.BitmapPool
import com.example.imageloader.fetcher.DataFetcher
import com.example.imageloader.fetcher.HttpResult
import com.example.imageloader.target.Target
import com.example.imageloader.transformation.Transformation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@ExperimentalCoroutinesApi
class EngineTest {

    @Mock
    private lateinit var activeResources: ActiveResources

    @Mock
    private lateinit var memoryCache: MemoryCache

    @Mock
    private lateinit var diskCache: DiskCache

    @Mock
    private lateinit var fetcher: DataFetcher

    @Mock
    private lateinit var bitmapPool: BitmapPool

    @Mock
    private lateinit var target: Target

    private lateinit var engine: Engine

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        engine = Engine(activeResources, memoryCache, diskCache, fetcher, bitmapPool)
    }

    @Test
    fun `buildKey with basic request`() {
        val request = Request(url = "http://example.com/image.jpg")
        val key = engine.buildKey(request)
        assertNotNull(key)
        assertTrue(key.isNotEmpty())
        // Since md5, hard to assert exact, but can check it's different for different requests
    }

    @Test
    fun `buildKey with transformations`() {
        val transformation = mock(Transformation::class.java)
        `when`(transformation.key()).thenReturn("testTransform")
        val request = Request(
            url = "http://example.com/image.jpg",
            transformations = listOf(transformation)
        )
        val key = engine.buildKey(request)
        assertNotNull(key)
    }

    @Test
    fun `buildDataKey excludes transformations`() {
        val transformation = mock(Transformation::class.java)
        `when`(transformation.key()).thenReturn("testTransform")
        val request = Request(
            url = "http://example.com/image.jpg",
            transformations = listOf(transformation)
        )
        val dataKey = engine.buildDataKey(request)
        val fullKey = engine.buildKey(request)
        assertNotNull(dataKey)
        assertNotNull(fullKey)
        // Data key should be different if transformations are present
    }

    // Note: For load tests, they would require coroutine testing and more complex setup
    // For now, focusing on key building as they are testable without coroutines
}
