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

package com.ealva.welite.test.db.table

import android.content.Context
import com.ealva.welite.db.expr.Expression
import com.ealva.welite.db.expr.SortOrder
import com.ealva.welite.db.expr.count
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.expr.substring
import com.ealva.welite.db.table.all
import com.ealva.welite.db.table.asExpression
import com.ealva.welite.db.table.groupBy
import com.ealva.welite.db.table.orderBy
import com.ealva.welite.db.table.orderByAsc
import com.ealva.welite.db.table.ordersBy
import com.ealva.welite.db.table.select
import com.ealva.welite.db.table.selectAll
import com.ealva.welite.db.table.where
import com.nhaarman.expect.expect
import com.nhaarman.expect.fail
import kotlinx.coroutines.CoroutineDispatcher

public object CommonOrderByTests {
  public suspend fun testOrderByPersonId(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withPlaceTestDatabase(
      context = appCtx,
      tables = setOf(Place, Person, Review),
      testDispatcher = testDispatcher
    ) {
      query {
        Person
          .selectAll()
          .orderByAsc { id }
          .sequence { it[Person.id] }
          .toList().also { list -> expect(list).toHaveSize(5) }
          .forEachIndexed { index, id ->
            when (index) {
              0 -> expect(id).toBe("amber")
              1 -> expect(id).toBe("louis")
              2 -> expect(id).toBe("mike")
              3 -> expect(id).toBe("nathalia")
              4 -> expect(id).toBe("rick")
              else -> fail("Too many results $index")
            }
          }
      }
    }
  }

  public suspend fun testOrderByPlaceIdDescThenPersonIdAsc(
    appCtx: Context,
    testDispatcher: CoroutineDispatcher
  ) {
    withPlaceTestDatabase(
      context = appCtx,
      tables = setOf(Place, Person, Review),
      testDispatcher = testDispatcher
    ) {
      query {
        val withoutCities = listOf("amber", "nathalia")
        val others = listOf("mike", "rick", "louis")
        val expectedList = others + withoutCities
        Person
          .selectAll()
          .ordersBy { listOf(cityId to SortOrder.DESC, id to SortOrder.ASC) }
          .sequence { it[Person.id] }
          .toList()
          .let { list ->
            expect(list).toHaveSize(5)
            expect(list).toBe(expectedList)
          }
      }
    }
  }

  public suspend fun testOrderByPlaceIdDescThenPersonIdAscVararg(
    appCtx: Context,
    testDispatcher: CoroutineDispatcher
  ) {
    withPlaceTestDatabase(
      context = appCtx,
      tables = setOf(Place, Person, Review),
      testDispatcher = testDispatcher
    ) {
      query {
        val withoutCities = listOf("amber", "nathalia")
        val others = listOf("mike", "rick", "louis")
        val expectedList = others + withoutCities
        Person
          .selectAll()
          .ordersBy { listOf(cityId to SortOrder.DESC, id to SortOrder.ASC) }
          .sequence { it[Person.id] }
          .toList()
          .let { list ->
            expect(list).toHaveSize(5)
            expect(list).toBe(expectedList)
          }
      }
    }
  }

  public suspend fun testOrderByOnJoinPlacePersonCountGroupBy(
    appCtx: Context,
    testDispatcher: CoroutineDispatcher
  ) {
    withPlaceTestDatabase(
      context = appCtx,
      tables = setOf(Place, Person, Review),
      testDispatcher = testDispatcher
    ) {
      query {
        Place
          .innerJoin(Person)
          .select(Place.name, Person.id.count())
          .all()
          .orderByAsc { Place.name }
          .groupBy { Place.name }
          .sequence { Pair(it[Place.name], it[Person.id.count()]) }
          .toList()
          .let { list ->
            expect(list).toHaveSize(2)
            expect(list[0]).toBe(Pair("Cleveland", 1))
            expect(list[1]).toBe(Pair("South Point", 2))
          }
      }
    }
  }

  public suspend fun testOrderBySubstringExpression(
    appCtx: Context,
    testDispatcher: CoroutineDispatcher
  ) {
    withPlaceTestDatabase(
      context = appCtx,
      tables = setOf(Place, Person, Review),
      testDispatcher = testDispatcher
    ) {
      query {
        val orderByExpr = Person.id.substring(2, 1) // 2nd letter in Person.id
        Person
          .selectAll()
          .orderByAsc { orderByExpr } // sort by 2nd letter in Person.id
          .sequence { it[Person.id] }
          .toList()
          .let { list ->
            expect(list).toHaveSize(5)
            expect(list[0]).toBe("nathalia")
            expect(list[1]).toBe("rick")
            expect(list[2]).toBe("mike")
            expect(list[3]).toBe("amber")
            expect(list[4]).toBe("louis")
          }
      }
    }
  }

  public suspend fun testOrderBySelectExpression(
    appCtx: Context,
    testDispatcher: CoroutineDispatcher
  ) {
    withPlaceTestDatabase(
      context = appCtx,
      tables = setOf(Place, Person, Review),
      testDispatcher = testDispatcher
    ) {
      query {
        val orderByExpr: Expression<Int> = Person
          .select { id.count() }
          .where { Place.id eq cityId }
          .asExpression()

        Place
          .selectAll()
          .orderBy { orderByExpr to SortOrder.DESC }
          .sequence { it[Place.name] }
          .toList()
          .let { list ->
            expect(list).toHaveSize(3)
            // South Point associated with 2
            // Cleveland associated with 1
            // Cincinnati not associated with any Person
            expect(list).toBe(listOf("South Point", "Cleveland", "Cincinnati"))
          }
      }
    }
  }
}
