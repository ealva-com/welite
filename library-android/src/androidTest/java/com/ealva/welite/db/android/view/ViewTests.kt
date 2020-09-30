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

package com.ealva.welite.db.android.view

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ealva.welite.test.shared.CoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.ealva.welite.test.db.view.CommonViewTests as Common

@ExperimentalUnsignedTypes
@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class ViewTests {
  @get:Rule var coroutineRule = CoroutineRule()

  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun testCreateView() {
    Common.testCreateView()
  }

  @Test
  fun testViewFromExistingQuery() {
    Common.testViewFromExistingQuery()
  }

  @Test
  fun testQueryView() = coroutineRule.runBlockingTest {
    Common.testQueryView(appCtx, coroutineRule.testDispatcher)
  }
}
