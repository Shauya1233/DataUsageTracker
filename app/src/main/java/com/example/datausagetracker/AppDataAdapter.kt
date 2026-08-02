herepackage com.example.datausagetracker
import android.content.Context
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView

class AppDataAdapter(
    context: Context,
    private val appList: List<AppData>
) : ArrayAdapter<AppData>(context, 0, appList) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(
            R.layout.app_data_item, parent, false
        )

        val item = getItem(position) ?: return view

        val appIconView = view.findViewById<ImageView>(R.id.appIcon)
        val appNameView = view.findViewById<TextView>(R.id.appName)
        val dataUsageView = view.findViewById<TextView>(R.id.dataUsage)
        val mobileDataView = view.findViewById<TextView>(R.id.mobileData)
        val wifiDataView = view.findViewById<TextView>(R.id.wifiData)
        val usageProgressBar = view.findViewById<ProgressBar>(R.id.usageProgress)

        appNameView.text = item.appName
        if (item.appIcon != null) {
            appIconView.setImageDrawable(item.appIcon)
        } else {
            appIconView.setImageResource(android.R.drawable.sym_def_app_icon)
        }

        dataUsageView.text = Formatter.formatShortFileSize(context, item.totalDataBytes)
        mobileDataView.text = "Mobile: " + Formatter.formatShortFileSize(context, item.mobileDataBytes)
        wifiDataView.text = "Wi-Fi: " + Formatter.formatShortFileSize(context, item.wifiDataBytes)

        val maxUsage = appList.maxOfOrNull { it.totalDataBytes } ?: 1L
        val progress = if (maxUsage > 0) ((item.totalDataBytes * 100) / maxUsage).toInt() else 0
        usageProgressBar.progress = progress

        return view
    }
}
