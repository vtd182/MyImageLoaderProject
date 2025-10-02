package com.example.myimageloaderproject.modules.home.presentation.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.imageloader.core.ImageLoader
import com.example.myimageloaderproject.R
import com.example.myimageloaderproject.modules.home.domain.model.UnsplashPhoto

class PhotoAdapter(
    private val spanProvider: () -> Int
) : ListAdapter<UnsplashPhoto, PhotoAdapter.PhotoViewHolder>(DiffCallback) {

    object DiffCallback : DiffUtil.ItemCallback<UnsplashPhoto>() {
        override fun areItemsTheSame(oldItem: UnsplashPhoto, newItem: UnsplashPhoto) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: UnsplashPhoto, newItem: UnsplashPhoto) =
            oldItem == newItem
    }

    inner class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgPhoto: ImageView = itemView.findViewById(R.id.imgPhoto)
        private val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)

        fun bind(photo: UnsplashPhoto, spanCount: Int) {
            val screenWidth = itemView.resources.displayMetrics.widthPixels
            val spacing = (8 * itemView.resources.displayMetrics.density).toInt() * 2
            val itemWidth = (screenWidth / spanCount) - spacing

            val ratio = photo.height.toFloat() / photo.width.toFloat()
            val itemHeight = (itemWidth * ratio).toInt()

            photo.urls.small?.let {
                ImageLoader.with(imgPhoto.context)
                    .overrideSize(itemWidth, itemHeight)
                    .placeholder(photo.color)
                    .load(it)
                    .into(imgPhoto)
            }

            val desc = photo.description ?: "Photo ${photo.id}"
            tvDescription.text = desc

            tvDescription.setOnLongClickListener {
                Toast.makeText(
                    it.context,
                    "Download option for ${photo.id}",
                    Toast.LENGTH_SHORT
                ).show()
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(getItem(position), spanProvider())
    }
}
