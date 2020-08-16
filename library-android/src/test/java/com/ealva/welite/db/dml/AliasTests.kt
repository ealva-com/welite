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

package com.ealva.welite.db.dml

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.expr.max
import com.ealva.welite.db.table.Join
import com.ealva.welite.db.table.JoinType
import com.ealva.welite.db.table.QueryBuilderAlias
import com.ealva.welite.db.table.SqlTypeExpressionAlias
import com.ealva.welite.db.table.alias
import com.ealva.welite.db.table.joinQuery
import com.ealva.welite.db.table.lastQueryBuilderAlias
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
class AliasTests {
  @get:Rule var coroutineRule = CoroutineRule()

  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `test joinQuery with expression alias`() = coroutineRule.runBlockingTest {
    withPlaceTestDatabase(
      context = appCtx,
      tables = listOf(Place, Person, Review),
      testDispatcher = coroutineRule.testDispatcher
    ) {
      query {
        val expAlias = Person.name.max().alias("m")

        val query: Join = Join(Person).joinQuery({ it[expAlias].eq(Person.name) }) {
          Person.select(Person.cityId, expAlias).all().groupBy(Person.cityId)
        }
        val innerExp = checkNotNull(query.lastQueryBuilderAlias)[expAlias]

        val actual = query.lastQueryBuilderAlias?.alias
        expect(actual).toBe("q0")
        expect(query.selectAll().count()).toBe(3L)
        query.select(Person.columns + innerExp).all().forEach {
          expect(it[innerExp]).toNotBeNull()
        }
      }
    }
  }

  @ExperimentalUnsignedTypes
  @Test
  fun `test joinQuery subquery alias expr alias query`() = coroutineRule.runBlockingTest {
    withPlaceTestDatabase(
      context = appCtx,
      tables = listOf(Place, Person, Review),
      testDispatcher = coroutineRule.testDispatcher
    ) {
      query {
        val expAlias: SqlTypeExpressionAlias<String> = Person.name.max().alias("pxa")
        val personAlias: QueryBuilderAlias = Person.select(Person.cityId, expAlias)
          .all()
          .groupBy(Person.cityId)
          .alias("pqa")

        expect(
          Person.join(personAlias, JoinType.INNER, Person.name, personAlias[expAlias])
            .selectAll()
            .sequence { personAlias[expAlias] }
            .count()
        ).toBe(3)
      }
    }
  }

  @Test
  fun `test query alias table`() = coroutineRule.runBlockingTest {
    withPlaceTestDatabase(
      context = appCtx,
      tables = listOf(Place, Person, Review),
      testDispatcher = coroutineRule.testDispatcher
    ) {
      query {
        val personAlias = Person.alias("person_alias")
        expect(
          personAlias.select(personAlias[Person.name], personAlias[Person.cityId])
            .where { personAlias[Person.name] eq "Rick" }
            .groupBy(personAlias[Person.cityId])
            .sequence { it[personAlias[Person.name]] }
            .count()
        ).toBe(1)
      }
    }
  }
}
