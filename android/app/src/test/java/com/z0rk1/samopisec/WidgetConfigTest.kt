package com.z0rk1.samopisec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetConfigTest {
  @Test
  fun `valid buttons are kept`() {
    val json = """
      {"buttons":[
        {"id":"tea","label":"Чай","color":"#1e88e5"},
        {"id":"coffee","label":"Кофе","color":"#8e24aa"}
      ]}
    """.trimIndent()
    val buttons = WidgetConfig.parseButtons(json)
    assertEquals(2, buttons.size)
    assertEquals("tea", buttons[0].optString("id"))
    assertEquals("Чай", buttons[0].optString("label"))
  }

  @Test
  fun `button without id is skipped`() {
    val json = """{"buttons":[{"label":"no-id"},{"id":"ok","label":"Ок"}]}"""
    val buttons = WidgetConfig.parseButtons(json)
    assertEquals(1, buttons.size)
    assertEquals("ok", buttons[0].optString("id"))
  }

  @Test
  fun `button without label is skipped`() {
    val json = """{"buttons":[{"id":"x"},{"id":"y","label":"Y"}]}"""
    val buttons = WidgetConfig.parseButtons(json)
    assertEquals(1, buttons.size)
    assertEquals("y", buttons[0].optString("id"))
  }

  @Test
  fun `non-object entry is skipped`() {
    val json = """{"buttons":["garbage",{"id":"ok","label":"Ок"}]}"""
    val buttons = WidgetConfig.parseButtons(json)
    assertEquals(1, buttons.size)
  }

  @Test
  fun `invalid json returns empty`() {
    assertTrue(WidgetConfig.parseButtons("not json").isEmpty())
  }

  @Test
  fun `missing buttons key returns empty`() {
    assertTrue(WidgetConfig.parseButtons("{}").isEmpty())
  }

  @Test
  fun `more than MAX_BUTTONS buttons are capped`() {
    val buttons = (1..10).joinToString(",") { """{"id":"b$it","label":"L$it"}""" }
    val json = """{"buttons":[$buttons]}"""
    val parsed = WidgetConfig.parseButtons(json)
    assertEquals(WidgetConfig.MAX_BUTTONS, parsed.size)
    assertEquals("b1", parsed[0].optString("id"))
    assertEquals("b${WidgetConfig.MAX_BUTTONS}", parsed.last().optString("id"))
  }
}