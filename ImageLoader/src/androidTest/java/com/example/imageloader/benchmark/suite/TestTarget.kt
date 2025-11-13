package com.example.imageloader.benchmark.suite

import android.graphics.Bitmap
import com.example.imageloader.core.EngineResource
import com.example.imageloader.target.Target
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * SimpleTestTarget - Target đơn giản cho benchmark testing.
 * 
 * KHÔNG phụ thuộc ImageView/View lifecycle.
 * Cho phép MANUAL control acquire/release để test cache behavior.
 */
class SimpleTestTarget : Target {
    
    @Volatile
    var resource: EngineResource? = null
        private set
    
    @Volatile
    var loadSuccess: Boolean = false
        private set
    
    @Volatile
    var loadFailed: Boolean = false
        private set
    
    private var latch: CountDownLatch? = null
    
    override fun onLoadStarted() {
        loadSuccess = false
        loadFailed = false
        latch = CountDownLatch(1)
    }
    
    override fun onPlaceholderColor(color: Int) {
        // No-op for test
    }
    
    override fun isValidFor(key: String): Boolean {
        return true  // Always valid for test
    }
    
    override fun onResourceReady(engineResource: EngineResource) {
        // Release old resource
        resource?.release()
        
        // Acquire new resource
        resource = engineResource
        resource?.acquire()
        
        loadSuccess = true
        latch?.countDown()
    }
    
    override fun onLoadFailed(onRetry: (() -> Unit)?) {
        loadFailed = true
        latch?.countDown()
    }
    
    /**
     * MANUAL release - move Active → Memory Cache
     */
    fun release() {
        resource?.release()
        resource = null
        loadSuccess = false
    }
    
    fun getBitmap(): Bitmap? {
        return resource?.getBitmap()
    }
    
    /**
     * Wait for load to complete (success or failure)
     */
    fun await(timeoutSeconds: Long = 10): Boolean {
        return latch?.await(timeoutSeconds, TimeUnit.SECONDS) ?: false
    }
}
