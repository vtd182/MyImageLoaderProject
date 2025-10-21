package com.example.imageloader.core

import android.widget.ImageView
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@ExperimentalCoroutinesApi
class RequestManagerTest {

    @Mock
    private lateinit var imageView: ImageView

    @Mock
    private lateinit var job: Job

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun `pauseAll sets paused`() {
        RequestManager.pauseAll()
        assertTrue(RequestManager.isPaused())
    }
}
