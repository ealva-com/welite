/*
 * Copyright 2020 eAlva.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress(
  "NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING",
  "NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING"
)

package com.ealva.welite.db.android.expr

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ealva.welite.test.shared.CoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.runner.RunWith
import com.ealva.welite.test.db.expr.CommonFunctionTests as Common

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class FunctionTests {
  @get:Rule internal var coroutineRule = CoroutineRule()

  @Suppress("DEPRECATION")
  @get:Rule var thrown: ExpectedException = ExpectedException.none()

  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun testSumColumn() = coroutineRule.runBlockingTest {
    Common.testSumColumn(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testAvgMinMaxColumns() = coroutineRule.runBlockingTest {
    Common.testAvgMinMaxColumns(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testSubstring() = coroutineRule.runBlockingTest {
    Common.testSubstring(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testLowercaseAndUppercase() = coroutineRule.runBlockingTest {
    Common.testLowercaseAndUppercase(appCtx, coroutineRule.testDispatcher)
  }
}
