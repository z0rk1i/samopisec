package com.z0rk1.samopisec

import org.json.JSONObject

/** Чистый парсинг конфига кнопок виджета (без Android-зависимостей) — покрыт unit-тестами. */
object WidgetConfig {
  fun parseButtons(json: String): List<JSONObject> {
    return try {
      val arr = JSONObject(json).optJSONArray("buttons")
      if (arr == null) emptyList()
      else (0 until arr.length()).mapNotNull { i ->
        val b = try {
          arr.getJSONObject(i)
        } catch (e: Exception) {
          null
        }
        if (isValidButton(b)) b else null
      }
    } catch (e: Exception) {
      emptyList()
    }
  }

  fun isValidButton(b: JSONObject?): Boolean =
    b != null && !b.optString("id", "").isBlank() && !b.optString("label", "").isBlank()
}