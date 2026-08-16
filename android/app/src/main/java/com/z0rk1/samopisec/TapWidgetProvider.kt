package com.z0rk1.samopisec

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.util.TypedValue
import android.widget.RemoteViews
import org.json.JSONObject
import java.io.File
import java.util.UUID

class TapWidgetProvider : AppWidgetProvider() {

  companion object {
    const val TAG = "TapWidgetProvider"
    const val ACTION_TAP = "com.z0rk1.samopisec.action.TAP"
    const val EXTRA_BUTTON_ID = "button_id"
    const val EXTRA_WIDGET_ID = "widget_id"

    fun configFile(context: Context): File =
      File(context.filesDir, "config.json")

    fun datapointsFile(context: Context): File =
      File(context.filesDir, "datapoints.jsonl")

    fun vibrate(context: Context) {
      val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        ?: return
      if (!vibrator.hasVibrator()) return
      // USAGE_HARDWARE_FEEDBACK входит в allowlist фоновых процессов
      // (VibrationSettings.BACKGROUND_PROCESS_USAGE_ALLOWLIST): тап по виджету будит
      // приложение в фоне, иначе VibratorManagerService молча игнорирует вибрацию
      // (Status.IGNORED_BACKGROUND, "Ignoring incoming vibration ... is background").
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        vibrator.vibrate(
          VibrationEffect.createOneShot(40, 200),
          VibrationAttributes.createForUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK))
      } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(40, 200))
      } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(40)
      }
    }
  }

  override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
    for (id in widgetIds) {
      manager.updateAppWidget(id, buildViews(context, id))
    }
  }

  override fun onAppWidgetOptionsChanged(
    context: Context,
    manager: AppWidgetManager,
    widgetId: Int,
    newOptions: Bundle?
  ) {
    super.onAppWidgetOptionsChanged(context, manager, widgetId, newOptions)
    manager.updateAppWidget(widgetId, buildViews(context, widgetId))
  }

  override fun onReceive(context: Context, intent: Intent) {
    Log.d(TAG, "onReceive: ${intent.action} extras=${intent.extras?.keySet()}")
    super.onReceive(context, intent)
    if (intent.action != ACTION_TAP) return
    val buttonId = intent.getStringExtra(EXTRA_BUTTON_ID) ?: return
    Log.d(TAG, "TAP for button=$buttonId")
    vibrate(context)
    appendDatapoint(context, buttonId)
  }

  fun buildViews(context: Context, widgetId: Int): RemoteViews {
    val buttons = readConfig(context)
    val opts = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)
    val hostCategory = opts.getInt(
      AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY,
      AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
    )
    val lockScreen = hostCategory == AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD
    val labelSize = if (lockScreen) 18f else 14f
    if (buttons.isEmpty()) {
      val empty = RemoteViews(context.packageName, R.layout.widget_empty)
      empty.setTextViewText(R.id.widget_empty_text, "Откройте приложение\nи добавьте кнопки")
      return empty
    }

    val root = RemoteViews(context.packageName, R.layout.widget_layout)
    val slots = intArrayOf(
      R.id.widget_slot_1, R.id.widget_slot_2, R.id.widget_slot_3,
      R.id.widget_slot_4, R.id.widget_slot_5, R.id.widget_slot_6
    )
    buttons.take(WidgetConfig.MAX_BUTTONS).forEachIndexed { i, button ->
      root.addView(slots[i], buttonView(context, widgetId, button, i, labelSize))
    }
    return root
  }

  private fun buttonView(
    context: Context,
    widgetId: Int,
    button: JSONObject,
    requestCode: Int,
    labelSize: Float
  ): RemoteViews {
    val view = RemoteViews(context.packageName, R.layout.widget_button)
    val id = button.optString("id", "")
    val label = button.optString("label", "?")
    val color = try {
      Color.parseColor(button.optString("color", "#1976D2"))
    } catch (e: IllegalArgumentException) {
      Color.parseColor("#1976D2")
    }
  view.setTextViewText(R.id.widget_label, label)
  view.setTextViewTextSize(R.id.widget_label, TypedValue.COMPLEX_UNIT_SP, labelSize)
  view.setInt(R.id.widget_button_root, "setBackgroundColor", color)
  view.setContentDescription(R.id.widget_button_root, label)

    val tap = Intent(context, TapWidgetProvider::class.java).apply {
      action = ACTION_TAP
      putExtra(EXTRA_BUTTON_ID, id)
      putExtra(EXTRA_WIDGET_ID, widgetId)
    }
    val pi = PendingIntent.getBroadcast(
      context, requestCode, tap,
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    view.setOnClickPendingIntent(R.id.widget_button_root, pi)
    return view
  }

  private fun readConfig(context: Context): List<JSONObject> {
    val file = configFile(context)
    if (!file.exists()) return emptyList()
    return WidgetConfig.parseButtons(file.readText())
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
