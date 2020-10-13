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
import com.ealva.welite.db.expr.and
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.expr.like
import com.ealva.welite.db.expr.or
import com.ealva.welite.db.table.exists
import com.ealva.welite.db.table.selectWhere
import com.nhaarman.expect.expect
import kotlinx.coroutines.CoroutineDispatcher

public object CommonExistsTests {
  public suspend fun testSelectExists(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withPlaceTestDatabase(
      context = appCtx,
      tables = listOf(Place, Person, Review),
      testDispatcher = testDispatcher
    ) {
      query {
        val result = Person
          .selectWhere {
            exists(Review.selectWhere { Review.userId eq Person.id and (Review.post like "%McD%") })
          }
          .sequence { it[Person.name] }
          .toList()

        expect(result).toHaveSize(1)
        expect(result[0]).toBe("Nathalia")
      }
    }
  }

  public suspend fun testSelectExistsAndOr(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withPlaceTestDatabase(
      context = appCtx,
      tables = listOf(Place, Person, Review),
      testDispatcher = testDispatcher
    ) {
      query {
        val result = Person
          .selectWhere {
            exists(
              Review.selectWhere {
                Review.userId eq Person.id and
                  ((Review.post like "%McD%") or (Review.post like "%ost"))
              }
            )
          }
          .sequence { it[Person.name] }
          .toList()

        expect(result).toHaveSize(2)
        expect(result).toBe(listOf("Mike", "Nathalia"))
      }
    }
  }

  public suspend fun testSelectExistsOrExists(
    appCtx: Context,
    testDispatcher: CoroutineDispatcher
  ) {
    withPlaceTestDatabase(
      context = appCtx,
      tables = listOf(Place, Person, Review),
      testDispatcher = testDispatcher
    ) {
      query {
        val result = Person
          .selectWhere {
            exists(
              Review.selectWhere { Review.userId eq Person.id and (Review.post like "%McD%") }
            ) or
              exists(
                Review.selectWhere { Review.userId eq Person.id and (Review.post like "%ost") }
              )
          }
          .sequence { it[Person.name] }
          .toList()

        expect(result).toHaveSize(2)
        expect(result).toBe(listOf("Mike", "Nathalia"))
      }
    }
  }
}
