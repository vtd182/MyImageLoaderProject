package com.example.imageloader.core

import android.widget.ImageView
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
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
    fun setup() {
        MockitoAnnotations.openMocks(this)
        RequestManager.clearInternalStateForTest()
    }
x
    @After
    fun teardown() {
        RequestManager.clearInternalStateForTest()
    }

    @Test
    fun `pauseAll sets paused`() {
        RequestManager.pauseAll()
        assertTrue(RequestManager.isPaused())
    }

    @Test
    fun `track should store job when not paused`() {
        RequestManager.track(imageView, job)
        // verify rằng job được thêm
        RequestManager.assertContainsRunning(imageView)
    }

    @Test
    fun `track should cancel old job and replace with new`() {
        val oldJob = mock(Job::class.java)
        RequestManager.track(imageView, oldJob)
        val newJob = mock(Job::class.java)
        RequestManager.track(imageView, newJob)

        verify(oldJob).cancel()
        RequestManager.assertContainsRunning(imageView)
    }

    @Test
    fun `track should add to pending when paused`() {
        RequestManager.pauseAll()
        val callback = mock(Runnable::class.java)
        RequestManager.track(imageView, null)
        RequestManager.track(imageView, null)
        RequestManager.track(imageView, job)
        RequestManager.track(imageView, job)

        RequestManager.track(imageView, null)
        RequestManager.track(imageView, job, onResume = { callback.run() })

        RequestManager.assertNotRunning(imageView)
    }

    @Test
    fun `clear should cancel job and reset imageView`() {
        RequestManager.track(imageView, job)
        RequestManager.clear(imageView)

        verify(job).cancel()
        verify(imageView).setImageDrawable(null)
        RequestManager.assertNotRunning(imageView)
    }

    @Test
    fun `resumeVisibleOnly should clear paused state when no pending`() = runTest {
        RequestManager.pauseAll()
        RequestManager.resumeVisibleOnly(emptyList())
        assertFalse(RequestManager.isPaused())
    }


    @Test
    fun `getAdaptiveDelay returns correct delays based on FPS`() {
        val method = RequestManager::class.java.getDeclaredMethod("getAdaptiveDelay")
        method.isAccessible = true

        // currentFps >= 50
        RequestManager.setFps(60f)
        assertEquals(80L, method.invoke(RequestManager))

        // 45 <= currentFps < 50
        RequestManager.setFps(48f)
        assertEquals(160L, method.invoke(RequestManager))

        // currentFps < 45
        RequestManager.setFps(30f)
        assertEquals(240L, method.invoke(RequestManager))
    }

    // ====== Helper extension for internal assertions =======
    private fun RequestManager.assertContainsRunning(view: ImageView) {
        val runningField = RequestManager::class.java.getDeclaredField("running")
        runningField.isAccessible = true
        val map = runningField.get(RequestManager) as Map<*, *>
        assertTrue(map.containsKey(view))
    }

    private fun RequestManager.assertNotRunning(view: ImageView) {
        val runningField = RequestManager::class.java.getDeclaredField("running")
        runningField.isAccessible = true
        val map = runningField.get(RequestManager) as Map<*, *>
        assertFalse(map.containsKey(view))
    }

    private fun RequestManager.addPending(view: ImageView, callback: () -> Unit) {
        val field = RequestManager::class.java.getDeclaredField("pending")
        field.isAccessible = true
        val map = field.get(RequestManager) as MutableMap<ImageView, () -> Unit>
        map[view] = callback
    }

    private fun RequestManager.setFps(value: Float) {
        val field = RequestManager::class.java.getDeclaredField("currentFps")
        field.isAccessible = true
        field.set(RequestManager, value)
    }

    private fun RequestManager.clearInternalStateForTest() {
        val fields = listOf("running", "pending")
        for (name in fields) {
            val field = RequestManager::class.java.getDeclaredField(name)
            field.isAccessible = true
            val map = field.get(RequestManager) as MutableMap<*, *>
            (map as MutableMap<Any?, Any?>).clear()
        }

        val pausedField = RequestManager::class.java.getDeclaredField("isPaused")
        pausedField.isAccessible = true
        pausedField.set(RequestManager, false)
    }
}
