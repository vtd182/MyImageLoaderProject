package com.example.myimageloaderproject.modules.home.presentation.adapter

import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.imageloader.core.ImageLoader
import com.example.myimageloaderproject.modules.home.domain.model.UnsplashPhoto

class PhotoAdapter(
    private val onLongClick: (UnsplashPhoto) -> Unit
) : ListAdapter<UnsplashPhoto, PhotoAdapter.PhotoViewHolder>(DiffCallback) {

    object DiffCallback : DiffUtil.ItemCallback<UnsplashPhoto>() {
        override fun areItemsTheSame(oldItem: UnsplashPhoto, newItem: UnsplashPhoto) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: UnsplashPhoto, newItem: UnsplashPhoto) =
            oldItem == newItem
    }

    inner class PhotoViewHolder(private val imageView: ImageView) :
        RecyclerView.ViewHolder(imageView) {

        fun bind(photo: UnsplashPhoto) {
            if (photo.urls.small == null) return
            ImageLoader.Companion.with(imageView.context)
                .load(photo.urls.small)
                .resize(400, 400)
                .into(imageView)

            imageView.setOnLongClickListener {
                onLongClick(photo)
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val imageView = ImageView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        return PhotoViewHolder(imageView)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}