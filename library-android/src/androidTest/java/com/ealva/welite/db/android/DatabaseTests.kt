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

package com.ealva.welite.db.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ealva.welite.db.DatabaseConfiguration
import com.ealva.welite.db.UnmarkedTransactionException
import com.ealva.welite.test.shared.CoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.runner.RunWith
import com.ealva.welite.test.db.CommonDatabaseTests as Common

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class DatabaseTests {
  @get:Rule var coroutineRule = CoroutineRule()

  @Suppress("DEPRECATION")
  @get:Rule var thrown: ExpectedException = ExpectedException.none()

  private lateinit var appCtx: Context
  private var config: DatabaseConfiguration? = null

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
    config = null
  }

  @Test
  fun testInTransaction() = coroutineRule.runBlockingTest {
    Common.testInTransaction(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testSqliteVersion() = coroutineRule.runBlockingTest {
    Common.testSqliteVersion(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testDbLifecycle() = coroutineRule.runBlockingTest {
    Common.testDbLifecycle(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun createTableTest() = coroutineRule.runBlockingTest {
    Common.createTableTest(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testCreateTablesRearrangeOrderForCreate() = coroutineRule.runBlockingTest {
    Common.testCreateTablesRearrangeOrderForCreate(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testIntegritySunnyDay() = coroutineRule.runBlockingTest {
    Common.testIntegritySunnyDay(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testCreateTablesWithForeignKeys() = coroutineRule.runBlockingTest {
    Common.testCreateTablesWithForeignKeys(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testCreateAndDropTable() = coroutineRule.runBlockingTest {
    Common.testCreateAndDropTable(appCtx, coroutineRule.testDispatcher)
  }

  @ExperimentalUnsignedTypes
  @Test
  fun testCreateAndDropView() = coroutineRule.runBlockingTest {
    Common.testCreateAndDropView(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testCreateAndDropTrigger() = coroutineRule.runBlockingTest {
    Common.testCreateAndDropTrigger(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testCreateAndDropIndex() = coroutineRule.runBlockingTest {
    Common.testCreateAndDropIndex(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testCreateAndRollback() = coroutineRule.runBlockingTest {
    Common.testCreateAndRollback(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testTxnToString() = coroutineRule.runBlockingTest {
    Common.testTxnToString(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testNoAutoCommitTxnWithoutMarkingSuccessOrRollback() =
    coroutineRule.runBlockingTest {
      thrown.expect(UnmarkedTransactionException::class.java)
      Common.testNoAutoCommitTxnWithoutMarkingSuccessOrRollback(
        appCtx,
        coroutineRule.testDispatcher
      )
    }
}
