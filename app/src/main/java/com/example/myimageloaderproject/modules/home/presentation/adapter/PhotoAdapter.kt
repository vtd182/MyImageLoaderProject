package com.example.myimageloaderproject.modules.home.presentation.adapter

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
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
import com.example.imageloader.core.RequestManager
import com.example.imageloader.core.RequestPriority
import com.example.imageloader.transformation.CenterCropRoundedCorners
import com.example.myimageloaderproject.R
import com.example.myimageloaderproject.modules.home.domain.model.UnsplashPhoto
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

class PhotoAdapter(
    private val spanProvider: () -> Int
) : ListAdapter<UnsplashPhoto, PhotoAdapter.PhotoViewHolder>(DiffCallback) {

    private var cornerEnabled = false

    fun setCornerEnabled(enabled: Boolean) {
        cornerEnabled = enabled
    }

    object DiffCallback : DiffUtil.ItemCallback<UnsplashPhoto>() {
        override fun areItemsTheSame(oldItem: UnsplashPhoto, newItem: UnsplashPhoto) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: UnsplashPhoto, newItem: UnsplashPhoto) =
            oldItem == newItem
    }

    inner class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgPhoto: ImageView = itemView.findViewById(R.id.imgPhoto)
        private val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)

        fun bind(photo: UnsplashPhoto, spanCount: Int, priority: RequestPriority) {
            val screenWidth = itemView.resources.displayMetrics.widthPixels
            val spacing = (8 * itemView.resources.displayMetrics.density).toInt()
            val itemWidth = (screenWidth / spanCount) - spacing

            val ratio = 1
            val itemHeight = (itemWidth * ratio).toInt()

            photo.urls.raw?.let {
                val request = ImageLoader.with(imgPhoto.context)
                    .overrideSize(itemWidth, itemHeight)
                    .placeholder(photo.color)
                    .error(R.drawable.ic_retry)
                    .resize(400, 400)
                    .enableShimmer(true)
                    .priority(priority)
                    .load(it)

                if (cornerEnabled) {
                    request.transform(CenterCropRoundedCorners(40f))
                }

                request.into(imgPhoto)
            }

            val desc = photo.description ?: "Photo ${photo.id}"
            tvDescription.text = desc

            tvDescription.setOnLongClickListener {
                showDownloadSheet(it, photo)
                true
            }
        }

        private fun showDownloadSheet(view: View, photo: UnsplashPhoto) {
            val context = view.context
            val dialog = BottomSheetDialog(context)
            val sheetView = LayoutInflater.from(context)
                .inflate(R.layout.bottom_sheet_download, view.parent as? ViewGroup, false)
            dialog.setContentView(sheetView)

            val btnDownload = sheetView.findViewById<TextView>(R.id.btnDownload)
            val btnCancel = sheetView.findViewById<TextView>(R.id.btnCancel)

            btnDownload.setOnClickListener {
                dialog.dismiss()
                Toast.makeText(
                    context,
                    context.getString(R.string.downloading_image),
                    Toast.LENGTH_SHORT
                ).show()
                downloadImage(
                    photo.urls.full ?: photo.urls.small ?: return@setOnClickListener,
                    context
                )
            }

            btnCancel.setOnClickListener { dialog.dismiss() }

            dialog.show()
        }

        private fun downloadImage(url: String, context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val fileName = "photo_${System.currentTimeMillis()}.jpg"
                    val input = URL(url).openStream()

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                            put(
                                MediaStore.Images.Media.RELATIVE_PATH,
                                Environment.DIRECTORY_DOWNLOADS
                            )
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }

                        val resolver = context.contentResolver
                        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)

                        uri?.let {
                            resolver.openOutputStream(it)?.use { output ->
                                input.copyTo(output)
                            }
                            values.clear()
                            values.put(MediaStore.Images.Media.IS_PENDING, 0)
                            resolver.update(uri, values, null, null)
                        }

                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.download_success),
                                Toast.LENGTH_LONG
                            )
                                .show()
                        }
                    } else {
                        val downloadsDir =
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        if (!downloadsDir.exists()) downloadsDir.mkdirs()

                        val file = File(downloadsDir, fileName)
                        FileOutputStream(file).use { output -> input.copyTo(output) }

                        val uri = Uri.fromFile(file)
                        context.sendBroadcast(
                            Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri)
                        )

                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.download_success_path,
                                    file.absolutePath
                                ),
                                Toast.LENGTH_LONG
                            )
                                .show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.download_failed, e.message),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        RequestManager.clear(holder.imgPhoto)
        val priority = when {
            position < 6 -> RequestPriority.HIGH
            position < 20 -> RequestPriority.NORMAL
            else -> RequestPriority.LOW
        }
        holder.bind(getItem(position), spanProvider(), priority)
    }

    override fun onViewRecycled(holder: PhotoViewHolder) {
        RequestManager.clear(holder.imgPhoto)
        super.onViewRecycled(holder)
    }
}
