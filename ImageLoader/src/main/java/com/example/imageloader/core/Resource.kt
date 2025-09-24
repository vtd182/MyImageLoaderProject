package com.example.imageloader.core

import android.graphics.Bitmap
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean


interface ResourceListener {
    fun onResourceReleased(key: String, resource: Resource)
}


class Resource(
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


    @Synchronized
    fun release() {
        Log.d("Resource", "refCount=$refCount release: $key ")
        check(refCount > 0) { "Cannot release a resource that is not acquired" }
        refCount--
        if (refCount == 0 && released.compareAndSet(false, true)) {
            listener.onResourceReleased(key, this)
        }
    }


    fun getBitmap(): Bitmap = bitmap


    fun isMutable(): Boolean = bitmap.isMutable


    fun sizeInBytes(): Int = try { bitmap.allocationByteCount } catch (t: Throwable) { bitmap.byteCount }


    fun recycle() {
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}