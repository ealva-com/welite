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
import com.ealva.welite.db.expr.and
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.expr.isNull
import com.ealva.welite.db.expr.or
import com.ealva.welite.db.table.Cursor
import com.ealva.welite.sharedtest.CoroutineRule
import com.ealva.welite.sharedtest.runBlockingTest
import com.nhaarman.expect.expect
import com.nhaarman.expect.fail
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.singleOrNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
class JoinTests {
  @get:Rule var coroutineRule = CoroutineRule()

  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `test join inner join`() = coroutineRule.runBlockingTest {
    withTestDatabase(
      context = appCtx,
      tables = listOf(Places, People, PeopleInfo),
      testDispatcher = coroutineRule.testDispatcher
    ) {
      query {
        (People innerJoin Places).select(People.name, Places.name)
          .where { (People.id eq "andrey" or (People.name eq "Sergey") and (People.cityId eq Places.id)) }
          .forEach {
            val userName = it[People.name]
            val cityName = it[Places.name]
            when (userName) {
              "Andrey" -> expect(cityName).toBe("St. Petersburg")
              "Sergey" -> expect(cityName).toBe("Munich")
              else -> error("Unexpected user $userName")
            }
          }
      }
    }
  }

  @Test
  fun `test fk join`() = coroutineRule.runBlockingTest {
    withTestDatabase(
      context = appCtx,
      tables = listOf(Places, People, PeopleInfo),
      testDispatcher = coroutineRule.testDispatcher
    ) {
      query {
        (People innerJoin Places)
          .select(People.name, Places.name)
          .where { Places.name eq "St. Petersburg" or People.cityId.isNull() }
          .entityFlow { cursor: Cursor ->
            Pair(cursor[People.name], cursor[Places.name])
          }.singleOrNull()?.let { (user, city) ->
            expect(user).toBe("Andrey")
            expect(city).toBe("St. Petersburg")
          } ?: fail("Expected an entity from the flow")
      }
    }
  }

  @Test
  fun `test join with order by`() = coroutineRule.runBlockingTest {
    withTestDatabase(
      context = appCtx,
      tables = listOf(Places, People, PeopleInfo),
      testDispatcher = coroutineRule.testDispatcher
    ) {
      query {
        (Places innerJoin People innerJoin PeopleInfo)
          .selectAll()
          .orderBy(People.id)
          .entityFlow { cursor ->
            println(cursor[People.name])
            Triple(cursor[People.name], cursor[PeopleInfo.comment], cursor[Places.name])
          }.collectIndexed { index, (person, comment, city) ->
            println("$person $comment $city")
            when (index) {
              0 -> {
                expect(person).toBe("Eugene")
                expect(comment).toBe("Comment for Eugene")
                expect(city).toBe("Munich")
              }
              1 -> {
                expect(person).toBe("Sergey")
                expect(comment).toBe("Comment for Sergey")
                expect(city).toBe("Munich")
              }
              else -> fail("Too many entities")
            }
          }
      }
    }
  }

  /*
              val r = (cities innerJoin users innerJoin userData).selectAll().orderBy(users.id).toList()
            assertEquals(2, r.size)
            assertEquals("Eugene", r[0][users.name])
            assertEquals("Comment for Eugene", r[0][userData.comment])
            assertEquals("Munich", r[0][cities.name])
            assertEquals("Sergey", r[1][users.name])
            assertEquals("Comment for Sergey", r[1][userData.comment])
            assertEquals("Munich", r[1][cities.name])

   */
}
