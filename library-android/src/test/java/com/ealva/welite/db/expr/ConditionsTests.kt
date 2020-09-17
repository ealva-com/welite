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
import com.ealva.welite.db.table.selectAll
import com.ealva.welite.db.table.selectWhere
import com.ealva.welite.db.table.where
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
class ConditionsTests {
  @get:Rule var coroutineRule = CoroutineRule()
  @get:Rule var thrown: ExpectedException = ExpectedException.none()

  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `test ops TRUE and FALSE`() = coroutineRule.runBlockingTest {
    withTestDatabase(appCtx, listOf(CondTable), coroutineRule.testDispatcher, true) {
      transaction {
        expect(CondTable.exists).toBe(true)
        CondTable.insert { it[data] = 10 }
        CondTable.insert { it[data] = 20 }
        CondTable.insert { it[data] = 30 }
      }

      query {
        val list = CondTable.selectAll().sequence { it[CondTable.id] }.toList()
        expect(CondTable.select().where { Op.FALSE }.count()).toBe(0)
        expect(CondTable.select().where { Op.TRUE }.count()).toBe(list.size.toLong())
      }
    }
  }

  @Test
  fun `test selecting same column`() = coroutineRule.runBlockingTest {
    withTestDatabase(appCtx, listOf(CondTable), coroutineRule.testDispatcher, true) {
      transaction {
        expect(CondTable.exists).toBe(true)
        CondTable.insert { it[data] = 10 }
        CondTable.insert { it[data] = 20 }
        CondTable.insert { it[data] = 30 }
      }

      query {
        val list = CondTable
          .select(CondTable.id, CondTable.name, CondTable.name, CondTable.id).all()
          .sequence {
            expect(it.columnCount).toBe(2)
            it[CondTable.id]
          }
          .toList()
        expect(list).toHaveSize(3)
      }
    }
  }

  @Test
  fun `compare with nullable column`() = coroutineRule.runBlockingTest {
    val table = object : Table("foo") {
      val c1 = integer("c1")
      val c2 = optInteger("c2")
      val c3 = optInteger("c3")  // will always be null
    }

    withTestDatabase(appCtx, listOf(table), coroutineRule.testDispatcher, true) {
      transaction {
        table.insert { it[c1] = 0; it[c2] = 0 }
        table.insert { it[c1] = 1; it[c2] = 2 }
        table.insert { it[c1] = 2; it[c2] = 1 }
      }
      query {
        table
          .selectWhere { table.c1 less table.c2 }
          .sequence { it[table.c1] }
          .toList()
          .let { list ->
            expect(list).toHaveSize(1)
            expect(list[0]).toBe(1)
          }

        table
          .selectWhere { table.c1 lessEq table.c2 }
          .orderBy(table.c1)
          .sequence { it[table.c1] }
          .toList()
          .let { list ->
            expect(list).toHaveSize(2)
            expect(list).toBe(listOf(0, 1))
          }
        table
          .selectWhere { table.c1 greater table.c2 }
          .sequence { it[table.c1] }
          .toList()
          .let { list ->
            expect(list).toHaveSize(1)
            expect(list[0]).toBe(2)
          }

        table
          .selectWhere { table.c1 greaterEq table.c2 }
          .orderBy(table.c1)
          .sequence { it[table.c1] }
          .toList()
          .let { list ->
            expect(list).toHaveSize(2)
            expect(list).toBe(listOf(0, 2))
          }

        table
          .selectWhere { table.c2 less table.c1 }
          .sequence { it[table.c1] }
          .toList()
          .let { list ->
            expect(list).toHaveSize(1)
            expect(list[0]).toBe(2)
          }

        table
          .selectWhere { table.c2 lessEq table.c1 }
          .orderBy(table.c1)
          .sequence { it[table.c1] }
          .toList()
          .let { list ->
            expect(list).toHaveSize(2)
            expect(list).toBe(listOf(0, 2))
          }
        table
          .selectWhere { table.c2 greater table.c1 }
          .sequence { it[table.c1] }
          .toList()
          .let { list ->
            expect(list).toHaveSize(1)
            expect(list[0]).toBe(1)
          }

        table
          .selectWhere { table.c2 greaterEq table.c1 }
          .orderBy(table.c1)
          .sequence { it[table.c1] }
          .toList()
          .let { list ->
            expect(list).toHaveSize(2)
            expect(list).toBe(listOf(0, 1))
          }

        table
          .selectWhere { table.c1 lessEq table.c3 }
          .sequence { it[table.c1] }
          .toList()
          .let { list ->
            expect(list).toHaveSize(0)
          }

        table
          .selectWhere { table.c1 greaterEq table.c3 }
          .sequence { it[table.c1] }
          .toList()
          .let { list ->
            expect(list).toHaveSize(0)
          }

        table
          .selectWhere { table.c1 greaterEq table.c3 }
          .sequence { it[table.c1] }
          .toList()
          .let { list ->
            expect(list).toHaveSize(0)
          }

        table
          .selectWhere { table.c1 lessEq table.c3 }
          .sequence { it[table.c1] }
          .toList()
          .let { list ->
            expect(list).toHaveSize(0)
          }

        table
          .selectWhere { (table.c1 lessEq table.c3).isNull() }
          .sequence { it[table.c1] }
          .toList()
          .let { list ->
            expect(list).toHaveSize(3)
          }
      }
    }
  }
}

object CondTable : Table() {
  val id = long("_id") { primaryKey() }
  val data = integer("data")
  val name = text("name") { default("") }
}
