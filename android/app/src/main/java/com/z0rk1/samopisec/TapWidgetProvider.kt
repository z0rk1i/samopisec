package com.z0rk1.samopisec

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.UUID

class TapWidgetProvider : AppWidgetProvider() {

  companion object {
    const val TAG = "TapWidgetProvider"
    const val ACTION_TAP = "com.z0rk1.samopisec.action.TAP"
    const val EXTRA_BUTTON_ID = "button_id"
    const val EXTRA_WIDGET_ID = "widget_id"
    const val MAX_BUTTONS = 6
    const val COLUMNS = 2

    fun configFile(context: Context): File =
      File(context.filesDir, "config.json")

    fun datapointsFile(context: Context): File =
      File(context.filesDir, "datapoints.jsonl")
  }

  override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
    for (id in widgetIds) {
      manager.updateAppWidget(id, buildViews(context, id))
    }
  }

  override fun onReceive(context: Context, intent: Intent) {
    Log.d(TAG, "onReceive: ${intent.action} extras=${intent.extras?.keySet()}")
    super.onReceive(context, intent)
    if (intent.action != ACTION_TAP) return
    val buttonId = intent.getStringExtra(EXTRA_BUTTON_ID) ?: return
    Log.d(TAG, "TAP for button=$buttonId")
    appendDatapoint(context, buttonId)
    val manager = AppWidgetManager.getInstance(context)
    val ids = manager.getAppWidgetIds(ComponentName(context, TapWidgetProvider::class.java))
    for (id in ids) {
      manager.updateAppWidget(id, buildViews(context, id))
    }
  }

  fun buildViews(context: Context, widgetId: Int): RemoteViews {
    val buttons = readConfig(context)
    val counts = todayCounts(context)
    val total = counts.values.sum()
    val root = RemoteViews(context.packageName, R.layout.widget_layout)
    root.setTextViewText(R.id.widget_header, if (total > 0) "Сегодня: $total" else "Samopisec")

    val rows = buttons.take(MAX_BUTTONS).chunked(COLUMNS)
    for (rowButtons in rows) {
      val single = rowButtons.size == 1
      val row = RemoteViews(context.packageName,
                            if (single) R.layout.widget_row_full else R.layout.widget_row)
      if (single) {
        row.addView(R.id.widget_row_full_root, buttonView(context, widgetId, rowButtons[0], counts))
      } else {
        row.addView(R.id.widget_row_left, buttonView(context, widgetId, rowButtons[0], counts))
        row.addView(R.id.widget_row_right, buttonView(context, widgetId, rowButtons[1], counts))
      }
      root.addView(R.id.widget_grid, row)
    }
    if (rows.isEmpty()) {
      val empty = RemoteViews(context.packageName, R.layout.widget_empty)
      empty.setTextViewText(R.id.widget_empty_text, "Откройте приложение\nи добавьте кнопки")
      root.addView(R.id.widget_grid, empty)
    }
    return root
  }

  private fun buttonView(
    context: Context,
    widgetId: Int,
    button: JSONObject?,
    counts: Map<String, Int>
  ): RemoteViews {
    val view = RemoteViews(context.packageName, R.layout.widget_button)
    if (button == null) {
      view.setViewVisibility(R.id.widget_button_root, View.GONE)
      return view
    }
    val id = button.optString("id", "")
    val label = button.optString("label", "?")
    val color = try {
      Color.parseColor(button.optString("color", "#1976D2"))
    } catch (e: IllegalArgumentException) {
      Color.parseColor("#1976D2")
    }
    view.setViewVisibility(R.id.widget_button_root, View.VISIBLE)
    view.setTextViewText(R.id.widget_label, label)
    view.setTextViewText(R.id.widget_count, counts[id]?.toString() ?: "0")
    view.setInt(R.id.widget_button_root, "setBackgroundColor", color)

    val tap = Intent(context, TapWidgetProvider::class.java).apply {
      action = ACTION_TAP
      putExtra(EXTRA_BUTTON_ID, id)
      putExtra(EXTRA_WIDGET_ID, widgetId)
    }
    val pi = PendingIntent.getBroadcast(
      context, id.hashCode(), tap,
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    view.setOnClickPendingIntent(R.id.widget_button_root, pi)
    return view
  }

  private fun readConfig(context: Context): List<JSONObject> {
    val file = configFile(context)
    if (!file.exists()) return emptyList()
    return try {
      val arr = JSONObject(file.readText()).optJSONArray("buttons")
      if (arr == null) emptyList()
      else (0 until arr.length()).map { arr.getJSONObject(it) }
    } catch (e: Exception) {
      emptyList()
    }
  }

  private fun todayCounts(context: Context): Map<String, Int> {
    val file = datapointsFile(context)
    if (!file.exists()) return emptyMap()

    val cal = Calendar.getInstance().apply {
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    val startToday = cal.timeInMillis

    val counts = mutableMapOf<String, Int>()
    file.forEachLine { line ->
      if (line.isBlank()) return@forEachLine
      try {
        val obj = JSONObject(line)
        if (obj.optLong("ts", 0) >= startToday) {
          val id = obj.optString("button-id", "")
          if (id.isNotEmpty()) counts[id] = (counts[id] ?: 0) + 1
        }
      } catch (e: Exception) {
        // skip malformed line
      }
    }
    return counts
  }

  private fun appendDatapoint(context: Context, buttonId: String) {
    val file = datapointsFile(context)
    val dp = JSONObject().apply {
      put("id", UUID.randomUUID().toString())
      put("button-id", buttonId)
      put("ts", System.currentTimeMillis())
    }
    file.appendText(dp.toString() + "\n")
  }
}
