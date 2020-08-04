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
import com.ealva.welite.db.expr.appendTo
import com.ealva.welite.db.table.NO_BIND
import com.ealva.welite.db.table.OnConflict
import com.ealva.welite.db.table.ParamBindings
import com.ealva.welite.db.table.Table

class UpdateBuilder<T : Table>(
  private val db: SQLiteDatabase,
  private val table: T,
  private val onConflict: OnConflict,
  private val bind: T.(ColumnValues) -> Unit
) {
  fun where(where: T.() -> Op<Boolean>): UpdateStatement {
    return UpdateStatement(db, table, onConflict, table.where(), bind)
  }
}

interface UpdateStatement {

  fun update(bindArgs: (ParamBindings) -> Unit = NO_BIND): Int

  companion object {
    operator fun <T : Table> invoke(
      db: SQLiteDatabase,
      table: T,
      onConflict: OnConflict = OnConflict.Unspecified,
      bind: T.(ColumnValues) -> Unit
    ): UpdateStatement {
      return UpdateStatementImpl(db, table, onConflict, null, bind)
    }

    operator fun <T : Table> invoke(
      db: SQLiteDatabase,
      table: T,
      onConflict: OnConflict = OnConflict.Unspecified,
      where: Op<Boolean>,
      bind: T.(ColumnValues) -> Unit
    ): UpdateStatement {
      return UpdateStatementImpl(db, table, onConflict, where, bind)
    }
  }
}

private class UpdateStatementImpl<T : Table>(
  db: SQLiteDatabase,
  private val table: T,
  private val onConflict: OnConflict,
  private val where: Op<Boolean>?,
  private val bind: T.(ColumnValues) -> Unit
) : BaseStatement(), UpdateStatement, ParamBindings {

  private val sqlBuilder: SqlBuilder = SqlBuilder().apply {
    append(onConflict.updateOr)
    append(table.identity.value)

    val columnValues = ColumnValues()
    table.bind(columnValues)

    columnValues.columnValueList.appendTo(this, prefix = " SET ") { columnValue ->
      append(columnValue)
    }

    where?.let { where ->
      append(" WHERE ")
      append(where)
    }
  }

  private val sql = sqlBuilder.toString()
  override val statement: SQLiteStatement = db.compileStatement(sql)
  override val types = sqlBuilder.types

  override fun update(bindArgs: (ParamBindings) -> Unit): Int {
    statement.clearBindings()
    if (bindArgs !== NO_BIND) bindArgs(this)
    return statement.executeUpdateDelete()
  }
}