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

package com.ealva.welite.db

import android.content.Context
import android.os.Build.VERSION_CODES.LOLLIPOP
import androidx.test.core.app.ApplicationProvider
import com.ealva.welite.test.db.SomeMediaTable
import com.ealva.welite.test.shared.CoroutineRule
import com.nhaarman.expect.expect
import com.nhaarman.expect.fail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.hamcrest.CoreMatchers.isA
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Semaphore
import com.ealva.welite.test.db.CommonDatabaseTests as Common

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [LOLLIPOP])
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
  fun `test inTransaction`() = coroutineRule.runBlockingTest {
    Common.testInTransaction(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test sqlite version`() = coroutineRule.runBlockingTest {
    Common.testSqliteVersion(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test db lifecycle`() = coroutineRule.runBlockingTest {
    Common.testDbLifecycle(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun createTableTest() = coroutineRule.runBlockingTest {
    Common.createTableTest(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `create tables rearrange order for create`() = coroutineRule.runBlockingTest {
    Common.testCreateTablesRearrangeOrderForCreate(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `check integrity sunny day`() = coroutineRule.runBlockingTest {
    Common.testIntegritySunnyDay(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `create tables with foreign keys`() = coroutineRule.runBlockingTest {
    Common.testCreateTablesWithForeignKeys(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test create and drop table`() = coroutineRule.runBlockingTest {
    Common.testCreateAndDropTable(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test create and drop View`() = coroutineRule.runBlockingTest {
    Common.testCreateAndDropView(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test create and drop Trigger`() = coroutineRule.runBlockingTest {
    Common.testCreateAndDropTrigger(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test create and drop Index`() = coroutineRule.runBlockingTest {
    Common.testCreateAndDropIndex(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test create and rollback`() = coroutineRule.runBlockingTest {
    Common.testCreateAndRollback(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test txn toString`() = coroutineRule.runBlockingTest {
    Common.testTxnToString(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test no auto commit txn without marking success or rollback`() =
    coroutineRule.runBlockingTest {
      thrown.expect(UnmarkedTransactionException::class.java)
      Common.testNoAutoCommitTxnWithoutMarkingSuccessOrRollback(
        appCtx,
        coroutineRule.testDispatcher
      )
    }

  @Test
  fun `test isUiThread`() {
    var testedOnUi = false
    var testedOnNonUi = false
    val lock = Semaphore(1, true).apply { acquire() }
    GlobalScope.launch(Dispatchers.IO) {
      expect(isUiThread).toBe(false)
      testedOnNonUi = true
      lock.release()
    }
    lock.acquire()
    GlobalScope.launch(Dispatchers.Main) {
      expect(isUiThread).toBe(true)
      testedOnUi = true
      lock.release()
    }
    lock.acquire()
    expect(testedOnUi).toBe(true)
    expect(testedOnNonUi).toBe(true)
    lock.release()
  }

  fun `test execPragma called in wrong scope`() = coroutineRule.runBlockingTest {
    thrown.expect(IllegalStateException::class.java)
    getDatabase().let { db ->
      db.transaction { rollback() }
      checkNotNull(config).execPragma("do my pragma")
      fail("Should not reach here")
    }
  }

  @Test
  fun `test transaction on UI thread throws`() = coroutineRule.runBlockingTest {
    thrown.expect(WeLiteException::class.java)
    val db = Database(
      context = appCtx,
      version = 1,
      tables = setOf(SomeMediaTable),
      migrations = emptyList(),
      requireMigration = false,
      openParams = OpenParams(
        allowWorkOnUiThread = false,
        dispatcher = Dispatchers.Main
      )
    )
    db.transaction { setSuccessful() }
  }

  @Test
  fun `test query on UI thread throws`() = coroutineRule.runBlockingTest {
    thrown.expectCause(isA(IllegalStateException::class.java))
    val db = Database(
      context = appCtx,
      version = 1,
      tables = setOf(SomeMediaTable),
      migrations = emptyList(),
      requireMigration = false,
      openParams = OpenParams(
        allowWorkOnUiThread = false,
        dispatcher = Dispatchers.Main
      )
    )
    db.query {}
  }

  private fun getDatabase(): Database {
    return Database(
      context = appCtx,
      version = 1,
      tables = setOf(),
      migrations = emptyList(),
      requireMigration = false,
      openParams = OpenParams(
        allowWorkOnUiThread = true,
        dispatcher = coroutineRule.testDispatcher
      )
    ) {
      onConfigure { config = it }
    }
  }
}
