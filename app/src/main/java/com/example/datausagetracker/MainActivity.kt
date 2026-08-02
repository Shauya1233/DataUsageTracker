package com.example.datausagetracker

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var totalUsageTextView: TextView
    private lateinit var appListView: ListView
    private val appDataList = mutableListOf<AppData>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        totalUsageTextView = findViewById(R.id.totalUsageText)
        appListView = findViewById(R.id.appListView)

        if (!hasUsageStatsPermission()) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasUsageStatsPermission()) {
            loadDataUsage()
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun loadDataUsage() {
        val networkStatsManager = getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        val packageManager = packageManager
        val installedApps = packageManager.getInstalledApplications(0)

        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        val startTime = cal.timeInMillis
        val endTime = System.currentTimeMillis()

        appDataList.clear()
        var grandTotalBytes = 0L

        for (app in installedApps) {
            if ((app.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue

            val uid = app.uid
            val mobileBytes = getUsageForUid(networkStatsManager, ConnectivityManager.TYPE_MOBILE, uid, startTime, endTime)
            val wifiBytes = getUsageForUid(networkStatsManager, ConnectivityManager.TYPE_WIFI, uid, startTime, endTime)

            val totalBytes = mobileBytes + wifiBytes
            if (totalBytes > 0) {
                val appName = packageManager.getApplicationLabel(app).toString()
                val icon = packageManager.getApplicationIcon(app)
                appDataList.add(AppData(appName, icon, mobileBytes, wifiBytes))
                grandTotalBytes += totalBytes
            }
        }

        appDataList.sortByDescending { it.totalDataBytes }

        val formattedTotal = android.text.format.Formatter.formatShortFileSize(this, grandTotalBytes)
        totalUsageTextView.text = "Total Monthly Usage: $formattedTotal"

        val adapter = AppDataAdapter(this, appDataList)
        appListView.adapter = adapter
    }

    private fun getUsageForUid(
        networkStatsManager: NetworkStatsManager,
        networkType: Int,
        uid: Int,
        startTime: Long,
        endTime: Long
    ): Long {
        return try {
            val bucket = NetworkStats.Bucket()
            val stats = networkStatsManager.queryDetailsForUid(networkType, null, startTime, endTime, uid)
            var total = 0L
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                total += bucket.rxBytes + bucket.txBytes
            }
            stats.close()
            total
        } catch (e: Exception) {
            0L
        }
    }
}

