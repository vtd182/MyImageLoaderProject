package com.example.myimageloaderproject.modules.home.presentation.components

import android.content.Context
import android.view.LayoutInflater
import android.widget.Button
import android.widget.Toast
import com.example.myimageloaderproject.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.ChipGroup
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsBottomSheetHelper(
    private val context: Context
) {
    
    fun show(
        currentSpanCount: Int,
        isCornerEnabled: Boolean,
        onSpanCountChanged: (Int) -> Unit,
        onCornerToggled: (Boolean) -> Unit
    ) {
        val bottomSheetDialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(
            R.layout.bottom_sheet_settings,
            null,
            false
        )
        bottomSheetDialog.setContentView(view)
        
        val switchCorner = view.findViewById<SwitchMaterial>(R.id.switchCorner)
        val chipGroup = view.findViewById<ChipGroup>(R.id.chipGroupColumns)
        val btnClose = view.findViewById<Button>(R.id.btnClose)
        
        switchCorner.isChecked = isCornerEnabled
        when (currentSpanCount) {
            1 -> chipGroup.check(R.id.chip1Column)
            2 -> chipGroup.check(R.id.chip2Columns)
            3 -> chipGroup.check(R.id.chip3Columns)
        }
        
        switchCorner.setOnCheckedChangeListener { _, isChecked ->
            onCornerToggled(isChecked)
            val msg = if (isChecked) {
                context.getString(R.string.corner_enabled)
            } else {
                context.getString(R.string.corner_disabled)
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
        
        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val newSpanCount = when (checkedIds.firstOrNull()) {
                R.id.chip1Column -> 1
                R.id.chip2Columns -> 2
                R.id.chip3Columns -> 3
                else -> currentSpanCount
            }
            
            if (newSpanCount != currentSpanCount) {
                onSpanCountChanged(newSpanCount)
                Toast.makeText(
                    context,
                    context.getString(R.string.columns_changed, newSpanCount),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        
        btnClose.setOnClickListener {
            bottomSheetDialog.dismiss()
        }
        
        bottomSheetDialog.show()
    }
}
