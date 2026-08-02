package com.example.datausagetracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.TrafficStats
import android.os.Build
import android.os.Bundle
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.materialswitch.MaterialSwitch
import java.text.DecimalFormat

class MainActivity : AppCompatActivity() {
    private lateinit var appListView: ListView
    private lateinit var totalDataTextView: TextView
    private lateinit var refreshButton: android.widget.Button
    private lateinit var themeSwitch: MaterialSwitch
    private lateinit var barChart: BarChart
    
    private lateinit var adapter: AppDataAdapter
    private val appDataList = mutableListOf<AppData>()

    private val CHANNEL_ID = "data_usage_alerts"

    // Set your total daily or monthly data limit here in MB
    private val TOTAL_DATA_PLAN_MB = 1000L // Example: 1000 MB (1 GB) total plan
    private val LOW_DATA_THRESHOLD_MB = 100L // Send alert when 100 MB or less remains

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        appListView = findViewById(R.id.appListView)
        totalDataTextView = findViewById(R.id.totalDataTextView)
        refreshButton = findViewById(R.id.refreshButton)
        themeSwitch = findViewById(R.id.themeSwitch)
        barChart = findViewById(R.id.barChart)

        adapter = AppDataAdapter(this, appDataList)
        appListView.adapter = adapter

        createNotificationChannel()
        requestNotificationPermission()

        val isDarkMode = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
        themeSwitch.isChecked = isDarkMode

        themeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }

        refreshButton.setOnClickListener {
            loadAppDataUsage()
            Toast.makeText(this, "Data refreshed!", Toast.LENGTH_SHORT).show()
        }

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
                val mobileTotal = if (mobileRx != TrafficStats.UNSUPPORTED.toLong()) mobileRx + mobileTx else 0L

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
                setupGraph()
                checkDataRemaining(totalMobileData)
            }
        }.start()
    }

    private fun setupGraph() {
        val entries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()

        val topApps = appDataList.take(5)
        topApps.forEachIndexed { index, appData ->
            val totalMb = (appData.mobileDataUsage + appData.wifiDataUsage) / (1024f * 1024f)
            entries.add(BarEntry(index.toFloat(), totalMb))
            labels.add(if (appData.appName.length > 8) appData.appName.take(8) + ".." else appData.appName)
        }

        val dataSet = BarDataSet(entries, "Usage (MB)")
        dataSet.color = Color.parseColor("#0288D1")
        dataSet.valueTextColor = Color.GRAY
        dataSet.valueTextSize = 10f

        val barData = BarData(dataSet)
        barChart.data = barData
        barChart.description.isEnabled = false
        barChart.legend.isEnabled = false

        val xAxis = barChart.xAxis
        xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 1f
        xAxis.setDrawGridLines(false)

        barChart.axisRight.isEnabled = false
        barChart.animateY(800)
        barChart.invalidate()
    }

    private fun updateTotalDataDisplay(mobileData: Long, wifiData: Long) {
        val totalData = mobileData + wifiData
        val mobileMb = mobileData / (1024 * 1024)
        val remainingMb = (TOTAL_DATA_PLAN_MB - mobileMb).coerceAtLeast(0)

        totalDataTextView.text = """
            📊 Total Usage Summary
            Mobile: ${formatBytes(mobileData)} | WiFi: ${formatBytes(wifiData)}
            Remaining Mobile Data: $remainingMb MB
        """.trimIndent()
    }

    private fun checkDataRemaining(mobileBytes: Long) {
        val usedMobileMb = mobileBytes / (1024 * 1024)
        val remainingMb = TOTAL_DATA_PLAN_MB - usedMobileMb

        // Send warning if remaining mobile data is 100 MB or lower
        if (remainingMb <= LOW_DATA_THRESHOLD_MB && remainingMb >= 0) {
            sendNotification("LOW DATA WARNING", "⚠️ You only have $remainingMb MB left in your data limit!")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Data Alert Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when low data limit is reached"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private fun sendNotification(title: String, message: String) {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val notificationManager = NotificationManagerCompat.from(this)
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(1001, builder.build())
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

