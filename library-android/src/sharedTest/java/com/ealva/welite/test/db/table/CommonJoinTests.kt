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
import com.ealva.welite.db.WeLiteException
import com.ealva.welite.db.expr.and
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.expr.isNull
import com.ealva.welite.db.expr.or
import com.ealva.welite.db.expr.stringLiteral
import com.ealva.welite.db.table.Alias
import com.ealva.welite.db.table.Column
import com.ealva.welite.db.table.Cursor
import com.ealva.welite.db.table.JoinType
import com.ealva.welite.db.table.Table
import com.ealva.welite.db.table.alias
import com.ealva.welite.db.table.select
import com.ealva.welite.db.table.selectAll
import com.ealva.welite.db.table.selectWhere
import com.ealva.welite.db.table.where
import com.nhaarman.expect.expect
import com.nhaarman.expect.fail
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList

public object CommonJoinTests {
  public suspend fun testJoinInnerJoin(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withPlaceTestDatabase(
      context = appCtx,
      tables = listOf(Place, Person, Review),
      testDispatcher = testDispatcher
    ) {
      query {
        Person.innerJoin(Place)
          .select(Person.name, Place.name)
          .where {
            (Person.id eq "louis" or (Person.name eq "Rick") and (Person.cityId eq Place.id))
          }
          .sequence { Pair(it[Person.name], it[Place.name]) }
          .toList().also { list -> expect(list.size).toBe(2) }
          .forEach { (person, city) ->
            when (person) {
              "Louis" -> expect(city).toBe("Cleveland")
              "Rick" -> expect(city).toBe("South Point")
              else -> error("Unexpected user/city $person/$city")
            }
          }
      }
    }
  }

  public suspend fun testFKJoin(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withPlaceTestDatabase(
      context = appCtx,
      tables = listOf(Place, Person, Review),
      testDispatcher = testDispatcher
    ) {
      query {
        Person.innerJoin(Place)
          .select(Person.name, Place.name)
          .where { Place.name eq "Cleveland" or Person.cityId.isNull() }
          .flow { cursor: Cursor -> Pair(cursor[Person.name], cursor[Place.name]) }
          .singleOrNull()
          ?.let { (user, city) ->
            expect(user).toBe("Louis")
            expect(city).toBe("Cleveland")
          } ?: fail("Expected an item from the flow")
      }
    }
  }

  public suspend fun testJoinWithOrderBy(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withPlaceTestDatabase(
      context = appCtx,
      tables = listOf(Place, Person, Review),
      testDispatcher = testDispatcher
    ) {
      query {
        (Place innerJoin Person innerJoin Review)
          .selectAll()
          .orderBy(Person.id)
          .flow { cursor ->
            Triple(cursor[Person.name], cursor[Review.post], cursor[Place.name])
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

  public suspend fun testJoinWithRelationshipTable(
    appCtx: Context,
    testDispatcher: CoroutineDispatcher
  ) {
    withPlaceTestDatabase(
      context = appCtx,
      tables = listOf(Numbers, Names, NumberNameRel),
      testDispatcher = testDispatcher
    ) {
      transaction {
        Numbers.insert { it[id] = 1 }
        Numbers.insert { it[id] = 2 }
        Names.insert { it[name] = "Francis" }
        Names.insert { it[name] = "Bart" }
        NumberNameRel.insert {
          it[numberId] = 2
          it[name] = "Francis"
        }
        setSuccessful()
      }
      query {
        (Numbers innerJoin NumberNameRel innerJoin Names)
          .selectAll()
          .flow { Pair(it[Numbers.id], it[Names.name]) }
          .singleOrNull()
          ?.let { (id, name) ->
            expect(id).toBe(2)
            expect(name).toBe("Francis")
          } ?: fail("Expected only 1 item")
      }
    }
  }

  public suspend fun testCrossJoin(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withPlaceTestDatabase(
      context = appCtx,
      tables = listOf(Place, Person, Review),
      testDispatcher = testDispatcher
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

  public suspend fun testJoinMultipleReferencesFKViolation(
    appCtx: Context,
    testDispatcher: CoroutineDispatcher
  ) {
    val fooTable = object : Table("foo") {
      val baz = long("baz") { uniqueIndex() }
    }
    val barTable = object : Table("bar") {
      val foo = reference("foo", fooTable.baz)
      val foo2 = reference("foo2", fooTable.baz)
      val baz = reference("baz", fooTable.baz)
    }

    withPlaceTestDatabase(
      context = appCtx,
      tables = listOf(fooTable, barTable),
      testDispatcher = testDispatcher
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
        }

        fail("insert should have failed with an foreign key violation")
      } catch (e: WeLiteException) {
        throw requireNotNull(e.cause) // rethrow underlying exception
      }
    }
  }

  public suspend fun testJoinWithAlias(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withPlaceTestDatabase(
      context = appCtx,
      tables = listOf(Place, Person, Review),
      testDispatcher = testDispatcher
    ) {
      val person = Person
      query {
        val personAlias: Alias<Person> = person.alias("u2")
        val pair = Person.join(
          personAlias,
          JoinType.LEFT,
          stringLiteral("nathalia"),
          personAlias[Person.id]
        ).selectWhere { Person.id eq "amber" }
          .flow { Pair(it[Person.name], it[personAlias[Person.name]]) }
          .singleOrNull() ?: fail("expected single item")

        expect(pair.first).toBe("Amber")
        expect(pair.second).toBe("Nathalia")
      }
    }
  }
}

public object Numbers : Table() {
  public val id: Column<Long> = long("id") { primaryKey() }
}

public object Names : Table() {
  public val name: Column<String> = text("name") { primaryKey() }
}

public object NumberNameRel : Table() {
  @Suppress("unused")
  public val id: Column<Long> = long("id") { primaryKey() }
  public val numberId: Column<Long> = reference("id_ref", Numbers.id)
  public val name: Column<String> = reference("name_ref", Names.name)
}
