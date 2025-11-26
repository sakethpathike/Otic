package com.sakethh.otic

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel

class OticVM(context: Context) : ViewModel() {
    var allPermissionsGranted by mutableStateOf(false)
        private set

    fun updatePermissionsGrantedState(granted: Boolean) {
        allPermissionsGranted = granted
    }

    init {
        allPermissionsGranted = OticService.permissions.all { permission ->
            ContextCompat.checkSelfPermission(
                context, permission
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}