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
import com.ealva.welite.db.Database
import com.ealva.welite.db.table.Column
import com.ealva.welite.db.table.Table
import kotlinx.coroutines.CoroutineDispatcher

object Places : Table() {
  val id: Column<Long> = long("cityId") { autoIncrement() }
  val name: Column<String> = text("name")
}

object People : Table() {
  val id: Column<String> = text("id")
  val name: Column<String> = text("name")
  val cityId: Column<Long?> = optReference("cityId", Places.id)
  override val primaryKey = PrimaryKey(id)
}

object PeopleInfo : Table() {
  val user_id: Column<String> = reference("userId", People.id)
  val comment: Column<String> = text("comment")
  val value: Column<Int> = integer("value")
}

suspend fun withTestDatabase(
  context: Context,
  tables: List<Table>,
  testDispatcher: CoroutineDispatcher,
  enableForeignKeyConstraints: Boolean = true,
  block: suspend Database.() -> Unit
) {
  val db = TestDatabase(context, tables, testDispatcher, enableForeignKeyConstraints)
  db.transaction {
    Places.insert {
      it[name] = "St. Petersburg"
    }

    val munichId = Places.insert {
      it[name] = "Munich"
    }

    Places.insert {
      it[name] = "Prague"
    }

    People.insert {
      it[id] = "andrey"
      it[name] = "Andrey"
      it[cityId] = 1
    }

    People.insert {
      it[id] = "sergey"
      it[name] = "Sergey"
      it[cityId] = munichId
    }

    People.insert {
      it[id] = "eugene"
      it[name] = "Eugene"
      it[cityId] = munichId
    }

    People.insert {
      it[id] = "alex"
      it[name] = "Alex"
      it[cityId] = null
    }

    People.insert {
      it[id] = "smth"
      it[name] = "Something"
      it[cityId] = null
    }

    PeopleInfo.insert {
      it[user_id] = "smth"
      it[comment] = "Something is here"
      it[value] = 10
    }

    PeopleInfo.insert {
      it[user_id] = "smth"
      it[comment] = "Comment #2"
      it[value] = 20
    }

    PeopleInfo.insert {
      it[user_id] = "eugene"
      it[comment] = "Comment for Eugene"
      it[value] = 20
    }

    PeopleInfo.insert {
      it[user_id] = "sergey"
      it[comment] = "Comment for Sergey"
      it[value] = 30
    }

    setSuccessful()
  }
  try {
    db.block()
  } finally {
    db.close()
  }
}

/**
 * Make in-memory database
 */
@Suppress("TestFunctionName")
fun TestDatabase(
  context: Context,
  tables: List<Table>,
  testDispatcher: CoroutineDispatcher,
  enableForeignKeyConstraints: Boolean
): Database {
  return Database(
    context = context,
    version = 1,
    tables = tables,
    migrations = emptyList(),
    requireMigration = enableForeignKeyConstraints,
    dispatcher = testDispatcher
  ) {
    preOpen { it.allowWorkOnUiThread = true }
    onConfigure { it.enableForeignKeyConstraints(enableForeignKeyConstraints) }
  }
}
