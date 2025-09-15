package com.example.myimageloaderproject

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.example.imageloader.core.ImageLoader

class TestActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Tạo ImageView
        val imageView = ImageView(this).apply {
            layoutParams =
                android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    500
                )
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        setContentView(imageView)

        ImageLoader.with()
            .load("https://images.unsplash.com/photo-1503023345310-bd7c1de61c7d")
            .resize(500, 500)
            .into(imageView)
    }
}
