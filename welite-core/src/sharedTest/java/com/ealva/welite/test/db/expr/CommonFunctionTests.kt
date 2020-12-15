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
import com.ealva.welite.db.expr.SortOrder
import com.ealva.welite.db.expr.avg
import com.ealva.welite.db.expr.lowerCase
import com.ealva.welite.db.expr.max
import com.ealva.welite.db.expr.min
import com.ealva.welite.db.expr.rem
import com.ealva.welite.db.expr.substring
import com.ealva.welite.db.expr.sum
import com.ealva.welite.db.expr.upperCase
import com.ealva.welite.db.table.Column
import com.ealva.welite.db.table.Table
import com.ealva.welite.test.shared.withTestDatabase
import com.nhaarman.expect.expect
import kotlinx.coroutines.CoroutineDispatcher

public object CommonFunctionTests {
  public suspend fun testSumColumn(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(appCtx, setOf(DataTable), testDispatcher) {
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

  public suspend fun testAvgMinMaxColumns(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(appCtx, setOf(DataTable), testDispatcher) {
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

  public suspend fun testSubstring(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(appCtx, setOf(DataTable), testDispatcher) {
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

  public suspend fun testLowercaseAndUppercase(
    appCtx: Context,
    testDispatcher: CoroutineDispatcher
  ) {
    withTestDatabase(appCtx, setOf(DataTable), testDispatcher) {
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

public object DataTable : Table() {
  public val id: Column<Long> = long("_id") { primaryKey() }
  public val data: Column<Int> = integer("data")
  public val name: Column<String> = text("name") { default("") }
}
