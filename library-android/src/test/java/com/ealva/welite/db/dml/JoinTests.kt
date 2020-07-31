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
import com.ealva.welite.db.table.Table
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
      tables = listOf(Place, Person, PersonInfo),
      testDispatcher = coroutineRule.testDispatcher
    ) {
      query {
        (Person innerJoin Place).select(Person.name, Place.name)
          .where { (Person.id eq "louis" or (Person.name eq "Rick") and (Person.cityId eq Place.id)) }
          .forEach {
            val userName = it[Person.name]
            val cityName = it[Place.name]
            when (userName) {
              "Louis" -> expect(cityName).toBe("Cleveland")
              "Rick" -> expect(cityName).toBe("South Point")
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
      tables = listOf(Place, Person, PersonInfo),
      testDispatcher = coroutineRule.testDispatcher
    ) {
      query {
        (Person innerJoin Place)
          .select(Person.name, Place.name)
          .where { Place.name eq "Cleveland" or Person.cityId.isNull() }
          .entityFlow { cursor: Cursor ->
            Pair(cursor[Person.name], cursor[Place.name])
          }.singleOrNull()?.let { (user, city) ->
            expect(user).toBe("Louis")
            expect(city).toBe("Cleveland")
          } ?: fail("Expected an entity from the flow")
      }
    }
  }

  @Test
  fun `test join with order by`() = coroutineRule.runBlockingTest {
    withTestDatabase(
      context = appCtx,
      tables = listOf(Place, Person, PersonInfo),
      testDispatcher = coroutineRule.testDispatcher
    ) {
      query {
        (Place innerJoin Person innerJoin PersonInfo)
          .selectAll()
          .orderBy(Person.id)
          .entityFlow { cursor ->
            Triple(cursor[Person.name], cursor[PersonInfo.post], cursor[Place.name])
          }.collectIndexed { index, (person, post, city) ->
            when (index) {
              0 -> {
                expect(person).toBe("Mike")
                expect(post).toBe("Mike's post")
                expect(city).toBe("South Point")
              }
              1 -> {
                expect(person).toBe("Rick")
                expect(post).toBe("Sup Dude")
                expect(city).toBe("South Point")
              }
              else -> fail("Too many entities")
            }
          }
      }
    }
  }

  @Test
  fun `test join with relationship table`() = coroutineRule.runBlockingTest {
    val numbers = object : Table() {
      val id = integer("id") { primaryKey() }
    }

    val names = object : Table() {
      val name = text("name")

      override val primaryKey = PrimaryKey(name)
    }

    val numberNameRel = object : Table() {
      val numberId = references("id_ref", numbers.id)
      val name = references("name_ref", names.name)
    }

    withTestDatabase(
      context = appCtx,
      tables = listOf(numbers, names, numberNameRel),
      testDispatcher = coroutineRule.testDispatcher
    ) {
      transaction {
        numbers.insert { it[id] = 1 }
        numbers.insert { it[id] = 2 }
        names.insert { it[name] = "Francis" }
        names.insert { it[name] = "Bart" }
        numberNameRel.insert {
          it[numberId] = 2
          it[name] = "Francis"
        }
        setSuccessful()
      }
      query {
        (numbers innerJoin numberNameRel innerJoin names)
          .selectAll()
          .entityFlow {
            Pair(it[numbers.id], it[names.name])
          }.singleOrNull()?.let { (id, name) ->
            expect(id).toBe(2)
            expect(name).toBe("Francis")
          } ?: fail("Expected only 1 entity")

      }
    }
  }
}
