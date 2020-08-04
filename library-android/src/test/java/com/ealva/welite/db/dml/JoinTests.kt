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
import android.database.sqlite.SQLiteException
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.ealva.welite.db.WeLiteException
import com.ealva.welite.db.expr.and
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.expr.isNull
import com.ealva.welite.db.expr.or
import com.ealva.welite.db.expr.stringLiteral
import com.ealva.welite.db.table.Alias
import com.ealva.welite.db.table.Cursor
import com.ealva.welite.db.table.Join
import com.ealva.welite.db.table.JoinType
import com.ealva.welite.db.table.Table
import com.ealva.welite.db.table.alias
import com.ealva.welite.sharedtest.CoroutineRule
import com.ealva.welite.sharedtest.runBlockingTest
import com.nhaarman.expect.expect
import com.nhaarman.expect.fail
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
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
          .where {
            (Person.id eq "louis" or (Person.name eq "Rick") and (Person.cityId eq Place.id))
          }
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
          .flow { cursor: Cursor ->
            Pair(cursor[Person.name], cursor[Place.name])
          }.singleOrNull()?.let { (user, city) ->
          expect(user).toBe("Louis")
          expect(city).toBe("Cleveland")
        } ?: fail("Expected an item from the flow")
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
          .flow { cursor ->
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
    val numbers = object : Table() { val id = long("id") { primaryKey() } }
    val names = object : Table() { val name = text("name") { primaryKey() } }
    val numberNameRel = object : Table() {
      @Suppress("unused")
      val id = long("id") { primaryKey() }
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
          .flow {
            Pair(it[numbers.id], it[names.name])
          }.singleOrNull()?.let { (id, name) ->
          expect(id).toBe(2)
          expect(name).toBe("Francis")
        } ?: fail("Expected only 1 item")
      }
    }
  }

  @Test
  fun `test cross join`() = coroutineRule.runBlockingTest {
    withTestDatabase(
      context = appCtx,
      tables = listOf(Place, Person, PersonInfo),
      testDispatcher = coroutineRule.testDispatcher
    ) {
      query {
        val allToSouthPoint: List<Pair<String, String>> = (Person crossJoin Place)
          .select(Person.name, Person.cityId, Place.name)
          .where { Place.name eq "South Point" }
          .flow {
            it[Person.name] to it[Place.name]
          }
          .toList()

        val allUsers = setOf(
          "Amber",
          "Louis",
          "Mike",
          "Nathalia",
          "Rick"
        )
        expect(allToSouthPoint.all { it.second == "South Point" }).toBe(true)
        expect(allToSouthPoint.map { it.first }.toSet()).toBe(allUsers)
      }
    }
  }

  @Test(expected = SQLiteException::class)
  fun `test join multiple references`() = coroutineRule.runBlockingTest {
    val fooTable = object : Table("foo") {
      val baz = long("baz") { uniqueIndex() }
    }
    val barTable = object : Table("bar") {
      val foo = references("foo", fooTable.baz)
      val foo2 = references("foo2", fooTable.baz)
      val baz = references("baz", fooTable.baz)
    }

    withTestDatabase(
      context = appCtx,
      tables = listOf(fooTable, barTable),
      testDispatcher = coroutineRule.testDispatcher
    ) {
      try {
        transaction {
          val fooId = fooTable.insert {
            it[baz] = 5
          }

          barTable.insert {
            it[foo] = fooId
            it[foo2] = fooId
            it[baz] = 5 // fk violation
          }

          setSuccessful()
        }

        fail("insert should have failed with an foreign key violation")
      } catch (e: WeLiteException) {
        throw requireNotNull(e.cause) // rethrow underlying exception
      }
    }
  }

  @ExperimentalUnsignedTypes
  @Test
  fun `test join with alias`() = coroutineRule.runBlockingTest {
    withTestDatabase(
      context = appCtx,
      tables = listOf(Place, Person, PersonInfo),
      testDispatcher = coroutineRule.testDispatcher
    ) {
      val person = Person
      query {
        val personAlias: Alias<Person> = person.alias("u2")
        val pair = Join(person)
          .join(personAlias, JoinType.LEFT, personAlias[person.id], stringLiteral("nathalia"))
          .selectWhere { person.id eq "amber" }
          .flow { Pair(it[person.name], it[personAlias[person.name]]) }
          .singleOrNull() ?: fail("expected single item")

        expect(pair.first).toBe("Amber")
        expect(pair.second).toBe("Nathalia")
      }
    }
  }
}