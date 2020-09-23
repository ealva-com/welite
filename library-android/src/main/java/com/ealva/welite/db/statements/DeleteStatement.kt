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

package com.ealva.welite.db.statements

import android.database.sqlite.SQLiteDatabase
import com.ealva.welite.db.expr.Op
import com.ealva.welite.db.table.ArgBindings
import com.ealva.welite.db.table.Table
import com.ealva.welite.db.type.PersistentType
import com.ealva.welite.db.type.StatementSeed
import com.ealva.welite.db.type.buildSql

interface DeleteStatement : Statement {

  companion object {
    /**
     * Make a DeleteStatement from a [table] and [where] clause
     */
    operator fun invoke(table: Table, where: Op<Boolean>?): DeleteStatement =
      DeleteStatementImpl(statementSeed(table, where))

    /**
     * Make the StatementSeed from a [table] and [where] clause for a DeleteStatement
     */
    fun statementSeed(table: Table, where: Op<Boolean>?): StatementSeed = buildSql {
      append("DELETE FROM ")
      append(table.identity.value)
      if (where != null) {
        append(" WHERE ")
        append(where)
      }
    }
  }
}

/**
 * Build a DeleteStatement which can be reused binding args each time
 */
fun <T : Table> T.deleteWhere(where: () -> Op<Boolean>): DeleteStatement {
  return DeleteStatement(this, where())
}

private class DeleteStatementImpl(
  private val seed: StatementSeed
) : BaseStatement(), DeleteStatement {
  override val sql: String
    get() = seed.sql

  override val types: List<PersistentType<*>>
    get() = seed.types

  override fun execute(db: SQLiteDatabase, bindArgs: (ArgBindings) -> Unit): Long =
    getStatementAndTypes(db).executeDelete(bindArgs)
}
