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
import com.ealva.welite.db.expr.SqlBuilder
import com.ealva.welite.db.expr.appendTo
import com.ealva.welite.db.table.NO_BIND
import com.ealva.welite.db.table.OnConflict
import com.ealva.welite.db.table.ParamBindings
import com.ealva.welite.db.table.Table
import com.ealva.welite.db.table.WeLiteMarker
import com.ealva.welite.db.type.PersistentType

/**
 * InsertSeed contains the components that make up an InsertStatement, less the values the user
 * will bind into statement parameters. This abstraction was initially for possible statement
 * caching or so a values() method on the insert statement could ensure consistency
 */
private class InsertSeed<T : Table>(
  table: T,
  onConflict: OnConflict,
  bind: T.(ColumnValues) -> Unit
) {
  val columnValues = ColumnValues().apply { table.bind(this) }

  private val builder = SqlBuilder().apply {
    append(onConflict.insertOr)
    append(" INTO ")
    append(table.identity.value)
    columnValues.columnValueList.let { list ->
      list.appendTo(this, prefix = " (", postfix = ") ") { columnValue ->
        appendName(columnValue)
      }
      append(" VALUES ")
      list.appendTo(this, prefix = " (", postfix = ") ") { columnValue ->
        appendValue(columnValue)
      }
    }
  }

  val sql: String = builder.toString()
  val types: List<PersistentType<*>> = builder.types
}

/**
 * Represents a compiled SQLiteStatement with bind parameters that can be used to repeatedly insert
 * a row via the [insert] method.
 */
@WeLiteMarker
interface InsertStatement {

  /**
   * Insert a row binding any parameters with [bind]
   *
   * An InsertStatement contains a compiled SQLiteStatement and this method clears the bindings,
   * provides for new bound parameters via [bind], and executes the insert
   * @return the row ID of the last row inserted if successful else -1
   */
  fun insert(bind: (ParamBindings) -> Unit = NO_BIND): Long

  companion object {
    /**
     * Make an InsertStatement for the given [Table]. The statement may be saved and reused for
     * multiple inserts.
     */
    operator fun <T : Table> invoke(
      db: SQLiteDatabase,
      table: T,
      onConflict: OnConflict = OnConflict.Unspecified,
      bind: T.(ColumnValues) -> Unit
    ): InsertStatement {
      return InsertStatementImpl(db, InsertSeed(table, onConflict, bind))
    }
  }
}

private class InsertStatementImpl<T : Table>(
  db: SQLiteDatabase,
  insertSeed: InsertSeed<T>
) : BaseStatement(), InsertStatement {

  override val types: List<PersistentType<*>> = insertSeed.types
  override val statement: SQLiteStatement = db.compileStatement(insertSeed.sql)

  override fun insert(bind: (ParamBindings) -> Unit): Long {
    statement.clearBindings() // bind arguments are all set to null
    bind(this)
    return statement.executeInsert()
  }
}
