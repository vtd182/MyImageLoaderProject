package com.example.imageloader.logger

import android.content.Context
import android.content.Intent
import com.example.imageloader.ui.LogViewerActivity

object LogViewer {
    
    /**
     * Open the log viewer activity to display all logs
     * 
     * @param context Context to start the activity
     */
    fun open(context: Context) {
        val intent = Intent(context, LogViewerActivity::class.java)
        context.startActivity(intent)
    }
}
