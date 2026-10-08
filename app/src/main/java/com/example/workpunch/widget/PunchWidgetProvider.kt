package com.example.workpunch.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.workpunch.MainActivity
import com.example.workpunch.R
import com.example.workpunch.data.AppDatabase
import com.example.workpunch.data.PunchRepository
import com.example.workpunch.data.toEpochDayKey
import com.example.workpunch.data.toLocalTimeText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class PunchWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateWidgets(context, appWidgetManager, appWidgetIds)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_CLOCK_IN || intent.action == ACTION_CLOCK_OUT) {
            val pendingResult = goAsync()
            val repository = PunchRepository(AppDatabase.create(context).punchDao())
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val dao = AppDatabase.create(context).punchDao()
                    val record = dao.getByDay(LocalDate.now().toEpochDayKey())
                    when (intent.action) {
                        ACTION_CLOCK_IN -> if (record?.clockInMillis == null) repository.clockIn()
                        ACTION_CLOCK_OUT -> if (record?.clockInMillis != null && record.clockOutMillis == null) {
                            repository.clockOut()
                        }
                    }
                    val manager = AppWidgetManager.getInstance(context)
                    val ids = manager.getAppWidgetIds(ComponentName(context, PunchWidgetProvider::class.java))
                    updateWidgets(context, manager, ids)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        const val ACTION_CLOCK_IN = "com.example.workpunch.action.CLOCK_IN"
        const val ACTION_CLOCK_OUT = "com.example.workpunch.action.CLOCK_OUT"

        fun refreshAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PunchWidgetProvider::class.java))
            CoroutineScope(Dispatchers.IO).launch {
                updateWidgets(context, manager, ids)
            }
        }

        private suspend fun updateWidgets(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
            val dao = AppDatabase.create(context).punchDao()
            val record = dao.getByDay(LocalDate.now().toEpochDayKey())
            val canClockIn = record?.clockInMillis == null
            val canClockOut = record?.clockInMillis != null && record.clockOutMillis == null
            widgetIds.forEach { widgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_punch).apply {
                    setTextViewText(R.id.widget_clock_in, record?.clockInMillis?.toLocalTimeText() ?: "上班未打卡")
                    setTextViewText(R.id.widget_clock_out, record?.clockOutMillis?.toLocalTimeText() ?: "下班未打卡")
                    setPunchButtonState(
                        context = context,
                        viewId = R.id.widget_clock_in_button,
                        enabled = canClockIn,
                        action = ACTION_CLOCK_IN,
                        requestCode = 10
                    )
                    setPunchButtonState(
                        context = context,
                        viewId = R.id.widget_clock_out_button,
                        enabled = canClockOut,
                        action = ACTION_CLOCK_OUT,
                        requestCode = 11
                    )
                    setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
                }
                manager.updateAppWidget(widgetId, views)
            }
        }

        private fun actionIntent(context: Context, action: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, PunchWidgetProvider::class.java).setAction(action)
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun RemoteViews.setPunchButtonState(
            context: Context,
            viewId: Int,
            enabled: Boolean,
            action: String,
            requestCode: Int
        ) {
            setBoolean(viewId, "setEnabled", enabled)
            setInt(
                viewId,
                "setBackgroundResource",
                if (enabled) R.drawable.widget_button_primary else R.drawable.widget_button_disabled
            )
            setTextColor(
                viewId,
                context.getColor(if (enabled) android.R.color.white else R.color.widget_disabled_text)
            )
            if (enabled) {
                setOnClickPendingIntent(viewId, actionIntent(context, action, requestCode))
            } else {
                // Replacing the old action avoids a stale pending intent after the button turns gray.
                setOnClickPendingIntent(viewId, openAppIntent(context))
            }
        }

        private fun openAppIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
            return PendingIntent.getActivity(
                context,
                20,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
