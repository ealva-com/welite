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

package com.ealva.welite.db.expr

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.ealva.welite.db.table.Table
import com.ealva.welite.db.table.all
import com.ealva.welite.db.table.select
import com.ealva.welite.test.common.CoroutineRule
import com.ealva.welite.test.common.runBlockingTest
import com.ealva.welite.test.common.withTestDatabase
import com.nhaarman.expect.expect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
class FunctionTests {
  @get:Rule var coroutineRule = CoroutineRule()
  @get:Rule var thrown: ExpectedException = ExpectedException.none()

  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `test sum column`() = coroutineRule.runBlockingTest {
    withTestDatabase(appCtx, listOf(DataTable), coroutineRule.testDispatcher, true) {
      transaction {
        expect(DataTable.exists).toBe(true)
        DataTable.insert { it[data] = 10 }
        DataTable.insert { it[data] = 20 }
        DataTable.insert { it[data] = 30 }
        setSuccessful()
      }

      query {
        val sumColumn = DataTable.data.sum()
        val list = DataTable.select(sumColumn).all().sequence { it[sumColumn] }.toList()
        expect(list).toHaveSize(1)
        expect(list[0]).toBe(60)
        expect(DataTable.select(sumColumn).all().longForQuery()).toBe(60)

        val modulo = sumColumn % 7
        expect(DataTable.select(modulo).all().longForQuery()).toBe(4)
      }
    }
  }

  @Test
  fun `test avg min max column`() = coroutineRule.runBlockingTest {
    withTestDatabase(appCtx, listOf(DataTable), coroutineRule.testDispatcher, true) {
      transaction {
        expect(DataTable.exists).toBe(true)
        DataTable.insert { it[data] = 10 }
        DataTable.insert { it[data] = 20 }
        DataTable.insert { it[data] = 30 }
        setSuccessful()
      }

      query {
        expect(DataTable.select(DataTable.data.avg()).all().longForQuery()).toBe(20)
        expect(DataTable.select(DataTable.data.min()).all().longForQuery()).toBe(10)
        expect(DataTable.select(DataTable.data.max()).all().longForQuery()).toBe(30)
      }
    }
  }

  @Test
  fun `test substring`() = coroutineRule.runBlockingTest {
    withTestDatabase(appCtx, listOf(DataTable), coroutineRule.testDispatcher, true) {
      transaction {
        expect(DataTable.exists).toBe(true)
        DataTable.insert { it[data] = 10; it[name] = "ZabY" }
        DataTable.insert { it[data] = 20; it[name] = "Zcd" }
        DataTable.insert { it[data] = 30; it[name] = "AefQ" }
        setSuccessful()
      }

      query {
        val substringColumn = DataTable.name.substring(2, 2)
        val list = DataTable
          .select(substringColumn)
          .all()
          .orderBy(DataTable.data to SortOrder.DESC)
          .sequence { it[substringColumn] }
          .toList()
        expect(list).toHaveSize(3)
        expect(list[0]).toBe("ef")
        expect(list[1]).toBe("cd")
        expect(list[2]).toBe("ab")
      }
    }
  }

  @Test
  fun `test lowercase and uppercase`() = coroutineRule.runBlockingTest {
    withTestDatabase(appCtx, listOf(DataTable), coroutineRule.testDispatcher, true) {
      transaction {
        expect(DataTable.exists).toBe(true)
        DataTable.insert { it[data] = 10; it[name] = "Bob" }
        DataTable.insert { it[data] = 20; it[name] = "Sally" }
        DataTable.insert { it[data] = 30; it[name] = "Jane" }
        setSuccessful()
      }

      query {
        val lowerCaseCol = DataTable.name.lowerCase()
        val list = DataTable
          .select(lowerCaseCol)
          .all()
          .orderBy(DataTable.data to SortOrder.DESC)
          .sequence { it[lowerCaseCol] }
          .toList()
        expect(list).toHaveSize(3)
        expect(list[0]).toBe("jane")
        expect(list[1]).toBe("sally")
        expect(list[2]).toBe("bob")

        val upperCaseCol = DataTable.name.upperCase()
        val upperList = DataTable
          .select(upperCaseCol)
          .all()
          .orderBy(DataTable.name to SortOrder.ASC)
          .sequence { it[upperCaseCol] }
          .toList()
        expect(upperList).toHaveSize(3)
        expect(upperList[0]).toBe("BOB")
        expect(upperList[1]).toBe("JANE")
        expect(upperList[2]).toBe("SALLY")
      }
    }
  }
}

object DataTable : Table() {
  val id = long("_id") { primaryKey() }
  val data = integer("data")
  val name = text("name") { default("") }
}
