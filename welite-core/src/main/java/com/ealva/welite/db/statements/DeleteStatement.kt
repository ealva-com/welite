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

package com.ealva.welite.db.statements

import android.database.sqlite.SQLiteDatabase
import com.ealva.welite.db.expr.Op
import com.ealva.welite.db.table.ArgBindings
import com.ealva.welite.db.table.ExpressionToIndexMap
import com.ealva.welite.db.table.Table
import com.ealva.welite.db.type.PersistentType
import com.ealva.welite.db.type.StatementSeed
import com.ealva.welite.db.type.buildSql

public interface DeleteStatement : Statement {

  public companion object {
    /**
     * Make a DeleteStatement from a [table] and [where] clause
     */
    public operator fun invoke(table: Table, where: Op<Boolean>?): DeleteStatement =
      DeleteStatementImpl(statementSeed(table, where))

    /**
     * Make the StatementSeed from a [table] and [where] clause for a DeleteStatement
     */
    public fun statementSeed(table: Table, where: Op<Boolean>?): StatementSeed = buildSql {
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
public fun <T : Table> T.deleteWhere(where: () -> Op<Boolean>): DeleteStatement {
  return DeleteStatement(this, where())
}

private class DeleteStatementImpl(
  private val seed: StatementSeed
) : BaseStatement(), DeleteStatement {
  override val sql: String
    get() = seed.sql
  override val expressionToIndexMap: ExpressionToIndexMap
    get() = seed.expressionToIndexMap
  override val types: List<PersistentType<*>>
    get() = seed.types

  override fun doExecute(db: SQLiteDatabase, bindArgs: (ArgBindings) -> Unit): Long =
    getStatementAndTypes(db).executeDelete(bindArgs)
}
