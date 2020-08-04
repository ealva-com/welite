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
import android.database.sqlite.SQLiteStatement
import com.ealva.welite.db.expr.Op
import com.ealva.welite.db.expr.SqlBuilder
import com.ealva.welite.db.table.NO_BIND
import com.ealva.welite.db.table.ParamBindings
import com.ealva.welite.db.table.Table
import com.ealva.welite.db.type.PersistentType

interface DeleteStatement {

  fun delete(binding: (ParamBindings) -> Unit = NO_BIND): Int

  companion object {
    operator fun invoke(db: SQLiteDatabase, table: Table, where: Op<Boolean>?): DeleteStatement {
      return DeleteStatementImpl(db, table, where)
    }
  }
}

private class DeleteStatementImpl(
  db: SQLiteDatabase,
  private val table: Table,
  private val where: Op<Boolean>?
) : BaseStatement(), DeleteStatement, ParamBindings {

  private val builder = SqlBuilder().apply {
    append("DELETE FROM ")
    append(table.identity.value)
    if (where != null) {
      append(" WHERE ")
      append(where)
    }
  }

  val sql: String = builder.toString()
  override val types: List<PersistentType<*>> = builder.types
  override val statement: SQLiteStatement = db.compileStatement(sql)

  override fun delete(binding: (ParamBindings) -> Unit): Int {
    statement.clearBindings()
    binding(this)
    return statement.executeUpdateDelete()
  }
}
