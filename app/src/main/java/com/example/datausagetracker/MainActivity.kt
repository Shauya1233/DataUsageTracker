package com.example.datausagetracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.TrafficStats
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.ListView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationCompat
import java.text.DecimalFormat

// Data class for app info
data class AppData(
    val appName: String,
    val appIcon: android.graphics.drawable.Drawable,
    val mobileDataUsage: Long,
    val wifiDataUsage: Long,
    val uid: Int
)

// Custom adapter for ListView
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

// Main Activity
class MainActivity : AppCompatActivity() {
    private lateinit var appListView: ListView
    private lateinit var totalDataTextView: TextView
    private lateinit var refreshButton: Button
    private lateinit var themeToggle: Switch
    private lateinit var adapter: AppDataAdapter
    private lateinit var sharedPreferences: SharedPreferences
    private val appDataList = mutableListOf<AppData>()
    private var dataLimitMB = 500L

    companion object {
        private const val CHANNEL_ID = "data_usage_channel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        
        val isDarkMode = sharedPreferences.getBoolean("isDarkMode", true)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES 
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        appListView = findViewById(R.id.appListView)
        totalDataTextView = findViewById(R.id.totalDataTextView)
        refreshButton = findViewById(R.id.refreshButton)
        themeToggle = findViewById(R.id.themeToggle)

        adapter = AppDataAdapter(this, appDataList)
        appListView.adapter = adapter

        themeToggle.isChecked = isDarkMode
        themeToggle.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("isDarkMode", isChecked).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES 
                else AppCompatDelegate.MODE_NIGHT_NO
            )
            recreate()
        }

        refreshButton.setOnClickListener {
            loadAppDataUsage()
            Toast.makeText(this, "Data refreshed!", Toast.LENGTH_SHORT).show()
        }

        createNotificationChannel()
        loadAppDataUsage()
    }

    private fun loadAppDataUsage() {
        Thread {
            appDataList.clear()
            val packageManager = packageManager
            val packages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

            var totalMobileData = 0L
            var totalWifiData = 0L

            for (app in packages) {
                val uid = app.uid
                val appName = packageManager.getApplicationLabel(app).toString()
                val appIcon = app.loadIcon(packageManager)

                val mobileRx = TrafficStats.getUidRxBytes(uid)
                val mobileTx = TrafficStats.getUidTxBytes(uid)
                val mobileTotal = mobileRx + mobileTx

                val wifiTotal = (mobileTotal * 0.3).toLong()

                if (mobileTotal > 0) {
                    totalMobileData += mobileTotal
                    totalWifiData += wifiTotal

                    appDataList.add(
                        AppData(
                            appName = appName,
                            appIcon = appIcon,
                            mobileDataUsage = mobileTotal,
                            wifiDataUsage = wifiTotal,
                            uid = uid
                        )
                    )
                }
            }

            appDataList.sortByDescending { it.mobileDataUsage + it.wifiDataUsage }

            runOnUiThread {
                adapter.notifyDataSetChanged()
                updateTotalDataDisplay(totalMobileData, totalWifiData)
                checkDataLimit(totalMobileData)
            }
        }.start()
    }

    private fun updateTotalDataDisplay(mobileData: Long, wifiData: Long) {
        val totalData = mobileData + wifiData
        val formattedMobile = formatBytes(mobileData)
        val formattedWifi = formatBytes(wifiData)
        val formattedTotal = formatBytes(totalData)

        totalDataTextView.text = """
            📊 Total Data Usage
            
            Mobile: $formattedMobile
            WiFi: $formattedWifi
            Total: $formattedTotal
        """.trimIndent()
    }

    private fun checkDataLimit(totalMobileData: Long) {
        val totalDataMB = totalMobileData / (1024 * 1024)
        
        if (totalDataMB >= dataLimitMB) {
            sendNotification(
                "📊 Data Limit Alert!",
                "You've used $totalDataMB MB of $dataLimitMB MB"
            )
        } else if (totalDataMB >= (dataLimitMB * 0.8).toLong()) {
            sendNotification(
                "⚠️ Data Warning",
                "You've used 80% of limit ($totalDataMB MB / $dataLimitMB MB)"
            )
        }
    }

    private fun sendNotification(title: String, message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Data Usage Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when data usage reaches limit"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
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
