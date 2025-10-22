package com.example.imageloader.core

import android.graphics.Bitmap
import android.util.Log
import com.example.imageloader.core.abstract.ResourceListener
import java.util.concurrent.atomic.AtomicBoolean


class EngineResource(
    val key: String,
    private val bitmap: Bitmap,
    private val listener: ResourceListener
) {
    private var refCount = 0
    private val released = AtomicBoolean(false)


    @Synchronized
    fun acquire() {
        check(!released.get()) { "Cannot acquire a released resource" }
        refCount++
    }
    
    fun isReleased(): Boolean = released.get()


    @Synchronized
    fun release() {
        check(refCount > 0) { "Cannot release a resource that is not acquired" }
        refCount--
        if (refCount == 0 && released.compareAndSet(false, true)) {
            Log.d("Resource", "release: $key ")
            listener.onResourceReleased(key, this)
        }
    }


    fun getBitmap(): Bitmap = bitmap


    fun isMutable(): Boolean = bitmap.isMutable


    fun sizeInBytes(): Int = try {
        bitmap.allocationByteCount
    } catch (t: Throwable) {
        bitmap.byteCount
    }


    fun recycle() {
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}