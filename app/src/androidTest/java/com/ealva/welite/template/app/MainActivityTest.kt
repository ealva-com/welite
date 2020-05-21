package com.ealva.welite.template.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import com.ealva.welite.app.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

  @get:Rule
  var activityRule: ActivityTestRule<MainActivity> = ActivityTestRule(
    MainActivity::class.java
  )

  @Test
  fun typeANumber_resultIsDisplayed() {
  }
}
