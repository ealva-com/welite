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
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.expr.max
import com.ealva.welite.db.table.Cursor
import com.ealva.welite.db.table.Join
import com.ealva.welite.db.table.JoinType
import com.ealva.welite.db.table.QueryBuilderAlias
import com.ealva.welite.db.table.SqlTypeExpressionAlias
import com.ealva.welite.db.table.alias
import com.ealva.welite.db.table.all
import com.ealva.welite.db.table.groupBy
import com.ealva.welite.db.table.joinQuery
import com.ealva.welite.db.table.lastQueryBuilderAlias
import com.ealva.welite.db.table.select
import com.ealva.welite.db.table.selectAll
import com.ealva.welite.db.table.selects
import com.ealva.welite.db.table.where
import com.nhaarman.expect.expect
import kotlinx.coroutines.CoroutineDispatcher

public object CommonAliasTests {
  public suspend fun testJoinQueryWithExpressionAlias(
    appCtx: Context,
    testDispatcher: CoroutineDispatcher
  ) {
    withPlaceTestDatabase(
      context = appCtx,
      tables = setOf(Place, Person, Review),
      testDispatcher = testDispatcher
    ) {
      query {
        val expAlias = Person.name.max().alias("m")

        val query: Join = Join(Person).joinQuery({ it[expAlias] eq Person.name }) {
          Person.selects { listOf(cityId, expAlias) }.all().groupBy { cityId }
        }
        val innerExp = checkNotNull(query.lastQueryBuilderAlias)[expAlias]

        val actual = query.lastQueryBuilderAlias?.alias
        expect(actual).toBe("q0")
        expect(query.selectAll().count()).toBe(3L)
        query.selects { Person.columns + innerExp }.all().sequence { cursor: Cursor ->
          expect(cursor[innerExp]).toNotBeNull()
        }
      }
    }
  }

  public suspend fun testJoinQuerySubqueryAliasExprAliasQuery(
    appCtx: Context,
    testDispatcher: CoroutineDispatcher
  ) {
    withPlaceTestDatabase(
      context = appCtx,
      tables = setOf(Place, Person, Review),
      testDispatcher = testDispatcher
    ) {
      query {
        val expAlias: SqlTypeExpressionAlias<String> = Person.name.max().alias("pxa")
        val personAlias: QueryBuilderAlias<Person> = Person.selects { listOf(cityId, expAlias) }
          .all()
          .groupBy { cityId }
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

  public suspend fun testQueryTableAlias(
    appCtx: Context,
    testDispatcher: CoroutineDispatcher
  ) {
    withPlaceTestDatabase(
      context = appCtx,
      tables = setOf(Place, Person, Review),
      testDispatcher = testDispatcher
    ) {
      query {
        val personAlias = Person.alias("person_alias")
        expect(
          personAlias.select(personAlias[Person.name], personAlias[Person.cityId])
            .where { personAlias[Person.name] eq "Rick" }
            .groupBy { personAlias[Person.cityId] }
            .sequence { it[personAlias[Person.name]] }
            .count()
        ).toBe(1)
      }
    }
  }
}
