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

package com.ealva.welite.db.table

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.ealva.welite.db.Person
import com.ealva.welite.db.Place
import com.ealva.welite.db.Review
import com.ealva.welite.db.expr.Expression
import com.ealva.welite.db.expr.SortOrder
import com.ealva.welite.db.expr.count
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.expr.substring
import com.ealva.welite.db.withPlaceTestDatabase
import com.ealva.welite.test.common.CoroutineRule
import com.ealva.welite.test.common.runBlockingTest
import com.nhaarman.expect.expect
import com.nhaarman.expect.fail
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
class OrderByTests {
  @get:Rule var coroutineRule = CoroutineRule()
  @get:Rule var thrown: ExpectedException = ExpectedException.none()

  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `test order by Person id`() = coroutineRule.runBlockingTest {
    withPlaceTestDatabase(
      context = appCtx,
      tables = listOf(Place, Person, Review),
      testDispatcher = coroutineRule.testDispatcher
    ) {
      query {
        Person
          .selectAll()
          .orderBy(Person.id)
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

  @Test
  fun `test order by Place id desc then Person id asc`() = coroutineRule.runBlockingTest {
    withPlaceTestDatabase(
      context = appCtx,
      tables = listOf(Place, Person, Review),
      testDispatcher = coroutineRule.testDispatcher
    ) {
      query {
        val withoutCities = listOf("amber", "nathalia")
        val others = listOf("mike", "rick", "louis")
        val expectedList = others + withoutCities
        Person
          .selectAll()
          .orderBy(Person.cityId, SortOrder.DESC)
          .orderBy(Person.id)
          .sequence { it[Person.id] }
          .toList()
          .let { list ->
            expect(list).toHaveSize(5)
            expect(list).toBe(expectedList)
          }
      }
    }
  }

  @Test
  fun `test order by Place id desc then Person id asc vararg`() = coroutineRule.runBlockingTest {
    withPlaceTestDatabase(
      context = appCtx,
      tables = listOf(Place, Person, Review),
      testDispatcher = coroutineRule.testDispatcher
    ) {
      query {
        val withoutCities = listOf("amber", "nathalia")
        val others = listOf("mike", "rick", "louis")
        val expectedList = others + withoutCities
        Person
          .selectAll()
          .orderBy(Person.cityId to SortOrder.DESC, Person.id to SortOrder.ASC)
          .sequence { it[Person.id] }
          .toList()
          .let { list ->
            expect(list).toHaveSize(5)
            expect(list).toBe(expectedList)
          }
      }
    }
  }

  @Test
  fun `test order by on join Place Person count group by`() = coroutineRule.runBlockingTest {
    withPlaceTestDatabase(
      context = appCtx,
      tables = listOf(Place, Person, Review),
      testDispatcher = coroutineRule.testDispatcher
    ) {
      query {
        Place
          .innerJoin(Person)
          .select(Place.name, Person.id.count())
          .all()
          .groupBy(Place.name)
          .orderBy(Place.name)
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

  @Test
  fun `test order by substring expression`() = coroutineRule.runBlockingTest {
    withPlaceTestDatabase(
      context = appCtx,
      tables = listOf(Place, Person, Review),
      testDispatcher = coroutineRule.testDispatcher
    ) {
      query {
        val orderByExpr = Person.id.substring(2, 1) // 2nd letter in Person.id
        Person
          .selectAll()
          .orderBy(orderByExpr to SortOrder.ASC) // sort by 2nd letter in Person.id
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

  @Test
  fun `test order by select expression`() = coroutineRule.runBlockingTest {
    withPlaceTestDatabase(
      context = appCtx,
      tables = listOf(Place, Person, Review),
      testDispatcher = coroutineRule.testDispatcher
    ) {
      query {
        val orderByExpr: Expression<Int> = Person
          .select(Person.id.count())
          .where { Place.id eq Person.cityId }
          .asExpression()

        Place
          .selectAll()
          .orderBy(orderByExpr to SortOrder.DESC)
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
