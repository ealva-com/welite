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

package com.ealva.welite.test.db.table

import android.content.Context
import com.ealva.welite.db.expr.GroupConcat
import com.ealva.welite.db.expr.count
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.expr.groupConcat
import com.ealva.welite.db.expr.max
import com.ealva.welite.db.table.alias
import com.ealva.welite.db.table.all
import com.ealva.welite.db.table.select
import com.ealva.welite.test.shared.expect
import com.nhaarman.expect.expect
import kotlinx.coroutines.CoroutineDispatcher

object CommonGroupByTests {
  suspend fun testGroupByPlaceNameWithCountAndCountAlias(
    appCtx: Context,
    testDispatcher: CoroutineDispatcher
  ) {
    withPlaceTestDatabase(
      context = appCtx,
      tables = listOf(Place, Person, Review),
      testDispatcher = testDispatcher
    ) {
      query {
        val cAlias = Person.id.count().alias("c")
        val list = (Place innerJoin Person)
          .select(Place.name, Person.id.count(), cAlias)
          .all()
          .groupBy(Place.name)
          .sequence { Triple(it[Place.name], it[Person.id.count()], it[cAlias]) }
          .toList()

        expect(list).toHaveSize(2)
        expect(list).toBe(
          listOf(
            Triple("Cleveland", 1, 1),
            Triple("South Point", 2, 2)
          )
        )
      }
    }
  }

  suspend fun testGroupByHaving(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withPlaceTestDatabase(
      context = appCtx,
      tables = listOf(Place, Person, Review),
      testDispatcher = testDispatcher
    ) {
      query {
        val list = (Place innerJoin Person)
          .select(Place.name, Person.id.count())
          .all()
          .groupBy(Place.name)
          .having { Person.id.count() eq 1 }
          .sequence { Pair(it[Place.name], it[Person.id.count()]) }
          .toList()

        expect(list).toHaveSize(1)
        expect(list).toBe(listOf(Pair("Cleveland", 1)))
      }
    }
  }

  suspend fun testGroupByHavingOrderBy(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withPlaceTestDatabase(
      context = appCtx,
      tables = listOf(Place, Person, Review),
      testDispatcher = testDispatcher
    ) {
      query {
        val maxExp = Place.id.max()
        val list = (Place innerJoin Person)
          .select(Place.name, Person.id.count(), maxExp)
          .all()
          .groupBy(Place.name)
          .having { Person.id.count() eq maxExp }
          .orderBy(Place.name)
          .sequence { Triple(it[Place.name], it[Person.id.count()], it[maxExp]) }
          .toList()

        expect(list).toHaveSize(2)
        expect(list).toBe(
          listOf(
            Triple("Cleveland", 1, 1),
            Triple("South Point", 2, 2)
          )
        )
      }
    }
  }

  suspend fun testGroupConcat(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withPlaceTestDatabase(
      context = appCtx,
      tables = listOf(Place, Person, Review),
      testDispatcher = testDispatcher
    ) {
      query {
        fun <T : String?> GroupConcat<T>.check(assertBlock: (Map<String, String?>) -> Unit) {
          val map = mutableMapOf<String, String?>()
          (Place leftJoin Person)
            .select(Place.name, this)
            .all()
            .groupBy(Place.id, Place.name)
            .forEach { map[it[Place.name]] = it.getOptional(this) }
          assertBlock(map)
        }

        Person.name.groupConcat().check { map ->
          expect(map).toHaveSize(3)
        }

        Person.name.groupConcat(separator = " | ").check { map ->
          expect(map).toHaveSize(3)
          expect(map).toContain(Pair("Cleveland", "Louis"))
          expect(map).toContain(Pair("South Point", "Mike | Rick"))
          expect(map).toContain(Pair("Cincinnati", null))
        }
      }
    }
  }
}
