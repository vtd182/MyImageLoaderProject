package com.example.imageloader.core

import android.widget.ImageView
import com.example.imageloader.target.Target
import com.example.imageloader.transformation.Transformation
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RequestBuilderTest {

    @Mock
    private lateinit var engine: Engine

    @Mock
    private lateinit var imageView: ImageView

    @Mock
    private lateinit var target: Target

    private lateinit var builder: RequestBuilder

    init {
        MockitoAnnotations.openMocks(this)
        builder = RequestBuilder(engine)
    }

    @Test
    fun `load sets url and returns builder`() {
        val result = builder.load("testUrl")
        assertEquals(builder, result)
    }

    @Test
    fun `resize sets dimensions and returns builder`() {
        val result = builder.resize(100, 200)
        assertEquals(builder, result)
    }

    @Test
    fun `transform adds transformations and returns builder`() {
        val transform = mock(Transformation::class.java)
        val result = builder.transform(transform)
        assertEquals(builder, result)
    }

}
