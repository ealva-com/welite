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

package com.ealva.welite.test.db.expr

import android.content.Context
import com.ealva.welite.db.expr.Op
import com.ealva.welite.db.expr.bindLong
import com.ealva.welite.db.expr.greater
import com.ealva.welite.db.expr.greaterEq
import com.ealva.welite.db.expr.inList
import com.ealva.welite.db.expr.isNull
import com.ealva.welite.db.expr.less
import com.ealva.welite.db.expr.lessEq
import com.ealva.welite.db.expr.notInList
import com.ealva.welite.db.table.Column
import com.ealva.welite.db.table.Table
import com.ealva.welite.db.table.all
import com.ealva.welite.db.table.orderByAsc
import com.ealva.welite.db.table.selectAll
import com.ealva.welite.db.table.selectWhere
import com.ealva.welite.db.table.selects
import com.ealva.welite.test.shared.withTestDatabase
import com.nhaarman.expect.expect
import kotlinx.coroutines.CoroutineDispatcher

public object CommonConditionsTests {
  public suspend fun testOpsTRUEAndFALSE(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(appCtx, setOf(CondTable), testDispatcher) {
      transaction {
        expect(CondTable.exists).toBe(true)
        CondTable.insert { it[data] = 10 }
        CondTable.insert { it[data] = 20 }
        CondTable.insert { it[data] = 30 }
      }

      query {
        val list = CondTable.selectAll().sequence { it[id] }.toList()
        expect(CondTable.selectWhere { Op.FALSE }.count()).toBe(0)
        expect(CondTable.selectWhere { Op.TRUE }.count()).toBe(list.size.toLong())
      }
    }
  }

  public suspend fun testSelectingSameColumn(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(appCtx, setOf(CondTable), testDispatcher) {
      transaction {
        expect(CondTable.exists).toBe(true)
        CondTable.insert { it[data] = 10 }
        CondTable.insert { it[data] = 20 }
        CondTable.insert { it[data] = 30 }
      }

      query {
        val list = CondTable
          .selects { listOf(id, name, name, id) }.all()
          .sequence { cursor ->
            expect(cursor.columnCount).toBe(2)
            cursor[id]
          }
          .toList()
        expect(list).toHaveSize(3)
      }
    }
  }

  public suspend fun testCompareWithNullableColumn(
    appCtx: Context,
    testDispatcher: CoroutineDispatcher
  ) {
    val table = object : Table("foo") {
      val c1 = integer("c1")
      val c2 = optInteger("c2")
      val c3 = optInteger("c3") // will always be null
    }

    withTestDatabase(appCtx, setOf(table), testDispatcher) {
      transaction {
        table.insert { it[c1] = 0; it[c2] = 0 }
        table.insert { it[c1] = 1; it[c2] = 2 }
        table.insert { it[c1] = 2; it[c2] = 1 }
      }
      query {
        table
          .selectWhere { c1 less c2 }
          .sequence { it[c1] }
          .toList()
          .let { list ->
            expect(list).toHaveSize(1)
            expect(list[0]).toBe(1)
          }

        table
          .selectWhere { c1 lessEq c2 }
          .orderByAsc { c1 }
          .sequence { it[c1] }
          .toList()
          .let { list ->
            expect(list).toHaveSize(2)
            expect(list).toBe(listOf(0, 1))
          }

        table
          .selectWhere { c1 lessEq c2 }
          .orderByAsc { c1 }
          .limit(1)
          .sequence { it[c1] }
          .toList()
          .let { list ->
            expect(list).toHaveSize(1)
            expect(list).toBe(listOf(0))
          }

        table
          .selectWhere { c1 lessEq c2 }
          .orderByAsc { c1 }
          .limit(0)
          .sequence { it[c1] }
          .toList()
          .let { list ->
            expect(list).toHaveSize(0)
          }

        table
          .selectWhere { c1 lessEq c2 }
          .orderByAsc { c1 }
          .limit(bindLong())
          .sequence({ it[0] = 1 }) { it[c1] }
          .toList()
          .let { list ->
            expect(list).toHaveSize(1)
            expect(list).toBe(listOf(0))
          }

        table
          .selectWhere { c1 greater c2 }
          .sequence { it[c1] }
          .toList()
          .let { list ->
            expect(list).toHaveSize(1)
            expect(list[0]).toBe(2)
          }

        table
          .selectWhere { c1 greaterEq c2 }
          .orderByAsc { c1 }
          .sequence { it[c1] }
          .toList()
          .let { list ->
            expect(list).toHaveSize(2)
            expect(list).toBe(listOf(0, 2))
          }

        table
          .selectWhere { c2 less c1 }
          .sequence { it[c1] }
          .toList()
          .let { list ->
            expect(list).toHaveSize(1)
            expect(list[0]).toBe(2)
          }

        table
          .selectWhere { c2 lessEq c1 }
          .orderByAsc { c1 }
          .sequence { it[c1] }
          .toList()
          .let { list ->
            expect(list).toHaveSize(2)
            expect(list).toBe(listOf(0, 2))
          }
        table
          .selectWhere { c2 greater c1 }
          .sequence { it[c1] }
          .toList()
          .let { list ->
            expect(list).toHaveSize(1)
            expect(list[0]).toBe(1)
          }

        table
          .selectWhere { c2 greaterEq c1 }
          .orderByAsc { c1 }
          .sequence { it[c1] }
          .toList()
          .let { list ->
            expect(list).toHaveSize(2)
            expect(list).toBe(listOf(0, 1))
          }

        table
          .selectWhere { c1 lessEq c3 }
          .sequence { it[c1] }
          .toList()
          .let { list ->
            expect(list).toHaveSize(0)
          }

        table
          .selectWhere { c1 greaterEq c3 }
          .sequence { it[c1] }
          .toList()
          .let { list ->
            expect(list).toHaveSize(0)
          }

        table
          .selectWhere { c1 greaterEq c3 }
          .sequence { it[c1] }
          .toList()
          .let { list ->
            expect(list).toHaveSize(0)
          }

        table
          .selectWhere { c1 lessEq c3 }
          .sequence { it[c1] }
          .toList()
          .let { list ->
            expect(list).toHaveSize(0)
          }

        table
          .selectWhere { (c1 lessEq c3).isNull() }
          .sequence { it[c1] }
          .toList()
          .let { list ->
            expect(list).toHaveSize(3)
          }

        table
          .selectWhere { (c1 lessEq c3).isNull() }
          .limit(-1)
          .sequence { it[c1] }
          .toList()
          .let { list ->
            expect(list).toHaveSize(3)
          }
      }
    }
  }

  public suspend fun testInList(
    appCtx: Context,
    testDispatcher: CoroutineDispatcher
  ) {
    val table = object : Table("foo") {
      val c1 = integer("c1")
      val c2 = integer("c2")
    }

    withTestDatabase(appCtx, setOf(table), testDispatcher) {
      transaction {
        table.insert { it[c1] = 0; it[c2] = 30 }
        table.insert { it[c1] = 1; it[c2] = 40 }
        table.insert { it[c1] = 2; it[c2] = 50 }
      }
      query {
        table
          .selectWhere { c2 inList listOf(40, 50) }
          .orderByAsc { c1 }
          .sequence { it[c1] }
          .toList()
          .let { list ->
            expect(list).toHaveSize(2)
            expect(list[0]).toBe(1)
            expect(list[1]).toBe(2)
          }
      }
    }
  }

  public suspend fun testNotInList(
    appCtx: Context,
    testDispatcher: CoroutineDispatcher
  ) {
    val table = object : Table("foo") {
      val c1 = integer("c1")
      val c2 = integer("c2")
    }

    withTestDatabase(appCtx, setOf(table), testDispatcher) {
      transaction {
        table.insert { it[c1] = 0; it[c2] = 30 }
        table.insert { it[c1] = 1; it[c2] = 40 }
        table.insert { it[c1] = 2; it[c2] = 50 }
      }
      query {
        table
          .selectWhere { c2 notInList listOf(40, 50) }
          .orderByAsc { c1 }
          .sequence { it[c1] }
          .toList()
          .let { list ->
            expect(list).toHaveSize(1)
            expect(list[0]).toBe(0)
          }
      }
    }
  }
}

public object CondTable : Table() {
  public val id: Column<Long> = long("_id") { primaryKey() }
  public val data: Column<Int> = integer("data")
  public val name: Column<String> = text("name") { default("") }
}
