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

package com.ealva.welite.db.compound

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
import com.ealva.welite.test.db.compound.CommonCompoundSelectTests as Common

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
class CompoundSelectTests {
  @get:Rule
  internal var coroutineRule = CoroutineRule()

  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `test union two selects`() = coroutineRule.runBlockingTest {
    Common.testUnionTwoSelects(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test union and alias select`() = coroutineRule.runBlockingTest {
    Common.testUnionAndAliasSelect(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test unionAll two selects`() = coroutineRule.runBlockingTest {
    Common.testUnionAllTwoSelects(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test intersect two selects`() = coroutineRule.runBlockingTest {
    Common.testIntersectTwoSelects(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test except two selects`() = coroutineRule.runBlockingTest {
    Common.testExceptTwoSelects(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test delete artist union count zero`() = coroutineRule.runBlockingTest {
    Common.testDeleteArtistUnionCountZero(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test simple union`() = coroutineRule.runBlockingTest {
    Common.testSimpleUnion(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test simple union all`() = coroutineRule.runBlockingTest {
    Common.testSimpleUnionAll(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test simple intersect`() = coroutineRule.runBlockingTest {
    Common.testSimpleIntersect(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test union 3`() = coroutineRule.runBlockingTest {
    Common.testUnionThree(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test union intersect 3`() = coroutineRule.runBlockingTest {
    Common.testUnionIntersectThree(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test unionAll except 3`() = coroutineRule.runBlockingTest {
    Common.testUnionAllExceptThree(appCtx, coroutineRule.testDispatcher)
  }
}
