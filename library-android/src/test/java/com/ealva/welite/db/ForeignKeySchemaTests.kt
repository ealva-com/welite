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

package com.ealva.welite.db

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.ealva.welite.test.shared.CoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.ealva.welite.test.db.CommonForeignKeySchemaTests as Common

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
class ForeignKeySchemaTests {
  @get:Rule var coroutineRule = CoroutineRule()

  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun testForeignKeyList() = coroutineRule.runBlockingTest {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      Common.testForeignKeyList(appCtx, coroutineRule.testDispatcher)
    }
  }

  @Test
  fun testForeignKeyIntegrity() = coroutineRule.runBlockingTest {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      Common.testForeignKeyIntegrity(appCtx, coroutineRule.testDispatcher)
    }
  }
}
