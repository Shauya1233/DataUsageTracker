package com.example.datausagetracker

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import java.text.DecimalFormat

class AppDataAdapter(
    context: Context,
    private val appDataList: List<AppData>
) : ArrayAdapter<AppData>(context, 0, appDataList) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(
            R.layout.app_data_item,
            parent,
            false
        )

        val appData = appDataList[position]

        val appIcon = view.findViewById<ImageView>(R.id.appIcon)
        val appName = view.findViewById<TextView>(R.id.appName)
        val dataUsage = view.findViewById<TextView>(R.id.dataUsage)
        val mobileData = view.findViewById<TextView>(R.id.mobileData)
        val wifiData = view.findViewById<TextView>(R.id.wifiData)
        val progressBar = view.findViewById<ProgressBar>(R.id.usageProgress)

        appIcon.setImageDrawable(appData.appIcon)
        appName.text = appData.appName

        val totalUsage = appData.mobileDataUsage + appData.wifiDataUsage
        dataUsage.text = formatBytes(totalUsage)
        mobileData.text = "📱 ${formatBytes(appData.mobileDataUsage)}"
        wifiData.text = "📶 ${formatBytes(appData.wifiDataUsage)}"

        // Calculate progress (relative to max app usage in list)
        val maxUsage = appDataList.maxOfOrNull { it.mobileDataUsage + it.wifiDataUsage } ?: 1L
        progressBar.progress = ((totalUsage.toFloat() / maxUsage) * 100).toInt()

        return view
    }

    private fun formatBytes(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB")
        var size = bytes.toDouble()
        var unitIndex = 0

        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }

        val df = DecimalFormat("#.##")
        return "${df.format(size)} ${units[unitIndex]}"
    }
}
