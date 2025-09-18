package com.example.myimageloaderproject

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myimageloaderproject.modules.home.presentation.viewmodel.PhotoViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TestActivity : AppCompatActivity() {

    private val viewModel: PhotoViewModel by viewModels()
    private lateinit var adapter: PhotoAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val recyclerView = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@TestActivity, 1)
        }
        setContentView(recyclerView)

        adapter = PhotoAdapter()
        recyclerView.adapter = adapter

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
