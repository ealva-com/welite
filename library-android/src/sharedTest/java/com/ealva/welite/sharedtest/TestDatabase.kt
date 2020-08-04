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

package com.ealva.welite.sharedtest

import android.content.Context
import com.ealva.welite.db.Database
import com.ealva.welite.db.table.Table
import kotlinx.coroutines.CoroutineDispatcher

suspend fun withTestDatabase(
  context: Context,
  tables: List<Table>,
  testDispatcher: CoroutineDispatcher,
  enableForeignKeyConstraints: Boolean = true,
  block: suspend Database.() -> Unit
) {
  val db = TestDatabase(context, tables, testDispatcher, enableForeignKeyConstraints)
  try {
    db.block()
  } finally {
    db.close()
  }
}

@Suppress("TestFunctionName", "FunctionNaming")
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
