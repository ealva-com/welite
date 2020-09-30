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

package com.ealva.welite.db.table

import android.content.Context
import android.database.sqlite.SQLiteException
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
import com.ealva.welite.test.db.table.CommonJoinTests as Common

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
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
  fun `test join inner join`() = coroutineRule.runBlockingTest {
    Common.testJoinInnerJoin(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test fk join`() = coroutineRule.runBlockingTest {
    Common.testFKJoin(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test join with order by`() = coroutineRule.runBlockingTest {
    Common.testJoinWithOrderBy(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test join with relationship table`() = coroutineRule.runBlockingTest {
    Common.testJoinWithRelationshipTable(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test cross join`() = coroutineRule.runBlockingTest {
    Common.testCrossJoin(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test join multiple references fk violation`() = coroutineRule.runBlockingTest {
    thrown.expect(SQLiteException::class.java)
    Common.testJoinMultipleReferencesFKViolation(appCtx, coroutineRule.testDispatcher)
  }

  @ExperimentalUnsignedTypes
  @Test
  fun `test join with alias`() = coroutineRule.runBlockingTest {
    Common.testJoinWithAlias(appCtx, coroutineRule.testDispatcher)
  }
}
