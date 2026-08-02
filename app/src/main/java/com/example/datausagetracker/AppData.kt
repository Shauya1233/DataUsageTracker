package com.example.datausagetracker

import android.graphics.drawable.Drawable

data class AppData(
    val appName: String,
    val appIcon: Drawable?,
    val mobileDataBytes: Long,
    val wifiDataBytes: Long
) {
    val totalDataBytes: Long
        get() = mobileDataBytes + wifiDataBytes
}

