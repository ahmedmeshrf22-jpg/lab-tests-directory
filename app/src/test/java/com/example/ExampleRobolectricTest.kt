package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.InputStreamReader

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("تحاليل العقاد", appName)
  }

  @Test
  fun `verify lab_tests_android json asset contains exactly 331 records and no lab_to_lab_price`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val inputStream = context.assets.open("lab_tests_android.json")
    val jsonString = InputStreamReader(inputStream, Charsets.UTF_8).use { it.readText() }
    val jsonArray = JSONArray(jsonString)

    // Confirm count is exactly 331
    assertEquals(331, jsonArray.length())

    val ids = mutableSetOf<Int>()
    for (i in 0 until jsonArray.length()) {
      val obj = jsonArray.getJSONObject(i)
      assertTrue("Record $i missing id", obj.has("id"))
      val id = obj.getInt("id")
      assertTrue("Duplicate ID found: $id", ids.add(id))

      assertFalse("Record $id contains lab_to_lab_price field", obj.has("lab_to_lab_price"))
      assertTrue("Record $id missing customer_price field", obj.has("customer_price"))
    }

    assertEquals(331, ids.size)
  }
}

