/*
 * Copyright 2020 Eric A. Snell
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

package com.ealva.welite.db.table

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.ealva.welite.test.shared.CoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.ealva.welite.test.db.table.CommonOrderByTests as Common

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
class OrderByTests {
  @get:Rule var coroutineRule = CoroutineRule()

  @Suppress("DEPRECATION")
  @get:Rule var thrown: ExpectedException = ExpectedException.none()

  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `test order by Person id`() = coroutineRule.runBlockingTest {
    Common.testOrderByPersonId(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test order by Place id desc then Person id asc`() = coroutineRule.runBlockingTest {
    Common.testOrderByPlaceIdDescThenPersonIdAsc(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test order by Place id desc then Person id asc vararg`() = coroutineRule.runBlockingTest {
    Common.testOrderByPlaceIdDescThenPersonIdAscVararg(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test order by on join Place Person count group by`() = coroutineRule.runBlockingTest {
    Common.testOrderByOnJoinPlacePersonCountGroupBy(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test order by substring expression`() = coroutineRule.runBlockingTest {
    Common.testOrderBySubstringExpression(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test order by select expression`() = coroutineRule.runBlockingTest {
    Common.testOrderBySelectExpression(appCtx, coroutineRule.testDispatcher)
  }
}
