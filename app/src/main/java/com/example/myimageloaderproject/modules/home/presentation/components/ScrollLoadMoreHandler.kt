package com.example.myimageloaderproject.modules.home.presentation.components

import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.imageloader.core.RequestManager
import com.example.myimageloaderproject.R
import com.example.myimageloaderproject.core.config.AppConfig

class ScrollLoadMoreHandler(
    private val recyclerView: RecyclerView,
    private val onLoadMore: () -> Unit,
    private val onScrollStateChanged: ((Int) -> Unit)? = null
) {
    
    private var lastScrollTime = 0L
    
    fun attach() {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)
                
                val currentTime = System.currentTimeMillis()
                lastScrollTime = currentTime
                
                val layoutManager = rv.layoutManager as? GridLayoutManager ?: return
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                val itemCount = rv.adapter?.itemCount ?: 0
                
                if (lastVisible >= itemCount - AppConfig.LOAD_MORE_THRESHOLD) {
                    onLoadMore()
                }
            }
            
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                super.onScrollStateChanged(rv, newState)
                onScrollStateChanged?.invoke(newState)
                
                when (newState) {
                    RecyclerView.SCROLL_STATE_SETTLING -> {
                        RequestManager.pauseAll()
                    }
                    
                    RecyclerView.SCROLL_STATE_IDLE -> {
                        val visibleViews = mutableListOf<ImageView>()
                        for (i in 0 until rv.childCount) {
                            val child = rv.getChildAt(i)
                            val img = child?.findViewById<ImageView>(R.id.imgPhoto)
                            if (img != null && child.isVisible) {
                                visibleViews.add(img)
                            }
                        }
                        RequestManager.resumeVisibleOnly(visibleViews)
                    }
                }
            }
        })
    }
}
