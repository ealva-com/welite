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
import android.os.Build.VERSION_CODES.LOLLIPOP
import androidx.test.core.app.ApplicationProvider
import com.ealva.welite.test.shared.CoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.ealva.welite.test.db.CommonDeleteTests as Common

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [LOLLIPOP])
class DeleteTests {
  @get:Rule var coroutineRule = CoroutineRule()

  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `test delete`() = coroutineRule.runBlockingTest {
    Common.testDelete(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test deleteAll and delete`() = coroutineRule.runBlockingTest {
    Common.testDeleteAllAndDelete(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test deleteWhere`() = coroutineRule.runBlockingTest {
    Common.testDeleteWhere(appCtx, coroutineRule.testDispatcher)
  }
}
