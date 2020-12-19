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
import com.ealva.welite.db.expr.max
import com.ealva.welite.db.table.alias
import com.ealva.welite.db.table.all
import com.ealva.welite.db.table.groupBy
import com.ealva.welite.db.table.select
import com.ealva.welite.db.table.selectAll
import com.nhaarman.expect.expect
import kotlinx.coroutines.CoroutineDispatcher

public object CommonCountTests {
  public suspend fun testCountWithDistinctAndColumnsWithSameNameInDifferentTables(
    appCtx: Context,
    testDispatcher: CoroutineDispatcher
  ) {
    withPlaceTestDatabase(
      context = appCtx,
      tables = setOf(Place, Person, Review),
      testDispatcher = testDispatcher
    ) {
      query {
        expect(Place.innerJoin(Person).selectAll().distinct().count()).toBe(3)
      }
    }
  }

  public suspend fun testCountWithDistinctAndColumnsWithSameNameInDifferentTablesWithAlias(
    appCtx: Context,
    testDispatcher: CoroutineDispatcher
  ) {
    withPlaceTestDatabase(
      context = appCtx,
      tables = setOf(Place, Person, Review),
      testDispatcher = testDispatcher
    ) {
      query {
        expect(
          Place
            .innerJoin(Person)
            .select(Person.id.alias("peopleId"), Place.id)
            .all()
            .distinct()
            .count()
        ).toBe(3)
      }
    }
  }

  public suspend fun testCountWithGroupByQuery(
    appCtx: Context,
    testDispatcher: CoroutineDispatcher
  ) {
    withPlaceTestDatabase(
      context = appCtx,
      tables = setOf(Place, Person, Review),
      testDispatcher = testDispatcher
    ) {
      query {
        expect(
          Review
            .select(Review.userId)
            .all()
            .distinct()
            .count()
        ).toBe(
          Review
            .select(Review.value.max())
            .all()
            .groupBy { userId }
            .count()
        )
      }
    }
  }
}
