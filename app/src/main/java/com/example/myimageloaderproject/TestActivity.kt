package com.example.myimageloaderproject

import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.imageloader.core.ImageLoader
import com.example.myimageloaderproject.modules.home.presentation.viewmodel.PhotoViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TestActivity : AppCompatActivity() {

    private val viewModel: PhotoViewModel by viewModels()
    private lateinit var adapter: PhotoAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        val headerImage = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                400
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        ImageLoader.with(this)
            .load("https://images.unsplash.com/photo-1511485977113-f34c92461ad9")
            .resize(200, 200)
            .into(headerImage)

        // RecyclerView
        val recyclerView = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@TestActivity, 5)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        adapter = PhotoAdapter()
        recyclerView.adapter = adapter

        container.addView(headerImage)
        container.addView(recyclerView)

        setContentView(container)

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val layoutManager = rv.layoutManager as GridLayoutManager
                val totalItemCount = layoutManager.itemCount
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (lastVisible >= totalItemCount - 5) {
                    viewModel.loadMorePhotos()
                }
            }
        })

        lifecycleScope.launch {
            viewModel.photos.collectLatest {
                adapter.submitList(it)
            }
        }

        viewModel.loadPhotos()
    }
}
