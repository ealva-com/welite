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
import com.ealva.welite.db.expr.and
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.expr.like
import com.ealva.welite.db.expr.or
import com.ealva.welite.test.common.CoroutineRule
import com.ealva.welite.test.common.runBlockingTest
import com.nhaarman.expect.expect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
class ExistsTests {
  @get:Rule var coroutineRule = CoroutineRule()

  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `test select exists`() = coroutineRule.runBlockingTest {
    withPlaceTestDatabase(
      context = appCtx,
      tables = listOf(Place, Person, Review),
      testDispatcher = coroutineRule.testDispatcher
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

  @Test
  fun `test select exists and or`() = coroutineRule.runBlockingTest {
    withPlaceTestDatabase(
      context = appCtx,
      tables = listOf(Place, Person, Review),
      testDispatcher = coroutineRule.testDispatcher
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

  @Test
  fun `test select exists or exists`() = coroutineRule.runBlockingTest {
    withPlaceTestDatabase(
      context = appCtx,
      tables = listOf(Place, Person, Review),
      testDispatcher = coroutineRule.testDispatcher
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
