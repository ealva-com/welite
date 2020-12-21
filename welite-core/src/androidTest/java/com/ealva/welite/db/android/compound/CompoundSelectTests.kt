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

package com.ealva.welite.db.android.compound

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ealva.welite.test.db.compound.CommonCompoundSelectTests
import com.ealva.welite.test.shared.CoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class CompoundSelectTests {
  @get:Rule
  var coroutineRule = CoroutineRule()

  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun testUnionTwoSelects() = coroutineRule.runBlockingTest {
    CommonCompoundSelectTests.testUnionTwoSelects(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testUnionAllTwoSelects() = coroutineRule.runBlockingTest {
    CommonCompoundSelectTests.testUnionAllTwoSelects(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testIntersectTwoSelects() = coroutineRule.runBlockingTest {
    CommonCompoundSelectTests.testIntersectTwoSelects(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testExceptTwoSelects() = coroutineRule.runBlockingTest {
    CommonCompoundSelectTests.testExceptTwoSelects(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testDeleteArtistUnionCountZero() = coroutineRule.runBlockingTest {
    CommonCompoundSelectTests.testDeleteArtistUnionCountZero(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testSimpleUnion() = coroutineRule.runBlockingTest {
    CommonCompoundSelectTests.testSimpleUnion(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testSimpleUnionAll() = coroutineRule.runBlockingTest {
    CommonCompoundSelectTests.testSimpleUnionAll(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testSimpleIntersect() = coroutineRule.runBlockingTest {
    CommonCompoundSelectTests.testSimpleIntersect(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testSimpleExcept() = coroutineRule.runBlockingTest {
    CommonCompoundSelectTests.testSimpleExcept(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testUnionThree() = coroutineRule.runBlockingTest {
    CommonCompoundSelectTests.testUnionThree(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testUnionUnionAllThree() = coroutineRule.runBlockingTest {
    CommonCompoundSelectTests.testUnionUnionAllThree(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testUnionIntersectThree() = coroutineRule.runBlockingTest {
    CommonCompoundSelectTests.testUnionIntersectThree(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testUnionAllExceptThree() = coroutineRule.runBlockingTest {
    CommonCompoundSelectTests.testUnionAllExceptThree(appCtx, coroutineRule.testDispatcher)
  }
}
