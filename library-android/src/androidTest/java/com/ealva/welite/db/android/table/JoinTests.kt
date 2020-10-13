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

package com.ealva.welite.db.android.table

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ealva.welite.test.shared.CoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.runner.RunWith
import com.ealva.welite.test.db.table.CommonJoinTests as Common

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class JoinTests {
  @get:Rule var coroutineRule = CoroutineRule()

  @Suppress("DEPRECATION")
  @get:Rule var thrown: ExpectedException = ExpectedException.none()

  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun testJoinInnerJoin() = coroutineRule.runBlockingTest {
    Common.testJoinInnerJoin(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testFKJoin() = coroutineRule.runBlockingTest {
    Common.testFKJoin(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testJoinWithOrderBy() = coroutineRule.runBlockingTest {
    Common.testJoinWithOrderBy(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testJoinWithRelationshipTable() = coroutineRule.runBlockingTest {
    Common.testJoinWithRelationshipTable(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testCrossJoin() = coroutineRule.runBlockingTest {
    Common.testCrossJoin(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testJoinMultipleReferencesFKViolation() = coroutineRule.runBlockingTest {
    thrown.expect(SQLiteException::class.java)
    Common.testJoinMultipleReferencesFKViolation(appCtx, coroutineRule.testDispatcher)
  }

  @ExperimentalUnsignedTypes
  @Test
  fun testJoinWithAlias() = coroutineRule.runBlockingTest {
    Common.testJoinWithAlias(appCtx, coroutineRule.testDispatcher)
  }
}
