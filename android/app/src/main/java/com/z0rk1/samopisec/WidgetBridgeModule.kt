package com.z0rk1.samopisec

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

class WidgetBridgeModule(reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext) {

  override fun getName(): String = "WidgetBridge"

  @ReactMethod
  fun refreshWidgets() {
    val context: Context = reactApplicationContext
    val manager = AppWidgetManager.getInstance(context)
    val ids = manager.getAppWidgetIds(ComponentName(context, TapWidgetProvider::class.java))
    Log.d("WidgetBridge", "refreshWidgets: ids=${ids.toList()}")
    if (ids.isEmpty()) return
    val provider = TapWidgetProvider()
    provider.onUpdate(context, manager, ids)
    Log.d("WidgetBridge", "refreshWidgets: onUpdate done for ${ids.size} ids")
  }
}
